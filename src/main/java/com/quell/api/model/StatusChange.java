package com.quell.api.model;

import com.quell.domain.Status;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Moving an incident to another status, with a note where the move needs one (for example resolving). */
public record StatusChange(@NotNull Status status, @Size(max = 2000) String note) {
}
