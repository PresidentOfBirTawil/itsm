package com.quell.repository;

import java.util.List;

import com.quell.domain.Administrator;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdministratorRepository extends JpaRepository<Administrator, Long> {

    List<Administrator> findAllByOrderByNameAsc();
}
