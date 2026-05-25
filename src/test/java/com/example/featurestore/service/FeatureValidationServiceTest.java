package com.example.featurestore.service;

import com.example.featurestore.dto.FeatureDefinition;
import com.example.featurestore.model.FeatureType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureValidationServiceTest {

    private final FeatureRegistryService registryService = mock(FeatureRegistryService.class);
    private final FeatureValidationService validationService = new FeatureValidationService(registryService);

    @Test
    void acceptsFeaturesMatchingRegisteredSchema() {
        when(registryService.schemaFor("user_profile")).thenReturn(List.of(
                new FeatureDefinition("age", FeatureType.INTEGER, false, null),
                new FeatureDefinition("country", FeatureType.STRING, false, null),
                new FeatureDefinition("is_premium", FeatureType.BOOLEAN, true, false),
                new FeatureDefinition("ltv", FeatureType.DOUBLE, true, 0.0)
        ));

        assertThatCode(() -> validationService.validate("user_profile", Map.of(
                "age", 31,
                "country", "IN",
                "is_premium", true,
                "ltv", 29.75
        ))).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownFeatureNames() {
        when(registryService.schemaFor("user_profile")).thenReturn(List.of(
                new FeatureDefinition("age", FeatureType.INTEGER, false, null)
        ));

        assertThatThrownBy(() -> validationService.validate("user_profile", Map.of("unknown", "value")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unknown feature");
    }

    @Test
    void rejectsWrongTypes() {
        when(registryService.schemaFor("user_profile")).thenReturn(List.of(
                new FeatureDefinition("age", FeatureType.INTEGER, false, null)
        ));

        assertThatThrownBy(() -> validationService.validate("user_profile", Map.of("age", "31")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("INTEGER");
    }

    @Test
    void rejectsNullForNonNullableFeature() {
        when(registryService.schemaFor("user_profile")).thenReturn(List.of(
                new FeatureDefinition("age", FeatureType.INTEGER, false, null)
        ));

        assertThatThrownBy(() -> validationService.validate("user_profile", new java.util.HashMap<>() {{
            put("age", null);
        }}))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be null");
    }
}
