package com.itsm.domain.state;

import java.util.List;

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

    @Override
    public boolean allowsAssignment() {
        return true;
    }
}
