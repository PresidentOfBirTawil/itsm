package com.itsm.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The statuses an incident can be in. The usual path is
 * NEW → CLASSIFIED → ASSIGNED → ACKNOWLEDGED → IN_PROGRESS → RESOLVED → VERIFIED → CLOSED,
 * with two side steps: IN_PROGRESS ⇄ AWAITING_INFO, and RESOLVED → IN_PROGRESS (reopen).
 *
 * <p>This enum only names the statuses. Which moves are allowed, and who may
 * make them, is decided by the state classes in {@code com.itsm.domain.state}.</p>
 */
public enum Status {

    NEW("New", true),
    CLASSIFIED("Classified", true),
    ASSIGNED("Assigned", true),
    ACKNOWLEDGED("Acknowledged", true),
    IN_PROGRESS("In progress", true),
    AWAITING_INFO("Awaiting information", true),
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

    /** The statuses in which the work is done: resolved, verified or closed. */
    public static Set<Status> finishedStatuses() {
        return EnumSet.complementOf(EnumSet.copyOf(openStatuses()));
    }
}
