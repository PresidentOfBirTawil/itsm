package com.itsm.domain;

/** How much of the organisation an incident affects. */
public enum Impact {

    LOW("Low", 1, "one or two people"),
    MEDIUM("Medium", 2, "a team or a department"),
    HIGH("High", 3, "many people or customers");

    private final String label;
    private final int weight;
    private final String hint;

    Impact(String label, int weight, String hint) {
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
