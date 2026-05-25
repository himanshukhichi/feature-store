package com.example.featurestore.service;

import com.example.featurestore.dto.FeatureIngestedEvent;
import com.example.featurestore.model.FeatureValueEntity;
import com.example.featurestore.repository.FeatureValueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OfflineStoreService {

    private final FeatureValueRepository repository;
    private final ObjectMapper objectMapper;
    private final Timer offlineWriteTimer;

    public OfflineStoreService(FeatureValueRepository repository, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.offlineWriteTimer = Timer.builder("offline_write_latency_ms")
                .description("Latency of PostgreSQL feature history writes")
                .tag("unit", "milliseconds")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    @Transactional
    public void write(FeatureIngestedEvent event) {
        offlineWriteTimer.record(() -> {
            for (Map.Entry<String, Object> entry : event.features().entrySet()) {
                writeValue(event.entityId(), event.featureGroup(), entry.getKey(), entry.getValue(), event.timestamp());
            }
        });
    }

    @Transactional
    public int writeDirect(String entityId, String featureGroup, Map<String, Object> features, Instant eventTime) {
        int written = 0;
        for (Map.Entry<String, Object> entry : features.entrySet()) {
            writeValue(entityId, featureGroup, entry.getKey(), entry.getValue(), eventTime);
            written++;
        }
        return written;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> readAsOf(String featureGroup, String entityId, Instant asOf) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (FeatureValueEntity value : repository.findLatestValuesAsOf(featureGroup, entityId, asOf)) {
            values.put(value.getFeatureName(), readJson(value.getValueJson()));
        }
        return values;
    }

    @Transactional(readOnly = true)
    public java.util.List<FeatureValueEntity> valuesForGroup(String featureGroup) {
        return repository.findByFeatureGroup(featureGroup);
    }

    @Transactional(readOnly = true)
    public java.util.List<FeatureValueEntity> allValues() {
        return repository.findAll();
    }

    private void writeValue(String entityId, String featureGroup, String featureName, Object value, Instant eventTime) {
        repository.save(new FeatureValueEntity(
                entityId,
                featureGroup,
                featureName,
                writeJson(value),
                eventTime
        ));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private Object readJson(String valueJson) {
        try {
            return objectMapper.readValue(valueJson, Object.class);
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
