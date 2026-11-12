package com.itsm.domain;

/** How much of the organisation an incident affects. */
public enum Impact {

    LOW("Low", 1),
    MEDIUM("Medium", 2),
    HIGH("High", 3);

    private final String label;
    private final int weight;

    Impact(String label, int weight) {
        this.label = label;
        this.weight = weight;
    }

    public String getLabel() {
        return label;
    }

    /** Numeric weight (1–3) used when a priority score is calculated. */
    public int getWeight() {
        return weight;
    }
}
