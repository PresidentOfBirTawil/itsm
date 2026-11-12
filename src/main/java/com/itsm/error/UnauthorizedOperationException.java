package com.itsm.error;

/**
 * Thrown when a signed-in user tries something they are not allowed to do,
 * such as opening someone else's incident or pressing another role's button.
 * Shown as the 403 page.
 */
public class UnauthorizedOperationException extends ItsmException {

    public UnauthorizedOperationException(String message) {
        super(message);
    }
}
