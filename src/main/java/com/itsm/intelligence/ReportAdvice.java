package com.itsm.intelligence;

import java.util.List;

/**
 * Everything the report form shows while an employee types: a category,
 * impact and urgency to consider, and articles that may solve the problem
 * straight away. Sent to the browser as JSON.
 */
public record ReportAdvice(Suggestion category, Suggestion impact, Suggestion urgency, List<Article> articles) {

    /**
     * One suggested value for a field of the form.
     *
     * @param value   what to put in the field (an id or an enum name)
     * @param label   what to show, e.g. "Network"
     * @param reason  why, e.g. "the report mentions: vpn"
     * @param percent how sure, 0 to 100, or null for a rule
     */
    public record Suggestion(String value, String label, String reason, Integer percent) {
    }

    /** A knowledge article that may answer the problem. */
    public record Article(Long id, String reference, String title, int percent, String url) {
    }
}
