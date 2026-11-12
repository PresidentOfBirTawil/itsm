package com.itsm.domain;

/** How soon an incident needs to be fixed before it hurts the business. */
public enum Urgency {

    LOW("Low", 1, "it can wait"),
    MEDIUM("Medium", 2, "work is slowed down"),
    HIGH("High", 3, "work has stopped");

    private final String label;
    private final int weight;
    private final String hint;

    Urgency(String label, int weight, String hint) {
        this.label = label;
        this.weight = weight;
        this.hint = hint;
    }

    public String getLabel() {
        return label;
    }

    /** Numeric weight (1–3) used when a priority score is calculated. */
    public int getWeight() {
        return weight;
    }

    /** A short explanation shown next to the choice on the report form. */
    public String getHint() {
        return hint;
    }
}
