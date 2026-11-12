package com.itsm.repository;

import java.util.List;
import java.util.Optional;

import com.itsm.domain.Technician;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechnicianRepository extends JpaRepository<Technician, Long> {

    /** Loads the technicians together with their skills in one query. */
    @EntityGraph(attributePaths = "skills")
    List<Technician> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "skills")
    Optional<Technician> findWithSkillsById(Long id);
}
