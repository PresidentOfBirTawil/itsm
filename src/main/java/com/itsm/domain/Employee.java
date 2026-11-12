package com.itsm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** A member of staff who reports incidents and follows their progress. */
@Entity
@DiscriminatorValue("EMPLOYEE")
public class Employee extends User {

    /** Required by JPA. */
    protected Employee() {
    }

    public Employee(String name, String email, String passwordHash) {
        super(name, email, passwordHash);
    }

    @Override
    public Role getRole() {
        return Role.EMPLOYEE;
    }

    /** An employee sees the incidents they reported themselves. */
    @Override
    public boolean canAccess(Incident incident) {
        return equals(incident.getReporter());
    }
}
