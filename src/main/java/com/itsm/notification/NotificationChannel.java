package com.itsm.notification;

/**
 * A way of reaching a person: in the app, by email, and one day perhaps by
 * SMS. To add a channel, write a class that implements this interface and
 * hand it out from {@link NotificationChannelFactory}; nothing else changes.
 */
public interface NotificationChannel {

    /** A short name for logs and error messages, e.g. "in-app". */
    String getName();

    /** Delivers one message. May throw; a failure here must never affect another channel. */
    void send(NotificationMessage message);
}
