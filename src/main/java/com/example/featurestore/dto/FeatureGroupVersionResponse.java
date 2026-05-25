package com.example.featurestore.dto;

import java.time.Instant;
import java.util.List;

public record FeatureGroupVersionResponse(
        String name,
        String entityType,
        List<FeatureDefinition> features,
        long freshnessTtlSeconds,
        int version,
        Instant createdAt
) {
}
