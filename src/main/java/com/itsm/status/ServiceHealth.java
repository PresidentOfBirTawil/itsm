package com.itsm.status;

import com.itsm.domain.Incident;
import com.itsm.domain.Priority;

/** How a service is doing, from its open incidents. Ordered from good to bad. */
public enum ServiceHealth {

    OPERATIONAL("Operational", "success", "bi-check-circle-fill"),
    DEGRADED("Degraded", "warning", "bi-exclamation-circle-fill"),
    PARTIAL_OUTAGE("Partial outage", "warning", "bi-exclamation-triangle-fill"),
    MAJOR_OUTAGE("Major outage", "danger", "bi-x-octagon-fill");

    private final String label;
    private final String tone;
    private final String icon;

    ServiceHealth(String label, String tone, String icon) {
        this.label = label;
        this.tone = tone;
        this.icon = icon;
    }

    public String getLabel() {
        return label;
    }

    /** The colour family for badges: success, warning or danger. */
    public String getTone() {
        return tone;
    }

    public String getIcon() {
        return icon;
    }

    /** What one open incident says about its service: a P1 is a major outage, a P4 changes nothing. */
    public static ServiceHealth of(Incident incident) {
        Priority priority = incident.getPriority();
        if (priority == null) {
            return DEGRADED;
        }
        return switch (priority) {
            case P1 -> MAJOR_OUTAGE;
            case P2 -> PARTIAL_OUTAGE;
            case P3 -> DEGRADED;
            case P4 -> OPERATIONAL;
        };
    }

    public ServiceHealth worst(ServiceHealth other) {
        return other.ordinal() > ordinal() ? other : this;
    }

    public boolean isProblem() {
        return this != OPERATIONAL;
    }

    /** A partial or major outage: serious enough to affect the services that depend on this one. */
    public boolean isOutage() {
        return this == PARTIAL_OUTAGE || this == MAJOR_OUTAGE;
    }
}
