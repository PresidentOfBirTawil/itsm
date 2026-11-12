package com.itsm.domain.state;

import java.time.LocalDateTime;
import java.util.List;

import com.itsm.domain.Incident;
import com.itsm.domain.Status;

/** Finished. A closed incident never changes again, so there are no moves out of this state. */
public final class ClosedState implements IncidentState {

    @Override
    public Status status() {
        return Status.CLOSED;
    }

    @Override
    public String summary() {
        return "Closed. Nothing more will happen to this incident.";
    }

    @Override
    public List<Transition> transitions() {
        return List.of();
    }

    @Override
    public void onEnter(Incident incident, Status from, LocalDateTime now) {
        incident.stampClosed(now);
    }

    @Override
    public boolean allowsRating() {
        return true;
    }

    /** A closed incident is finished: nothing more is added to it. */
    @Override
    public boolean allowsNotes() {
        return false;
    }
}
