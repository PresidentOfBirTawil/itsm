package com.itsm.domain.state;

import java.time.LocalDateTime;
import java.util.List;

import com.itsm.domain.Incident;
import com.itsm.domain.Role;
import com.itsm.domain.Status;

/** The technician is working on it. From here they can resolve it, or ask the reporter for more information. */
public final class InProgressState implements IncidentState {

    @Override
    public Status status() {
        return Status.IN_PROGRESS;
    }

    @Override
    public String summary() {
        return "The technician is working on it.";
    }

    @Override
    public List<Transition> transitions() {
        return List.of(
                Transition.to(Status.RESOLVED).by(Role.TECHNICIAN, "Resolve").withNote(),
                Transition.to(Status.AWAITING_INFO).by(Role.TECHNICIAN, "Request information").withNote());
    }

    /** Coming back from RESOLVED means the fix did not hold: the incident is no longer resolved. */
    @Override
    public void onEnter(Incident incident, Status from, LocalDateTime now) {
        if (from == Status.RESOLVED) {
            incident.clearResolved();
        }
    }

    @Override
    public boolean allowsAssignment() {
        return true;
    }
}
