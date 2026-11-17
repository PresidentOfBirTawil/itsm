package com.itsm.api.model;

import com.itsm.domain.Impact;
import com.itsm.domain.Urgency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What is needed to report an incident through the API. */
public record NewIncident(
        @NotBlank @Size(max = 150) @Schema(example = "Checkout returns error 502") String title,
        @NotBlank @Size(max = 4000) @Schema(example = "Since 09:40 about one in five checkouts fails with a 502 from the payment gateway.") String description,
        @NotNull @Schema(description = "The id of the affected service (see GET /api/v1/services)", example = "1") Long serviceId,
        @Schema(description = "Optional: left out, the category is worked out from the words used") Long categoryId,
        @NotNull Impact impact,
        @NotNull Urgency urgency,
        @Email @Schema(description = "Administrators' keys only: report on behalf of this employee, e.g. for a monitoring tool", example = "emp1@itsm.local") String reporterEmail) {
}
