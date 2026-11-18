package com.quell.event;

import java.time.LocalDateTime;

import com.quell.domain.Incident;
import com.quell.domain.IncidentAction;
import com.quell.domain.Status;
import com.quell.domain.User;

/**
 * Something that just happened to an incident. Whoever changes an incident
 * publishes one of these; whoever is interested listens for them.
 *
 * @param incident  the incident it happened to
 * @param actor     the user who did it, or null when the system acted on its own
 * @param action    what happened
 * @param oldStatus the status before
 * @param newStatus the status after (the same as before if the status did not change)
 * @param note      extra detail in words, may be null
 * @param occurredAt when it happened
 */
public record IncidentEvent(Incident incident, User actor, IncidentAction action,
                            Status oldStatus, Status newStatus, String note, LocalDateTime occurredAt) {

    public IncidentEvent(Incident incident, User actor, IncidentAction action,
                         Status oldStatus, Status newStatus, String note) {
        this(incident, actor, action, oldStatus, newStatus, note, LocalDateTime.now());
    }
}
