package com.example.featurestore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "feature_values")
public class FeatureValueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(name = "feature_group", nullable = false)
    private String featureGroup;

    @Column(name = "feature_name", nullable = false)
    private String featureName;

    @Column(name = "value_json", nullable = false, columnDefinition = "TEXT")
    private String valueJson;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FeatureValueEntity() {
    }

    public FeatureValueEntity(String entityId, String featureGroup, String featureName, String valueJson, Instant eventTime) {
        this.entityId = entityId;
        this.featureGroup = featureGroup;
        this.featureName = featureName;
        this.valueJson = valueJson;
        this.eventTime = eventTime;
        this.createdAt = Instant.now();
    }

    public String getEntityId() {
        return entityId;
    }

    public String getFeatureGroup() {
        return featureGroup;
    }

    public String getFeatureName() {
        return featureName;
    }

    public String getValueJson() {
        return valueJson;
    }

    public Instant getEventTime() {
        return eventTime;
    }
}
