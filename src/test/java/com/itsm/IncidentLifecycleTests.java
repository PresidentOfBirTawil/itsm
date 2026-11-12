package com.itsm;

import com.itsm.error.InvalidInputException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import com.itsm.domain.Role;
import com.itsm.domain.Status;
import com.itsm.domain.Technician;
import com.itsm.domain.state.IncidentState;
import com.itsm.domain.state.IncidentStates;
import com.itsm.error.InvalidStateTransitionException;
import com.itsm.domain.state.Transition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** The incident lifecycle: which moves are allowed, what a refused move does, and who may make each move. */
class IncidentLifecycleTests {

    /**
     * The lifecycle as it was specified, written out by hand so that it is
     * checked against the state classes rather than copied from them.
     */
    private static final Map<Status, Set<Status>> LEGAL_MOVES = Map.of(
            Status.NEW, EnumSet.of(Status.CLASSIFIED),
            Status.CLASSIFIED, EnumSet.of(Status.ASSIGNED),
            Status.ASSIGNED, EnumSet.of(Status.ACKNOWLEDGED),
            Status.ACKNOWLEDGED, EnumSet.of(Status.IN_PROGRESS),
            Status.IN_PROGRESS, EnumSet.of(Status.RESOLVED, Status.AWAITING_INFO),
            Status.AWAITING_INFO, EnumSet.of(Status.IN_PROGRESS),
            Status.RESOLVED, EnumSet.of(Status.VERIFIED, Status.IN_PROGRESS),
            Status.VERIFIED, EnumSet.of(Status.CLOSED),
            Status.CLOSED, EnumSet.noneOf(Status.class));

    /** Every pair of statuses: 9 × 9 = 81 possible moves, including "moving" to the same status. */
    static Stream<Arguments> everyPossibleMove() {
        return Arrays.stream(Status.values())
                .flatMap(from -> Arrays.stream(Status.values()).map(to -> Arguments.of(from, to)));
    }

    // ---- every legal and illegal transition ----

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyPossibleMove")
    void aMoveSucceedsOnlyIfTheLifecycleAllowsIt(Status from, Status to) {
        Incident incident = Fixtures.incidentIn(from);

        if (LEGAL_MOVES.get(from).contains(to)) {
            makeLegalMove(incident, to);

            assertThat(incident.getStatus()).isEqualTo(to);
        } else {
            assertThatThrownBy(() -> incident.transitionTo(to))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining(from.getLabel())
                    .hasMessageContaining(to.getLabel());

            assertThat(incident.getStatus()).isEqualTo(from);
        }
    }

    /**
     * The first two moves happen by themselves when their cause happens: a
     * priority is set (NEW to CLASSIFIED) or a technician is assigned
     * (CLASSIFIED to ASSIGNED). Every other move is asked for directly.
     */
    private static void makeLegalMove(Incident incident, Status to) {
        if (incident.getStatus() == Status.NEW && to == Status.CLASSIFIED) {
            incident.prioritise(Fixtures.assessment(Priority.P3), Fixtures.policy(Priority.P3));
        } else if (incident.getStatus() == Status.CLASSIFIED && to == Status.ASSIGNED) {
            incident.assignTo(Fixtures.technician(1, "Ravi Kumar", 5));
        } else {
            if (to == Status.RESOLVED) {
                incident.recordResolution("Restarted the service."); // an incident needs a resolution to be RESOLVED
            }
            incident.transitionTo(to);
        }
    }

    @Test
    void thereAreExactlyTenLegalMoves() {
        long legal = IncidentStates.all().stream().mapToLong(state -> state.transitions().size()).sum();

        assertThat(legal).isEqualTo(10);
        for (IncidentState state : IncidentStates.all()) {
            assertThat(state.transitions()).extracting(Transition::target)
                    .containsExactlyInAnyOrderElementsOf(LEGAL_MOVES.get(state.status()));
        }
    }

