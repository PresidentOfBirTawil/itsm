package com.itsm.notification;

import com.itsm.domain.Notification;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.NotificationRepository;
import com.itsm.repository.UserRepository;
import org.springframework.stereotype.Component;

/** Stores the message as a row the recipient sees behind the bell in the navbar. */
@Component
public class InAppChannel implements NotificationChannel {

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final IncidentRepository incidents;

    public InAppChannel(NotificationRepository notifications, UserRepository users, IncidentRepository incidents) {
        this.notifications = notifications;
        this.users = users;
        this.incidents = incidents;
    }

    @Override
    public String getName() {
        return "in-app";
    }

    @Override
    public void send(NotificationMessage message) {
        notifications.save(new Notification(
                users.getReferenceById(message.recipientId()),
                message.incidentId() == null ? null : incidents.getReferenceById(message.incidentId()),
                message.title(), message.body()));
    }
}
