package com.quell.notification;

import com.quell.live.LiveUpdates;
import com.quell.repository.NotificationRepository;
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

    /**
     * Updates the bell, and passes on what the notification says so that the
     * browser can show a desktop alert. Clicking it opens the notification,
     * which marks it read and goes to the incident.
     */
    @Override
    public void send(NotificationMessage message) {
        long unread = notifications.countByRecipientIdAndReadAtIsNull(message.recipientId());
        String url = notifications.findFirstByRecipientIdOrderByIdDesc(message.recipientId())
                .map(stored -> "/notifications/" + stored.getId())
                .orElse("/notifications");
        liveUpdates.notified(message.recipientId(), unread, message.title(), message.body(), url);
    }
}
