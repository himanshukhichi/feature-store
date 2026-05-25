package com.example.featurestore.dto;

import java.time.Instant;
import java.util.Map;

public record FeatureVectorResponse(
        String entityId,
        String featureGroup,
        Map<String, Object> features,
        Instant asOf,
        String source
) {
}
