package com.quell.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.quell.webhook.WebhookEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One message for one webhook, from the moment it is decided until it has
 * been delivered or given up. Saved in the same transaction as the incident
 * change that caused it (a transactional outbox), so a change is never
 * announced without having happened, and never lost if sending fails: it is
 * retried with growing pauses.
 */
@Entity
@Table(name = "webhook_deliveries", indexes = @Index(name = "idx_webhook_deliveries_due", columnList = "state, nextAttemptAt"))
public class WebhookDelivery {

    /** Where a delivery stands. */
    public enum State { PENDING, DELIVERED, FAILED }

    /** The pause before each retry: 30 seconds, 2 minutes, 10 minutes, 1 hour; then it is given up. */
    public static final List<Duration> RETRY_PAUSES = List.of(Duration.ofSeconds(30), Duration.ofMinutes(2),
            Duration.ofMinutes(10), Duration.ofHours(1));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "webhook_id", nullable = false)
    private Webhook webhook;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebhookEvent event;

    @Column(length = 20)
    private String incidentCode;

    /** Who made the change, what they wrote, and when: kept from the moment of the event. */
    @Column(length = 120)
    private String actorName;

    @Column(length = 2000)
    private String note;

    private LocalDateTime occurredAt;

    /** The message, built just before the first attempt (when the change is saved) and then kept for retries. */
    @Column(length = 20_000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private State state = State.PENDING;

    @Column(nullable = false)
    private int attempts;

    private Integer responseStatus;

    @Column(length = 500)
    private String error;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime nextAttemptAt;

    private LocalDateTime deliveredAt;

    protected WebhookDelivery() {
        // for JPA
    }

    /** A message about an incident event; its text is built when it is first sent. */
    public WebhookDelivery(Webhook webhook, WebhookEvent event, String incidentCode, String actorName, String note,
                           LocalDateTime occurredAt, LocalDateTime now) {
        this.webhook = Objects.requireNonNull(webhook);
        this.event = Objects.requireNonNull(event);
        this.incidentCode = Objects.requireNonNull(incidentCode);
        this.actorName = actorName;
        this.note = note == null || note.length() <= 2000 ? note : note.substring(0, 2000);
        this.occurredAt = occurredAt;
        this.createdAt = now;
        this.nextAttemptAt = now;
    }

    /** A message whose text is known already, such as a test message. */
    public WebhookDelivery(Webhook webhook, WebhookEvent event, String payload, LocalDateTime now) {
        this.webhook = Objects.requireNonNull(webhook);
        this.event = Objects.requireNonNull(event);
        this.payload = Objects.requireNonNull(payload);
        this.createdAt = now;
        this.occurredAt = now;
        this.nextAttemptAt = now;
    }

    /** Fixes the text before the first attempt; retries send exactly the same. */
    public void prepare(String builtPayload) {
        if (payload == null) {
            payload = Objects.requireNonNull(builtPayload);
        }
    }

    /** The incident is gone (for example after a demo reset): nothing to tell any more. */
    public void abandon(String reason, LocalDateTime now) {
        attempts++;
        error = reason;
        state = State.FAILED;
        nextAttemptAt = null;
    }

    /** The receiver accepted it. */
    public void delivered(int status, LocalDateTime now) {
        attempts++;
        responseStatus = status;
        error = null;
        state = State.DELIVERED;
        deliveredAt = now;
        nextAttemptAt = null;
    }

    /** It did not work: try again later, or give up after the last pause. */
    public void failed(Integer status, String reason, LocalDateTime now) {
        attempts++;
        responseStatus = status;
        error = reason == null ? null : reason.length() > 500 ? reason.substring(0, 500) : reason;
        if (attempts > RETRY_PAUSES.size()) {
            state = State.FAILED;
            nextAttemptAt = null;
        } else {
            nextAttemptAt = now.plus(RETRY_PAUSES.get(attempts - 1));
        }
    }

    /** "Retry" in the delivery log: once more, now. */
    public void retryNow(LocalDateTime now) {
        state = State.PENDING;
        nextAttemptAt = now;
    }

    public Long getId() {
        return id;
    }

    public Webhook getWebhook() {
        return webhook;
    }

    public WebhookEvent getEvent() {
        return event;
    }

    public String getIncidentCode() {
        return incidentCode;
    }

    public String getPayload() {
        return payload;
    }

    public String getActorName() {
        return actorName;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public State getState() {
        return state;
    }

    public int getAttempts() {
        return attempts;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getError() {
        return error;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }
}
