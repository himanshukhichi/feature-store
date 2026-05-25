package com.example.featurestore.service;

import com.example.featurestore.model.FeatureGroupEntity;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class StalenessDetectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StalenessDetectionService.class);

    private final OnlineStoreService onlineStoreService;
    private final FeatureRegistryService registryService;
    private final AtomicLong staleFeatureVectors = new AtomicLong();

    public StalenessDetectionService(
            OnlineStoreService onlineStoreService,
            FeatureRegistryService registryService,
            MeterRegistry meterRegistry
    ) {
        this.onlineStoreService = onlineStoreService;
        this.registryService = registryService;
        Gauge.builder("stale_feature_vectors", staleFeatureVectors, AtomicLong::get)
                .description("Feature vectors whose last update is older than twice freshness TTL")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${feature-store.staleness-check-delay-ms:60000}")
    public void detectStaleFeatures() {
        long staleCount = 0;
        Instant now = Instant.now();
        for (Map.Entry<String, Instant> entry : onlineStoreService.lastUpdatedEntries().entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            String featureGroup = parts[0];
            String entityId = parts[1];
            FeatureGroupEntity group;
            try {
                group = registryService.requireGroup(featureGroup);
            } catch (NotFoundException ignored) {
                continue;
            }
            Duration age = Duration.between(entry.getValue(), now);
            Duration threshold = Duration.ofSeconds(group.getFreshnessTtlSeconds() * 2);
            if (age.compareTo(threshold) > 0) {
                staleCount++;
                LOGGER.warn(
                        "Stale feature vector detected: featureGroup={}, entityId={}, ageSeconds={}, thresholdSeconds={}",
                        featureGroup,
                        entityId,
                        age.toSeconds(),
                        threshold.toSeconds()
                );
            }
        }
        staleFeatureVectors.set(staleCount);
    }
}
