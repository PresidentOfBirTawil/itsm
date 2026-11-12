package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import com.itsm.domain.AuditEntry;
import com.itsm.domain.Employee;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.IncidentAction;
import com.itsm.domain.Priority;
import com.itsm.domain.SlaPolicy;
import com.itsm.domain.SlaStatus;
import com.itsm.domain.Status;
import com.itsm.domain.Technician;
import com.itsm.domain.Urgency;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.UserRepository;
import com.itsm.service.IncidentService;
import com.itsm.sla.SlaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The SLA check as the minute job runs it, against real incidents. The tests
 * do not wait: they run the check with a time of their choosing ("five
 * minutes from now") to see what the job would do at that moment.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SlaMonitoringTests {

    @Autowired
    private SlaService slaService;
    @Autowired
    private IncidentService incidentService;
    @Autowired
    private UserRepository users;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;

    private Employee priya;
    private Technician sara;

    @BeforeEach
    void loadUsers() {
        priya = (Employee) users.findByEmail("emp1@itsm.local").orElseThrow();
        sara = (Technician) users.findByEmail("tech4@itsm.local").orElseThrow();
    }

    /** P1: respond within 5 minutes, resolve within 30. Assigned to Sara. */
    private Incident reportPaymentOutage() {
        return incidentService.report(priya, "Payment application is unavailable", "Details.",
                services.findByName("Payment Application").orElseThrow().getId(),
                categories.findByNameIgnoreCase("Application").orElseThrow().getId(), Impact.HIGH, Urgency.HIGH);
    }

    private List<IncidentAction> slaEntries(Incident incident) {
        return incidentService.history(incident).stream()
                .map(AuditEntry::getAction)
                .filter(action -> action == IncidentAction.SLA_AT_RISK || action == IncidentAction.SLA_BREACHED)
                .toList();
    }

    @Test
    void aNewIncidentIsOnTrack() {
        Incident incident = reportPaymentOutage();

        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(slaEntries(incident)).isEmpty();
    }

    @Test
    void theCheckWarnsAtSeventyFivePercentAndRecordsABreachAfterTheDeadlineEachOnce() {
        Incident incident = reportPaymentOutage();
        LocalDateTime reported = incident.getCreatedAt();

        slaService.checkOpenIncidents(reported.plusMinutes(3));               // 60% of the 5 response minutes
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(slaEntries(incident)).isEmpty();

        slaService.checkOpenIncidents(reported.plusMinutes(4));               // 80%
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.AT_RISK);
        assertThat(slaEntries(incident)).containsExactly(IncidentAction.SLA_AT_RISK);

        slaService.checkOpenIncidents(reported.plusMinutes(4).plusSeconds(30)); // the next run: nothing new
        assertThat(slaEntries(incident)).containsExactly(IncidentAction.SLA_AT_RISK);

        LocalDateTime noticed = reported.plusMinutes(6);                      // past the deadline
        slaService.checkOpenIncidents(noticed);
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.BREACHED);
        assertThat(incident.getResponseBreachedAt()).isEqualTo(noticed);
        assertThat(slaEntries(incident)).containsExactly(IncidentAction.SLA_AT_RISK, IncidentAction.SLA_BREACHED);

        slaService.checkOpenIncidents(reported.plusMinutes(7));               // later runs: breach recorded once
        slaService.checkOpenIncidents(reported.plusMinutes(8));
        assertThat(incident.getResponseBreachedAt()).isEqualTo(noticed);
        assertThat(slaEntries(incident)).containsExactly(IncidentAction.SLA_AT_RISK, IncidentAction.SLA_BREACHED);
    }

    @Test
    void theAuditEntrySaysWhichDeadlineItIsAbout() {
        Incident incident = reportPaymentOutage();

        slaService.checkOpenIncidents(incident.getCreatedAt().plusMinutes(6));

        AuditEntry breach = incidentService.history(incident).getLast();
        assertThat(breach.getAction()).isEqualTo(IncidentAction.SLA_BREACHED);
        assertThat(breach.getActorName()).isEqualTo("System");
        assertThat(breach.getNote()).contains("response deadline", "was missed");
    }

    @Test
    void anAcknowledgedIncidentIsMeasuredAgainstTheResolutionDeadline() {
        Incident incident = reportPaymentOutage();
        LocalDateTime reported = incident.getCreatedAt();
        incidentService.changeStatus(incident.getCode(), Status.ACKNOWLEDGED, sara, null);

        slaService.checkOpenIncidents(reported.plusMinutes(10)); // the response was met, 33% of resolution used
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);

        slaService.checkOpenIncidents(reported.plusMinutes(23)); // 77% of the 30 resolution minutes
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.AT_RISK);

        slaService.checkOpenIncidents(reported.plusMinutes(31));
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.BREACHED);
        assertThat(incident.getResponseBreachedAt()).isNull();
        assertThat(incident.getResolutionBreachedAt()).isEqualTo(reported.plusMinutes(31));
    }

    @Test
    void anIncidentWaitingForInformationIsPausedAndCannotBreach() {
        Incident incident = reportPaymentOutage();
        incidentService.changeStatus(incident.getCode(), Status.ACKNOWLEDGED, sara, null);
        incidentService.changeStatus(incident.getCode(), Status.IN_PROGRESS, sara, null);
        incidentService.changeStatus(incident.getCode(), Status.AWAITING_INFO, sara, "Which browser?");

        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.PAUSED); // at once, without waiting for the job

        slaService.checkOpenIncidents(incident.getCreatedAt().plusHours(10)); // far past the 30 minutes
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.PAUSED);
        assertThat(incident.isSlaBreached()).isFalse();

        incidentService.changeStatus(incident.getCode(), Status.IN_PROGRESS, priya, "Firefox.");
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(incident.getSlaPausedAt()).isNull();
    }

    @Test
    void resolvingInTimeMeetsTheSlaAndTheJobLeavesItAlone() {
        Incident incident = reportPaymentOutage();
        incidentService.changeStatus(incident.getCode(), Status.ACKNOWLEDGED, sara, null);
        incidentService.changeStatus(incident.getCode(), Status.IN_PROGRESS, sara, null);
        incidentService.changeStatus(incident.getCode(), Status.RESOLVED, sara, "Restarted the gateway.");

        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.MET);

        slaService.checkOpenIncidents(incident.getCreatedAt().plusDays(3));
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.MET);
        assertThat(slaEntries(incident)).isEmpty();
    }

    @Test
    void theCheckLooksAtEveryOpenIncidentAndReportsHowMany() {
        reportPaymentOutage();
        Incident second = incidentService.report(priya, "Laptop will not start", "Details.",
                services.findByName("Email & Collaboration").orElseThrow().getId(),
                categories.findByNameIgnoreCase("Hardware").orElseThrow().getId(), Impact.LOW, Urgency.LOW);

        int checked = slaService.checkOpenIncidents(LocalDateTime.now());

        assertThat(checked).isEqualTo(2);
        assertThat(second.getPriority()).isEqualTo(Priority.P4);
        assertThat(second.getSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);
    }

    // ---- demo SLA ----

    @Test
    void demoSlaIsOffUnlessAnAdministratorSwitchesItOn() {
        assertThat(slaService.isDemoMode()).isFalse();

        SlaPolicy normal = slaService.policyFor(Priority.P1);

        assertThat(normal.getResponseMinutes()).isEqualTo(5);
        assertThat(normal.getResolutionMinutes()).isEqualTo(30);
    }

    @Test
    void withDemoSlaOnNewIncidentsGetTheShortDeadlines() {
        slaService.setDemoMode(true);

        Incident incident = reportPaymentOutage();

        assertThat(incident.getResponseDue()).isEqualTo(incident.getCreatedAt().plusMinutes(1));
        assertThat(incident.getResolutionDue()).isEqualTo(incident.getCreatedAt().plusMinutes(3));

        // So a P1 that nobody acknowledges is breached little more than a minute after it was reported.
        slaService.checkOpenIncidents(incident.getCreatedAt().plusSeconds(61));
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    void switchingDemoSlaOffRestoresTheRealPoliciesForNewIncidentsOnly() {
        slaService.setDemoMode(true);
        Incident duringDemo = reportPaymentOutage();
        slaService.setDemoMode(false);

        Incident afterDemo = incidentService.report(priya, "Payment page shows an error", "Details.",
                services.findByName("Payment Application").orElseThrow().getId(),
                categories.findByNameIgnoreCase("Application").orElseThrow().getId(), Impact.HIGH, Urgency.HIGH);

        assertThat(afterDemo.getResponseDue()).isEqualTo(afterDemo.getCreatedAt().plusMinutes(5));
        assertThat(duringDemo.getResponseDue()).isEqualTo(duringDemo.getCreatedAt().plusMinutes(1)); // unchanged
    }

    @Test
    void theDemoDeadlinesCoverEveryPriority() {
        assertThat(slaService.demoPolicies()).extracting(SlaPolicy::getPriority)
                .containsExactly(Priority.P1, Priority.P2, Priority.P3, Priority.P4);
        assertThat(slaService.demoPolicies()).allMatch(policy -> policy.getResolutionMinutes() <= 10);
    }
}
