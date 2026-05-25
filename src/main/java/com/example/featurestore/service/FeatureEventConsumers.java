package com.example.featurestore.service;

import com.example.featurestore.dto.FeatureIngestedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class FeatureEventConsumers {

    private final OnlineStoreService onlineStoreService;
    private final OfflineStoreService offlineStoreService;

    public FeatureEventConsumers(OnlineStoreService onlineStoreService, OfflineStoreService offlineStoreService) {
        this.onlineStoreService = onlineStoreService;
        this.offlineStoreService = offlineStoreService;
    }

    @KafkaListener(
            topics = "${feature-store.kafka.feature-events-topic}",
            groupId = "online-store-consumer"
    )
    public void writeOnlineStore(FeatureIngestedEvent event) {
        onlineStoreService.write(event);
    }

    @KafkaListener(
            topics = "${feature-store.kafka.feature-events-topic}",
            groupId = "offline-store-consumer"
    )
    public void writeOfflineStore(FeatureIngestedEvent event) {
        offlineStoreService.write(event);
    }
}
