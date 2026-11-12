package com.itsm.error;

/** Thrown when something that was typed or sent cannot be used, and the form's own checks could not catch it. */
public class InvalidInputException extends ItsmException {

    public InvalidInputException(String message) {
        super(message);
    }
}
