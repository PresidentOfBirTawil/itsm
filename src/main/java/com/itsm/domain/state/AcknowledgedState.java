package com.itsm.domain.state;

import java.time.LocalDateTime;
import java.util.List;

import com.itsm.domain.Incident;
import com.itsm.domain.Role;
import com.itsm.domain.Status;

/** The technician has seen the incident. This is the "response" the SLA response deadline is about. */
public final class AcknowledgedState implements IncidentState {

    @Override
    public Status status() {
        return Status.ACKNOWLEDGED;
    }

    @Override
    public String summary() {
        return "The technician has seen it and will start work.";
    }

    @Override
    public List<Transition> transitions() {
        return List.of(Transition.to(Status.IN_PROGRESS).by(Role.TECHNICIAN, "Start work"));
    }

    @Override
    public void onEnter(Incident incident, Status from, LocalDateTime now) {
        incident.stampAcknowledged(now);
    }

    @Override
    public boolean allowsAssignment() {
        return true;
    }
}
