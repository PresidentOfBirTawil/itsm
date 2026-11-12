package com.itsm.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The incident lifecycle from the project brief, in order:
 * NEW → CLASSIFIED → ASSIGNED → ACKNOWLEDGED → IN_PROGRESS → RESOLVED → VERIFIED → CLOSED.
 */
public enum Status {

    NEW("New", true),
    CLASSIFIED("Classified", true),
    ASSIGNED("Assigned", true),
    ACKNOWLEDGED("Acknowledged", true),
    IN_PROGRESS("In progress", true),
    RESOLVED("Resolved", false),
    VERIFIED("Verified", false),
    CLOSED("Closed", false);

    private final String label;
    private final boolean open;

    Status(String label, boolean open) {
        this.label = label;
        this.open = open;
    }

    public String getLabel() {
        return label;
    }

    /** True while the incident still needs work, i.e. it has not been resolved yet. */
    public boolean isOpen() {
        return open;
    }

    /** All statuses that still need work. An incident in one of these counts towards a technician's workload. */
    public static Set<Status> openStatuses() {
        return Arrays.stream(values())
                .filter(Status::isOpen)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Status.class)));
    }
}
