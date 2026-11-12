package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import com.itsm.domain.SlaPolicy;
import com.itsm.domain.Status;
import com.itsm.intelligence.EscalationPolicy;
import com.itsm.intelligence.SlaRisk;
import com.itsm.intelligence.SlaRiskRanker;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The escalation rule and the SLA risk list, on plain objects with exact times.
 * Incidents are reported at 09:00; "+6" means 09:06.
 */
class EscalationAndRiskTests {

    private static final LocalDateTime REPORTED = LocalDateTime.of(2026, 1, 5, 9, 0);

    private final EscalationPolicy policy = new EscalationPolicy();
    private final SlaRiskRanker ranker = new SlaRiskRanker();

    private static LocalDateTime at(long minutesAfterReport) {
        return REPORTED.plusMinutes(minutesAfterReport);
    }

    /** An assigned incident with the given priority and deadlines, reported at 09:00. */
    private static Incident incident(Priority priority, int responseMinutes, int resolutionMinutes, long number) {
        Incident incident = Fixtures.incidentReportedAt(REPORTED);
        incident.prioritise(Fixtures.assessment(priority), new SlaPolicy(priority, responseMinutes, resolutionMinutes));
        incident.assignTo(Fixtures.technician(1, "Ravi Kumar", 5));
        ReflectionTestUtils.setField(incident, "code", Incident.codeFor(number));
        return incident;
    }

    // ---- automatic escalation ----

    @Test
    void aP1NobodyHasAcknowledgedByItsResponseDeadlineIsEscalated() {
        Incident p1 = incident(Priority.P1, 5, 30, 1);

        assertThat(policy.shouldEscalate(p1, at(4))).isFalse();
        assertThat(policy.shouldEscalate(p1, at(5))).isFalse();              // exactly at the deadline: not yet
        assertThat(policy.shouldEscalate(p1, at(5).plusSeconds(1))).isTrue();
        assertThat(policy.shouldEscalate(p1, at(60))).isTrue();
    }

    @Test
    void theExplanationSaysWhy() {
        Incident p1 = incident(Priority.P1, 5, 30, 1);

        assertThat(policy.explain(p1))
                .contains("P1", "not acknowledged", "response time of 5 min", "assigned to Ravi Kumar");
    }

    @Test
    void anAcknowledgedP1IsNotEscalated() {
        Incident p1 = incident(Priority.P1, 5, 30, 1);
        p1.transitionTo(Status.ACKNOWLEDGED, at(3));

        assertThat(policy.shouldEscalate(p1, at(60))).isFalse();
    }

    @Test
    void lowerPrioritiesAreNotEscalatedHoweverLate() {
        for (Priority priority : List.of(Priority.P2, Priority.P3, Priority.P4)) {
            assertThat(policy.shouldEscalate(incident(priority, 5, 30, 1), at(600))).isFalse();
        }
    }

    @Test
    void anIncidentIsEscalatedOnlyOnce() {
        Incident p1 = incident(Priority.P1, 5, 30, 1);
        assertThat(p1.isEscalated()).isFalse();

        p1.escalate(at(6));
        assertThat(p1.isEscalated()).isTrue();
        assertThat(policy.shouldEscalate(p1, at(7))).isFalse();

        p1.escalate(at(20)); // a second call changes nothing
        assertThat(p1.getEscalatedAt()).isEqualTo(at(6));
    }

    @Test
    void anUnassignedP1IsEscalatedToo() {
        Incident unassigned = Fixtures.incidentReportedAt(REPORTED);
        unassigned.prioritise(Fixtures.assessment(Priority.P1), new SlaPolicy(Priority.P1, 5, 30));

        assertThat(policy.shouldEscalate(unassigned, at(6))).isTrue();
        assertThat(policy.explain(unassigned)).contains("No technician is assigned");
    }

    // ---- SLA risk list ----

    @Test
    void theIncidentWithTheLeastTimeLeftComesFirst() {
        Incident relaxed = incident(Priority.P4, 240, 1440, 1);   // response due at +240
        Incident urgent = incident(Priority.P1, 5, 30, 2);        // response due at +5
        Incident middling = incident(Priority.P2, 15, 120, 3);    // response due at +15

        List<SlaRisk> risks = ranker.rank(List.of(relaxed, urgent, middling), at(3), 5);

        assertThat(risks).extracting(risk -> risk.incident().getCode())
                .containsExactly("INC-0002", "INC-0003", "INC-0001");
        assertThat(risks.getFirst().target()).isEqualTo("response");
        assertThat(risks.getFirst().remaining()).isEqualTo(Duration.ofMinutes(2));
        assertThat(risks.getFirst().percentUsed()).isEqualTo(60);
        assertThat(risks.getFirst().explain())
                .isEqualTo("Response due in 2 min · 60% of the time used · not acknowledged yet");
    }

    @Test
    void onceAcknowledgedTheResolutionDeadlineIsTheOneThatCounts() {
        Incident acknowledged = incident(Priority.P1, 5, 30, 1);
        acknowledged.transitionTo(Status.ACKNOWLEDGED, at(2));

        SlaRisk risk = ranker.rank(List.of(acknowledged), at(21), 5).getFirst();

        assertThat(risk.target()).isEqualTo("resolution");
        assertThat(risk.explain()).isEqualTo("Resolution due in 9 min · 70% of the time used");
    }

    @Test
    void pausedFinishedAndAlreadyBreachedDeadlinesAreLeftOut() {
        Incident paused = incident(Priority.P1, 5, 30, 1);
        paused.transitionTo(Status.ACKNOWLEDGED, at(1));
        paused.transitionTo(Status.IN_PROGRESS, at(2));
        paused.transitionTo(Status.AWAITING_INFO, at(3));

        Incident resolved = incident(Priority.P1, 5, 30, 2);
        resolved.transitionTo(Status.ACKNOWLEDGED, at(1));
        resolved.transitionTo(Status.IN_PROGRESS, at(2));
        resolved.recordResolution("Fixed.");
        resolved.transitionTo(Status.RESOLVED, at(3));

        Incident alreadyLate = incident(Priority.P1, 5, 30, 3);  // both deadlines have passed by +40
        Incident unprioritised = Fixtures.incidentReportedAt(REPORTED);

        assertThat(ranker.rank(List.of(paused, resolved, alreadyLate, unprioritised), at(40), 5)).isEmpty();
    }

    @Test
    void anIncidentThatMissedItsResponseStillHasAResolutionDeadlineToMiss() {
        Incident lateResponse = incident(Priority.P1, 5, 30, 1);

        SlaRisk risk = ranker.rank(List.of(lateResponse), at(10), 5).getFirst(); // response missed at +5

        assertThat(risk.target()).isEqualTo("resolution");
        assertThat(risk.remaining()).isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    void theListIsCutOffAtTheLimit() {
        List<Incident> many = List.of(incident(Priority.P1, 5, 30, 1), incident(Priority.P1, 5, 30, 2),
                incident(Priority.P1, 5, 30, 3));

        assertThat(ranker.rank(many, at(1), 2)).hasSize(2);
    }
}
