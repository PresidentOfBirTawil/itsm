package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.itsm.domain.Category;
import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import com.itsm.domain.SlaPolicy;
import com.itsm.domain.Technician;
import com.itsm.sla.SlaForecast;
import com.itsm.sla.SlaPredictor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Predictive SLA: the forecast of when an open incident will be resolved and
 * how likely it is to miss its deadline, from known past durations. P3 here:
 * 8 hours (480 minutes) to resolve. Plain objects.
 */
class SlaPredictorTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 2, 12, 0);

    private final SlaPredictor predictor = new SlaPredictor(null);
    private final Category network = Fixtures.category("Network");
    private final Category hardware = Fixtures.category("Hardware");
    private final Technician ravi = Fixtures.technician(1, "Ravi Kumar", 5);
    private final Technician meera = Fixtures.technician(2, "Meera Nair", 5);
    private long nextId = 100;

    /** A finished P3 incident that took this many working minutes. */
    private Incident past(Category category, long minutes, Technician technician) {
        Incident incident = Fixtures.incidentReportedAt(NOW.minusDays(20));
        incident.setCategory(category);
        incident.prioritise(Fixtures.assessment(Priority.P3), new SlaPolicy(Priority.P3, 60, 480));
        ReflectionTestUtils.setField(incident, "id", nextId++);
        ReflectionTestUtils.setField(incident, "assignee", technician);
        ReflectionTestUtils.setField(incident, "resolvedAt", NOW.minusDays(20).plusMinutes(minutes));
        return incident;
    }

    /** An open P3 incident reported this many minutes ago. */
    private Incident open(Category category, long minutesAgo, Technician technician) {
        Incident incident = Fixtures.incidentReportedAt(NOW.minusMinutes(minutesAgo));
        incident.setCategory(category);
        incident.prioritise(Fixtures.assessment(Priority.P3), new SlaPolicy(Priority.P3, 60, 480));
        ReflectionTestUtils.setField(incident, "id", 1L);
        if (technician != null) {
            incident.assignTo(technician);
        }
        return incident;
    }

    private List<Incident> networkHistory(long... minutes) {
        List<Incident> history = new ArrayList<>();
        for (long m : minutes) {
            history.add(past(network, m, null));
        }
        return history;
    }

    @Test
    void withTooLittleHistoryThereIsNoForecast() {
        assertThat(predictor.forecast(open(network, 10, null), networkHistory(100, 200, 300), NOW)).isEmpty();
    }

    @Test
    void theExpectedTimeIsTheMedianOfSimilarIncidents() {
        Optional<SlaForecast> forecast = predictor.forecast(open(network, 0, null),
                networkHistory(100, 200, 300, 400, 600), NOW);

        assertThat(forecast).isPresent();
        assertThat(forecast.get().expectedResolution()).isEqualTo(NOW.plusMinutes(300));
        assertThat(forecast.get().breachPercent()).isEqualTo(20);       // 1 of 5 took longer than 480 minutes
        assertThat(forecast.get().sampleSize()).isEqualTo(5);
        assertThat(forecast.get().basis()).isEqualTo("5 resolved P3 Network incidents");
    }

    @Test
    void timeAlreadySpentChangesTheOdds() {
        // After 350 minutes only the incidents that took longer are relevant: 400 and 600, so 1 in 2 is late.
        SlaForecast forecast = predictor.forecast(open(network, 350, null),
                networkHistory(100, 200, 300, 400, 600), NOW).orElseThrow();

        assertThat(forecast.breachPercent()).isEqualTo(50);
        assertThat(forecast.expectedResolution()).isEqualTo(NOW.plusMinutes(150));   // median 500, 350 already used
        assertThat(forecast.level()).isEqualTo("medium");
    }

    @Test
    void withoutEnoughOfTheSameCategoryItUsesTheSamePriority() {
        List<Incident> history = networkHistory(100, 200);
        history.add(past(hardware, 300, null));
        history.add(past(hardware, 400, null));

        SlaForecast forecast = predictor.forecast(open(network, 0, null), history, NOW).orElseThrow();

        assertThat(forecast.basis()).isEqualTo("4 resolved P3 incidents");
    }

    @Test
    void longerThanEverySimilarIncidentMeansItIsAboutToBeLate() {
        SlaForecast forecast = predictor.forecast(open(network, 700, null),
                networkHistory(100, 200, 300, 400), NOW).orElseThrow();

        assertThat(forecast.breachPercent()).isEqualTo(100);
        assertThat(forecast.level()).isEqualTo("high");
    }

    @Test
    void aTechnicianWhoIsUsuallyQuickerGetsAnEarlierForecast() {
        List<Incident> history = new ArrayList<>();
        for (long m : new long[] {100, 120, 140}) {
            history.add(past(network, m, ravi));          // Ravi: quick
        }
        for (long m : new long[] {400, 420, 440}) {
            history.add(past(network, m, meera));         // Meera: slower
        }

        SlaForecast withRavi = predictor.forecast(open(network, 0, ravi), history, NOW).orElseThrow();
        SlaForecast withMeera = predictor.forecast(open(network, 0, meera), history, NOW).orElseThrow();

        assertThat(withRavi.expectedResolution()).isBefore(withMeera.expectedResolution());
        assertThat(withRavi.basis()).contains("adjusted for Ravi Kumar's usual speed");
    }

    @Test
    void aFinishedIncidentHasNoForecast() {
        assertThat(predictor.forecast(Fixtures.incidentIn(com.itsm.domain.Status.RESOLVED),
                networkHistory(100, 200, 300, 400), NOW)).isEmpty();
    }
}
