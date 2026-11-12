package com.itsm.error;

/** Thrown when no technician can take an incident, or the chosen technician is not available. */
public class TechnicianUnavailableException extends ItsmException {

    public TechnicianUnavailableException(String message) {
        super(message);
    }
}
