package com.itsm.repository;

import java.util.Optional;

import com.itsm.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

/** Finds users of any kind. The objects that come back are Employees, Technicians or Administrators. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
