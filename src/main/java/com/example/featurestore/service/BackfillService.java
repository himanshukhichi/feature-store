package com.example.featurestore.service;

import com.example.featurestore.dto.BackfillRecord;
import com.example.featurestore.dto.BackfillRequest;
import com.example.featurestore.dto.BackfillResponse;
import com.example.featurestore.dto.FeatureDefinition;
import com.example.featurestore.model.FeatureType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BackfillService {

    private final FeatureValidationService validationService;
    private final FeatureRegistryService registryService;
    private final OfflineStoreService offlineStoreService;

    public BackfillService(
            FeatureValidationService validationService,
            FeatureRegistryService registryService,
            OfflineStoreService offlineStoreService
    ) {
        this.validationService = validationService;
        this.registryService = registryService;
        this.offlineStoreService = offlineStoreService;
    }

    @Transactional
    public BackfillResponse backfillJson(BackfillRequest request) {
        int featureValuesWritten = 0;
        for (BackfillRecord record : request.records()) {
            validationService.validate(record.featureGroup(), record.features());
            featureValuesWritten += offlineStoreService.writeDirect(
                    record.entityId(),
                    record.featureGroup(),
                    record.features(),
                    record.eventTime()
            );
        }
        return new BackfillResponse(request.records().size(), featureValuesWritten);
    }

    @Transactional
    public BackfillResponse backfillCsv(String csvBody) {
        int recordsAccepted = 0;
        int featureValuesWritten = 0;
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build()
                .parse(new StringReader(csvBody))) {
            for (CSVRecord csvRecord : parser) {
                String featureGroup = csvRecord.get("featureGroup");
                String entityId = csvRecord.get("entityId");
                Instant eventTime = Instant.parse(csvRecord.get("eventTime"));
                Map<String, Object> features = parseFeatureColumns(featureGroup, csvRecord);
                validationService.validate(featureGroup, features);
                featureValuesWritten += offlineStoreService.writeDirect(entityId, featureGroup, features, eventTime);
                recordsAccepted++;
            }
        } catch (IOException exception) {
            throw new ValidationException("Invalid CSV body: " + exception.getMessage());
        }
        return new BackfillResponse(recordsAccepted, featureValuesWritten);
    }

    private Map<String, Object> parseFeatureColumns(String featureGroup, CSVRecord csvRecord) {
        Map<String, FeatureDefinition> schema = registryService.schemaFor(featureGroup).stream()
                .collect(Collectors.toMap(FeatureDefinition::name, Function.identity()));
        Map<String, Object> features = new LinkedHashMap<>();
        for (Map.Entry<String, String> column : csvRecord.toMap().entrySet()) {
            if (column.getKey().equals("featureGroup") || column.getKey().equals("entityId") || column.getKey().equals("eventTime")) {
                continue;
            }
            FeatureDefinition definition = schema.get(column.getKey());
            if (definition == null) {
                throw new ValidationException("Unknown feature '" + column.getKey() + "' for group '" + featureGroup + "'");
            }
            features.put(column.getKey(), parseValue(column.getValue(), definition.type()));
        }
        return features;
    }

    private Object parseValue(String rawValue, FeatureType type) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return switch (type) {
            case STRING -> rawValue;
            case INTEGER -> Long.parseLong(rawValue);
            case DOUBLE -> Double.parseDouble(rawValue);
            case BOOLEAN -> Boolean.parseBoolean(rawValue);
        };
    }
}
