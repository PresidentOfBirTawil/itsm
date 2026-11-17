package com.itsm.webhook;

import java.util.List;
import java.util.Optional;

import com.itsm.domain.Status;
import com.itsm.event.IncidentEvent;

/** The changes a webhook can be told about. */
public enum WebhookEvent {

    REPORTED("incident.reported", "Incident reported"),
    ASSIGNED("incident.assigned", "Technician assigned"),
    STATUS_CHANGED("incident.status_changed", "Status changed"),
    RESOLVED("incident.resolved", "Incident resolved"),
    SLA_BREACHED("incident.sla_breached", "SLA breached"),
    ESCALATED("incident.escalated", "Escalated"),
    /** Sent by the "Send test" button only. */
    TEST("webhook.test", "Test message");

    private final String name;
    private final String label;

    WebhookEvent(String name, String label) {
        this.name = name;
        this.label = label;
    }

    /** The name sent to receivers, e.g. "incident.reported". */
    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    /** The events an administrator can choose from. */
    public static List<WebhookEvent> choosable() {
        return List.of(REPORTED, ASSIGNED, STATUS_CHANGED, RESOLVED, SLA_BREACHED, ESCALATED);
    }

    /** Which webhook event an incident event is, if any. */
    public static Optional<WebhookEvent> of(IncidentEvent event) {
        return Optional.ofNullable(switch (event.action()) {
            case REPORTED -> REPORTED;
            case ASSIGNED, REASSIGNED -> ASSIGNED;
            case STATUS_CHANGED -> event.newStatus() == Status.RESOLVED ? RESOLVED : STATUS_CHANGED;
            case SLA_BREACHED -> SLA_BREACHED;
            case ESCALATED -> ESCALATED;
            default -> null;
        });
    }
}
