package com.itsm.intelligence;

import java.time.LocalDateTime;

import com.itsm.domain.Incident;
import com.itsm.domain.IncidentAction;
import com.itsm.event.IncidentEvent;
import com.itsm.event.IncidentEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Carries out an escalation when the {@link EscalationPolicy} calls for one:
 * it marks the incident as escalated and publishes an event. The event does
 * the rest: the audit listener writes the timeline entry and the notification
 * listener tells the administrators.
 */
@Component
public class AutoEscalation {

    private final EscalationPolicy policy;
    private final IncidentEventPublisher events;

    public AutoEscalation(EscalationPolicy policy, IncidentEventPublisher events) {
        this.policy = policy;
        this.events = events;
    }

    /** Escalates the incident if the policy says so. Called by the SLA check for every open incident. */
    public boolean escalateIfDue(Incident incident, LocalDateTime now) {
        if (!policy.shouldEscalate(incident, now)) {
            return false;
        }
        incident.escalate(now);
        events.publish(new IncidentEvent(incident, null, IncidentAction.ESCALATED,
                incident.getStatus(), incident.getStatus(), policy.explain(incident)));
        return true;
    }
}
