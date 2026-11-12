package com.itsm.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.itsm.BusinessRuleException;
import com.itsm.domain.state.IncidentState;
import com.itsm.domain.state.IncidentStates;
import com.itsm.domain.state.InvalidStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
 *
 * <p>The status, the priority and the assignee have no setters. They change
 * only through {@link #transitionTo}, {@link #prioritise} and {@link #assignTo},
 * which check the rules first.</p>
 */
@Entity
@Table(name = "incidents")
public class Incident {

    private static final String CODE_FORMAT = "INC-%04d";

    /** The share of an SLA's time after which the incident counts as "at risk". */
    public static final double AT_RISK_FRACTION = 0.75;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(10)")
    private Impact impact;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(10)")
    private Urgency urgency;

    /** The priority and how it was calculated. Empty until {@link #prioritise} has been called. */
    @Embedded
    private PriorityAssessment assessment;

    /** Changed only by {@link #transitionTo}. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
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

    /** Where the incident stands against its SLA. Kept up to date by {@link #refreshSla}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "sla_status", columnDefinition = "varchar(10)")
    private SlaStatus slaStatus;

    /** When the resolution clock was stopped, while the incident is waiting for information. */
    @Column(name = "sla_paused_at")
    private LocalDateTime slaPausedAt;

    /** How long the resolution clock has stood still in total, in pauses that are over. */
    @Column(name = "sla_paused_seconds")
    private Long slaPausedSeconds;

    /** When a missed response deadline was noticed. Recorded once, never changed. */
    @Column(name = "response_breached_at")
    private LocalDateTime responseBreachedAt;

    /** When a missed resolution deadline was noticed. Recorded once, never changed. */
    @Column(name = "resolution_breached_at")
    private LocalDateTime resolutionBreachedAt;

    /** Required by JPA. */
    protected Incident() {
    }

    /** A new incident always starts in status NEW, with no priority and no assignee. */
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

    // ---- code ----

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

    // ---- lifecycle ----

    /** The object that holds the rules for the status this incident is in right now. */
    public IncidentState getState() {
        return IncidentStates.of(status);
    }

    /**
     * Moves the incident to another status. This is the only way the status changes.
     *
     * <p>The current state decides whether the move is allowed, and the new
     * state checks that the incident is ready for it. Both checks happen before
     * anything is changed, so a refused move leaves the incident exactly as it was.</p>
     *
     * @throws InvalidStateTransitionException if the lifecycle does not allow the move
     */
    public void transitionTo(Status target) {
        transitionTo(target, LocalDateTime.now());
    }

    /** The same, at a given moment. The moment is used for the times the states record. */
    public void transitionTo(Status target, LocalDateTime now) {
        IncidentState current = getState();
        if (target == null || !current.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(this, target);
        }
        IncidentState next = IncidentStates.of(target);
        next.checkEntry(this);

        Status previous = status;
        current.onExit(this, target, now);
        status = target;
        next.onEnter(this, previous, now);
    }

    /**
     * Sets the priority and, from the matching SLA policy, the two deadlines
     * (counted from the moment the incident was created). A new incident
     * becomes CLASSIFIED as soon as this has happened.
     */
    public void prioritise(PriorityAssessment assessment, SlaPolicy policy) {
        if (assessment == null || policy == null) {
            throw new IllegalArgumentException("A priority assessment and an SLA policy are required");
        }
        if (policy.getPriority() != assessment.priority()) {
            throw new IllegalArgumentException("The SLA policy is for %s but the priority is %s"
                    .formatted(policy.getPriority(), assessment.priority()));
        }
        this.assessment = assessment;
        this.responseDue = policy.responseDueFrom(createdAt);
        // Time the clock has already stood still does not count against the new deadline either.
        this.resolutionDue = policy.resolutionDueFrom(createdAt).plusSeconds(getSlaPausedSeconds());
        if (slaStatus == null) {
            slaStatus = SlaStatus.ON_TRACK;
        }
        getState().onPrioritised(this);
    }

    /**
     * Gives the incident to a technician. A classified incident becomes
     * ASSIGNED; an incident that already has a technician keeps its status and
     * simply changes hands.
     *
     * @throws BusinessRuleException if the incident is in a status where it cannot be assigned
     */
    public void assignTo(Technician technician) {
        if (technician == null) {
            throw new IllegalArgumentException("A technician is required");
        }
        if (!getState().allowsAssignment()) {
            throw new BusinessRuleException("%s cannot be assigned while it is %s."
                    .formatted(code == null ? "The incident" : code, status.getLabel().toLowerCase()));
        }
        this.assignee = technician;
        getState().onAssigned(this);
    }

    /** Records the first time a technician acknowledged the incident. Called by the ACKNOWLEDGED state. */
    public void stampAcknowledged(LocalDateTime when) {
        if (acknowledgedAt == null) {
            acknowledgedAt = when;
        }
    }

    /** Records when the incident was resolved. Called by the RESOLVED state. */
    public void stampResolved(LocalDateTime when) {
        resolvedAt = when;
    }

    /**
     * The incident was reopened, so it is no longer resolved. The technician
     * was not working on it while it counted as resolved, so the resolution
     * deadline moves back by that time. Called by the RESOLVED state.
     */
    public void reopen(LocalDateTime when) {
        if (resolvedAt != null) {
            extendResolutionDeadline(Duration.between(resolvedAt, when));
        }
        resolvedAt = null;
    }

    /** Records when the incident was closed. Called by the CLOSED state. */
    public void stampClosed(LocalDateTime when) {
        closedAt = when;
    }

    // ---- SLA ----

    /** The response deadline: met when a technician acknowledges the incident. It is never paused. */
    public SlaTarget responseTarget() {
        return new SlaTarget("response", createdAt, responseDue, acknowledgedAt, Duration.ZERO, null);
    }

    /** The resolution deadline: met when the incident is resolved. It pauses while waiting for information. */
    public SlaTarget resolutionTarget() {
        return new SlaTarget("resolution", createdAt, resolutionDue, resolvedAt,
                Duration.ofSeconds(getSlaPausedSeconds()), slaPausedAt);
    }

    /** Stops the resolution clock. Called by the AWAITING_INFO state when the incident enters it. */
    public void pauseSlaClock(LocalDateTime when) {
        if (slaPausedAt == null) {
            slaPausedAt = when;
        }
    }

    /**
     * Starts the resolution clock again and moves the deadline back by the
     * time it stood still. Called by the AWAITING_INFO state when the incident leaves it.
     */
    public void resumeSlaClock(LocalDateTime when) {
        if (slaPausedAt != null) {
            extendResolutionDeadline(Duration.between(slaPausedAt, when));
            slaPausedAt = null;
        }
    }

    private void extendResolutionDeadline(Duration standstill) {
        long seconds = Math.max(0, standstill.getSeconds());
        if (resolutionDue != null) {
            resolutionDue = resolutionDue.plusSeconds(seconds);
        }
        slaPausedSeconds = getSlaPausedSeconds() + seconds;
    }

    /**
     * Brings the SLA status up to date for the moment {@code now} and reports
     * what is new. A missed deadline is recorded once, with the time it was
     * noticed; later calls do not report it again.
     *
     * @return what people should be told about: empty if nothing changed
     */
    public List<SlaSignal> refreshSla(LocalDateTime now) {
        if (assessment == null) {
            return List.of(); // no priority yet, so no deadlines
        }
        List<SlaSignal> signals = new ArrayList<>();
        SlaTarget response = responseTarget();
        SlaTarget resolution = resolutionTarget();

        if (responseBreachedAt == null && response.isLate(now)) {
            responseBreachedAt = now;
            signals.add(SlaSignal.breached(response));
        }
        if (resolutionBreachedAt == null && resolution.isLate(now)) {
            resolutionBreachedAt = now;
            signals.add(SlaSignal.breached(resolution));
        }

        SlaStatus before = slaStatus;
        SlaTarget closeToDeadline = isCloseToDeadline(response, now) ? response
                : isCloseToDeadline(resolution, now) ? resolution : null;
        if (isSlaBreached()) {
            slaStatus = SlaStatus.BREACHED;
        } else if (slaPausedAt != null) {
            slaStatus = SlaStatus.PAUSED;
        } else if (resolvedAt != null) {
            slaStatus = SlaStatus.MET;
        } else if (closeToDeadline != null) {
            slaStatus = SlaStatus.AT_RISK;
        } else {
            slaStatus = SlaStatus.ON_TRACK;
        }
        if (slaStatus == SlaStatus.AT_RISK && before != SlaStatus.AT_RISK) {
            signals.add(SlaSignal.atRisk(closeToDeadline));
        }
        return signals;
    }

    private static boolean isCloseToDeadline(SlaTarget target, LocalDateTime now) {
        return target.isRunning() && target.fractionUsed(now) >= AT_RISK_FRACTION;
    }

    public SlaStatus getSlaStatus() {
        return slaStatus;
    }

    /** True if either deadline was missed. */
    public boolean isSlaBreached() {
        return responseBreachedAt != null || resolutionBreachedAt != null;
    }

    public LocalDateTime getResponseBreachedAt() {
        return responseBreachedAt;
    }

    public LocalDateTime getResolutionBreachedAt() {
        return resolutionBreachedAt;
    }

    public LocalDateTime getSlaPausedAt() {
        return slaPausedAt;
    }

    /** Total time the resolution clock has stood still in pauses that are over, in seconds. */
    public long getSlaPausedSeconds() {
        return slaPausedSeconds == null ? 0 : slaPausedSeconds;
    }

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    // ---- reading ----

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

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Impact getImpact() {
        return impact;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    /** The priority, or null if it has not been calculated yet. */
    public Priority getPriority() {
        return assessment == null ? null : assessment.priority();
    }

    /** How the priority was calculated, or null if it has not been calculated yet. */
    public PriorityAssessment getAssessment() {
        return assessment;
    }

    public Status getStatus() {
        return status;
    }

    public Technician getAssignee() {
        return assignee;
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

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public LocalDateTime getResponseDue() {
        return responseDue;
    }

    public LocalDateTime getResolutionDue() {
        return resolutionDue;
    }

    /** True if the incident is still open and its resolution deadline has passed. */
    public boolean isOverdue() {
        return status.isOpen() && resolutionDue != null && resolutionTarget().isLate(LocalDateTime.now());
    }

    @Override
    public String toString() {
        return code == null ? "Incident (not saved yet)" : code;
    }
}
