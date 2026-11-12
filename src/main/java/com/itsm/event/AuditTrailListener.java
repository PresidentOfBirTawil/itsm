package com.itsm.event;

import com.itsm.domain.AuditEntry;
import com.itsm.repository.AuditEntryRepository;
import org.springframework.stereotype.Component;

/** Writes one audit entry for every incident event. This is what builds the timeline on the incident page. */
@Component
public class AuditTrailListener implements IncidentEventListener {

    private final AuditEntryRepository auditEntries;

    public AuditTrailListener(AuditEntryRepository auditEntries) {
        this.auditEntries = auditEntries;
    }

    @Override
    public void onIncidentEvent(IncidentEvent event) {
        auditEntries.save(new AuditEntry(event.incident(), event.occurredAt(), event.actor(), event.action(),
                event.oldStatus(), event.newStatus(), event.note()));
    }
}
