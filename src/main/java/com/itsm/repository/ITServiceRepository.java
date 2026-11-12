package com.itsm.repository;

import java.util.List;

import com.itsm.domain.ITService;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ITServiceRepository extends JpaRepository<ITService, Long> {

    /** Most critical services first, then alphabetical. */
    List<ITService> findAllByOrderByCriticalityDescNameAsc();
}
