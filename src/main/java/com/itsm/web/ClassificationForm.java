package com.itsm.web;

import com.itsm.domain.Impact;
import com.itsm.domain.Urgency;
import jakarta.validation.constraints.NotNull;

/** A correction to how an incident is classified. */
public class ClassificationForm {

    @NotNull(message = "Please choose a category")
    private Long categoryId;

    @NotNull(message = "Please choose the impact")
    private Impact impact;

    @NotNull(message = "Please choose the urgency")
    private Urgency urgency;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Impact getImpact() {
        return impact;
    }

    public void setImpact(Impact impact) {
        this.impact = impact;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    public void setUrgency(Urgency urgency) {
        this.urgency = urgency;
    }
}
