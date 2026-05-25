package com.example.featurestore.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateFeatureGroupRequest(
        @NotEmpty List<@Valid FeatureDefinition> features,
        @Min(1) long freshnessTtlSeconds
) {
}
