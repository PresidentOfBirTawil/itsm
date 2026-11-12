package com.itsm.service;

/** Thrown when someone tries to register with an email address that already has an account. */
public class EmailAlreadyRegisteredException extends RuntimeException {

    private final String email;

    public EmailAlreadyRegisteredException(String email) {
        super("An account with the email " + email + " already exists");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
