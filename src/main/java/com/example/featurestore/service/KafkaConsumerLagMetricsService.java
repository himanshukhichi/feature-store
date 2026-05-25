package com.example.featurestore.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class KafkaConsumerLagMetricsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumerLagMetricsService.class);

    private final AdminClient adminClient;
    private final String topicName;
    private final List<String> consumerGroups = List.of("online-store-consumer", "offline-store-consumer");
    private final AtomicLong consumerLag = new AtomicLong();

    public KafkaConsumerLagMetricsService(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${feature-store.kafka.feature-events-topic}") String topicName,
            MeterRegistry meterRegistry
    ) {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", bootstrapServers);
        this.adminClient = AdminClient.create(properties);
        this.topicName = topicName;
        Gauge.builder("kafka_consumer_lag", consumerLag, AtomicLong::get)
                .description("Total lag across feature-store Kafka consumer groups")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${feature-store.kafka-lag-check-delay-ms:30000}")
    public void refreshConsumerLag() {
        try {
            long totalLag = 0;
            for (String consumerGroup : consumerGroups) {
                Map<TopicPartition, OffsetAndMetadata> committedOffsets = adminClient
                        .listConsumerGroupOffsets(consumerGroup)
                        .partitionsToOffsetAndMetadata()
                        .get(5, TimeUnit.SECONDS);
                Map<TopicPartition, OffsetSpec> latestOffsetRequests = new HashMap<>();
                for (TopicPartition partition : committedOffsets.keySet()) {
                    if (partition.topic().equals(topicName)) {
                        latestOffsetRequests.put(partition, OffsetSpec.latest());
                    }
                }
                Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets = adminClient
                        .listOffsets(latestOffsetRequests)
                        .all()
                        .get(5, TimeUnit.SECONDS);
                for (Map.Entry<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> entry : latestOffsets.entrySet()) {
                    OffsetAndMetadata committed = committedOffsets.get(entry.getKey());
                    if (committed != null) {
                        totalLag += Math.max(0, entry.getValue().offset() - committed.offset());
                    }
                }
            }
            consumerLag.set(totalLag);
        } catch (Exception exception) {
            LOGGER.debug("Unable to refresh Kafka consumer lag yet: {}", exception.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        adminClient.close();
    }
}
