package com.example.featurestore.dto;

import java.time.Instant;

public record FeatureStatisticsResponse(
        String featureGroup,
        String featureName,
        Instant computedAt,
        long totalCount,
        long nullCount,
        double nullRate,
        Double mean,
        Double stddev,
        Double min,
        Double max,
        Double p50,
        Double p95
) {
}
