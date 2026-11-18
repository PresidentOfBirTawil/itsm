package com.quell.webhook;

import java.time.LocalDateTime;

import com.quell.domain.Incident;
import com.quell.domain.Priority;
import com.quell.domain.Webhook;
import com.quell.domain.WebhookDelivery;
import com.quell.event.IncidentEvent;
import com.quell.event.IncidentEventListener;
import com.quell.priority.PriorityCalculator;
import com.quell.repository.WebhookDeliveryRepository;
import com.quell.repository.WebhookRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Another observer of incident events: decides which webhooks want to hear
 * about an event and queues one message for each, in the same transaction
 * as the change itself (a transactional outbox). Sending happens after the
 * commit, in {@link WebhookDispatcher}, so a slow or broken receiver never
 * slows down or breaks the change. The message text is built just before it
 * is first sent, when everything the change did (priority, technician) is in.
 */
@Component
public class WebhookListener implements IncidentEventListener {

    private final WebhookRepository webhooks;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookDispatcher dispatcher;
    private final PriorityCalculator priorityCalculator;

    public WebhookListener(WebhookRepository webhooks, WebhookDeliveryRepository deliveries, WebhookDispatcher dispatcher,
                           PriorityCalculator priorityCalculator) {
        this.webhooks = webhooks;
        this.deliveries = deliveries;
        this.dispatcher = dispatcher;
        this.priorityCalculator = priorityCalculator;
    }

    @Override
    public void onIncidentEvent(IncidentEvent event) {
        WebhookEvent kind = WebhookEvent.of(event).orElse(null);
        if (kind == null) {
            return;
        }
        Incident incident = event.incident();
        LocalDateTime now = LocalDateTime.now();
        boolean queued = false;
        // A new incident is announced a moment before its priority is set; work it out the same way here.
        Priority priority = incident.getPriority() != null ? incident.getPriority()
                : priorityCalculator.calculate(incident).priority();
        for (Webhook webhook : webhooks.findByEnabledTrue()) {
            if (webhook.wants(kind, priority)) {
                deliveries.save(new WebhookDelivery(webhook, kind, incident.getCode(),
                        event.actor() == null ? "System" : event.actor().getName(), event.note(), event.occurredAt(), now));
                queued = true;
            }
        }
        if (queued) {
            sendAfterCommit();
        }
    }

    /** Sends at once when the change is saved; the dispatcher's timer is the safety net. */
    private void sendAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatcher.dispatchSoon();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatcher.dispatchSoon();
            }
        });
    }
}
