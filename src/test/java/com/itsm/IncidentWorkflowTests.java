package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.itsm.assignment.AssignmentService;
import com.itsm.assignment.LeastLoadedStrategy;
import com.itsm.assignment.RoundRobinStrategy;
import com.itsm.assignment.SkillBasedStrategy;
import com.itsm.assignment.TechnicianUnavailableException;
import com.itsm.domain.Administrator;
import com.itsm.domain.AuditEntry;
import com.itsm.domain.Employee;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.IncidentAction;
import com.itsm.domain.Priority;
import com.itsm.domain.Status;
import com.itsm.domain.Technician;
import com.itsm.domain.Urgency;
import com.itsm.domain.state.InvalidStateTransitionException;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.SlaPolicyRepository;
import com.itsm.repository.UserRepository;
import com.itsm.service.IncidentFactory;
import com.itsm.service.IncidentNotFoundException;
import com.itsm.service.IncidentService;
import com.itsm.sla.MissingSlaPolicyException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reporting, prioritising, assigning and working on incidents, through the
 * real services and the seeded demo data. Each test is rolled back afterwards.
 *
 * <p>Seeded technicians: Ravi (Network, Hardware; max 5), Meera (Database,
 * Storage, Cloud; max 4), Arjun (Security, Authentication, Network; max 3),
 * Sara (Software, Application, Cloud; max 5).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IncidentWorkflowTests {

    @Autowired
    private IncidentService incidentService;
    @Autowired
    private AssignmentService assignmentService;
    @Autowired
    private IncidentFactory incidentFactory;
    @Autowired
    private IncidentRepository incidents;
    @Autowired
    private UserRepository users;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private SlaPolicyRepository slaPolicies;
    @Autowired
    private EntityManager entityManager;

    private Employee priya;
    private Employee rahul;
    private Administrator admin;
    private Technician ravi;
    private Technician meera;
    private Technician arjun;
    private Technician sara;

    @BeforeEach
    void loadUsers() {
        priya = (Employee) users.findByEmail("emp1@itsm.local").orElseThrow();
        rahul = (Employee) users.findByEmail("emp2@itsm.local").orElseThrow();
        admin = (Administrator) users.findByEmail("admin@itsm.local").orElseThrow();
        ravi = (Technician) users.findByEmail("tech1@itsm.local").orElseThrow();
        meera = (Technician) users.findByEmail("tech2@itsm.local").orElseThrow();
        arjun = (Technician) users.findByEmail("tech3@itsm.local").orElseThrow();
        sara = (Technician) users.findByEmail("tech4@itsm.local").orElseThrow();
    }

    private Incident report(Employee reporter, String service, String category, Impact impact, Urgency urgency) {
        return incidentService.report(reporter, "Something is wrong with " + service, "Details.",
                services.findByName(service).orElseThrow().getId(),
                categories.findByNameIgnoreCase(category).orElseThrow().getId(), impact, urgency);
    }

    /** The brief's example: the payment application is down. Lands with Sara, the only one with Application. */
    private Incident reportPaymentOutage() {
        return report(priya, "Payment Application", "Application", Impact.HIGH, Urgency.HIGH);
    }

    private List<IncidentAction> actionsOf(Incident incident) {
        return incidentService.history(incident).stream().map(AuditEntry::getAction).toList();
    }

    // ---- reporting ----

    @Test
    void reportingCreatesPrioritisesAndAssignsInOneGo() {
        Incident incident = reportPaymentOutage();

        assertThat(incident.getCode()).matches("INC-\\d{4,}");
        assertThat(incident.getReporter()).isEqualTo(priya);

        // impact 3 × urgency 3 × criticality 3 = 27, which is P1: respond in 5 minutes, resolve in 30
        assertThat(incident.getAssessment().score()).isEqualTo(27);
        assertThat(incident.getPriority()).isEqualTo(Priority.P1);
        assertThat(incident.getResponseDue()).isEqualTo(incident.getCreatedAt().plusMinutes(5));
        assertThat(incident.getResolutionDue()).isEqualTo(incident.getCreatedAt().plusMinutes(30));

        assertThat(incident.getStatus()).isEqualTo(Status.ASSIGNED);
        assertThat(incident.getAssignee()).isEqualTo(sara);
    }

    @Test
    void everyStepOfReportingIsWrittenToTheAuditTrail() {
        Incident incident = reportPaymentOutage();

        List<AuditEntry> history = incidentService.history(incident);
        assertThat(history).extracting(AuditEntry::getAction).containsExactly(
                IncidentAction.REPORTED, IncidentAction.PRIORITISED, IncidentAction.ASSIGNED);

        AuditEntry reported = history.get(0);
        assertThat(reported.getActor()).isEqualTo(priya);
        assertThat(reported.getOldStatus()).isNull();
        assertThat(reported.getNewStatus()).isEqualTo(Status.NEW);
        assertThat(reported.getOccurredAt()).isNotNull();

        AuditEntry prioritised = history.get(1);
        assertThat(prioritised.getActor()).isNull(); // the system did it
        assertThat(prioritised.getActorName()).isEqualTo("System");
        assertThat(prioritised.getOldStatus()).isEqualTo(Status.NEW);
        assertThat(prioritised.getNewStatus()).isEqualTo(Status.CLASSIFIED);
        assertThat(prioritised.getNote()).contains("P1", "3 × 3 × 3 = 27");

        AuditEntry assigned = history.get(2);
        assertThat(assigned.getOldStatus()).isEqualTo(Status.CLASSIFIED);
        assertThat(assigned.getNewStatus()).isEqualTo(Status.ASSIGNED);
        assertThat(assigned.getNote()).contains("Sara Thomas", "Skill-based");
    }

    @Test
    void theFactoryMakesNewIncidentsWithConsecutiveCodes() {
        var service = services.findByName("Corporate Network").orElseThrow();
        var category = categories.findByNameIgnoreCase("Network").orElseThrow();

        Incident first = incidentFactory.create(priya, "First", "Details.", service, category, Impact.LOW, Urgency.LOW);
        Incident second = incidentFactory.create(priya, "Second", "Details.", service, category, Impact.LOW, Urgency.LOW);

        assertThat(first.getCode()).matches("INC-\\d{4,}").isEqualTo(Incident.codeFor(first.getId()));
        assertThat(second.getCode()).isEqualTo(Incident.codeFor(first.getId() + 1));
        assertThat(first.getStatus()).isEqualTo(Status.NEW);
        assertThat(first.getPriority()).isNull();
        assertThat(first.getAssignee()).isNull();
        assertThat(first.getCategory()).isEqualTo(category);
    }

    @Test
    void theFactoryRefusesAnIncidentWithAMissingPart() {
        var service = services.findByName("Corporate Network").orElseThrow();
        var category = categories.findByNameIgnoreCase("Network").orElseThrow();

        assertThatThrownBy(() -> incidentFactory.create(null, "T", "D", service, category, Impact.LOW, Urgency.LOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> incidentFactory.create(priya, "T", "D", null, category, Impact.LOW, Urgency.LOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> incidentFactory.create(priya, "T", "D", service, null, Impact.LOW, Urgency.LOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportingFailsCleanlyWhenThePriorityHasNoSlaPolicy() {
        slaPolicies.delete(slaPolicies.findByPriority(Priority.P1).orElseThrow());
        entityManager.flush();

        assertThatThrownBy(this::reportPaymentOutage)
                .isInstanceOf(MissingSlaPolicyException.class)
                .hasMessageContaining("P1");
    }

    // ---- automatic assignment ----

    @Test
    void skillBasedIsTheStrategyUntilAnAdministratorChoosesAnother() {
        assertThat(assignmentService.getActiveStrategy()).isInstanceOf(SkillBasedStrategy.class);
    }

    @Test
    void skillBasedSharesWorkBetweenTheTechniciansWhoHaveTheSkill() {
        // Ravi and Arjun both have Network.
        Incident first = report(priya, "Corporate Network", "Network", Impact.LOW, Urgency.LOW);
        Incident second = report(priya, "Corporate Network", "Network", Impact.LOW, Urgency.LOW);

        assertThat(first.getAssignee()).isEqualTo(arjun);  // both idle: settled by name
        assertThat(second.getAssignee()).isEqualTo(ravi);  // Arjun now has one, Ravi none
    }

    @Test
    void whenNobodyFitsTheIncidentStaysClassifiedAndAdministratorsAreWarned() {
        arjun.setAvailable(false); // the only technician with Security

        Incident incident = report(priya, "Authentication Service", "Security", Impact.HIGH, Urgency.HIGH);

        assertThat(incident.getStatus()).isEqualTo(Status.CLASSIFIED);
        assertThat(incident.getAssignee()).isNull();
        assertThat(incident.getPriority()).isEqualTo(Priority.P1); // it is still prioritised and has its deadlines

        AuditEntry failure = incidentService.history(incident).getLast();
        assertThat(failure.getAction()).isEqualTo(IncidentAction.ASSIGNMENT_FAILED);
        assertThat(failure.getOldStatus()).isEqualTo(Status.CLASSIFIED);
        assertThat(failure.getNewStatus()).isEqualTo(Status.CLASSIFIED);
        assertThat(failure.getNote()).contains("Security", "administrator");

        assertThat(assignmentService.waitingForTechnician()).containsExactly(incident);
        assertThat(assignmentService.countWaitingForTechnician()).isEqualTo(1);
    }

    @Test
    void aTechnicianAtMaximumWorkloadGetsNoMore() {
        // Arjun is the only technician with Security and his maximum is 3.
        for (int i = 0; i < 3; i++) {
            assertThat(report(priya, "Authentication Service", "Security", Impact.LOW, Urgency.LOW).getAssignee())
                    .isEqualTo(arjun);
        }

        Incident fourth = report(priya, "Authentication Service", "Security", Impact.LOW, Urgency.LOW);

        assertThat(fourth.getStatus()).isEqualTo(Status.CLASSIFIED);
        assertThat(fourth.getAssignee()).isNull();
        assertThat(incidentService.history(fourth).getLast().getNote()).contains("Security");
    }

    @Test
    void resolvedIncidentsNoLongerCountTowardsTheWorkload() {
        Incident first = report(priya, "Authentication Service", "Security", Impact.LOW, Urgency.LOW);
        report(priya, "Authentication Service", "Security", Impact.LOW, Urgency.LOW);
        report(priya, "Authentication Service", "Security", Impact.LOW, Urgency.LOW);
        incidentService.changeStatus(first.getCode(), Status.ACKNOWLEDGED, arjun, null);
        incidentService.changeStatus(first.getCode(), Status.IN_PROGRESS, arjun, null);
        incidentService.changeStatus(first.getCode(), Status.RESOLVED, arjun, "Fixed.");

        Incident fourth = report(priya, "Authentication Service", "Security", Impact.LOW, Urgency.LOW);

        assertThat(fourth.getAssignee()).isEqualTo(arjun); // one of his three is resolved, so he has room again
    }

    // ---- switching strategy while the application runs ----

    @Test
    void anAdministratorCanSwitchStrategyAtRuntime() {
        // Skill-based: Hardware goes to Ravi, the only one with that skill.
        assertThat(report(priya, "Email & Collaboration", "Hardware", Impact.LOW, Urgency.LOW).getAssignee())
                .isEqualTo(ravi);

        assignmentService.useStrategy(LeastLoadedStrategy.KEY);
        assertThat(assignmentService.getActiveStrategy()).isInstanceOf(LeastLoadedStrategy.class);

        // Least loaded: Ravi has one, the others none; of those, Arjun comes first by name. Skill no longer matters.
        assertThat(report(priya, "Email & Collaboration", "Hardware", Impact.LOW, Urgency.LOW).getAssignee())
                .isEqualTo(arjun);

        assignmentService.useStrategy(RoundRobinStrategy.KEY);
        assertThat(assignmentService.getActiveStrategy()).isInstanceOf(RoundRobinStrategy.class);

        // Round robin: each incident goes to the next technician in id order, wrapping around.
        List<Technician> inTurnOrder = List.of(ravi, meera, arjun, sara);
        Technician third = report(priya, "Email & Collaboration", "Hardware", Impact.LOW, Urgency.LOW).getAssignee();
        Technician fourth = report(priya, "Email & Collaboration", "Hardware", Impact.LOW, Urgency.LOW).getAssignee();
        assertThat(fourth).isEqualTo(inTurnOrder.get((inTurnOrder.indexOf(third) + 1) % inTurnOrder.size()));
    }

    @Test
    void theHistorySaysWhichStrategyChose() {
        assignmentService.useStrategy(LeastLoadedStrategy.KEY);

        Incident incident = reportPaymentOutage();

        assertThat(incidentService.history(incident).getLast().getNote()).contains("Least loaded");
    }

    @Test
    void anUnknownStrategyIsRefusedAndTheActiveOneStays() {
        assertThatThrownBy(() -> assignmentService.useStrategy("coin-toss"))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(assignmentService.getActiveStrategy()).isInstanceOf(SkillBasedStrategy.class);
    }

    @Test
    void thereAreThreeStrategiesToChooseFrom() {
        assertThat(assignmentService.getStrategies()).extracting(strategy -> strategy.getKey())
                .containsExactly(SkillBasedStrategy.KEY, LeastLoadedStrategy.KEY, RoundRobinStrategy.KEY);
    }

    // ---- assigning by hand ----

    @Test
    void anAdministratorCanAssignAWaitingIncidentByHand() {
        arjun.setAvailable(false);
        Incident incident = report(priya, "Authentication Service", "Security", Impact.HIGH, Urgency.HIGH);

        incidentService.assign(incident.getCode(), ravi.getId(), admin);

        assertThat(incident.getStatus()).isEqualTo(Status.ASSIGNED);
        assertThat(incident.getAssignee()).isEqualTo(ravi);
        AuditEntry entry = incidentService.history(incident).getLast();
        assertThat(entry.getAction()).isEqualTo(IncidentAction.ASSIGNED);
        assertThat(entry.getActor()).isEqualTo(admin);
        assertThat(entry.getOldStatus()).isEqualTo(Status.CLASSIFIED);
        assertThat(entry.getNewStatus()).isEqualTo(Status.ASSIGNED);
        assertThat(assignmentService.waitingForTechnician()).isEmpty();
    }

    @Test
    void anAdministratorCanAskTheStrategyToTryAgain() {
        arjun.setAvailable(false);
        Incident incident = report(priya, "Authentication Service", "Security", Impact.HIGH, Urgency.HIGH);
        assertThat(incident.getAssignee()).isNull();

        arjun.setAvailable(true);
        incidentService.autoAssign(incident.getCode(), admin);

        assertThat(incident.getStatus()).isEqualTo(Status.ASSIGNED);
        assertThat(incident.getAssignee()).isEqualTo(arjun);
        assertThat(incidentService.history(incident).getLast().getActor()).isEqualTo(admin);
    }

    @Test
    void reassigningChangesTheTechnicianButNotTheStatus() {
        Incident incident = reportPaymentOutage();
        incidentService.changeStatus(incident.getCode(), Status.ACKNOWLEDGED, sara, null);
        incidentService.changeStatus(incident.getCode(), Status.IN_PROGRESS, sara, null);

        incidentService.assign(incident.getCode(), meera.getId(), admin);

        assertThat(incident.getAssignee()).isEqualTo(meera);
        assertThat(incident.getStatus()).isEqualTo(Status.IN_PROGRESS);
        AuditEntry entry = incidentService.history(incident).getLast();
        assertThat(entry.getAction()).isEqualTo(IncidentAction.REASSIGNED);
        assertThat(entry.getNote()).contains("Sara Thomas", "Meera Nair");
        assertThat(entry.isStatusChange()).isFalse();
    }

    @Test
    void anIncidentCannotBeGivenToAnUnavailableTechnician() {
        Incident incident = reportPaymentOutage();
        ravi.setAvailable(false);

        assertThatThrownBy(() -> incidentService.assign(incident.getCode(), ravi.getId(), admin))
                .isInstanceOf(TechnicianUnavailableException.class)
                .hasMessageContaining("Ravi Kumar is not available");

        assertThat(incident.getAssignee()).isEqualTo(sara);
    }

    @Test
    void anIncidentCannotBeGivenToATechnicianAtMaximumWorkload() {
        for (int i = 0; i < 3; i++) {
            report(priya, "Authentication Service", "Security", Impact.LOW, Urgency.LOW); // fills Arjun (max 3)
        }
        Incident incident = reportPaymentOutage();

        assertThatThrownBy(() -> incidentService.assign(incident.getCode(), arjun.getId(), admin))
                .isInstanceOf(TechnicianUnavailableException.class)
                .hasMessageContaining("3 of 3");

        assertThat(incident.getAssignee()).isEqualTo(sara);
    }

    @Test
    void assigningToTheSameTechnicianAgainIsRefused() {
        Incident incident = reportPaymentOutage();
        int entriesBefore = incidentService.history(incident).size();

        assertThatThrownBy(() -> incidentService.assign(incident.getCode(), sara.getId(), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already assigned");

        assertThat(incidentService.history(incident)).hasSize(entriesBefore);
    }

    @Test
    void aResolvedIncidentCannotBeReassigned() {
        Incident incident = reportPaymentOutage();
        incidentService.changeStatus(incident.getCode(), Status.ACKNOWLEDGED, sara, null);
        incidentService.changeStatus(incident.getCode(), Status.IN_PROGRESS, sara, null);
        incidentService.changeStatus(incident.getCode(), Status.RESOLVED, sara, "Restarted the service.");

        assertThatThrownBy(() -> incidentService.assign(incident.getCode(), meera.getId(), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("resolved");

        assertThat(incident.getAssignee()).isEqualTo(sara);
    }

    // ---- working on an incident ----

    @Test
    void anIncidentCanGoThroughItsWholeLifeIncludingBothSideSteps() {
        Incident incident = reportPaymentOutage();
        String code = incident.getCode();

        incidentService.changeStatus(code, Status.ACKNOWLEDGED, sara, null);
        incidentService.changeStatus(code, Status.IN_PROGRESS, sara, null);
        incidentService.changeStatus(code, Status.AWAITING_INFO, sara, "Which browser are you using?");
        incidentService.changeStatus(code, Status.IN_PROGRESS, priya, "Firefox, on every laptop in the office.");
        incidentService.changeStatus(code, Status.RESOLVED, sara, "Restarted the payment gateway.");
        incidentService.changeStatus(code, Status.IN_PROGRESS, priya, "It is still failing for card payments.");
        incidentService.changeStatus(code, Status.RESOLVED, sara, "Renewed the expired certificate.");
        incidentService.changeStatus(code, Status.VERIFIED, priya, null);
        incidentService.changeStatus(code, Status.CLOSED, admin, null);

        assertThat(incident.getStatus()).isEqualTo(Status.CLOSED);
        assertThat(incident.getAcknowledgedAt()).isNotNull();
        assertThat(incident.getResolvedAt()).isNotNull();
        assertThat(incident.getClosedAt()).isNotNull();

        List<AuditEntry> history = incidentService.history(incident);
        assertThat(history).hasSize(3 + 9); // three entries from reporting, one per move after that
        assertThat(history).extracting(AuditEntry::getNewStatus).containsExactly(
                Status.NEW, Status.CLASSIFIED, Status.ASSIGNED, Status.ACKNOWLEDGED, Status.IN_PROGRESS,
                Status.AWAITING_INFO, Status.IN_PROGRESS, Status.RESOLVED, Status.IN_PROGRESS, Status.RESOLVED,
                Status.VERIFIED, Status.CLOSED);
        assertThat(history.get(5).getActor()).isEqualTo(sara);
        assertThat(history.get(5).getNote()).isEqualTo("Which browser are you using?");
        assertThat(history.get(6).getActor()).isEqualTo(priya);
        assertThat(history.getLast().getActor()).isEqualTo(admin);
        assertThat(history.getLast().getOldStatus()).isEqualTo(Status.VERIFIED);
    }

    @Test
    void onlyTheRightPersonMayMakeEachMove() {
        Incident incident = reportPaymentOutage(); // ASSIGNED to Sara, reported by Priya
        String code = incident.getCode();

        // Acknowledging is the assigned technician's job: not the reporter's, an admin's or another technician's.
        assertThatThrownBy(() -> incidentService.changeStatus(code, Status.ACKNOWLEDGED, priya, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> incidentService.changeStatus(code, Status.ACKNOWLEDGED, admin, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> incidentService.changeStatus(code, Status.ACKNOWLEDGED, meera, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(incident.getStatus()).isEqualTo(Status.ASSIGNED);

        incidentService.changeStatus(code, Status.ACKNOWLEDGED, sara, null);
        incidentService.changeStatus(code, Status.IN_PROGRESS, sara, null);
        incidentService.changeStatus(code, Status.RESOLVED, sara, "Fixed.");

        // Confirming the fix is the reporter's job: not another employee's and not the technician's.
        assertThatThrownBy(() -> incidentService.changeStatus(code, Status.VERIFIED, rahul, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> incidentService.changeStatus(code, Status.VERIFIED, sara, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(incident.getStatus()).isEqualTo(Status.RESOLVED);
    }

    @Test
    void aMoveThatNeedsANoteIsRefusedWithoutOne() {
        Incident incident = reportPaymentOutage();
        incidentService.changeStatus(incident.getCode(), Status.ACKNOWLEDGED, sara, null);
        incidentService.changeStatus(incident.getCode(), Status.IN_PROGRESS, sara, null);

        assertThatThrownBy(() -> incidentService.changeStatus(incident.getCode(), Status.RESOLVED, sara, "  "))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("note");

        assertThat(incident.getStatus()).isEqualTo(Status.IN_PROGRESS);
    }

    @Test
    void anIllegalMoveChangesNothingAndWritesNoAuditEntry() {
        Incident incident = reportPaymentOutage();
        int entriesBefore = incidentService.history(incident).size();

        assertThatThrownBy(() -> incidentService.changeStatus(incident.getCode(), Status.CLOSED, admin, null))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("cannot go from Assigned to Closed");

        assertThat(incident.getStatus()).isEqualTo(Status.ASSIGNED);
        assertThat(incident.getClosedAt()).isNull();
        assertThat(incidentService.history(incident)).hasSize(entriesBefore);
    }

    // ---- who can see what ----

    @Test
    void usersCanOnlyOpenIncidentsThatConcernThem() {
        String code = reportPaymentOutage().getCode(); // reported by Priya, assigned to Sara

        assertThat(incidentService.getFor(code, priya).getCode()).isEqualTo(code);
        assertThat(incidentService.getFor(code, sara).getCode()).isEqualTo(code);
        assertThat(incidentService.getFor(code, admin).getCode()).isEqualTo(code);
        assertThat(incidentService.getFor(code, meera).getCode()).isEqualTo(code); // any technician may look

        assertThatThrownBy(() -> incidentService.getFor(code, rahul)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> incidentService.getFor("INC-9999", admin))
                .isInstanceOf(IncidentNotFoundException.class);
    }

    // ---- the technician's queue ----

    @Test
    void theQueueIsSortedByResolutionDeadlineSoonestFirst() {
        // Both go to Sara (Application). Reported first but due later: P3, 8 hours. Reported second: P1, 30 minutes.
        Incident relaxed = report(priya, "Cloud Storage", "Application", Impact.MEDIUM, Urgency.MEDIUM);
        Incident urgent = reportPaymentOutage();
        assertThat(relaxed.getPriority()).isEqualTo(Priority.P3);
        assertThat(urgent.getPriority()).isEqualTo(Priority.P1);

        List<Incident> queue = incidents.findByAssigneeIdAndStatusInOrderByResolutionDueAscIdAsc(
                sara.getId(), Status.openStatuses());

        assertThat(queue).containsExactly(urgent, relaxed);
    }

    @Test
    void finishedIncidentsLeaveTheQueue() {
        Incident incident = reportPaymentOutage();
        incidentService.changeStatus(incident.getCode(), Status.ACKNOWLEDGED, sara, null);
        incidentService.changeStatus(incident.getCode(), Status.IN_PROGRESS, sara, null);
        incidentService.changeStatus(incident.getCode(), Status.RESOLVED, sara, "Fixed.");

        assertThat(incidents.findByAssigneeIdAndStatusInOrderByResolutionDueAscIdAsc(
                sara.getId(), Status.openStatuses())).isEmpty();
        assertThat(incidents.findByAssigneeIdAndStatusInOrderByUpdatedAtDescIdDesc(
                sara.getId(), Status.finishedStatuses())).containsExactly(incident);
    }
}
