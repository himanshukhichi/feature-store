package com.example.featurestore.controller;

import com.example.featurestore.dto.BackfillRequest;
import com.example.featurestore.dto.BackfillResponse;
import com.example.featurestore.dto.BatchFeatureRequest;
import com.example.featurestore.dto.BatchFeatureResponse;
import com.example.featurestore.dto.FeatureExportRequest;
import com.example.featurestore.dto.FeatureExportResponse;
import com.example.featurestore.dto.FeatureIngestionResponse;
import com.example.featurestore.dto.FeatureVectorResponse;
import com.example.featurestore.dto.MultiGroupFeatureRequest;
import com.example.featurestore.dto.MultiGroupFeatureResponse;
import com.example.featurestore.service.BackfillService;
import com.example.featurestore.service.FeatureExportService;
import com.example.featurestore.service.FeatureIngestionService;
import com.example.featurestore.service.FeatureRetrievalService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private final BackfillService backfillService;
    private final FeatureExportService exportService;

    public FeatureController(
            FeatureIngestionService ingestionService,
            FeatureRetrievalService retrievalService,
            BackfillService backfillService,
            FeatureExportService exportService
    ) {
        this.ingestionService = ingestionService;
        this.retrievalService = retrievalService;
        this.backfillService = backfillService;
        this.exportService = exportService;
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

    @PostMapping("/{featureGroup}/batch")
    public BatchFeatureResponse batch(
            @PathVariable String featureGroup,
            @Valid @RequestBody BatchFeatureRequest request
    ) {
        return retrievalService.readBatch(featureGroup, request.entityIds());
    }

    @PostMapping("/multi-group")
    public MultiGroupFeatureResponse multiGroup(@Valid @RequestBody MultiGroupFeatureRequest request) {
        return retrievalService.readMultiGroup(request.entityId(), request.featureGroups());
    }

    @PostMapping(path = "/backfill", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BackfillResponse backfillJson(@Valid @RequestBody BackfillRequest request) {
        return backfillService.backfillJson(request);
    }

    @PostMapping(path = "/backfill", consumes = "text/csv")
    public BackfillResponse backfillCsv(@RequestBody String csvBody) {
        return backfillService.backfillCsv(csvBody);
    }

    @PostMapping("/export")
    public FeatureExportResponse export(@Valid @RequestBody FeatureExportRequest request) {
        return exportService.export(request);
    }
}
