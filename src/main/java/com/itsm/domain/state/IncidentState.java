package com.itsm.domain.state;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.itsm.domain.Incident;
import com.itsm.domain.Role;
import com.itsm.domain.Status;

/**
 * How an incident behaves while it is in one particular status (the State pattern).
 *
 * <p>There is one class per status. Each class knows which moves are allowed
 * from its status and who may make them, what must be true before an incident
 * can enter, and what happens when it does. The incident itself has no
 * {@code if} or {@code switch} on its status: it asks its current state.</p>
 */
public interface IncidentState {

    /** The status this state stands for. */
    Status status();

    /** One sentence saying what the incident is waiting for in this state. Shown on the incident page. */
    String summary();

    /** Every move allowed out of this state. An empty list means the state is final. */
    List<Transition> transitions();

    /** The move from this state to {@code target}, if there is one. */
    default Optional<Transition> transitionTo(Status target) {
        return transitions().stream().filter(transition -> transition.target() == target).findFirst();
    }

    default boolean canTransitionTo(Status target) {
        return transitionTo(target).isPresent();
    }

    /** The moves a user with the given role may make from this state: the buttons to show them. */
    default List<Transition> transitionsFor(Role role) {
        return transitions().stream().filter(transition -> transition.allowedFor(role)).toList();
    }

    /**
     * Checks that the incident is ready to enter this state. Called before
     * anything is changed.
     *
     * @throws InvalidStateTransitionException if it is not ready
     */
    default void checkEntry(Incident incident) {
    }

    /** Called right after the incident has entered this state, coming from {@code from}. */
    default void onEnter(Incident incident, Status from, LocalDateTime now) {
    }

    /** Called just before the incident leaves this state for {@code to}. */
    default void onExit(Incident incident, Status to, LocalDateTime now) {
    }

    /** Called when a priority has been set on an incident that is in this state. */
    default void onPrioritised(Incident incident) {
    }

    /** True if a technician can be assigned, or changed, while the incident is in this state. */
    default boolean allowsAssignment() {
        return false;
    }

    /** Called when a technician has been assigned to an incident that is in this state. */
    default void onAssigned(Incident incident) {
    }
}
