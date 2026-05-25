package com.example.featurestore.dto;

import java.time.Instant;
import java.util.Map;

public record MultiGroupFeatureResponse(
        String entityId,
        Map<String, Object> features,
        Map<String, String> sources,
        Instant asOf
) {
}
