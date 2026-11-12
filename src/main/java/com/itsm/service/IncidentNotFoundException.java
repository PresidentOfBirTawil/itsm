package com.itsm.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when someone asks for an incident code that does not exist. Shown as the 404 page. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException(String code) {
        super("There is no incident " + code);
    }
}
