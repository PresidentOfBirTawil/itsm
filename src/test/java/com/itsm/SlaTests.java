package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import com.itsm.domain.SlaPolicy;
import com.itsm.domain.SlaSignal;
import com.itsm.domain.SlaStatus;
import com.itsm.domain.SlaTarget;
import com.itsm.domain.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SLA arithmetic, pausing and breach detection, on plain objects with exact
 * times (no database, no waiting).
 *
 * <p>Every test uses an incident reported at 09:00 with 60 minutes to respond
 * and 480 minutes (8 hours) to resolve. "+45" below means 09:45.</p>
 */
class SlaTests {

    private static final LocalDateTime REPORTED = LocalDateTime.of(2026, 1, 5, 9, 0);

    private Incident incident;

    @BeforeEach
    void reportAndAssign() {
        incident = Fixtures.incidentReportedAt(REPORTED);
        incident.prioritise(Fixtures.assessment(Priority.P3), new SlaPolicy(Priority.P3, 60, 480));
        incident.assignTo(Fixtures.technician(1, "Ravi Kumar", 5));
    }

    private static LocalDateTime at(long minutesAfterReport) {
        return REPORTED.plusMinutes(minutesAfterReport);
    }

    /** Moves the incident to IN_PROGRESS: acknowledged at +5, work started at +10. */
    private void startWork() {
        incident.transitionTo(Status.ACKNOWLEDGED, at(5));
        incident.transitionTo(Status.IN_PROGRESS, at(10));
    }

    // ---- the maths ----