    @ParameterizedTest
    @EnumSource(Status.class)
    void everyStatusHasItsOwnStateClass(Status status) {
        IncidentState state = IncidentStates.of(status);

        assertThat(state).isNotNull();
        assertThat(state.status()).isEqualTo(status);
        assertThat(state.summary()).isNotBlank();
        assertThat(Fixtures.incidentIn(status).getState()).isSameAs(state);
    }

    @Test
    void eachStateIsADifferentClass() {
        assertThat(IncidentStates.all()).extracting(Object::getClass).doesNotHaveDuplicates().hasSize(9);
    }

    // ---- an illegal move changes nothing ----

    @Test
    void aRefusedMoveLeavesTheIncidentExactlyAsItWas() {
        Incident incident = Fixtures.incidentIn(Status.ASSIGNED);
        Technician assignee = incident.getAssignee();

        assertThatThrownBy(() -> incident.transitionTo(Status.CLOSED))
                .isInstanceOf(InvalidStateTransitionException.class);

        assertThat(incident.getStatus()).isEqualTo(Status.ASSIGNED);
        assertThat(incident.getAssignee()).isSameAs(assignee);
        assertThat(incident.getAcknowledgedAt()).isNull();
        assertThat(incident.getResolvedAt()).isNull();
        assertThat(incident.getClosedAt()).isNull();
    }

    @Test
    void newCannotJumpStraightToClosed() {
        Incident incident = Fixtures.incident();

        assertThatThrownBy(() -> incident.transitionTo(Status.CLOSED))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("cannot go from New to Closed");
        assertThat(incident.getStatus()).isEqualTo(Status.NEW);
    }

    @Test
    void movingToNoStatusAtAllIsRefused() {
        Incident incident = Fixtures.incidentIn(Status.ASSIGNED);

        assertThatThrownBy(() -> incident.transitionTo(null)).isInstanceOf(InvalidStateTransitionException.class);
        assertThat(incident.getStatus()).isEqualTo(Status.ASSIGNED);
    }

