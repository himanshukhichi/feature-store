package com.example.featurestore.dto;

import java.util.Map;

public record BatchFeatureResponse(
        String featureGroup,
        Map<String, FeatureVectorResponse> results
) {
}
