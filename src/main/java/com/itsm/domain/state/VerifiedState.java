package com.itsm.domain.state;

import java.util.List;

import com.itsm.domain.Role;
import com.itsm.domain.Status;

/** The reporter has confirmed the fix. All that is left is to close the incident. */
public final class VerifiedState implements IncidentState {

    @Override
    public Status status() {
        return Status.VERIFIED;
    }

    @Override
    public String summary() {
        return "The reporter confirmed the fix. Waiting to be closed.";
    }

    @Override
    public List<Transition> transitions() {
        return List.of(Transition.to(Status.CLOSED)
                .by(Role.TECHNICIAN, "Close")
                .by(Role.ADMIN, "Close"));
    }

    /** The fix is confirmed, so the reporter can now say how good it was. */
    @Override
    public boolean allowsRating() {
        return true;
    }
}
