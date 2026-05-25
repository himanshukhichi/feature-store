package com.example.featurestore.service;

import com.example.featurestore.dto.FeatureVectorResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class FeatureRetrievalService {

    private final FeatureRegistryService registryService;
    private final OnlineStoreService onlineStoreService;
    private final OfflineStoreService offlineStoreService;
    private final Timer onlineReadTimer;
    private final Counter cacheHits;
    private final Counter cacheMisses;

    public FeatureRetrievalService(
            FeatureRegistryService registryService,
            OnlineStoreService onlineStoreService,
            OfflineStoreService offlineStoreService,
            MeterRegistry meterRegistry
    ) {
        this.registryService = registryService;
        this.onlineStoreService = onlineStoreService;
        this.offlineStoreService = offlineStoreService;
        this.onlineReadTimer = Timer.builder("online_read_latency")
                .description("Latency of online feature reads")
                .tag("unit", "milliseconds")
                .publishPercentiles(0.95, 0.99)
                .register(meterRegistry);
        this.cacheHits = Counter.builder("cache_hits_total").description("Redis feature lookup hits").register(meterRegistry);
        this.cacheMisses = Counter.builder("cache_misses_total").description("Redis feature lookup misses").register(meterRegistry);
    }

    public FeatureVectorResponse readCurrent(String featureGroup, String entityId) {
        registryService.requireGroup(featureGroup);
        Map<String, Object> onlineValues = onlineReadTimer.record(() -> onlineStoreService.read(featureGroup, entityId));
        if (onlineValues != null && !onlineValues.isEmpty()) {
            cacheHits.increment();
            return new FeatureVectorResponse(entityId, featureGroup, onlineValues, Instant.now(), "redis");
        }

        cacheMisses.increment();
        Instant now = Instant.now();
        Map<String, Object> fallbackValues = offlineStoreService.readAsOf(featureGroup, entityId, now);
        return new FeatureVectorResponse(entityId, featureGroup, fallbackValues, now, "postgres");
    }

    public FeatureVectorResponse readAsOf(String featureGroup, String entityId, Instant asOf) {
        registryService.requireGroup(featureGroup);
        return new FeatureVectorResponse(
                entityId,
                featureGroup,
                offlineStoreService.readAsOf(featureGroup, entityId, asOf),
                asOf,
                "postgres"
        );
    }
}
