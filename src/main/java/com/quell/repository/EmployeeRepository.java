package com.quell.repository;

import java.util.List;

import com.quell.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findAllByOrderByNameAsc();
}
