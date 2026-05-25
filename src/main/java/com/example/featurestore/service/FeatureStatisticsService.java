package com.example.featurestore.service;

import com.example.featurestore.dto.FeatureStatisticsResponse;
import com.example.featurestore.model.FeatureStatisticsEntity;
import com.example.featurestore.model.FeatureValueEntity;
import com.example.featurestore.repository.FeatureStatisticsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FeatureStatisticsService {

    private final OfflineStoreService offlineStoreService;
    private final FeatureStatisticsRepository statisticsRepository;
    private final ObjectMapper objectMapper;

    public FeatureStatisticsService(
            OfflineStoreService offlineStoreService,
            FeatureStatisticsRepository statisticsRepository,
            ObjectMapper objectMapper
    ) {
        this.offlineStoreService = offlineStoreService;
        this.statisticsRepository = statisticsRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public List<FeatureStatisticsResponse> computeDailyStatistics() {
        return computeStatistics();
    }

    @Transactional
    public List<FeatureStatisticsResponse> computeStatistics() {
        Instant computedAt = Instant.now();
        Map<FeatureKey, List<FeatureValueEntity>> byFeature = new LinkedHashMap<>();
        for (FeatureValueEntity value : offlineStoreService.allValues()) {
            byFeature.computeIfAbsent(new FeatureKey(value.getFeatureGroup(), value.getFeatureName()), ignored -> new ArrayList<>())
                    .add(value);
        }

        List<FeatureStatisticsEntity> computed = new ArrayList<>();
        for (Map.Entry<FeatureKey, List<FeatureValueEntity>> entry : byFeature.entrySet()) {
            computed.add(computeOne(entry.getKey().featureGroup(), entry.getKey().featureName(), entry.getValue(), computedAt));
        }

        return statisticsRepository.saveAll(computed).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FeatureStatisticsResponse> latestForGroup(String featureGroup) {
        return statisticsRepository.findLatestByFeatureGroup(featureGroup).stream()
                .map(this::toResponse)
                .toList();
    }

    private FeatureStatisticsEntity computeOne(String featureGroup, String featureName, List<FeatureValueEntity> values, Instant computedAt) {
        List<Double> numericValues = new ArrayList<>();
        long nullCount = 0;
        for (FeatureValueEntity value : values) {
            Object parsed = readJson(value.getValueJson());
            if (parsed == null) {
                nullCount++;
            } else if (parsed instanceof Number number) {
                numericValues.add(number.doubleValue());
            }
        }

        long totalCount = values.size();
        double nullRate = totalCount == 0 ? 0.0 : (double) nullCount / totalCount;
        numericValues.sort(Comparator.naturalOrder());

        Double mean = null;
        Double stddev = null;
        Double min = null;
        Double max = null;
        Double p50 = null;
        Double p95 = null;

        if (!numericValues.isEmpty()) {
            mean = numericValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double finalMean = mean;
            stddev = Math.sqrt(numericValues.stream()
                    .mapToDouble(value -> Math.pow(value - finalMean, 2))
                    .average()
                    .orElse(0.0));
            min = numericValues.getFirst();
            max = numericValues.getLast();
            p50 = percentile(numericValues, 0.50);
            p95 = percentile(numericValues, 0.95);
        }

        return new FeatureStatisticsEntity(
                featureGroup,
                featureName,
                computedAt,
                totalCount,
                nullCount,
                nullRate,
                mean,
                stddev,
                min,
                max,
                p50,
                p95
        );
    }

    private double percentile(List<Double> values, double percentile) {
        if (values.size() == 1) {
            return values.getFirst();
        }
        double index = percentile * (values.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return values.get(lower);
        }
        double fraction = index - lower;
        return values.get(lower) + (values.get(upper) - values.get(lower)) * fraction;
    }

    private Object readJson(String valueJson) {
        try {
            return objectMapper.readValue(valueJson, Object.class);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private FeatureStatisticsResponse toResponse(FeatureStatisticsEntity entity) {
        return new FeatureStatisticsResponse(
                entity.getFeatureGroup(),
                entity.getFeatureName(),
                entity.getComputedAt(),
                entity.getTotalCount(),
                entity.getNullCount(),
                entity.getNullRate(),
                entity.getMean(),
                entity.getStddev(),
                entity.getMin(),
                entity.getMax(),
                entity.getP50(),
                entity.getP95()
        );
    }

    private record FeatureKey(String featureGroup, String featureName) {
    }
}
