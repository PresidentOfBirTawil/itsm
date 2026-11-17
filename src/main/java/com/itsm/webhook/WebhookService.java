package com.itsm.webhook;

import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.itsm.domain.Webhook;
import com.itsm.domain.WebhookDelivery;
import com.itsm.error.InvalidInputException;
import com.itsm.error.NotFoundException;
import com.itsm.repository.WebhookDeliveryRepository;
import com.itsm.repository.WebhookRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Managing webhooks (administrators only): add, change, remove, test, and the delivery log. */
@Service
public class WebhookService {

    static final int LOG_SIZE = 50;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookRepository webhooks;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookPayloads payloads;
    private final WebhookDispatcher dispatcher;

    public WebhookService(WebhookRepository webhooks, WebhookDeliveryRepository deliveries, WebhookPayloads payloads,
                          WebhookDispatcher dispatcher) {
        this.webhooks = webhooks;
        this.deliveries = deliveries;
        this.payloads = payloads;
        this.dispatcher = dispatcher;
    }

    @Transactional(readOnly = true)
    public List<Webhook> all() {
        return webhooks.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Webhook get(Long id) {
        return webhooks.findById(id).orElseThrow(() -> new NotFoundException("There is no webhook %d.".formatted(id)));
    }

    @Transactional
    public Webhook create(String name, WebhookKind kind, String url, Set<WebhookEvent> events, boolean majorOnly) {
        check(name, kind, url, events);
        byte[] secret = new byte[24];
        RANDOM.nextBytes(secret);
        return webhooks.save(new Webhook(name.strip(), kind, url.strip(), "whsec_" + HexFormat.of().formatHex(secret),
                events, majorOnly));
    }

    @Transactional
    public Webhook update(Long id, String name, WebhookKind kind, String url, Set<WebhookEvent> events, boolean majorOnly,
                          boolean enabled) {
        check(name, kind, url, events);
        Webhook webhook = get(id);
        webhook.update(name.strip(), kind, url.strip(), events, majorOnly, enabled);
        return webhook;
    }

    @Transactional
    public void delete(Long id) {
        Webhook webhook = get(id);
        deliveries.deleteByWebhook(id);
        webhooks.delete(webhook);
    }

    /**
     * Sends a test message straight away and waits for the answer.
     *
     * @return the delivery, with its outcome
     */
    public WebhookDelivery test(Long id) {
        Webhook webhook = get(id);
        WebhookDelivery delivery = deliveries.save(new WebhookDelivery(webhook, WebhookEvent.TEST,
                payloads.build(webhook.getKind(), WebhookEvent.TEST, payloads.testFacts()), LocalDateTime.now()));
        dispatcher.send(delivery);
        return deliveries.findById(delivery.getId()).orElseThrow();
    }

    /** "Retry" in the delivery log. */
    @Transactional
    public void retry(Long deliveryId) {
        WebhookDelivery delivery = deliveries.findById(deliveryId)
                .orElseThrow(() -> new NotFoundException("There is no delivery %d.".formatted(deliveryId)));
        delivery.retryNow(LocalDateTime.now());
        dispatcher.dispatchSoon();
    }

    /** The latest deliveries, newest first. */
    @Transactional(readOnly = true)
    public List<WebhookDelivery> log(Long webhookId) {
        return webhookId == null ? deliveries.findAllByOrderByIdDesc(Limit.of(LOG_SIZE))
                : deliveries.findByWebhookIdOrderByIdDesc(webhookId, Limit.of(LOG_SIZE));
    }

    private static void check(String name, WebhookKind kind, String url, Set<WebhookEvent> events) {
        if (name == null || name.isBlank() || name.strip().length() > 80) {
            throw new InvalidInputException("Give the webhook a name of 1 to 80 characters.");
        }
        if (kind == null) {
            throw new InvalidInputException("Choose where the webhook sends to.");
        }
        if (events == null || events.isEmpty() || events.contains(WebhookEvent.TEST)) {
            throw new InvalidInputException("Choose at least one event to send.");
        }
        String address = url == null ? "" : url.strip();
        URI uri;
        try {
            uri = URI.create(address);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("\"%s\" is not a web address.".formatted(address));
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http") || uri.getHost() == null || address.length() > 500) {
            throw new InvalidInputException("The address must start with https:// (or http://) and be at most 500 characters.");
        }
        if (uri.getUserInfo() != null) {
            throw new InvalidInputException("Put no user name or password in the address.");
        }
    }
}
