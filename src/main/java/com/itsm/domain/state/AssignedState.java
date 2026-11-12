package com.itsm.domain.state;

import com.itsm.error.InvalidStateTransitionException;

import java.util.List;

import com.itsm.domain.Incident;
import com.itsm.domain.Role;
import com.itsm.domain.Status;

/** A technician has been chosen but has not looked at the incident yet. */
public final class AssignedState implements IncidentState {

    @Override
    public Status status() {
        return Status.ASSIGNED;
    }

    @Override
    public String summary() {
        return "Waiting for the technician to acknowledge it.";
    }

    @Override
    public List<Transition> transitions() {
        return List.of(Transition.to(Status.ACKNOWLEDGED).by(Role.TECHNICIAN, "Acknowledge"));
    }

    @Override
    public void checkEntry(Incident incident) {
        if (incident.getAssignee() == null) {
            throw new InvalidStateTransitionException(incident, status(), "no technician is assigned");
        }
    }

    @Override
    public boolean allowsAssignment() {
        return true;
    }
}
