package com.itsm.domain;

/** How quickly an incident must be handled. P1 is the most serious. */
public enum Priority {

    P1("Critical"),
    P2("High"),
    P3("Medium"),
    P4("Low");

    private final String label;

    Priority(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
