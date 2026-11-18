package com.quell.api.model;

import java.time.LocalDateTime;

import com.quell.domain.Impact;
import com.quell.domain.Incident;
import com.quell.domain.Priority;
import com.quell.domain.Role;
import com.quell.domain.SlaStatus;
import com.quell.domain.Status;
import com.quell.domain.Urgency;
import com.quell.web.RolePaths;
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
