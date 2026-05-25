package com.example.featurestore.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record FeatureExportRequest(
        @NotEmpty List<String> featureGroups,
        @NotEmpty List<@Valid ExportPoint> points
) {
}
