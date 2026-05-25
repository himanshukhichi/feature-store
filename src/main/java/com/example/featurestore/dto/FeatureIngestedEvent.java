package com.example.featurestore.dto;

import java.time.Instant;
import java.util.Map;

public record FeatureIngestedEvent(
        String entityId,
        String featureGroup,
        Map<String, Object> features,
        Instant timestamp
) {
}
