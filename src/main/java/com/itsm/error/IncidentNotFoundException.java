package com.itsm.error;

/** Thrown when someone asks for an incident code that does not exist. */
public class IncidentNotFoundException extends NotFoundException {

    public IncidentNotFoundException(String code) {
        super("There is no incident " + code + ".");
    }
}
