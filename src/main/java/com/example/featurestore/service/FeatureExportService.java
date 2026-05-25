package com.example.featurestore.service;

import com.example.featurestore.dto.ExportPoint;
import com.example.featurestore.dto.FeatureDefinition;
import com.example.featurestore.dto.FeatureExportRequest;
import com.example.featurestore.dto.FeatureExportResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FeatureExportService {

    private final FeatureRegistryService registryService;
    private final OfflineStoreService offlineStoreService;

    public FeatureExportService(FeatureRegistryService registryService, OfflineStoreService offlineStoreService) {
        this.registryService = registryService;
        this.offlineStoreService = offlineStoreService;
    }

    public FeatureExportResponse export(FeatureExportRequest request) {
        List<String> featureColumns = featureColumns(request.featureGroups());
        List<String> lines = new ArrayList<>();
        lines.add(csvLine(header(featureColumns)));

        for (ExportPoint point : request.points()) {
            Map<String, Object> merged = pointInTimeVector(request.featureGroups(), point);
            List<String> row = new ArrayList<>();
            row.add(point.entityId());
            row.add(point.timestamp().toString());
            for (String featureColumn : featureColumns) {
                Object value = merged.get(featureColumn);
                row.add(value == null ? "" : String.valueOf(value));
            }
            lines.add(csvLine(row));
        }

        Path exportPath = Path.of("build", "exports", "training-dataset-" + Instant.now().toEpochMilli() + ".csv")
                .toAbsolutePath()
                .normalize();
        try {
            Files.createDirectories(exportPath.getParent());
            Files.write(exportPath, lines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return new FeatureExportResponse(exportPath.toString(), request.points().size());
    }

    private List<String> featureColumns(List<String> featureGroups) {
        List<String> columns = new ArrayList<>();
        for (String featureGroup : featureGroups) {
            registryService.requireGroup(featureGroup);
            for (FeatureDefinition definition : registryService.schemaFor(featureGroup)) {
                columns.add(featureGroup + "." + definition.name());
            }
        }
        return columns;
    }

    private List<String> header(List<String> featureColumns) {
        List<String> header = new ArrayList<>();
        header.add("entity_id");
        header.add("event_time");
        header.addAll(featureColumns);
        return header;
    }

    private Map<String, Object> pointInTimeVector(List<String> featureGroups, ExportPoint point) {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (String featureGroup : featureGroups) {
            Map<String, Object> values = offlineStoreService.readAsOf(featureGroup, point.entityId(), point.timestamp());
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                merged.put(featureGroup + "." + entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    private String csvLine(List<String> values) {
        return values.stream().map(this::escapeCsv).reduce((left, right) -> left + "," + right).orElse("");
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
