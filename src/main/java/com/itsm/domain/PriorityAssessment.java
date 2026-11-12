package com.itsm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * The result of a priority calculation: the priority, the score behind it and
 * the three numbers the score was built from.
 *
 * <p>It is stored as part of the incident (same table row), so the incident
 * page can always show how the priority was reached, even if the service's
 * criticality is changed later. An assessment is never edited: a new
 * calculation replaces it with a new one.</p>
 */
@Embeddable
public record PriorityAssessment(

        @Enumerated(EnumType.STRING)
        @Column(name = "priority", columnDefinition = "varchar(5)")
        Priority priority,

        @Column(name = "priority_score")
        Integer score,

        @Column(name = "priority_impact_weight")
        Integer impactWeight,

        @Column(name = "priority_urgency_weight")
        Integer urgencyWeight,

        @Column(name = "priority_criticality")
        Integer serviceCriticality) {

    public PriorityAssessment {
        if (priority == null || score == null || impactWeight == null || urgencyWeight == null
                || serviceCriticality == null) {
            throw new IllegalArgumentException("A priority assessment needs all of its parts");
        }
    }

    /** The sum as people would write it, for example "3 × 3 × 3 = 27". */
    public String formula() {
        return "%d × %d × %d = %d".formatted(impactWeight, urgencyWeight, serviceCriticality, score);
    }
}
