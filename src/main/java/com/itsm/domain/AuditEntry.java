package com.itsm.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One line of an incident's history: what happened, when, who did it, and how
 * the status changed. Entries are written once and never edited, which is why
 * this class has no setters.
 */
@Entity
@Table(name = "audit_entries")
public class AuditEntry {

    public static final int MAX_NOTE_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The incident this entry belongs to. An entry cannot exist without one. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, updatable = false)
    private Incident incident;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    /** The user who did it. Empty when the system acted on its own. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", updatable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "varchar(30)")
    private IncidentAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", updatable = false, columnDefinition = "varchar(20)")
    private Status oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", updatable = false, columnDefinition = "varchar(20)")
    private Status newStatus;

    @Column(updatable = false, length = MAX_NOTE_LENGTH)
    private String note;

    /** Required by JPA. */
    protected AuditEntry() {
    }

    public AuditEntry(Incident incident, LocalDateTime occurredAt, User actor, IncidentAction action,
                      Status oldStatus, Status newStatus, String note) {
        this.incident = incident;
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.action = action;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.note = shorten(note);
    }

    private static String shorten(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.length() <= MAX_NOTE_LENGTH ? trimmed : trimmed.substring(0, MAX_NOTE_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public Incident getIncident() {
        return incident;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public User getActor() {
        return actor;
    }

    /** The name to show for who did it: the user's name, or "System". */
    public String getActorName() {
        return actor == null ? "System" : actor.getName();
    }

    public IncidentAction getAction() {
        return action;
    }

    public Status getOldStatus() {
        return oldStatus;
    }

    public Status getNewStatus() {
        return newStatus;
    }

    /** True when this entry moved the incident from one status to another. */
    public boolean isStatusChange() {
        return newStatus != null && newStatus != oldStatus;
    }

    public String getNote() {
        return note;
    }
}
