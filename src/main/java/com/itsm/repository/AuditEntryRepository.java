package com.itsm.repository;

import java.util.List;

import com.itsm.domain.AuditEntry;
import com.itsm.domain.IncidentAction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    /** The history of one incident, oldest first, with the users loaded for display. */
    @EntityGraph(attributePaths = "actor")
    List<AuditEntry> findByIncidentIdOrderByOccurredAtAscIdAsc(Long incidentId);

    /** The most recent entry of one kind for an incident, e.g. why automatic assignment last failed. */
    AuditEntry findFirstByIncidentIdAndActionOrderByOccurredAtDescIdDesc(Long incidentId, IncidentAction action);
}
