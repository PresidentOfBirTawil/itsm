package com.itsm.repository;

import java.util.List;
import java.util.Optional;

import com.itsm.domain.ITService;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ITServiceRepository extends JpaRepository<ITService, Long> {

    Optional<ITService> findByName(String name);

    /** Most critical services first, then alphabetical. */
    List<ITService> findAllByOrderByCriticalityDescNameAsc();
}
