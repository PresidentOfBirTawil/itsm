package com.itsm.service;

import com.itsm.error.InvalidInputException;

/**
 * Thrown when someone tries to register with an email address that already
 * has an account. It is a kind of invalid input, so it belongs to the
 * application's one family of exceptions.
 */
public class EmailAlreadyRegisteredException extends InvalidInputException {

    private final String email;

    public EmailAlreadyRegisteredException(String email) {
        super("An account with the email " + email + " already exists");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