    @Test
    void theDeadlinesComeFromThePolicy() {
        assertThat(incident.getResponseDue()).isEqualTo(at(60));
        assertThat(incident.getResolutionDue()).isEqualTo(at(480));
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);
    }

    @Test
    void theShareOfTimeUsedGrowsFromZeroToOneAtTheDeadline() {
        SlaTarget response = incident.responseTarget();

        assertThat(response.fractionUsed(at(0))).isEqualTo(0.0);
        assertThat(response.fractionUsed(at(30))).isEqualTo(0.5);
        assertThat(response.fractionUsed(at(45))).isEqualTo(0.75);
        assertThat(response.fractionUsed(at(60))).isEqualTo(1.0);
        assertThat(response.fractionUsed(at(90))).isEqualTo(1.5);

        assertThat(incident.resolutionTarget().fractionUsed(at(120))).isEqualTo(0.25);
        assertThat(incident.resolutionTarget().remaining(at(120))).isEqualTo(Duration.ofMinutes(360));
    }

    @Test
    void aClockThatHasBeenMetStopsAtThatMoment() {
        incident.transitionTo(Status.ACKNOWLEDGED, at(20));
        SlaTarget response = incident.responseTarget();

        assertThat(response.isMet()).isTrue();
        assertThat(response.isRunning()).isFalse();
        assertThat(response.fractionUsed(at(500))).isCloseTo(20.0 / 60, within(0.0001)); // frozen at +20
        assertThat(response.isLate(at(500))).isFalse();
    }

    // ---- at risk ----

    @Test
    void justUnderSeventyFivePercentIsStillOnTrack() {
        List<SlaSignal> signals = incident.refreshSla(at(44));

        assertThat(signals).isEmpty();
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);
    }

    @Test
    void atSeventyFivePercentOfTheResponseTimeTheIncidentIsAtRiskAndAWarningIsRaisedOnce() {
        List<SlaSignal> first = incident.refreshSla(at(45));

        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.AT_RISK);
        assertThat(first).hasSize(1);
        assertThat(first.getFirst().type()).isEqualTo(SlaSignal.Type.AT_RISK);
        assertThat(first.getFirst().target().name()).isEqualTo("response");

        assertThat(incident.refreshSla(at(50))).isEmpty(); // still at risk: no second warning
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.AT_RISK);
    }

    @Test
    void acknowledgingInTimeMeetsTheResponseAndTheIncidentIsOnTrackAgain() {
        incident.refreshSla(at(45));
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.AT_RISK);

        incident.transitionTo(Status.ACKNOWLEDGED, at(50));
        List<SlaSignal> signals = incident.refreshSla(at(55));

        assertThat(signals).isEmpty();
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.ON_TRACK); // only 55 of 480 resolution minutes used
        assertThat(incident.getResponseBreachedAt()).isNull();
    }

    @Test
    void atSeventyFivePercentOfTheResolutionTimeAWarningIsRaisedForTheResolution() {
        startWork();
        assertThat(incident.refreshSla(at(359))).isEmpty();

        List<SlaSignal> signals = incident.refreshSla(at(360)); // 360 of 480 = 75%

        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.AT_RISK);
        assertThat(signals).extracting(SlaSignal::type).containsExactly(SlaSignal.Type.AT_RISK);
        assertThat(signals.getFirst().target().name()).isEqualTo("resolution");
    }

    // ---- breach ----

    @Test
    void exactlyAtTheDeadlineIsNotYetABreach() {
        incident.refreshSla(at(60));

        assertThat(incident.isSlaBreached()).isFalse();
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.AT_RISK);
    }

    @Test
    void pastTheResponseDeadlineWithoutAcknowledgementIsABreachRecordedOnce() {
        LocalDateTime noticed = at(60).plusSeconds(1);

        List<SlaSignal> signals = incident.refreshSla(noticed);

        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.BREACHED);
        assertThat(incident.getResponseBreachedAt()).isEqualTo(noticed);
        assertThat(signals).extracting(SlaSignal::type).containsExactly(SlaSignal.Type.BREACHED);
        assertThat(signals.getFirst().target().name()).isEqualTo("response");

        // Checking again later reports nothing new and does not move the recorded time.
        assertThat(incident.refreshSla(at(90))).isEmpty();
        assertThat(incident.refreshSla(at(200))).isEmpty();
        assertThat(incident.getResponseBreachedAt()).isEqualTo(noticed);
    }

    @Test
    void acknowledgingLateIsStillABreach() {
        incident.transitionTo(Status.ACKNOWLEDGED, at(70)); // ten minutes too late

        List<SlaSignal> signals = incident.refreshSla(at(71));

        assertThat(signals).extracting(SlaSignal::type).containsExactly(SlaSignal.Type.BREACHED);
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    void pastTheResolutionDeadlineWithoutAResolutionIsABreach() {
        startWork();
        incident.refreshSla(at(400)); // at risk first

        List<SlaSignal> signals = incident.refreshSla(at(481));

        assertThat(signals).extracting(SlaSignal::type).containsExactly(SlaSignal.Type.BREACHED);
        assertThat(signals.getFirst().target().name()).isEqualTo("resolution");
        assertThat(incident.getResolutionBreachedAt()).isEqualTo(at(481));
        assertThat(incident.getResponseBreachedAt()).isNull();
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    void bothDeadlinesCanBeMissedAndEachIsReportedOnce() {
        List<SlaSignal> first = incident.refreshSla(at(61));
        List<SlaSignal> second = incident.refreshSla(at(481));
        List<SlaSignal> third = incident.refreshSla(at(600));

        assertThat(first).extracting(signal -> signal.target().name()).containsExactly("response");
        assertThat(second).extracting(signal -> signal.target().name()).containsExactly("resolution");
        assertThat(third).isEmpty();
    }

    @Test
    void aBreachIsNeverForgottenEvenAfterTheIncidentIsResolved() {
        startWork();
        incident.refreshSla(at(481));

        incident.recordResolution("Restarted the service.");

        incident.transitionTo(Status.RESOLVED, at(490));
        incident.refreshSla(at(491));

        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    void resolvingInTimeMeetsTheSla() {
        startWork();
        incident.recordResolution("Restarted the service.");
        incident.transitionTo(Status.RESOLVED, at(100));

        assertThat(incident.refreshSla(at(101))).isEmpty();
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.MET);

        assertThat(incident.refreshSla(at(5000))).isEmpty(); // long after the deadline: it was met, so no breach
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.MET);
    }

    // ---- pausing ----

    @Test
    void waitingForInformationPausesTheResolutionClock() {
        startWork();
        incident.transitionTo(Status.AWAITING_INFO, at(100));

        assertThat(incident.refreshSla(at(100))).isEmpty();
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.PAUSED);
        assertThat(incident.getSlaPausedAt()).isEqualTo(at(100));

        // Two days later, far past the original deadline: still paused, nothing breached, nothing used.
        assertThat(incident.refreshSla(at(2880))).isEmpty();
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.PAUSED);
        assertThat(incident.isSlaBreached()).isFalse();
        assertThat(incident.resolutionTarget().used(at(2880))).isEqualTo(Duration.ofMinutes(100));
    }

    @Test
    void resumingPushesTheDeadlineBackByThePausedDuration() {
        startWork();
        incident.transitionTo(Status.AWAITING_INFO, at(100));
        incident.transitionTo(Status.IN_PROGRESS, at(280)); // waited 180 minutes

        assertThat(incident.getResolutionDue()).isEqualTo(at(480 + 180));
        assertThat(incident.getSlaPausedAt()).isNull();
        assertThat(incident.getSlaPausedSeconds()).isEqualTo(180 * 60);
        assertThat(incident.getResponseDue()).isEqualTo(at(60)); // the response deadline is never moved

        // The time spent waiting does not count as time used: 100 working minutes of 480.
        assertThat(incident.resolutionTarget().fractionUsed(at(280))).isCloseTo(100.0 / 480, within(0.0001));
        incident.refreshSla(at(280));
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);
    }

    @Test
    void afterAPauseTheWarningAndTheBreachComeLaterByTheSameAmount() {
        startWork();
        incident.transitionTo(Status.AWAITING_INFO, at(100));
        incident.transitionTo(Status.IN_PROGRESS, at(280)); // paused 180 minutes

        assertThat(incident.refreshSla(at(539))).isEmpty();                 // would have been at risk at +360
        assertThat(incident.refreshSla(at(540))).extracting(SlaSignal::type)
                .containsExactly(SlaSignal.Type.AT_RISK);                    // 360 working minutes = 75%
        assertThat(incident.refreshSla(at(660))).isEmpty();                 // the new deadline, not yet passed
        assertThat(incident.refreshSla(at(661))).extracting(SlaSignal::type)
                .containsExactly(SlaSignal.Type.BREACHED);
    }

    @Test
    void severalPausesAddUp() {
        startWork();
        incident.transitionTo(Status.AWAITING_INFO, at(100));
        incident.transitionTo(Status.IN_PROGRESS, at(130));  // 30 minutes
        incident.transitionTo(Status.AWAITING_INFO, at(200));
        incident.transitionTo(Status.IN_PROGRESS, at(290));  // 90 minutes

        assertThat(incident.getSlaPausedSeconds()).isEqualTo(120 * 60);
        assertThat(incident.getResolutionDue()).isEqualTo(at(480 + 120));
    }

    @Test
    void reopeningDoesNotCountTheTimeTheIncidentStoodAsResolved() {
        startWork();
        incident.recordResolution("Restarted the service.");
        incident.transitionTo(Status.RESOLVED, at(100));
        incident.refreshSla(at(100));
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.MET);

        incident.transitionTo(Status.IN_PROGRESS, at(160)); // reopened an hour later

        assertThat(incident.getResolvedAt()).isNull();
        assertThat(incident.getResolutionDue()).isEqualTo(at(480 + 60));
        assertThat(incident.refreshSla(at(160))).isEmpty();
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);
    }

    @Test
    void changingThePriorityAfterAPauseKeepsTheTimeAddedForThePause() {
        startWork();
        incident.transitionTo(Status.AWAITING_INFO, at(100));
        incident.transitionTo(Status.IN_PROGRESS, at(160)); // paused 60 minutes

        incident.prioritise(Fixtures.assessment(Priority.P2), new SlaPolicy(Priority.P2, 15, 120));

        assertThat(incident.getResolutionDue()).isEqualTo(at(120 + 60));
    }

    // ---- no deadlines, no SLA ----

    @Test
    void anIncidentWithoutAPriorityHasNoSlaYet() {
        Incident unprioritised = Fixtures.incidentReportedAt(REPORTED);

        assertThat(unprioritised.refreshSla(at(10_000))).isEmpty();
        assertThat(unprioritised.getSlaStatus()).isNull();
        assertThat(unprioritised.isSlaBreached()).isFalse();
    }
}
