package com.itsm.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.itsm.domain.Incident;
import com.itsm.domain.Status;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

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

    long countByReporterIdAndStatusIn(Long reporterId, Collection<Status> statuses);

    long countByAssigneeIdAndStatusIn(Long assigneeId, Collection<Status> statuses);
}
