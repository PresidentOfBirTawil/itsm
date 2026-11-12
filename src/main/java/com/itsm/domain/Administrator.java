package com.itsm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** An IT manager who configures the system and monitors all incidents. */
@Entity
@DiscriminatorValue("ADMIN")
public class Administrator extends User {

    /** Required by JPA. */
    protected Administrator() {
    }

    public Administrator(String name, String email, String passwordHash) {
        super(name, email, passwordHash);
    }

    @Override
    public Role getRole() {
        return Role.ADMIN;
    }

    /** An administrator sees every incident. */
    @Override
    public boolean canView(Incident incident) {
        return true;
    }

    @Override
    public boolean canWorkOn(Incident incident) {
        return true;
    }
}
