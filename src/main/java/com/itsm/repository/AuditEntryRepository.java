package com.itsm.repository;

import java.util.List;

import com.itsm.domain.AuditEntry;
import com.itsm.domain.IncidentAction;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    /** The history of one incident, oldest first, with the users loaded for display. */
    @EntityGraph(attributePaths = "actor")
    List<AuditEntry> findByIncidentIdOrderByOccurredAtAscIdAsc(Long incidentId);

    /**
     * The audit log across all incidents, newest first. Both filters are
     * optional: pass null to leave one out.
     *
     * @param codePattern an incident code pattern in upper case with % wildcards, e.g. "%INC-0007%"
     */
    @Query("""
            select entry from AuditEntry entry join fetch entry.incident incident left join fetch entry.actor
            where (:codePattern is null or upper(incident.code) like :codePattern)
              and (:action is null or entry.action = :action)
            order by entry.occurredAt desc, entry.id desc""")
    List<AuditEntry> search(@Param("codePattern") String codePattern, @Param("action") IncidentAction action,
                            Limit limit);

    /** The most recent entry of one kind for an incident, e.g. why automatic assignment last failed. */
    AuditEntry findFirstByIncidentIdAndActionOrderByOccurredAtDescIdDesc(Long incidentId, IncidentAction action);
}
