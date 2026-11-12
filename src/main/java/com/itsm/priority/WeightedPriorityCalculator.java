package com.itsm.priority;

import com.itsm.domain.ITService;
import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import com.itsm.domain.PriorityAssessment;
import org.springframework.stereotype.Component;

/**
 * Score = impact × urgency × service criticality, each a number from 1 to 3,
 * so the score runs from 1 to 27.
 *
 * <pre>
 *   18 – 27  P1 Critical
 *    9 – 17  P2 High
 *    4 – 8   P3 Medium
 *    1 – 3   P4 Low
 * </pre>
 *
 * Multiplying means one low factor pulls the whole score down: a total outage
 * (impact 3, urgency 3) of a low-criticality service scores 9, not 27.
 */
@Component
public class WeightedPriorityCalculator implements PriorityCalculator {

    public static final int MIN_SCORE = 1;
    public static final int MAX_SCORE = 27;

    /** The lowest score that still earns each priority. */
    public static final int P1_FROM = 18;
    public static final int P2_FROM = 9;
    public static final int P3_FROM = 4;

    @Override
    public PriorityAssessment calculate(Incident incident) {
        int impact = incident.getImpact().getWeight();
        int urgency = incident.getUrgency().getWeight();
        int criticality = incident.getService().getCriticality();
        int score = impact * urgency * criticality;
        return new PriorityAssessment(priorityFor(score), score, impact, urgency, criticality);
    }

    /** Turns a score into a priority band. */
    public Priority priorityFor(int score) {
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException(
                    "A priority score must be between " + MIN_SCORE + " and " + MAX_SCORE + ", not " + score);
        }
        if (score >= P1_FROM) {
            return Priority.P1;
        }
        if (score >= P2_FROM) {
            return Priority.P2;
        }
        if (score >= P3_FROM) {
            return Priority.P3;
        }
        return Priority.P4;
    }

    @Override
    public String describeRule() {
        return ("Score = impact × urgency × service criticality (each %d to %d). "
                + "%d–%d is P1, %d–%d is P2, %d–%d is P3, %d–%d is P4.").formatted(
                ITService.MIN_CRITICALITY, ITService.MAX_CRITICALITY,
                P1_FROM, MAX_SCORE, P2_FROM, P1_FROM - 1, P3_FROM, P2_FROM - 1, MIN_SCORE, P3_FROM - 1);
    }
}
