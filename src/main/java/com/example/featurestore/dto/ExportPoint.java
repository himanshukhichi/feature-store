package com.example.featurestore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ExportPoint(
        @NotBlank String entityId,
        @NotNull Instant timestamp
) {
}
