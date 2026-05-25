package com.example.featurestore.dto;

import com.example.featurestore.model.FeatureType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FeatureDefinition(
        @NotBlank String name,
        @NotNull FeatureType type,
        boolean nullable,
        Object defaultValue
) {
}
