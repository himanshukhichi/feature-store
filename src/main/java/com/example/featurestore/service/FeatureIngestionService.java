package com.example.featurestore.service;

import com.example.featurestore.dto.FeatureIngestedEvent;
import com.example.featurestore.dto.FeatureIngestionResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class FeatureIngestionService {

    private final FeatureValidationService validationService;
    private final KafkaTemplate<String, FeatureIngestedEvent> kafkaTemplate;
    private final String topicName;
    private final Counter ingestionCounter;
    private final AtomicLong acceptedEvents = new AtomicLong();
    private final AtomicLong ingestionEventsPerSecond = new AtomicLong();
    private long lastRateSampleCount;
    private long lastRateSampleNanos = System.nanoTime();

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
        Gauge.builder("ingestion_events_per_sec", ingestionEventsPerSecond, AtomicLong::get)
                .description("Accepted ingestion events per second")
                .register(meterRegistry);
    }

    public FeatureIngestionResponse ingest(String featureGroup, String entityId, Map<String, Object> features) {
        Map<String, Object> stableFeatures = new LinkedHashMap<>(features);
        validationService.validate(featureGroup, stableFeatures);

        FeatureIngestedEvent event = new FeatureIngestedEvent(entityId, featureGroup, stableFeatures, Instant.now());
        kafkaTemplate.send(topicName, entityId, event);
        ingestionCounter.increment();
        acceptedEvents.incrementAndGet();

        return new FeatureIngestionResponse(entityId, featureGroup, stableFeatures.keySet(), event.timestamp());
    }

    @Scheduled(fixedDelay = 1000)
    public void refreshIngestionRate() {
        long now = System.nanoTime();
        long currentCount = acceptedEvents.get();
        double elapsedSeconds = Math.max(1.0, (now - lastRateSampleNanos) / 1_000_000_000.0);
        ingestionEventsPerSecond.set(Math.round((currentCount - lastRateSampleCount) / elapsedSeconds));
        lastRateSampleCount = currentCount;
        lastRateSampleNanos = now;
    }
}
