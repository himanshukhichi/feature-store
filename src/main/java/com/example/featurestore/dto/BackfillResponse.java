package com.example.featurestore.dto;

public record BackfillResponse(
        int recordsAccepted,
        int featureValuesWritten
) {
}
