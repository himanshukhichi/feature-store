package com.example.featurestore.service;

import com.example.featurestore.dto.FeatureIngestedEvent;
import com.example.featurestore.model.FeatureGroupEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

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
    }

    public Map<String, Object> read(String featureGroup, String entityId) {
        Map<Object, Object> stored = redisTemplate.opsForHash().entries(redisKey(featureGroup, entityId));
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : stored.entrySet()) {
            values.put(String.valueOf(entry.getKey()), readJson(String.valueOf(entry.getValue())));
        }
        return values;
    }

    private String redisKey(String featureGroup, String entityId) {
        return "feature:%s:%s".formatted(featureGroup, entityId);
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
