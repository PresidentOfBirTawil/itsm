package com.itsm.webhook;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import com.itsm.domain.Incident;
import com.itsm.domain.WebhookDelivery;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.WebhookDeliveryRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends the queued webhook messages: right after the change that caused them
 * ({@link #dispatchSoon}), and every few seconds for retries that have come
 * due. One sending round at a time, so a message is never sent twice in
 * parallel. Sending happens outside any database transaction.
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
    static final int BATCH = 50;

    private final WebhookDeliveryRepository deliveries;
    private final IncidentRepository incidents;
    private final WebhookPayloads payloads;
    private final WebhookSender sender;
    private final TransactionTemplate transaction;
    private final ReentrantLock sending = new ReentrantLock();
    private final AtomicBoolean queued = new AtomicBoolean();
    private final ExecutorService background = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "webhooks");
        thread.setDaemon(true);
        return thread;
    });

    public WebhookDispatcher(WebhookDeliveryRepository deliveries, IncidentRepository incidents, WebhookPayloads payloads,
                             WebhookSender sender, PlatformTransactionManager transactionManager) {
        this.deliveries = deliveries;
        this.incidents = incidents;
        this.payloads = payloads;
        this.sender = sender;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Sends what is due in the background, as soon as possible. */
    public void dispatchSoon() {
        if (queued.compareAndSet(false, true)) {
            background.execute(() -> {
                queued.set(false);
                dispatchDue();
            });
        }
    }

    /** The safety net: retries that have come due, and anything a restart interrupted. */
    @Scheduled(fixedDelayString = "${itsm.webhooks.retry-check-ms:15000}", initialDelay = 15000)
    public void retryDue() {
        dispatchDue();
    }

    /**
     * Sends every message that is due now.
     *
     * @return how many were sent successfully
     */
    public int dispatchDue() {
        if (!sending.tryLock()) {
            return 0;                        // another round is running; it will see these too
        }
        try {
            int ok = 0;
            List<WebhookDelivery> due;
            do {
                due = deliveries.findByStateAndNextAttemptAtLessThanEqualOrderByIdAsc(WebhookDelivery.State.PENDING,
                        LocalDateTime.now(), Limit.of(BATCH));
                for (WebhookDelivery delivery : due) {
                    if (send(delivery)) {
                        ok++;
                    }
                }
            } while (due.size() == BATCH);
            return ok;
        } catch (RuntimeException e) {
            log.warn("Sending webhooks failed; it is tried again shortly: {}", e.toString());
            return 0;
        } finally {
            sending.unlock();
        }
    }

    /** Sends one message and records the outcome. */
    public boolean send(WebhookDelivery delivery) {
        Long id = delivery.getId();
        String body = transaction.execute(status -> prepare(id));
        if (body == null) {
            return false;                     // the incident is gone; recorded as failed
        }
        WebhookSender.Result result = sender.send(delivery.getWebhook().getUrl(), body,
                delivery.getWebhook().getSecret(), delivery.getEvent().getName(), id);
        transaction.executeWithoutResult(status -> deliveries.findById(id).ifPresent(saved -> {
            if (result.ok()) {
                saved.delivered(result.status(), LocalDateTime.now());
            } else {
                saved.failed(result.status(), result.error(), LocalDateTime.now());
                log.info("Webhook {} delivery {} failed (attempt {}): {}", saved.getWebhook().getName(), id,
                        saved.getAttempts(), result.error());
            }
        }));
        return result.ok();
    }

    /** The message text: built from the incident as it is now on the first attempt, the same on every retry. */
    private String prepare(Long id) {
        WebhookDelivery saved = deliveries.findById(id).orElse(null);
        if (saved == null) {
            return null;
        }
        if (saved.getPayload() == null) {
            Incident incident = incidents.findByCode(saved.getIncidentCode()).orElse(null);
            if (incident == null) {
                saved.abandon("The incident no longer exists.", LocalDateTime.now());
                return null;
            }
            saved.prepare(payloads.build(saved.getWebhook().getKind(), saved.getEvent(),
                    WebhookPayloads.Facts.of(incident, saved.getActorName(), saved.getNote(), saved.getOccurredAt())));
        }
        return saved.getPayload();
    }

    @PreDestroy
    void stop() {
        background.shutdownNow();
    }
}
