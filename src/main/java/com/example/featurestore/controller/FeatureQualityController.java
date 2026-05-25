package com.example.featurestore.controller;

import com.example.featurestore.dto.FeatureStatisticsResponse;
import com.example.featurestore.service.FeatureStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/feature-stats")
public class FeatureQualityController {

    private final FeatureStatisticsService statisticsService;

    public FeatureQualityController(FeatureStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @PostMapping("/compute")
    public List<FeatureStatisticsResponse> compute() {
        return statisticsService.computeStatistics();
    }

    @GetMapping("/{featureGroup}")
    public List<FeatureStatisticsResponse> latest(@PathVariable String featureGroup) {
        return statisticsService.latestForGroup(featureGroup);
    }
}
