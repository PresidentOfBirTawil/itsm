package com.itsm.search;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import com.itsm.domain.SlaStatus;
import com.itsm.domain.Status;
import org.springframework.data.jpa.domain.Specification;

/**
 * The search filters, one small {@link Specification} each. A specification
 * is a reusable piece of a WHERE clause; the filters that were filled in are
 * joined with AND, so any combination works without writing a query for each.
 */
public final class IncidentSpecifications {

    private IncidentSpecifications() {
    }

    /** All filters of the form that were filled in, combined with AND. */
    public static Specification<Incident> matching(IncidentSearchCriteria criteria) {
        List<Specification<Incident>> filters = new ArrayList<>();
        if (criteria.getCode() != null && !criteria.getCode().isBlank()) {
            filters.add(codeContains(criteria.getCode()));
        }
        if (criteria.getStatus() != null) {
            filters.add(hasStatus(criteria.getStatus()));
        }
        if (criteria.getPriority() != null) {
            filters.add(hasPriority(criteria.getPriority()));
        }
        if (criteria.getCategoryId() != null) {
            filters.add(inCategory(criteria.getCategoryId()));
        }
        if (criteria.getServiceId() != null) {
            filters.add(onService(criteria.getServiceId()));
        }
        if (criteria.getTechnicianId() != null) {
            filters.add(assignedTo(criteria.getTechnicianId()));
        }
        if (criteria.getFrom() != null) {
            filters.add(reportedOnOrAfter(criteria.getFrom()));
        }
        if (criteria.getTo() != null) {
            filters.add(reportedOnOrBefore(criteria.getTo()));
        }
        if (criteria.getSlaStatus() != null) {
            filters.add(hasSlaStatus(criteria.getSlaStatus()));
        }
        return Specification.allOf(filters);
    }

    /** "7", "0007" and "inc-0007" all find INC-0007. */
    public static Specification<Incident> codeContains(String text) {
        String pattern = "%" + text.trim().toUpperCase() + "%";
        return (incident, query, builder) -> builder.like(builder.upper(incident.get("code")), pattern);
    }

    public static Specification<Incident> hasStatus(Status status) {
        return (incident, query, builder) -> builder.equal(incident.get("status"), status);
    }

    public static Specification<Incident> hasPriority(Priority priority) {
        return (incident, query, builder) -> builder.equal(incident.get("assessment").get("priority"), priority);
    }

    public static Specification<Incident> inCategory(Long categoryId) {
        return (incident, query, builder) -> builder.equal(incident.get("category").get("id"), categoryId);
    }

    public static Specification<Incident> onService(Long serviceId) {
        return (incident, query, builder) -> builder.equal(incident.get("service").get("id"), serviceId);
    }

    public static Specification<Incident> assignedTo(Long technicianId) {
        return (incident, query, builder) -> builder.equal(incident.get("assignee").get("id"), technicianId);
    }

    public static Specification<Incident> reportedOnOrAfter(LocalDate day) {
        return (incident, query, builder) ->
                builder.greaterThanOrEqualTo(incident.get("createdAt"), day.atStartOfDay());
    }

    /** Includes the whole of the given day. */
    public static Specification<Incident> reportedOnOrBefore(LocalDate day) {
        return (incident, query, builder) ->
                builder.lessThan(incident.get("createdAt"), day.plusDays(1).atStartOfDay());
    }

    public static Specification<Incident> hasSlaStatus(SlaStatus slaStatus) {
        return (incident, query, builder) -> builder.equal(incident.get("slaStatus"), slaStatus);
    }
}
