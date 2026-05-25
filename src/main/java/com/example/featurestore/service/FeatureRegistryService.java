package com.example.featurestore.service;

import com.example.featurestore.dto.FeatureDefinition;
import com.example.featurestore.dto.FeatureGroupResponse;
import com.example.featurestore.dto.FeatureGroupVersionResponse;
import com.example.featurestore.dto.RegisterFeatureGroupRequest;
import com.example.featurestore.dto.UpdateFeatureGroupRequest;
import com.example.featurestore.model.FeatureGroupEntity;
import com.example.featurestore.model.FeatureGroupVersionEntity;
import com.example.featurestore.repository.FeatureGroupRepository;
import com.example.featurestore.repository.FeatureGroupVersionRepository;
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
    private final FeatureGroupVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    public FeatureRegistryService(
            FeatureGroupRepository repository,
            FeatureGroupVersionRepository versionRepository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.versionRepository = versionRepository;
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
        FeatureGroupEntity saved = repository.save(entity);
        versionRepository.save(new FeatureGroupVersionEntity(
                saved.getName(),
                saved.getEntityType(),
                saved.getSchemaJson(),
                saved.getFreshnessTtlSeconds(),
                saved.getVersion()
        ));
        return toResponse(saved);
    }

    @Transactional
    public FeatureGroupResponse update(String name, UpdateFeatureGroupRequest request) {
        ensureUniqueFeatureNames(request.features());
        FeatureGroupEntity group = requireGroup(name);
        int nextVersion = group.getVersion() + 1;
        group.updateSchema(writeSchema(request.features()), request.freshnessTtlSeconds(), nextVersion);
        FeatureGroupEntity saved = repository.save(group);
        versionRepository.save(new FeatureGroupVersionEntity(
                saved.getName(),
                saved.getEntityType(),
                saved.getSchemaJson(),
                saved.getFreshnessTtlSeconds(),
                saved.getVersion()
        ));
        return toResponse(saved);
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

    @Transactional(readOnly = true)
    public List<FeatureGroupVersionResponse> versions(String featureGroup) {
        requireGroup(featureGroup);
        return versionRepository.findByFeatureGroupNameOrderByVersionDesc(featureGroup).stream()
                .map(this::toVersionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FeatureGroupVersionResponse version(String featureGroup, int version) {
        requireGroup(featureGroup);
        return versionRepository.findByFeatureGroupNameAndVersion(featureGroup, version)
                .map(this::toVersionResponse)
                .orElseThrow(() -> new NotFoundException("Unknown version " + version + " for feature group: " + featureGroup));
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

    private FeatureGroupVersionResponse toVersionResponse(FeatureGroupVersionEntity entity) {
        return new FeatureGroupVersionResponse(
                entity.getFeatureGroupName(),
                entity.getEntityType(),
                readSchema(entity.getSchemaJson()),
                entity.getFreshnessTtlSeconds(),
                entity.getVersion(),
                entity.getCreatedAt()
        );
    }

    private void ensureUniqueFeatureNames(List<FeatureDefinition> definitions) {
        long distinctCount = definitions.stream().map(FeatureDefinition::name).distinct().count();
        if (distinctCount != definitions.size()) {
            throw new ValidationException("Feature names within a group must be unique");
        }
    }

    public String writeSchema(List<FeatureDefinition> definitions) {
        try {
            return objectMapper.writeValueAsString(definitions);
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public List<FeatureDefinition> readSchema(String schemaJson) {
        try {
            return objectMapper.readValue(schemaJson, FEATURE_DEFINITIONS);
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
