package com.itsm.domain;

/** The kinds of thing that can happen to an incident. Each one is recorded in the audit trail. */
public enum IncidentAction {

    REPORTED("Incident reported"),
    PRIORITISED("Priority calculated"),
    ASSIGNED("Technician assigned"),
    REASSIGNED("Technician changed"),
    ASSIGNMENT_FAILED("Automatic assignment failed"),
    STATUS_CHANGED("Status changed");

    private final String label;

    IncidentAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
