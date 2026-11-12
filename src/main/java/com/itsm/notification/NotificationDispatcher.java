package com.itsm.notification;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends notification messages through their channels, in a way that keeps
 * failures apart:
 *
 * <ul>
 *   <li>Messages are sent only <b>after</b> the incident change has been
 *       committed. A notification problem can therefore never undo the change.</li>
 *   <li>Each channel sends each message in its <b>own</b> database transaction
 *       and inside its own try/catch. If one channel fails, its work is rolled
 *       back and logged, and the next channel carries on.</li>
 * </ul>
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationChannelFactory channels;
    private final TransactionOperations ownTransaction;

    @Autowired
    public NotificationDispatcher(NotificationChannelFactory channels, PlatformTransactionManager transactionManager) {
        this(channels, newTransactionEachTime(transactionManager));
    }

    /** For tests: lets them choose how (or whether) each send is wrapped in a transaction. */
    public NotificationDispatcher(NotificationChannelFactory channels, TransactionOperations ownTransaction) {
        this.channels = channels;
        this.ownTransaction = ownTransaction;
    }

    private static TransactionOperations newTransactionEachTime(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    /**
     * Sends the messages once the current database transaction has committed.
     * If that transaction is rolled back, the messages are dropped: nobody is
     * told about a change that did not happen. With no transaction running,
     * they are sent straight away.
     */
    public void sendAfterCommit(List<NotificationMessage> messages) {
        if (messages.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(messages);
                }
            });
        } else {
            send(messages);
        }
    }

    /** Sends each message through each of its channels. Never throws. */
    public void send(List<NotificationMessage> messages) {
        for (NotificationMessage message : messages) {
            for (NotificationChannel channel : channels.channelsFor(message.type())) {
                try {
                    ownTransaction.executeWithoutResult(status -> channel.send(message));
                } catch (RuntimeException e) {
                    log.error("The {} channel could not deliver \"{}\" to {}: {}",
                            channel.getName(), message.title(), message.recipientEmail(), e.toString());
                }
            }
        }
    }
}
