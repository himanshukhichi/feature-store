package com.example.featurestore.service;

import com.example.featurestore.dto.FeatureVectorResponse;
import com.example.featurestore.dto.BatchFeatureResponse;
import com.example.featurestore.dto.MultiGroupFeatureResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class FeatureRetrievalService {

    private final FeatureRegistryService registryService;
    private final OnlineStoreService onlineStoreService;
    private final OfflineStoreService offlineStoreService;
    private final Timer onlineReadTimer;
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final AtomicLong cacheHitTotal = new AtomicLong();
    private final AtomicLong cacheMissTotal = new AtomicLong();

    public FeatureRetrievalService(
            FeatureRegistryService registryService,
            OnlineStoreService onlineStoreService,
            OfflineStoreService offlineStoreService,
            MeterRegistry meterRegistry
    ) {
        this.registryService = registryService;
        this.onlineStoreService = onlineStoreService;
        this.offlineStoreService = offlineStoreService;
        this.onlineReadTimer = Timer.builder("online_read_latency_ms")
                .description("Latency of online feature reads")
                .tag("unit", "milliseconds")
                .publishPercentiles(0.95, 0.99)
                .register(meterRegistry);
        this.cacheHits = Counter.builder("cache_hits_total").description("Redis feature lookup hits").register(meterRegistry);
        this.cacheMisses = Counter.builder("cache_misses_total").description("Redis feature lookup misses").register(meterRegistry);
        Gauge.builder("cache_hit_rate", this, FeatureRetrievalService::cacheHitRate)
                .description("Fraction of online lookups served from Redis")
                .register(meterRegistry);
        FunctionCounter.builder("online_reads_total", cacheHitTotal, AtomicLong::get)
                .description("Total Redis feature lookup hits")
                .tag("result", "hit")
                .register(meterRegistry);
        FunctionCounter.builder("online_reads_total", cacheMissTotal, AtomicLong::get)
                .description("Total Redis feature lookup misses")
                .tag("result", "miss")
                .register(meterRegistry);
    }

    public FeatureVectorResponse readCurrent(String featureGroup, String entityId) {
        registryService.requireGroup(featureGroup);
        Map<String, Object> onlineValues = onlineReadTimer.record(() -> onlineStoreService.read(featureGroup, entityId));
        if (onlineValues != null && !onlineValues.isEmpty()) {
            recordCacheHit();
            return new FeatureVectorResponse(entityId, featureGroup, onlineValues, Instant.now(), "redis");
        }

        recordCacheMiss();
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

    public BatchFeatureResponse readBatch(String featureGroup, List<String> entityIds) {
        registryService.requireGroup(featureGroup);
        Instant now = Instant.now();
        Map<String, Map<String, Object>> onlineValues = onlineReadTimer.record(() -> onlineStoreService.readBatch(featureGroup, entityIds));
        Map<String, FeatureVectorResponse> results = new LinkedHashMap<>();

        for (String entityId : entityIds) {
            Map<String, Object> values = onlineValues.getOrDefault(entityId, Map.of());
            if (!values.isEmpty()) {
                recordCacheHit();
                results.put(entityId, new FeatureVectorResponse(entityId, featureGroup, values, now, "redis"));
                continue;
            }

            recordCacheMiss();
            Map<String, Object> fallbackValues = offlineStoreService.readAsOf(featureGroup, entityId, now);
            results.put(entityId, new FeatureVectorResponse(entityId, featureGroup, fallbackValues, now, "postgres"));
        }

        return new BatchFeatureResponse(featureGroup, results);
    }

    public MultiGroupFeatureResponse readMultiGroup(String entityId, List<String> featureGroups) {
        Instant now = Instant.now();
        Map<String, Object> merged = new LinkedHashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();

        for (String featureGroup : featureGroups) {
            FeatureVectorResponse response = readCurrent(featureGroup, entityId);
            sources.put(featureGroup, response.source());
            for (Map.Entry<String, Object> entry : response.features().entrySet()) {
                merged.put(featureGroup + "." + entry.getKey(), entry.getValue());
            }
        }

        return new MultiGroupFeatureResponse(entityId, merged, sources, now);
    }

    private void recordCacheHit() {
        cacheHits.increment();
        cacheHitTotal.incrementAndGet();
    }

    private void recordCacheMiss() {
        cacheMisses.increment();
        cacheMissTotal.incrementAndGet();
    }

    private double cacheHitRate() {
        long hits = cacheHitTotal.get();
        long total = hits + cacheMissTotal.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) hits / total;
    }
}
