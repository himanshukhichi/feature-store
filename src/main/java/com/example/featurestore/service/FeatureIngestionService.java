package com.example.featurestore.service;

import com.example.featurestore.dto.FeatureIngestedEvent;
import com.example.featurestore.dto.FeatureIngestionResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FeatureIngestionService {

    private final FeatureValidationService validationService;
    private final KafkaTemplate<String, FeatureIngestedEvent> kafkaTemplate;
    private final String topicName;
    private final Counter ingestionCounter;

    public FeatureIngestionService(
            FeatureValidationService validationService,
            KafkaTemplate<String, FeatureIngestedEvent> kafkaTemplate,
            @Value("${feature-store.kafka.feature-events-topic}") String topicName,
            MeterRegistry meterRegistry
    ) {
        this.validationService = validationService;
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
        this.ingestionCounter = Counter.builder("ingestion_events_total")
                .description("Total accepted ingestion events")
                .register(meterRegistry);
    }

    public FeatureIngestionResponse ingest(String featureGroup, String entityId, Map<String, Object> features) {
        Map<String, Object> stableFeatures = new LinkedHashMap<>(features);
        validationService.validate(featureGroup, stableFeatures);

        FeatureIngestedEvent event = new FeatureIngestedEvent(entityId, featureGroup, stableFeatures, Instant.now());
        kafkaTemplate.send(topicName, entityId, event);
        ingestionCounter.increment();

        return new FeatureIngestionResponse(entityId, featureGroup, stableFeatures.keySet(), event.timestamp());
    }
}
