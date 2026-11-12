package com.itsm.domain;

/** The three kinds of user the system knows about. */
public enum Role {

    EMPLOYEE("Employee"),
    TECHNICIAN("Technician"),
    ADMIN("Administrator");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    /** Human-readable name, e.g. "Administrator". */
    public String getLabel() {
        return label;
    }

    /** The authority name Spring Security checks, e.g. ROLE_ADMIN. */
    public String getAuthority() {
        return "ROLE_" + name();
    }
}
