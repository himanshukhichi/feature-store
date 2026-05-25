package com.example.featurestore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "feature_group_versions")
public class FeatureGroupVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feature_group_name", nullable = false)
    private String featureGroupName;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "schema_json", nullable = false, columnDefinition = "TEXT")
    private String schemaJson;

    @Column(name = "freshness_ttl_seconds", nullable = false)
    private long freshnessTtlSeconds;

    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FeatureGroupVersionEntity() {
    }

    public FeatureGroupVersionEntity(String featureGroupName, String entityType, String schemaJson, long freshnessTtlSeconds, int version) {
        this.featureGroupName = featureGroupName;
        this.entityType = entityType;
        this.schemaJson = schemaJson;
        this.freshnessTtlSeconds = freshnessTtlSeconds;
        this.version = version;
        this.createdAt = Instant.now();
    }

    public String getFeatureGroupName() {
        return featureGroupName;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getSchemaJson() {
        return schemaJson;
    }

    public long getFreshnessTtlSeconds() {
        return freshnessTtlSeconds;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
