package com.itsm.repository;

import java.util.List;

import com.itsm.domain.Administrator;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdministratorRepository extends JpaRepository<Administrator, Long> {

    List<Administrator> findAllByOrderByNameAsc();
}
