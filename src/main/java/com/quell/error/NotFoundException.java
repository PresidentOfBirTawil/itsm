package com.quell.error;

/** Thrown when the thing that was asked for does not exist. Shown as the 404 page. */
public class NotFoundException extends QuellException {

    public NotFoundException(String message) {
        super(message);
    }
}
