package com.example.featurestore.dto;

import java.time.Instant;
import java.util.Set;

public record FeatureIngestionResponse(
        String entityId,
        String featureGroup,
        Set<String> acceptedFeatures,
        Instant timestamp
) {
}
