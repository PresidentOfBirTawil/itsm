package com.itsm.service;

import com.itsm.domain.Employee;
import com.itsm.domain.User;
import com.itsm.repository.EmployeeRepository;
import com.itsm.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-registration. People who sign themselves up always become employees;
 * technicians and administrators are never created this way.
 */
@Service
public class RegistrationService {

    private final UserRepository users;
    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository users, EmployeeRepository employees, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.employees = employees;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates an employee account. The password is hashed with BCrypt before it is stored.
     *
     * @throws EmailAlreadyRegisteredException if the email address is already in use
     */
    @Transactional
    public Employee registerEmployee(String name, String email, String rawPassword) {
        String normalisedEmail = User.normaliseEmail(email);
        if (users.existsByEmail(normalisedEmail)) {
            throw new EmailAlreadyRegisteredException(normalisedEmail);
        }
        return employees.save(new Employee(name, normalisedEmail, passwordEncoder.encode(rawPassword)));
    }
}
