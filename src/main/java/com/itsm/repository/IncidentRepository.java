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
    List<Incident> findAllByOrderByCreatedAtDesc();

    /** The incidents one employee reported, newest first. */
    @EntityGraph(attributePaths = {"reporter", "service", "category", "assignee"})
    List<Incident> findByReporterIdOrderByCreatedAtDesc(Long reporterId);

    /** The incidents assigned to one technician, newest first. */
    @EntityGraph(attributePaths = {"reporter", "service", "category", "assignee"})
    List<Incident> findByAssigneeIdOrderByCreatedAtDesc(Long assigneeId);

    long countByReporterIdAndStatusIn(Long reporterId, Collection<Status> statuses);

    long countByAssigneeIdAndStatusIn(Long assigneeId, Collection<Status> statuses);
}
