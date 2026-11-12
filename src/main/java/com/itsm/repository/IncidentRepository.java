package com.itsm.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import com.itsm.domain.SlaStatus;
import com.itsm.domain.Status;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    Optional<Incident> findByCode(String code);

    /** Everything, newest first, with the linked objects loaded for display. */
    @EntityGraph(attributePaths = {"reporter", "service", "category", "assignee"})
    List<Incident> findAllByOrderByCreatedAtDescIdDesc();

    /** The incidents one employee reported, newest first. */
    @EntityGraph(attributePaths = {"reporter", "service", "category", "assignee"})
    List<Incident> findByReporterIdOrderByCreatedAtDescIdDesc(Long reporterId);

    /**
     * A technician's queue: their incidents in the given statuses, the one
     * whose resolution deadline comes first at the top.
     */
    @EntityGraph(attributePaths = {"reporter", "service", "category", "assignee"})
    List<Incident> findByAssigneeIdAndStatusInOrderByResolutionDueAscIdAsc(Long assigneeId,
                                                                          Collection<Status> statuses);

    /** A technician's incidents in the given statuses, most recently changed first. */
    @EntityGraph(attributePaths = {"reporter", "service", "category", "assignee"})
    List<Incident> findByAssigneeIdAndStatusInOrderByUpdatedAtDescIdDesc(Long assigneeId,
                                                                        Collection<Status> statuses);

    /** Incidents in one status that nobody is assigned to, oldest first. */
    @EntityGraph(attributePaths = {"reporter", "service", "category", "assignee"})
    List<Incident> findByStatusAndAssigneeIsNullOrderByCreatedAtAscIdAsc(Status status);

    long countByStatusAndAssigneeIsNull(Status status);

    /** Search: every incident that matches the given filters, with the linked objects loaded for display. */
    @Override
    @EntityGraph(attributePaths = {"reporter", "service", "category", "assignee"})
    List<Incident> findAll(Specification<Incident> filters, Sort sort);

    /** Every incident in one of the given statuses. Used by the SLA check. */
    @EntityGraph(attributePaths = {"reporter", "service", "category", "assignee"})
    List<Incident> findByStatusIn(Collection<Status> statuses);

    // ---- duplicates and escalation ----

    /** An open incident with exactly this title that the same person reported against the same service. */
    Optional<Incident> findFirstByReporterIdAndServiceIdAndTitleIgnoreCaseAndStatusInOrderByIdDesc(
            Long reporterId, Long serviceId, String title, Collection<Status> statuses);

    /** Other incidents on one service, in the given statuses, reported since the given moment. */
    @EntityGraph(attributePaths = "service")
    List<Incident> findByServiceIdAndStatusInAndCreatedAtGreaterThanEqualAndIdNot(
            Long serviceId, Collection<Status> statuses, LocalDateTime since, Long excludedId);

    /** Later incidents flagged as possible duplicates of the given one. */
    List<Incident> findByPossibleDuplicateOfIdOrderByIdAsc(Long originalId);

    /** Escalated incidents in the given statuses, most recently escalated first. */
    @EntityGraph(attributePaths = {"reporter", "service", "category", "assignee"})
    List<Incident> findByEscalatedAtIsNotNullAndStatusInOrderByEscalatedAtDescIdDesc(Collection<Status> statuses);

    // ---- numbers for the administrator's dashboard ----

    long countByStatusIn(Collection<Status> statuses);

    @Query("select count(i) from Incident i where i.assessment.priority = :priority and i.status in :statuses")
    long countByPriorityAndStatusIn(@Param("priority") Priority priority,
                                    @Param("statuses") Collection<Status> statuses);

    long countBySlaStatus(SlaStatus slaStatus);

    /** Every service with its number of incidents, busiest first. Services without incidents are included. */
    @Query("""
            select new com.itsm.repository.ServiceIncidentCount(s.name, s.criticality, count(i))
            from ITService s left join Incident i on i.service = s
            group by s.id, s.name, s.criticality
            order by count(i) desc, s.name""")
    List<ServiceIncidentCount> countIncidentsPerService();

    long countByReporterIdAndStatusIn(Long reporterId, Collection<Status> statuses);

    long countByAssigneeIdAndStatusIn(Long assigneeId, Collection<Status> statuses);
}
