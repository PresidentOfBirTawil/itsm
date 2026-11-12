package com.itsm.web;

import com.itsm.domain.Impact;
import com.itsm.domain.Urgency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What an employee fills in to report an incident, with the rules each field must meet. */
public class IncidentForm {

    @NotBlank(message = "Please give the incident a short title")
    @Size(max = 150, message = "The title can be at most 150 characters")
    private String title;

    @NotBlank(message = "Please describe what is wrong")
    @Size(max = 4000, message = "The description can be at most 4000 characters")
    private String description;

    @NotNull(message = "Please choose the affected service")
    private Long serviceId;

    /** Optional: when left empty, the system chooses a category from the title and description. */
    private Long categoryId;

    @NotNull(message = "Please choose the impact")
    private Impact impact;

    @NotNull(message = "Please choose the urgency")
    private Urgency urgency;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

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
