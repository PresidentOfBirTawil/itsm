package com.itsm.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What an administrator fills in to add or change an IT service. */
public class ServiceForm {

    @NotBlank(message = "Please enter a name")
    @Size(max = 100, message = "The name can be at most 100 characters")
    private String name;

    @NotNull(message = "Please choose a criticality")
    @Min(value = 1, message = "Criticality must be 1, 2 or 3")
    @Max(value = 3, message = "Criticality must be 1, 2 or 3")
    private Integer criticality;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getCriticality() {
        return criticality;
    }

    public void setCriticality(Integer criticality) {
        this.criticality = criticality;
    }
}
