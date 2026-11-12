package com.itsm.error;

/** Thrown when someone reports exactly the same thing again while their first report is still open. */
public class DuplicateIncidentException extends ItsmException {

    private final String existingCode;

    public DuplicateIncidentException(String existingCode) {
        super("You have already reported this: %s is still open. Add any new information there instead."
                .formatted(existingCode));
        this.existingCode = existingCode;
    }

    /** The code of the incident that was reported first. */
    public String getExistingCode() {
        return existingCode;
    }
}
