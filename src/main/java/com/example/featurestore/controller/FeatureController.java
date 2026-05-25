package com.example.featurestore.controller;

import com.example.featurestore.dto.FeatureIngestionResponse;
import com.example.featurestore.dto.FeatureVectorResponse;
import com.example.featurestore.service.FeatureIngestionService;
import com.example.featurestore.service.FeatureRetrievalService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/features")
public class FeatureController {

    private final FeatureIngestionService ingestionService;
    private final FeatureRetrievalService retrievalService;

    public FeatureController(FeatureIngestionService ingestionService, FeatureRetrievalService retrievalService) {
        this.ingestionService = ingestionService;
        this.retrievalService = retrievalService;
    }

    @PostMapping("/{featureGroup}/{entityId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FeatureIngestionResponse ingest(
            @PathVariable String featureGroup,
            @PathVariable String entityId,
            @RequestBody Map<String, Object> features
    ) {
        return ingestionService.ingest(featureGroup, entityId, features);
    }

    @GetMapping("/{featureGroup}/{entityId}")
    public FeatureVectorResponse read(
            @PathVariable String featureGroup,
            @PathVariable String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf
    ) {
        if (asOf == null) {
            return retrievalService.readCurrent(featureGroup, entityId);
        }
        return retrievalService.readAsOf(featureGroup, entityId, asOf);
    }
}
