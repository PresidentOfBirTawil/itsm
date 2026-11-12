package com.itsm.web;

import jakarta.validation.constraints.NotNull;

/** An administrator's choice of technician for an incident. */
public class AssignForm {

    @NotNull(message = "Please choose a technician")
    private Long technicianId;

    public Long getTechnicianId() {
        return technicianId;
    }

    public void setTechnicianId(Long technicianId) {
        this.technicianId = technicianId;
    }
}
