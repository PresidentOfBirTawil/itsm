package com.itsm.repository;

import java.util.List;
import java.util.Optional;

import com.itsm.domain.Priority;
import com.itsm.domain.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    Optional<SlaPolicy> findByPriority(Priority priority);

    List<SlaPolicy> findAllByOrderByPriorityAsc();
}
