package com.itsm.domain;

/** Where an incident stands against its SLA deadlines. Shown as a badge on every incident list. */
public enum SlaStatus {

    /** The clock is running and there is plenty of time left. */
    ON_TRACK("On track"),
    /** At least 75% of the time for the next deadline has been used. */
    AT_RISK("At risk"),
    /** The resolution clock is stopped because the technician is waiting for information. */
    PAUSED("Paused"),
    /** A deadline was missed. This never changes back: a breach is a fact about the past. */
    BREACHED("Breached"),
    /** The incident was resolved and no deadline was missed. */
    MET("Met");

    private final String label;

    SlaStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
