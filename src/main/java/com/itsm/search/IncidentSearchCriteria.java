package com.itsm.search;

import java.time.LocalDate;

import com.itsm.domain.Priority;
import com.itsm.domain.SlaStatus;
import com.itsm.domain.Status;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * What the search form asks for. Every field is optional: an empty field
 * means "do not filter on this".
 */
public class IncidentSearchCriteria {

    @Size(max = 20, message = "The code can be at most 20 characters")
    private String code;
    private Status status;
    private Priority priority;
    private Long categoryId;
    private Long serviceId;
    private Long technicianId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;
    private SlaStatus slaStatus;

    /** True if no filter has been filled in. */
    public boolean isEmpty() {
        return (code == null || code.isBlank()) && status == null && priority == null && categoryId == null
                && serviceId == null && technicianId == null && from == null && to == null && slaStatus == null;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public Long getTechnicianId() {
        return technicianId;
    }

    public void setTechnicianId(Long technicianId) {
        this.technicianId = technicianId;
    }

    public LocalDate getFrom() {
        return from;
    }

    public void setFrom(LocalDate from) {
        this.from = from;
    }

    public LocalDate getTo() {
        return to;
    }

    public void setTo(LocalDate to) {
        this.to = to;
    }

    public SlaStatus getSlaStatus() {
        return slaStatus;
    }

    public void setSlaStatus(SlaStatus slaStatus) {
        this.slaStatus = slaStatus;
    }
}
