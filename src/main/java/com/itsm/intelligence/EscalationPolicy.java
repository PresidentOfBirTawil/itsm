package com.itsm.intelligence;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import org.springframework.stereotype.Component;

/**
 * Decides when an incident must be escalated to the administrators.
 *
 * <p>The rule: a <b>P1</b> incident that is still open and that <b>no
 * technician has acknowledged</b> by its <b>response deadline</b> is
 * escalated, once. A critical incident nobody has even looked at needs a
 * manager's attention, whether or not a technician was assigned.</p>
 */
@Component
public class EscalationPolicy {

    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss");

    public boolean shouldEscalate(Incident incident, LocalDateTime now) {
        return incident.getPriority() == Priority.P1
                && incident.getStatus().isOpen()
                && incident.getAcknowledgedAt() == null
                && incident.getEscalatedAt() == null
                && incident.getResponseDue() != null
                && now.isAfter(incident.getResponseDue());
    }

    /** Why the incident is being escalated, in words, for the timeline and the notification. */
    public String explain(Incident incident) {
        long minutes = Duration.between(incident.getCreatedAt(), incident.getResponseDue()).toMinutes();
        String responsible = incident.getAssignee() == null
                ? "No technician is assigned."
                : "It is assigned to %s.".formatted(incident.getAssignee().getName());
        return ("Escalated to the administrators automatically: this P1 incident was not acknowledged within its "
                + "response time of %d min (deadline %s). %s").formatted(
                minutes, DEADLINE.format(incident.getResponseDue()), responsible);
    }
}
