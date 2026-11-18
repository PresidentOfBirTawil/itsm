package com.quell.domain;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.quell.webhook.WebhookEvent;
import com.quell.webhook.WebhookKind;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * Another system to tell about incident changes as they happen: a Slack or
 * Teams channel, or any app that accepts JSON. Messages to apps are signed
 * with the webhook's secret, so the receiver can check they came from here.
 */
@Entity
@Table(name = "webhooks")
public class Webhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WebhookKind kind;

    @Column(nullable = false, length = 500)
    private String url;

    /** For signing what is sent (HMAC-SHA256). */
    @Column(nullable = false, length = 64)
    private String secret;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "webhook_events", joinColumns = @JoinColumn(name = "webhook_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "event", length = 20)
    private Set<WebhookEvent> events = EnumSet.noneOf(WebhookEvent.class);

    /** Only incidents with priority P1 or P2. */
    @Column(nullable = false)
    private boolean majorOnly;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Webhook() {
        // for JPA
    }

    public Webhook(String name, WebhookKind kind, String url, String secret, Set<WebhookEvent> events, boolean majorOnly) {
        this.secret = Objects.requireNonNull(secret);
        this.createdAt = LocalDateTime.now();
        update(name, kind, url, events, majorOnly, true);
    }

    public void update(String name, WebhookKind kind, String url, Set<WebhookEvent> events, boolean majorOnly,
                       boolean enabled) {
        this.name = Objects.requireNonNull(name);
        this.kind = Objects.requireNonNull(kind);
        this.url = Objects.requireNonNull(url);
        this.events = events.isEmpty() ? EnumSet.noneOf(WebhookEvent.class) : EnumSet.copyOf(events);
        this.majorOnly = majorOnly;
        this.enabled = enabled;
    }

    /** Whether this webhook wants to hear about an event on an incident of this priority. */
    public boolean wants(WebhookEvent event, Priority priority) {
        if (!enabled || !events.contains(event)) {
            return false;
        }
        return !majorOnly || priority == Priority.P1 || priority == Priority.P2;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public WebhookKind getKind() {
        return kind;
    }

    public String getUrl() {
        return url;
    }

    public String getSecret() {
        return secret;
    }

    public Set<WebhookEvent> getEvents() {
        return EnumSet.copyOf(events.isEmpty() ? EnumSet.noneOf(WebhookEvent.class) : events);
    }

    public boolean isMajorOnly() {
        return majorOnly;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** The address with its secret part hidden, e.g. "https://hooks.slack.com/…/x7Kq". */
    public String getMaskedUrl() {
        int slashes = 0;
        int cut = -1;
        for (int i = 0; i < url.length(); i++) {
            if (url.charAt(i) == '/' && ++slashes == 3) {
                cut = i;
                break;
            }
        }
        if (cut < 0 || url.length() - cut <= 8) {
            return url;
        }
        return url.substring(0, cut) + "/…" + url.substring(url.length() - 4);
    }
}
