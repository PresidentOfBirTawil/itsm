package com.quell.error;

/** Thrown when no technician can take an incident, or the chosen technician is not available. */
public class TechnicianUnavailableException extends QuellException {

    public TechnicianUnavailableException(String message) {
        super(message);
    }
}
