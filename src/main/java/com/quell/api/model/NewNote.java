package com.quell.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A note to add to an incident's timeline. */
public record NewNote(@NotBlank @Size(max = 2000) String text) {
}
