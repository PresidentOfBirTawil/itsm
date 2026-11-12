package com.itsm.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A message for one user, shown behind the bell in the navbar. Written by the in-app notification channel. */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, updatable = false)
    private User recipient;

    /** The incident the message is about, so the user can jump to it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", updatable = false)
    private Incident incident;

    @Column(nullable = false, updatable = false, length = 200)
    private String title;

    @Column(updatable = false, length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** When the user opened it. Empty while it is unread. */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /** Required by JPA. */
    protected Notification() {
    }

    public Notification(User recipient, Incident incident, String title, String message) {
        this.recipient = recipient;
        this.incident = incident;
        this.title = title;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    public void markRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public Long getId() {
        return id;
    }

    public User getRecipient() {
        return recipient;
    }

    public Incident getIncident() {
        return incident;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }
}
