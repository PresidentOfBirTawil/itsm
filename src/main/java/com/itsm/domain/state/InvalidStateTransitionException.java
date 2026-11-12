package com.itsm.domain.state;

import com.itsm.BusinessRuleException;
import com.itsm.domain.Incident;
import com.itsm.domain.Status;

/** Thrown when an incident is asked to make a move its lifecycle does not allow. Nothing is changed. */
public class InvalidStateTransitionException extends BusinessRuleException {

    private final Status from;
    private final Status to;

    public InvalidStateTransitionException(Incident incident, Status to) {
        this(incident, to, "that move is not part of the lifecycle");
    }

    public InvalidStateTransitionException(Incident incident, Status to, String reason) {
        super("%s cannot go from %s to %s: %s.".formatted(
                incident.getCode() == null ? "The incident" : incident.getCode(),
                incident.getStatus().getLabel(), to == null ? "nothing" : to.getLabel(), reason));
        this.from = incident.getStatus();
        this.to = to;
    }

    public Status getFrom() {
        return from;
    }

    public Status getTo() {
        return to;
    }
}
