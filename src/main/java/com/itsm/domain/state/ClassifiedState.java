package com.itsm.domain.state;

import com.itsm.error.InvalidStateTransitionException;

import java.util.List;

import com.itsm.domain.Incident;
import com.itsm.domain.Status;

/** Has a priority and SLA deadlines, and is waiting for a technician. It becomes ASSIGNED once it gets one. */
public final class ClassifiedState implements IncidentState {

    @Override
    public Status status() {
        return Status.CLASSIFIED;
    }

    @Override
    public String summary() {
        return "Has a priority and is waiting for a technician to be assigned.";
    }

    @Override
    public List<Transition> transitions() {
        return List.of(Transition.to(Status.ASSIGNED));
    }

    @Override
    public void checkEntry(Incident incident) {
        if (incident.getPriority() == null) {
            throw new InvalidStateTransitionException(incident, status(), "it has no priority yet");
        }
    }

    @Override
    public boolean allowsAssignment() {
        return true;
    }

    @Override
    public void onAssigned(Incident incident) {
        incident.transitionTo(Status.ASSIGNED);
    }
}
