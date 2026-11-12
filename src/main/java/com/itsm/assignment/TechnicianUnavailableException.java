package com.itsm.assignment;

import com.itsm.BusinessRuleException;

/** Thrown when no technician, or the chosen technician, can take an incident. */
public class TechnicianUnavailableException extends BusinessRuleException {

    public TechnicianUnavailableException(String message) {
        super(message);
    }
}
