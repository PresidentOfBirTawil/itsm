package com.itsm.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * The service level promised for one priority: how fast someone must respond
 * and how fast the incident must be resolved. There is one policy per priority.
 */
@Entity
@Table(name = "sla_policies")
public class SlaPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 5)
    private Priority priority;

    @Positive
    @Column(name = "response_minutes", nullable = false)
    private int responseMinutes;

    @Positive
    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;

    /** Required by JPA. */
    protected SlaPolicy() {
    }

    public SlaPolicy(Priority priority, int responseMinutes, int resolutionMinutes) {
        this.priority = priority;
        setTargets(responseMinutes, resolutionMinutes);
    }

    public Long getId() {
        return id;
    }

    public Priority getPriority() {
        return priority;
    }

    public int getResponseMinutes() {
        return responseMinutes;
    }

    public int getResolutionMinutes() {
        return resolutionMinutes;
    }

    /** Changes both targets together so that they always make sense as a pair. */
    public void setTargets(int responseMinutes, int resolutionMinutes) {
        if (responseMinutes <= 0 || resolutionMinutes <= 0) {
            throw new IllegalArgumentException("SLA targets must be positive");
        }
        if (responseMinutes > resolutionMinutes) {
            throw new IllegalArgumentException("The response target cannot be later than the resolution target");
        }
        this.responseMinutes = responseMinutes;
        this.resolutionMinutes = resolutionMinutes;
    }

    /** The moment by which someone must have responded to an incident opened at {@code start}. */
    public LocalDateTime responseDueFrom(LocalDateTime start) {
        return start.plusMinutes(responseMinutes);
    }

    /** The moment by which an incident opened at {@code start} must be resolved. */
    public LocalDateTime resolutionDueFrom(LocalDateTime start) {
        return start.plusMinutes(resolutionMinutes);
    }
}
