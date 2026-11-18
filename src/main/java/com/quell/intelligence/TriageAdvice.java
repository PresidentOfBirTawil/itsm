package com.quell.intelligence;

import com.quell.domain.Impact;
import com.quell.domain.Urgency;

/**
 * What the words of a report suggest about its impact and urgency. Either
 * part may be missing (null) when the text gives no clue.
 *
 * @param impact        the suggested impact, or null
 * @param impactReason  the words that suggested it, e.g. "all users"
 * @param urgency       the suggested urgency, or null
 * @param urgencyReason the words that suggested it, e.g. "cannot work"
 */
public record TriageAdvice(Impact impact, String impactReason, Urgency urgency, String urgencyReason) {

    public boolean isEmpty() {
        return impact == null && urgency == null;
    }
}
