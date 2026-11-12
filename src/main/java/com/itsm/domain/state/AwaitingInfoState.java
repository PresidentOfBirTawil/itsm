package com.itsm.domain.state;

import java.time.LocalDateTime;
import java.util.List;

import com.itsm.domain.Incident;
import com.itsm.domain.Role;
import com.itsm.domain.Status;

/** The technician has asked a question and work is paused until it is answered. */
public final class AwaitingInfoState implements IncidentState {

    @Override
    public Status status() {
        return Status.AWAITING_INFO;
    }

    @Override
    public String summary() {
        return "Waiting for the reporter to answer the technician's question.";
    }

    @Override
    public List<Transition> transitions() {
        return List.of(Transition.to(Status.IN_PROGRESS)
                .by(Role.EMPLOYEE, "Send information")
                .by(Role.TECHNICIAN, "Resume work")
                .withNote());
    }

    /** The technician cannot work while waiting, so the resolution clock stops ... */
    @Override
    public void onEnter(Incident incident, Status from, LocalDateTime now) {
        incident.pauseSlaClock(now);
    }

    /** ... and starts again when the answer arrives. The deadline moves back by the time spent waiting. */
    @Override
    public void onExit(Incident incident, Status to, LocalDateTime now) {
        incident.resumeSlaClock(now);
    }

    @Override
    public boolean allowsAssignment() {
        return true;
    }
}
