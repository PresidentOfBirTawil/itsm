package com.quell.api.model;

import java.time.LocalDateTime;

import com.quell.domain.AuditEntry;
import com.quell.domain.IncidentAction;
import com.quell.domain.Status;

/** One step in an incident's history. */
public record TimelineEntry(LocalDateTime at, IncidentAction action, String label, String actor, Status from,
                            Status to, String note) {

    public static TimelineEntry of(AuditEntry entry) {
        return new TimelineEntry(entry.getOccurredAt(), entry.getAction(), entry.getAction().getLabel(),
                entry.getActorName(), entry.getOldStatus(), entry.getNewStatus(), entry.getNote());
    }
}
