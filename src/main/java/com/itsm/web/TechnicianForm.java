package com.itsm.web;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What an administrator can change about a technician. */
public class TechnicianForm {

    @NotBlank(message = "Please enter a name")
    @Size(max = 100, message = "The name can be at most 100 characters")
    private String name;

    @NotNull(message = "Please enter a maximum workload")
    @Min(value = 1, message = "The maximum workload must be at least 1")
    @Max(value = 50, message = "The maximum workload can be at most 50")
    private Integer maxWorkload = 5;

    /** False unless the box is ticked: a browser sends nothing at all for an unticked checkbox. */
    private boolean available;

    /** The ids of the categories this technician can handle. */
    private List<Long> skillIds = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getMaxWorkload() {
        return maxWorkload;
    }

    public void setMaxWorkload(Integer maxWorkload) {
        this.maxWorkload = maxWorkload;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public List<Long> getSkillIds() {
        return skillIds;
    }

    public void setSkillIds(List<Long> skillIds) {
        this.skillIds = skillIds == null ? new ArrayList<>() : skillIds;
    }
}
