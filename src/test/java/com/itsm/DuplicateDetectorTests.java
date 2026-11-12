package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.itsm.domain.ITService;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.Status;
import com.itsm.domain.Urgency;
import com.itsm.intelligence.DuplicateDetector;
import com.itsm.intelligence.DuplicateMatch;
import com.itsm.intelligence.Words;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Duplicate detection on plain objects, using the three examples from the
 * project brief: "Payment application unavailable", "Payment transaction
 * failing" and "Unable to make payment".
 */
class DuplicateDetectorTests {

    private static final String UNAVAILABLE = "Payment application unavailable";
    private static final String FAILING = "Payment transaction failing";
    private static final String UNABLE = "Unable to make payment";

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 1, 5, 12, 0);

    private final DuplicateDetector detector = new DuplicateDetector();
    private final ITService payment = new ITService("Payment Application", 3);
    private final ITService network = new ITService("Corporate Network", 2);

    /** An open incident with a code, as if it had been saved. */
    private Incident incident(long number, String title, ITService service, LocalDateTime reportedAt) {
        Incident incident = new Incident(title, "Details.", Fixtures.employee(), service, Impact.HIGH, Urgency.HIGH);
        ReflectionTestUtils.setField(incident, "createdAt", reportedAt);
        ReflectionTestUtils.setField(incident, "id", number);
        ReflectionTestUtils.setField(incident, "code", Incident.codeFor(number));
        return incident;
    }

    // ---- the brief's examples ----

    @ParameterizedTest(name = "\"{0}\" and \"{1}\"")
    @CsvSource({
            "Payment application unavailable, Payment transaction failing",
            "Payment application unavailable, Unable to make payment",
            "Payment transaction failing,     Unable to make payment"})
    void theBriefsThreeReportsAreSimilarEnoughToEachOther(String first, String second) {
        assertThat(detector.similarity(first, second)).isGreaterThanOrEqualTo(DuplicateDetector.THRESHOLD);
        assertThat(detector.similarity(second, first)).isEqualTo(detector.similarity(first, second)); // either way round
    }

    @Test
    void theBriefsSecondAndThirdReportsAreLinkedToAnEarlierOne() {
        Incident inc101 = incident(101, UNAVAILABLE, payment, NOON);
        Incident inc102 = incident(102, FAILING, payment, NOON.plusMinutes(6));
        Incident inc103 = incident(103, UNABLE, payment, NOON.plusMinutes(9));

        DuplicateMatch second = detector.findDuplicate(inc102, List.of(inc101)).orElseThrow();
        assertThat(second.original()).isSameAs(inc101);
        assertThat(second.similarity()).isCloseTo(0.5, within(0.001)); // {payment} of {payment, transaction}
        assertThat(second.explain())
                .isEqualTo("Possible duplicate of INC-0101 (similarity 0.50, same service, 6 min apart)");

        // The third is word for word about the same thing as the first once common words are gone.
        DuplicateMatch third = detector.findDuplicate(inc103, List.of(inc101, inc102)).orElseThrow();
        assertThat(third.original()).isSameAs(inc101);
        assertThat(third.similarity()).isEqualTo(1.0);
        assertThat(third.explain())
                .isEqualTo("Possible duplicate of INC-0101 (similarity 1.00, same service, 9 min apart)");
    }

    // ---- common words ----

    @Test
    void commonWordsAreRemovedBeforeComparing() {
        assertThat(Words.significantWords(UNAVAILABLE)).containsExactly("payment");
        assertThat(Words.significantWords(FAILING)).containsExactly("payment", "transaction");
        assertThat(Words.significantWords(UNABLE)).containsExactly("payment");
        assertThat(Words.significantWords("The VPN is NOT working, please help!")).containsExactly("vpn");
        assertThat(Words.significantWords("Payments are failing")).containsExactly("payment"); // plural counts the same
        assertThat(Words.significantWords("It is not working")).isEmpty();
    }

    // ---- the similarity number ----

    @Test
    void similarityIsTheSharedWordsOverAllTheWords() {
        assertThat(detector.similarity("vpn drops on wifi", "vpn drops on ethernet"))
                .isCloseTo(2.0 / 4, within(0.001)); // {vpn, drop} shared; {vpn, drop, wifi, ethernet} in all
        assertThat(detector.similarity("printer jammed", "printer jammed")).isEqualTo(1.0);
        assertThat(detector.similarity("printer jammed", "router restarting")).isEqualTo(0.0);
    }

    @Test
    void titlesWithNoSignificantWordsAreNeverSimilar() {
        assertThat(detector.similarity("It is not working", "It is not working")).isEqualTo(0.0);
        assertThat(detector.similarity("", "Payment failing")).isEqualTo(0.0);
        assertThat(detector.similarity(null, "Payment failing")).isEqualTo(0.0);
    }

    // ---- which incidents are compared ----

    @Test
    void belowTheThresholdThereIsNoMatch() {
        Incident earlier = incident(1, "Payment report export is slow on Mondays", payment, NOON);
        Incident fresh = incident(2, UNAVAILABLE, payment, NOON.plusMinutes(5)); // shares only "payment": 1 of 5

        assertThat(detector.similarity(earlier.getTitle(), fresh.getTitle())).isLessThan(DuplicateDetector.THRESHOLD);
        assertThat(detector.findDuplicate(fresh, List.of(earlier))).isEmpty();
    }

    @Test
    void anIncidentOnAnotherServiceIsNotADuplicate() {
        Incident onAnotherService = incident(1, UNAVAILABLE, network, NOON);
        Incident fresh = incident(2, UNAVAILABLE, payment, NOON.plusMinutes(5));

        assertThat(detector.findDuplicate(fresh, List.of(onAnotherService))).isEmpty();
    }

    @Test
    void onlyTheLastTwentyFourHoursCount() {
        Incident justInside = incident(1, UNAVAILABLE, payment, NOON.minusHours(24));
        Incident justOutside = incident(2, UNAVAILABLE, payment, NOON.minusHours(24).minusMinutes(1));
        Incident fresh = incident(3, UNABLE, payment, NOON);

        assertThat(detector.findDuplicate(fresh, List.of(justOutside))).isEmpty();
        assertThat(detector.findDuplicate(fresh, List.of(justOutside, justInside)).orElseThrow().original())
                .isSameAs(justInside);
        assertThat(detector.findDuplicate(fresh, List.of(justInside)).orElseThrow().explain())
                .endsWith("24 h apart)");
    }

    @Test
    void anIncidentThatIsNoLongerOpenIsNotCompared() {
        Incident resolved = Fixtures.incidentIn(Status.RESOLVED);
        ReflectionTestUtils.setField(resolved, "title", UNAVAILABLE);
        ReflectionTestUtils.setField(resolved, "service", payment);
        ReflectionTestUtils.setField(resolved, "createdAt", NOON);
        Incident fresh = incident(2, UNAVAILABLE, payment, NOON.plusMinutes(5));

        assertThat(detector.findDuplicate(fresh, List.of(resolved))).isEmpty();
    }

    @Test
    void theMostSimilarEarlierIncidentWins() {
        Incident somewhatAlike = incident(1, "Payment transaction failing for cards", payment, NOON);
        Incident veryAlike = incident(2, "Payment transaction failing", payment, NOON.plusMinutes(1));
        Incident fresh = incident(3, "Payment transactions fail", payment, NOON.plusMinutes(10));

        Optional<DuplicateMatch> match = detector.findDuplicate(fresh, List.of(somewhatAlike, veryAlike));

        assertThat(match.orElseThrow().original()).isSameAs(veryAlike);
    }

    @Test
    void anIncidentIsNeverItsOwnDuplicateAndLaterIncidentsDoNotCount() {
        Incident fresh = incident(1, UNAVAILABLE, payment, NOON);
        Incident later = incident(2, UNAVAILABLE, payment, NOON.plusMinutes(5));

        assertThat(detector.findDuplicate(fresh, List.of(fresh, later))).isEmpty();
    }
}
