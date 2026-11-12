package com.itsm.error;

import com.itsm.domain.Priority;

/** Thrown when an incident gets a priority for which no SLA policy has been set up. */
public class MissingSlaConfigurationException extends ItsmException {

    public MissingSlaConfigurationException(Priority priority) {
        super("No SLA policy is set up for priority %s. Ask an administrator to add one.".formatted(priority));
    }
}
