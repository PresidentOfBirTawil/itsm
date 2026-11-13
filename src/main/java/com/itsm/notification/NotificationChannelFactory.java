package com.itsm.notification;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Hands out the channels a notification should go through (the Factory
 * pattern). The code that sends notifications asks this factory and gets
 * back a list of {@link NotificationChannel}s; it never names a concrete
 * channel itself. Which channels exist, and which news goes where, is
 * decided here and nowhere else.
 */
@Component
public class NotificationChannelFactory {

    private final InAppChannel inApp;
    private final LiveChannel live;
    private final EmailChannel email;

    public NotificationChannelFactory(InAppChannel inApp, LiveChannel live, EmailChannel email) {
        this.inApp = inApp;
        this.live = live;
        this.email = email;
    }

    /** Every channel the application has. */
    public List<NotificationChannel> allChannels() {
        return List.of(inApp, live, email);
    }

    /**
     * The channels for one kind of news: always the app (the stored
     * notification, then the live update of the bell), and email as well if
     * the news is important enough.
     */
    public List<NotificationChannel> channelsFor(NotificationType type) {
        return type.isEmailed() ? List.of(inApp, live, email) : List.of(inApp, live);
    }
}
