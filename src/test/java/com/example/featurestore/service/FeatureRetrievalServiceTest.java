package com.example.featurestore.service;

import com.example.featurestore.dto.FeatureVectorResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureRetrievalServiceTest {

    private final FeatureRegistryService registryService = mock(FeatureRegistryService.class);
    private final OnlineStoreService onlineStoreService = mock(OnlineStoreService.class);
    private final OfflineStoreService offlineStoreService = mock(OfflineStoreService.class);
    private final FeatureRetrievalService retrievalService = new FeatureRetrievalService(
            registryService,
            onlineStoreService,
            offlineStoreService,
            new SimpleMeterRegistry()
    );

    @Test
    void readsCurrentValuesFromRedisWhenPresent() {
        when(onlineStoreService.read("user_profile", "u-1")).thenReturn(Map.of("age", 31));

        FeatureVectorResponse response = retrievalService.readCurrent("user_profile", "u-1");

        assertThat(response.source()).isEqualTo("redis");
        assertThat(response.features()).containsEntry("age", 31);
        verify(registryService).requireGroup("user_profile");
    }

    @Test
    void fallsBackToPostgresWhenRedisMisses() {
        when(onlineStoreService.read("user_profile", "u-1")).thenReturn(Map.of());
        when(offlineStoreService.readAsOf(org.mockito.Mockito.eq("user_profile"), org.mockito.Mockito.eq("u-1"), org.mockito.Mockito.any()))
                .thenReturn(Map.of("age", 30));

        FeatureVectorResponse response = retrievalService.readCurrent("user_profile", "u-1");

        assertThat(response.source()).isEqualTo("postgres");
        assertThat(response.features()).containsEntry("age", 30);
    }

    @Test
    void pointInTimeReadsAlwaysUseOfflineHistory() {
        Instant asOf = Instant.parse("2024-01-15T10:00:00Z");
        when(offlineStoreService.readAsOf("user_profile", "u-1", asOf)).thenReturn(Map.of("age", 29));

        FeatureVectorResponse response = retrievalService.readAsOf("user_profile", "u-1", asOf);

        assertThat(response.source()).isEqualTo("postgres");
        assertThat(response.asOf()).isEqualTo(asOf);
        assertThat(response.features()).containsEntry("age", 29);
    }
}
