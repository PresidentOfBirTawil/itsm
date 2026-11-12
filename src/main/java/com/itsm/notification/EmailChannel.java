package com.itsm.notification;

import com.itsm.domain.EmailMessage;
import com.itsm.repository.EmailMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A simulated email channel. Nothing is really sent: each email is written to
 * the outbox table, where an administrator can read it, and to the log.
 * Swapping this class for one that talks to a mail server would not change
 * any other code.
 */
@Component
public class EmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);

    private final EmailMessageRepository outbox;

    public EmailChannel(EmailMessageRepository outbox) {
        this.outbox = outbox;
    }

    @Override
    public String getName() {
        return "email";
    }

    @Override
    public void send(NotificationMessage message) {
        outbox.save(new EmailMessage(message.recipientEmail(), message.recipientName(),
                message.title(), message.body()));
        log.info("Simulated email to {} <{}>: {}", message.recipientName(), message.recipientEmail(),
                message.title());
    }
}
