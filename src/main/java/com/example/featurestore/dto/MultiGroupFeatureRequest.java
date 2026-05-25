package com.example.featurestore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record MultiGroupFeatureRequest(
        @NotBlank String entityId,
        @NotEmpty List<String> featureGroups
) {
}
