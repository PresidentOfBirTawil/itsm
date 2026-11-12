package com.itsm.domain.state;

import java.time.LocalDateTime;
import java.util.List;

import com.itsm.domain.Incident;
import com.itsm.domain.Role;
import com.itsm.domain.Status;

/** The technician says it is fixed. The reporter now confirms that, or reopens the incident. */
public final class ResolvedState implements IncidentState {

    @Override
    public Status status() {
        return Status.RESOLVED;
    }

    @Override
    public String summary() {
        return "The technician reports it fixed. Waiting for the reporter to confirm.";
    }

    @Override
    public List<Transition> transitions() {
        return List.of(
                Transition.to(Status.VERIFIED).by(Role.EMPLOYEE, "Confirm it is fixed"),
                Transition.to(Status.IN_PROGRESS)
                        .by(Role.EMPLOYEE, "Not fixed, reopen")
                        .by(Role.ADMIN, "Reopen")
                        .withNote());
    }

    @Override
    public void onEnter(Incident incident, Status from, LocalDateTime now) {
        incident.stampResolved(now);
    }
}
