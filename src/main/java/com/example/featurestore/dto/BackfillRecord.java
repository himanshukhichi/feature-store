package com.example.featurestore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public record BackfillRecord(
        @NotBlank String featureGroup,
        @NotBlank String entityId,
        @NotNull Instant eventTime,
        @NotEmpty Map<String, Object> features
) {
}