    @Test
    void aClosedIncidentNeverChangesAgain() {
        Incident incident = Fixtures.incidentIn(Status.CLOSED);

        for (Status target : Status.values()) {
            assertThatThrownBy(() -> incident.transitionTo(target))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
        assertThat(incident.getStatus()).isEqualTo(Status.CLOSED);
    }

    // ---- what a state needs before an incident can enter it ----

    @Test
    void anIncidentWithoutAPriorityCannotBecomeClassified() {
        Incident incident = Fixtures.incident();

        assertThatThrownBy(() -> incident.transitionTo(Status.CLASSIFIED))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("no priority");
        assertThat(incident.getStatus()).isEqualTo(Status.NEW);
    }

    @Test
    void anIncidentWithoutATechnicianCannotBecomeAssigned() {
        Incident incident = Fixtures.incidentIn(Status.CLASSIFIED);

        assertThatThrownBy(() -> incident.transitionTo(Status.ASSIGNED))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("no technician");
        assertThat(incident.getStatus()).isEqualTo(Status.CLASSIFIED);
    }

    // ---- priority and assignment drive the first two moves ----

    @Test
    void aNewIncidentBecomesClassifiedOnceItsPriorityIsSet() {
        Incident incident = Fixtures.incident();

        incident.prioritise(Fixtures.assessment(Priority.P2), Fixtures.policy(Priority.P2));

        assertThat(incident.getStatus()).isEqualTo(Status.CLASSIFIED);
        assertThat(incident.getPriority()).isEqualTo(Priority.P2);
        assertThat(incident.getResponseDue()).isEqualTo(incident.getCreatedAt().plusMinutes(60));
        assertThat(incident.getResolutionDue()).isEqualTo(incident.getCreatedAt().plusMinutes(480));
    }

    @Test
    void changingThePriorityLaterKeepsTheStatus() {
        Incident incident = Fixtures.incidentIn(Status.IN_PROGRESS);

        incident.prioritise(Fixtures.assessment(Priority.P1), Fixtures.policy(Priority.P1));

        assertThat(incident.getStatus()).isEqualTo(Status.IN_PROGRESS);
        assertThat(incident.getPriority()).isEqualTo(Priority.P1);
    }

    @Test
    void thePriorityAndTheSlaPolicyMustMatch() {
        Incident incident = Fixtures.incident();

        assertThatThrownBy(() -> incident.prioritise(Fixtures.assessment(Priority.P1), Fixtures.policy(Priority.P4)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(incident.getStatus()).isEqualTo(Status.NEW);
        assertThat(incident.getPriority()).isNull();
    }

    @Test
    void aClassifiedIncidentBecomesAssignedOnceItHasATechnician() {
        Incident incident = Fixtures.incidentIn(Status.CLASSIFIED);
        Technician technician = Fixtures.technician(7, "Meera Nair", 4);

        incident.assignTo(technician);

        assertThat(incident.getStatus()).isEqualTo(Status.ASSIGNED);
        assertThat(incident.getAssignee()).isSameAs(technician);
    }

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"ASSIGNED", "ACKNOWLEDGED", "IN_PROGRESS", "AWAITING_INFO"})
    void reassigningKeepsTheStatus(Status status) {
        Incident incident = Fixtures.incidentIn(status);
        Technician someoneElse = Fixtures.technician(7, "Meera Nair", 4);

        incident.assignTo(someoneElse);

        assertThat(incident.getStatus()).isEqualTo(status);
        assertThat(incident.getAssignee()).isSameAs(someoneElse);
    }

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"NEW", "RESOLVED", "VERIFIED", "CLOSED"})
    void anIncidentCannotBeAssignedBeforeItIsClassifiedOrAfterItIsResolved(Status status) {
        Incident incident = Fixtures.incidentIn(status);
        Technician before = incident.getAssignee();

        assertThatThrownBy(() -> incident.assignTo(Fixtures.technician(7, "Meera Nair", 4)))
                .isInstanceOf(InvalidInputException.class);

        assertThat(incident.getStatus()).isEqualTo(status);
        assertThat(incident.getAssignee()).isSameAs(before);
    }

    // ---- what happens on the way in ----

    @Test
    void timesAreRecordedAsTheIncidentMovesAlong() {
        Incident incident = Fixtures.incidentIn(Status.ASSIGNED);
        assertThat(incident.getAcknowledgedAt()).isNull();

        incident.transitionTo(Status.ACKNOWLEDGED);
        assertThat(incident.getAcknowledgedAt()).isNotNull();

        incident.transitionTo(Status.IN_PROGRESS);
        incident.recordResolution("Restarted the service.");
        incident.transitionTo(Status.RESOLVED);
        assertThat(incident.getResolvedAt()).isNotNull();
        assertThat(incident.getClosedAt()).isNull();

        incident.transitionTo(Status.VERIFIED);
        incident.transitionTo(Status.CLOSED);
        assertThat(incident.getClosedAt()).isNotNull();
    }

    @Test
    void reopeningForgetsTheResolutionTimeButNotTheAcknowledgement() {
        Incident incident = Fixtures.incidentIn(Status.RESOLVED);
        var acknowledgedAt = incident.getAcknowledgedAt();

        incident.transitionTo(Status.IN_PROGRESS);

        assertThat(incident.getStatus()).isEqualTo(Status.IN_PROGRESS);
        assertThat(incident.getResolvedAt()).isNull();
        assertThat(incident.getAcknowledgedAt()).isEqualTo(acknowledgedAt);
    }

    @Test
    void askingForInformationAndGettingItGoesThereAndBack() {
        Incident incident = Fixtures.incidentIn(Status.IN_PROGRESS);

        incident.transitionTo(Status.AWAITING_INFO);
        assertThat(incident.getStatus()).isEqualTo(Status.AWAITING_INFO);
        assertThat(incident.getStatus().isOpen()).isTrue();

        incident.transitionTo(Status.IN_PROGRESS);
        assertThat(incident.getStatus()).isEqualTo(Status.IN_PROGRESS);
    }

    // ---- who may make each move (the buttons each role is shown) ----

    @Test
    void theButtonsEachRoleGetsInEachStatus() {
        assertButtons(Status.NEW, Role.EMPLOYEE);
        assertButtons(Status.NEW, Role.TECHNICIAN);
        assertButtons(Status.NEW, Role.ADMIN);

        assertButtons(Status.CLASSIFIED, Role.EMPLOYEE);
        assertButtons(Status.CLASSIFIED, Role.TECHNICIAN);
        assertButtons(Status.CLASSIFIED, Role.ADMIN);

        assertButtons(Status.ASSIGNED, Role.EMPLOYEE);
        assertButtons(Status.ASSIGNED, Role.TECHNICIAN, "Acknowledge");
        assertButtons(Status.ASSIGNED, Role.ADMIN);

        assertButtons(Status.ACKNOWLEDGED, Role.EMPLOYEE);
        assertButtons(Status.ACKNOWLEDGED, Role.TECHNICIAN, "Start work");
        assertButtons(Status.ACKNOWLEDGED, Role.ADMIN);

        assertButtons(Status.IN_PROGRESS, Role.EMPLOYEE);
        assertButtons(Status.IN_PROGRESS, Role.TECHNICIAN, "Resolve", "Request information");
        assertButtons(Status.IN_PROGRESS, Role.ADMIN);

        assertButtons(Status.AWAITING_INFO, Role.EMPLOYEE, "Send information");
        assertButtons(Status.AWAITING_INFO, Role.TECHNICIAN, "Resume work");
        assertButtons(Status.AWAITING_INFO, Role.ADMIN);

        assertButtons(Status.RESOLVED, Role.EMPLOYEE, "Confirm it is fixed", "Not fixed, reopen");
        assertButtons(Status.RESOLVED, Role.TECHNICIAN);
        assertButtons(Status.RESOLVED, Role.ADMIN, "Reopen");

        assertButtons(Status.VERIFIED, Role.EMPLOYEE);
        assertButtons(Status.VERIFIED, Role.TECHNICIAN, "Close");
        assertButtons(Status.VERIFIED, Role.ADMIN, "Close");

        assertButtons(Status.CLOSED, Role.EMPLOYEE);
        assertButtons(Status.CLOSED, Role.TECHNICIAN);
        assertButtons(Status.CLOSED, Role.ADMIN);
    }

    private static void assertButtons(Status status, Role role, String... expectedLabels) {
        List<String> labels = IncidentStates.of(status).transitionsFor(role).stream()
                .map(transition -> transition.labelFor(role))
                .toList();

        assertThat(labels).as("%s buttons while %s", role, status).containsExactly(expectedLabels);
    }

    @Test
    void theFirstTwoMovesAreMadeByTheSystemNotByAUser() {
        assertThat(IncidentStates.of(Status.NEW).transitionTo(Status.CLASSIFIED).orElseThrow().isAutomatic()).isTrue();
        assertThat(IncidentStates.of(Status.CLASSIFIED).transitionTo(Status.ASSIGNED).orElseThrow().isAutomatic())
                .isTrue();
    }

    @Test
    void movesThatNeedAnExplanationSaySo() {
        assertThat(noteRequired(Status.IN_PROGRESS, Status.RESOLVED)).isTrue();
        assertThat(noteRequired(Status.IN_PROGRESS, Status.AWAITING_INFO)).isTrue();
        assertThat(noteRequired(Status.AWAITING_INFO, Status.IN_PROGRESS)).isTrue();
        assertThat(noteRequired(Status.RESOLVED, Status.IN_PROGRESS)).isTrue();

        assertThat(noteRequired(Status.ASSIGNED, Status.ACKNOWLEDGED)).isFalse();
        assertThat(noteRequired(Status.ACKNOWLEDGED, Status.IN_PROGRESS)).isFalse();
        assertThat(noteRequired(Status.RESOLVED, Status.VERIFIED)).isFalse();
        assertThat(noteRequired(Status.VERIFIED, Status.CLOSED)).isFalse();
    }

    private static boolean noteRequired(Status from, Status to) {
        return IncidentStates.of(from).transitionTo(to).orElseThrow().noteRequired();
    }
}
