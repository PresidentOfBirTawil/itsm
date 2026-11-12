package com.itsm.domain.state;

import java.util.List;

import com.itsm.domain.Incident;
import com.itsm.domain.Status;

/** Just reported. Nobody acts on a new incident by hand: it becomes CLASSIFIED once its priority is set. */
public final class NewState implements IncidentState {

    @Override
    public Status status() {
        return Status.NEW;
    }

    @Override
    public String summary() {
        return "Just reported. The priority is being worked out.";
    }

    @Override
    public List<Transition> transitions() {
        return List.of(Transition.to(Status.CLASSIFIED));
    }

    @Override
    public void onPrioritised(Incident incident) {
        incident.transitionTo(Status.CLASSIFIED);
    }
}
