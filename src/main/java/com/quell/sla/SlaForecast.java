package com.quell.sla;

import java.time.LocalDateTime;

/**
 * A prediction of when an open incident will be resolved and how likely it is
 * to miss its resolution deadline.
 *
 * @param expectedResolution when it will probably be resolved
 * @param breachPercent      the chance, 0 to 100, that it misses the resolution deadline
 * @param sampleSize         how many past incidents the prediction is based on
 * @param basis              what those incidents were, e.g. "14 resolved P3 Network incidents"
 */
public record SlaForecast(LocalDateTime expectedResolution, int breachPercent, int sampleSize, String basis) {

    /** For the colour of the badge: high above 60%, medium above 30%. */
    public String level() {
        return breachPercent > 60 ? "high" : breachPercent > 30 ? "medium" : "low";
    }
}
