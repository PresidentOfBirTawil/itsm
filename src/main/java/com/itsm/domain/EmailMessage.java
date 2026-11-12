package com.itsm.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An email the system would have sent. Emails are simulated: instead of going
 * to a mail server they are kept in this outbox, where an administrator can read them.
 */
@Entity
@Table(name = "email_outbox")
public class EmailMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "to_address", nullable = false, updatable = false, length = 150)
    private String toAddress;

    @Column(name = "to_name", updatable = false, length = 100)
    private String toName;

    @Column(nullable = false, updatable = false, length = 200)
    private String subject;

    @Column(updatable = false, length = 2000)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Required by JPA. */
    protected EmailMessage() {
    }

    public EmailMessage(String toAddress, String toName, String subject, String body) {
        this.toAddress = toAddress;
        this.toName = toName;
        this.subject = subject;
        this.body = body;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getToAddress() {
        return toAddress;
    }

    public String getToName() {
        return toName;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
