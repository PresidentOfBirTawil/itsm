package com.itsm.api.model;

import java.time.LocalDateTime;

import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import com.itsm.domain.Role;
import com.itsm.domain.SlaStatus;
import com.itsm.domain.Status;
import com.itsm.domain.Urgency;
import com.itsm.web.RolePaths;
import io.swagger.v3.oas.annotations.media.Schema;

/** An incident, as the API shows it. */
@Schema(name = "Incident")
public record IncidentView(
        @Schema(example = "INC-0042") String code,
        String title,
        String description,
        Status status,
        Priority priority,
        Impact impact,
        Urgency urgency,
        @Schema(example = "Payment Application") String service,
        @Schema(example = "Application") String category,
        PersonView reporter,
        PersonView assignee,
        SlaStatus slaStatus,
        boolean escalated,
        @Schema(description = "The reporter plus everyone who said they are affected too") int affectedCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime responseDue,
        LocalDateTime resolutionDue,
        LocalDateTime resolvedAt,
        LocalDateTime closedAt,
        String resolution,
        @Schema(description = "The reporter's rating of the fix, 1 to 5") Integer rating,
        Links links) {

    /** Where the incident is in the API and in the web app (for the person asking). */
    public record Links(@Schema(example = "/api/v1/incidents/INC-0042") String self,
                        @Schema(example = "/employee/incidents/INC-0042") String web) {
    }

    public static IncidentView of(Incident incident, Role viewerRole) {
        return new IncidentView(incident.getCode(), incident.getTitle(), incident.getDescription(),
                incident.getStatus(), incident.getPriority(), incident.getImpact(), incident.getUrgency(),
                incident.getService().getName(), incident.getCategory() == null ? null : incident.getCategory().getName(),
                PersonView.of(incident.getReporter()), PersonView.of(incident.getAssignee()),
                incident.getSlaStatus(), incident.isEscalated(), incident.getAffectedCount(),
                incident.getCreatedAt(), incident.getUpdatedAt(), incident.getResponseDue(), incident.getResolutionDue(),
                incident.getResolvedAt(), incident.getClosedAt(), incident.getResolution(), incident.getRating(),
                new Links("/api/v1/incidents/" + incident.getCode(),
                        RolePaths.incidents(viewerRole) + "/" + incident.getCode()));
    }
}
