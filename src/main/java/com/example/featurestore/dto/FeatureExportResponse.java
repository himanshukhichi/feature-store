package com.example.featurestore.dto;

public record FeatureExportResponse(
        String path,
        int rowsWritten
) {
}
