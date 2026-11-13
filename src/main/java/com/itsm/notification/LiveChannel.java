package com.itsm.notification;

import com.itsm.live.LiveUpdates;
import com.itsm.repository.NotificationRepository;
import org.springframework.stereotype.Component;

/**
 * Updates the number on the recipient's bell in every browser tab they have
 * open, without a page refresh. It runs after the {@link InAppChannel}, so
 * the notification it counts has already been stored.
 *
 * <p>This is the third channel. Adding it took this class and one line in
 * {@link NotificationChannelFactory}; the dispatcher and the code that
 * decides who is told what did not change.</p>
 */
@Component
public class LiveChannel implements NotificationChannel {

    private final NotificationRepository notifications;
    private final LiveUpdates liveUpdates;

    public LiveChannel(NotificationRepository notifications, LiveUpdates liveUpdates) {
        this.notifications = notifications;
        this.liveUpdates = liveUpdates;
    }

    @Override
    public String getName() {
        return "live";
    }

    @Override
    public void send(NotificationMessage message) {
        liveUpdates.unreadCount(message.recipientId(),
                notifications.countByRecipientIdAndReadAtIsNull(message.recipientId()));
    }
}
