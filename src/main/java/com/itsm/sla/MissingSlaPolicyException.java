package com.itsm.sla;

import com.itsm.BusinessRuleException;
import com.itsm.domain.Priority;

/** Thrown when an incident gets a priority for which no SLA policy has been set up. */
public class MissingSlaPolicyException extends BusinessRuleException {

    public MissingSlaPolicyException(Priority priority) {
        super("No SLA policy is set up for priority %s. Ask an administrator to add one.".formatted(priority));
    }
}
