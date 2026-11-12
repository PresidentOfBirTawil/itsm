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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A problem with an IT service, reported by an employee and worked on by a technician.
 *
 * <p>An incident points at other objects that exist on their own: the employee
 * who reported it, the service it affects, its category and the technician it
 * is assigned to. Deleting an incident deletes none of them.</p>
 */
@Entity
@Table(name = "incidents")
public class Incident {

    private static final String CODE_FORMAT = "INC-%04d";

    /** The id comes from a database sequence. Allocation size 1 keeps the numbers going 1, 2, 3, … */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "incident_seq")
    @SequenceGenerator(name = "incident_seq", sequenceName = "incident_seq", allocationSize = 1)
    private Long id;

    /** The reference people quote, e.g. INC-0001. Built from the id when the incident is first saved. */
    @Column(nullable = false, unique = true, updatable = false, length = 12)
    private String code;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String title;

    @NotBlank
    @Size(max = 4000)
    @Column(nullable = false, length = 4000)
    private String description;

    /** Only employees report incidents, so the reporter is an Employee, not just any User. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false, updatable = false)
    private Employee reporter;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ITService service;

    /** Empty until the incident has been classified. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Impact impact;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Urgency urgency;

    /** Empty until a priority has been worked out. Set together with the deadlines in {@link #applySla}. */
    @Enumerated(EnumType.STRING)
    @Column(length = 5)
    private Priority priority;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.NEW;

    /** Only technicians work on incidents, so the assignee is a Technician. Empty until assigned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private Technician assignee;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** When a technician first acknowledged the incident. Compared with {@link #responseDue}. */
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    /** When the incident was resolved. Compared with {@link #resolutionDue}. */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** SLA deadline for the first response. */
    @Column(name = "response_due")
    private LocalDateTime responseDue;

    /** SLA deadline for the resolution. */
    @Column(name = "resolution_due")
    private LocalDateTime resolutionDue;

    /** Required by JPA. */
    protected Incident() {
    }

    public Incident(String title, String description, Employee reporter, ITService service,
                    Impact impact, Urgency urgency) {
        this.title = title == null ? null : title.trim();
        this.description = description == null ? null : description.trim();
        this.reporter = reporter;
        this.service = service;
        this.impact = impact;
        this.urgency = urgency;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Runs once, just before the incident is first written to the database.
     * By then the database sequence has handed out the id, so the public code
     * can be built from it: id 1 becomes INC-0001, id 12 becomes INC-0012.
     */
    @PrePersist
    void assignCode() {
        if (id == null) {
            throw new IllegalStateException("The incident needs an id before it can get a code");
        }
        code = codeFor(id);
    }

    /** The code that belongs to an incident number, e.g. 7 gives INC-0007. */
    public static String codeFor(long number) {
        return CODE_FORMAT.formatted(number);
    }

    /**
     * Applies an SLA policy: the incident takes the policy's priority, and both
     * deadlines are counted from the moment the incident was created.
     */
    public void applySla(SlaPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("An SLA policy is required");
        }
        this.priority = policy.getPriority();
        this.responseDue = policy.responseDueFrom(createdAt);
        this.resolutionDue = policy.resolutionDueFrom(createdAt);
    }

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? null : title.trim();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? null : description.trim();
    }

    public Employee getReporter() {
        return reporter;
    }

    public ITService getService() {
        return service;
    }

    public void setService(ITService service) {
        this.service = service;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Impact getImpact() {
        return impact;
    }

    public void setImpact(Impact impact) {
        this.impact = impact;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    public void setUrgency(Urgency urgency) {
        this.urgency = urgency;
    }

    public Priority getPriority() {
        return priority;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Technician getAssignee() {
        return assignee;
    }

    public void setAssignee(Technician assignee) {
        this.assignee = assignee;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public LocalDateTime getResponseDue() {
        return responseDue;
    }

    public LocalDateTime getResolutionDue() {
        return resolutionDue;
    }

    @Override
    public String toString() {
        return code == null ? "Incident (not saved yet)" : code;
    }
}
