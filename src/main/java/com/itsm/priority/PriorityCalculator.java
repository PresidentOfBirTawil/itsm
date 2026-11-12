package com.itsm.priority;

import com.itsm.domain.Incident;
import com.itsm.domain.PriorityAssessment;

/**
 * Works out how urgent an incident is (a Strategy: the rest of the
 * application only knows this interface, so the rule can be replaced by
 * writing another class that implements it).
 */
public interface PriorityCalculator {

    /** Calculates the priority of an incident and returns it together with the numbers behind it. */
    PriorityAssessment calculate(Incident incident);

    /** The rule in one sentence, shown next to the breakdown on the incident page. */
    String describeRule();
}
