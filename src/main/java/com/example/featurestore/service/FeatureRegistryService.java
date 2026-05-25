package com.example.featurestore.service;

import com.example.featurestore.dto.FeatureDefinition;
import com.example.featurestore.dto.FeatureGroupResponse;
import com.example.featurestore.dto.RegisterFeatureGroupRequest;
import com.example.featurestore.model.FeatureGroupEntity;
import com.example.featurestore.repository.FeatureGroupRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.util.List;

@Service
public class FeatureRegistryService {

    private static final TypeReference<List<FeatureDefinition>> FEATURE_DEFINITIONS =
            new TypeReference<>() {
            };

    private final FeatureGroupRepository repository;
    private final ObjectMapper objectMapper;

    public FeatureRegistryService(FeatureGroupRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FeatureGroupResponse register(RegisterFeatureGroupRequest request) {
        if (repository.existsByName(request.name())) {
            throw new ValidationException("Feature group already exists: " + request.name());
        }
        ensureUniqueFeatureNames(request.features());

        FeatureGroupEntity entity = new FeatureGroupEntity(
                request.name(),
                request.entityType(),
                writeSchema(request.features()),
                request.freshnessTtlSeconds()
        );
        return toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public FeatureGroupEntity requireGroup(String name) {
        return repository.findByName(name)
                .orElseThrow(() -> new NotFoundException("Unknown feature group: " + name));
    }

    @Transactional(readOnly = true)
    public List<FeatureDefinition> schemaFor(String featureGroup) {
        return readSchema(requireGroup(featureGroup).getSchemaJson());
    }

    public FeatureGroupResponse toResponse(FeatureGroupEntity entity) {
        return new FeatureGroupResponse(
                entity.getName(),
                entity.getEntityType(),
                readSchema(entity.getSchemaJson()),
                entity.getFreshnessTtlSeconds(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void ensureUniqueFeatureNames(List<FeatureDefinition> definitions) {
        long distinctCount = definitions.stream().map(FeatureDefinition::name).distinct().count();
        if (distinctCount != definitions.size()) {
            throw new ValidationException("Feature names within a group must be unique");
        }
    }

    private String writeSchema(List<FeatureDefinition> definitions) {
        try {
            return objectMapper.writeValueAsString(definitions);
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private List<FeatureDefinition> readSchema(String schemaJson) {
        try {
            return objectMapper.readValue(schemaJson, FEATURE_DEFINITIONS);
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
