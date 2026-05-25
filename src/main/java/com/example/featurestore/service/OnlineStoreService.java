package com.example.featurestore.service;

import com.example.featurestore.dto.FeatureIngestedEvent;
import com.example.featurestore.model.FeatureGroupEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class OnlineStoreService {

    private final StringRedisTemplate redisTemplate;
    private final FeatureRegistryService registryService;
    private final ObjectMapper objectMapper;

    public OnlineStoreService(StringRedisTemplate redisTemplate, FeatureRegistryService registryService, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.registryService = registryService;
        this.objectMapper = objectMapper;
    }

    public void write(FeatureIngestedEvent event) {
        FeatureGroupEntity group = registryService.requireGroup(event.featureGroup());
        String key = redisKey(event.featureGroup(), event.entityId());
        Map<String, String> serialized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : event.features().entrySet()) {
            serialized.put(entry.getKey(), writeJson(entry.getValue()));
        }
        redisTemplate.opsForHash().putAll(key, serialized);
        redisTemplate.expire(key, Duration.ofSeconds(group.getFreshnessTtlSeconds()));
        redisTemplate.opsForHash().put(lastUpdatedKey(), lastUpdatedField(event.featureGroup(), event.entityId()), event.timestamp().toString());
    }

    public Map<String, Object> read(String featureGroup, String entityId) {
        Map<Object, Object> stored = redisTemplate.opsForHash().entries(redisKey(featureGroup, entityId));
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : stored.entrySet()) {
            values.put(String.valueOf(entry.getKey()), readJson(String.valueOf(entry.getValue())));
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Object>> readBatch(String featureGroup, List<String> entityIds) {
        List<Object> rawResults = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                for (String entityId : entityIds) {
                    operations.opsForHash().entries(redisKey(featureGroup, entityId));
                }
                return null;
            }
        });

        Map<String, Map<String, Object>> results = new LinkedHashMap<>();
        for (int index = 0; index < entityIds.size(); index++) {
            Map<Object, Object> raw = (Map<Object, Object>) rawResults.get(index);
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<Object, Object> entry : raw.entrySet()) {
                values.put(String.valueOf(entry.getKey()), readJson(String.valueOf(entry.getValue())));
            }
            results.put(entityIds.get(index), values);
        }
        return results;
    }

    public Map<String, Instant> lastUpdatedEntries() {
        Map<Object, Object> stored = redisTemplate.opsForHash().entries(lastUpdatedKey());
        Map<String, Instant> entries = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : stored.entrySet()) {
            entries.put(String.valueOf(entry.getKey()), Instant.parse(String.valueOf(entry.getValue())));
        }
        return entries;
    }

    public Set<String> onlineKeys() {
        return redisTemplate.keys("feature:*");
    }

    public String redisKey(String featureGroup, String entityId) {
        return "feature:%s:%s".formatted(featureGroup, entityId);
    }

    private String lastUpdatedKey() {
        return "feature:last-updated";
    }

    private String lastUpdatedField(String featureGroup, String entityId) {
        return "%s:%s".formatted(featureGroup, entityId);
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
