package com.example.featurestore.service;

import com.example.featurestore.dto.FeatureDefinition;
import com.example.featurestore.model.FeatureType;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FeatureValidationService {

    private final FeatureRegistryService registryService;

    public FeatureValidationService(FeatureRegistryService registryService) {
        this.registryService = registryService;
    }

    public void validate(String featureGroup, Map<String, Object> features) {
        if (features == null || features.isEmpty()) {
            throw new ValidationException("Feature payload must contain at least one feature");
        }

        Map<String, FeatureDefinition> schema = definitionsByName(registryService.schemaFor(featureGroup));
        for (Map.Entry<String, Object> entry : features.entrySet()) {
            FeatureDefinition definition = schema.get(entry.getKey());
            if (definition == null) {
                throw new ValidationException("Unknown feature '" + entry.getKey() + "' for group '" + featureGroup + "'");
            }
            validateValue(definition, entry.getValue());
        }
    }

    private Map<String, FeatureDefinition> definitionsByName(List<FeatureDefinition> definitions) {
        Map<String, FeatureDefinition> byName = new HashMap<>();
        for (FeatureDefinition definition : definitions) {
            byName.put(definition.name(), definition);
        }
        return byName;
    }

    private void validateValue(FeatureDefinition definition, Object value) {
        if (value == null) {
            if (!definition.nullable()) {
                throw new ValidationException("Feature '" + definition.name() + "' cannot be null");
            }
            return;
        }

        if (!matchesType(definition.type(), value)) {
            throw new ValidationException("Feature '" + definition.name() + "' must be " + definition.type());
        }
    }

    private boolean matchesType(FeatureType type, Object value) {
        return switch (type) {
            case STRING -> value instanceof String;
            case INTEGER -> value instanceof Integer || value instanceof Long;
            case DOUBLE -> value instanceof Float || value instanceof Double || value instanceof Integer || value instanceof Long;
            case BOOLEAN -> value instanceof Boolean;
        };
    }
}
