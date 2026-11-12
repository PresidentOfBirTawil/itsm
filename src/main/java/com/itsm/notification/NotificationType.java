package com.itsm.notification;

/** The kinds of news people are told about. */
public enum NotificationType {

    INCIDENT_CREATED(true),
    ASSIGNED(true),
    ASSIGNMENT_FAILED(true),
    /** An ordinary step forward, e.g. "acknowledged". Not worth an email. */
    STATUS_CHANGED(false),
    INFO_REQUESTED(true),
    RESOLVED(true),
    CLOSED(true),
    SLA_AT_RISK(true),
    SLA_BREACHED(true);

    private final boolean emailed;

    NotificationType(boolean emailed) {
        this.emailed = emailed;
    }

    /** True if this kind of news is important enough to be emailed as well as shown in the app. */
    public boolean isEmailed() {
        return emailed;
    }
}
