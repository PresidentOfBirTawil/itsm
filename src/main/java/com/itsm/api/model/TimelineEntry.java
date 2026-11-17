package com.itsm.api.model;

import java.time.LocalDateTime;

import com.itsm.domain.AuditEntry;
import com.itsm.domain.IncidentAction;
import com.itsm.domain.Status;

/** One step in an incident's history. */
public record TimelineEntry(LocalDateTime at, IncidentAction action, String label, String actor, Status from,
                            Status to, String note) {

    public static TimelineEntry of(AuditEntry entry) {
        return new TimelineEntry(entry.getOccurredAt(), entry.getAction(), entry.getAction().getLabel(),
                entry.getActorName(), entry.getOldStatus(), entry.getNewStatus(), entry.getNote());
    }
}
