package com.example.featurestore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "feature_groups")
public class FeatureGroupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

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

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FeatureGroupEntity() {
    }

    public FeatureGroupEntity(String name, String entityType, String schemaJson, long freshnessTtlSeconds) {
        this.name = name;
        this.entityType = entityType;
        this.schemaJson = schemaJson;
        this.freshnessTtlSeconds = freshnessTtlSeconds;
        this.version = 1;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
