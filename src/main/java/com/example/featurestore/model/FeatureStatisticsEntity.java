package com.example.featurestore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "feature_statistics")
public class FeatureStatisticsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feature_group", nullable = false)
    private String featureGroup;

    @Column(name = "feature_name", nullable = false)
    private String featureName;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "total_count", nullable = false)
    private long totalCount;

    @Column(name = "null_count", nullable = false)
    private long nullCount;

    @Column(name = "null_rate", nullable = false)
    private double nullRate;

    private Double mean;
    private Double stddev;
    private Double min;
    private Double max;
    private Double p50;
    private Double p95;

    protected FeatureStatisticsEntity() {
    }

    public FeatureStatisticsEntity(
            String featureGroup,
            String featureName,
            Instant computedAt,
            long totalCount,
            long nullCount,
            double nullRate,
            Double mean,
            Double stddev,
            Double min,
            Double max,
            Double p50,
            Double p95
    ) {
        this.featureGroup = featureGroup;
        this.featureName = featureName;
        this.computedAt = computedAt;
        this.totalCount = totalCount;
        this.nullCount = nullCount;
        this.nullRate = nullRate;
        this.mean = mean;
        this.stddev = stddev;
        this.min = min;
        this.max = max;
        this.p50 = p50;
        this.p95 = p95;
    }

    public String getFeatureGroup() {
        return featureGroup;
    }

    public String getFeatureName() {
        return featureName;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public long getNullCount() {
        return nullCount;
    }

    public double getNullRate() {
        return nullRate;
    }

    public Double getMean() {
        return mean;
    }

    public Double getStddev() {
        return stddev;
    }

    public Double getMin() {
        return min;
    }

    public Double getMax() {
        return max;
    }

    public Double getP50() {
        return p50;
    }

    public Double getP95() {
        return p95;
    }
}
