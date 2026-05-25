package com.example.featurestore.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchFeatureRequest(
        @NotEmpty List<String> entityIds
) {
}
