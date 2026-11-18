package com.quell.repository;

import java.util.List;
import java.util.Optional;

import com.quell.domain.Priority;
import com.quell.domain.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    Optional<SlaPolicy> findByPriority(Priority priority);

    List<SlaPolicy> findAllByOrderByPriorityAsc();
}
