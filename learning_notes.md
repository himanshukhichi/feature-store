# Feature Store Learning Notes

This document explains the current Feature Store project from beginner level to SDE-2 interview depth.

It is based on the actual files in this repository, not on a generic feature store design. When something is not implemented yet, this document says so explicitly.

## Table Of Contents

1. [Project Overview](#1-project-overview)
2. [Complete End-To-End Flow](#2-complete-end-to-end-flow)
3. [Component-By-Component Explanation](#3-component-by-component-explanation)
4. [API Flow](#4-api-flow)
5. [Feature Registry](#5-feature-registry)
6. [Feature Ingestion Flow](#6-feature-ingestion-flow)
7. [Feature Serving Flow](#7-feature-serving-flow)
8. [Algorithms And Core Logic](#8-algorithms-and-core-logic)
9. [Redis Integration](#9-redis-integration)
10. [Database And Persistent Storage](#10-database-and-persistent-storage)
11. [Prometheus Metrics](#11-prometheus-metrics)
12. [Grafana Dashboard](#12-grafana-dashboard)
13. [Spring Boot And Java Architecture](#13-spring-boot-and-java-architecture)
14. [Test Module](#14-test-module)
15. [Configuration](#15-configuration)
16. [Docker And Local Setup](#16-docker-and-local-setup)
17. [Error Handling And Edge Cases](#17-error-handling-and-edge-cases)
18. [Performance And Scalability](#18-performance-and-scalability)
19. [Production Readiness](#19-production-readiness)
20. [Interview Explanation](#20-interview-explanation)
21. [Code Walkthrough](#21-code-walkthrough)
22. [Best Practices And Improvement Roadmap](#22-best-practices-and-improvement-roadmap)

---

# 1. Project Overview

## 1.1 What Problem This Feature Store Solves

This project is a backend service for storing, validating, serving, and monitoring machine learning features.

A feature is an input used by a machine learning model. Examples:

```json
{
  "age": 31,
  "country": "IN",
  "ltv": 42.5,
  "is_premium": true
}
```

In a real ML application, a model rarely receives raw user data directly. Instead, the system computes or stores meaningful values, then the model consumes those values. Those values are features.

This project solves the following problems:

- A team can register feature schemas, such as `age` must be an integer and `country` must be a string.
- Clients can ingest feature values through an HTTP API.
- Ingested values are validated before they enter the system.
- Accepted values are published to Kafka.
- Redis stores the latest feature vector for low-latency online inference.
- PostgreSQL stores historical feature values for offline and point-in-time use.
- Clients can read current features, batch features, multi-group features, and point-in-time features.
- Historical values can be backfilled directly into PostgreSQL.
- Training datasets can be exported to CSV.
- Feature statistics can be computed.
- Prometheus and Grafana expose operational visibility.

The simplest mental model:

```text
Register schema -> ingest values -> validate -> Kafka
                                      |
                                      +-> Redis current values
                                      |
                                      +-> Postgres historical values

Read current values -> Redis first -> Postgres fallback
Read historical values -> Postgres
Export training data -> Postgres point-in-time lookups -> CSV
```

## 1.2 Why Feature Stores Are Needed In ML Systems

In ML systems, the same feature is often needed in two different contexts:

- Training: build datasets from historical data.
- Serving: fetch current features quickly during real-time prediction.

Without a feature store, teams often duplicate feature logic:

```text
Training pipeline calculates user_ltv one way.
Production service calculates user_ltv another way.
Analytics job calculates user_ltv a third way.
```

That creates training-serving skew:

```text
model learns from one version of the feature
production sends another version of the feature
prediction quality drops
```

This project reduces that risk by making feature definitions explicit and routing feature values through one ingestion and validation path.

## 1.3 Offline Features vs Online Features

In this project, offline and online have very specific meanings.

| Area | This project uses | Purpose |
|---|---|---|
| Online store | Redis | Fast current feature lookup for inference |
| Offline store | PostgreSQL | Historical feature storage for point-in-time lookup and export |
| Event stream | Kafka | Decouples ingestion from Redis/Postgres writes |

Online features:

- Stored in Redis.
- Represent the latest known values for an entity.
- Used by `GET /features/{featureGroup}/{entityId}` and batch/multi-group reads.
- Expire using the feature group's `freshnessTtlSeconds`.

Offline features:

- Stored in PostgreSQL table `feature_values`.
- Keep historical event-time rows.
- Used by `?asOf=...` reads, backfill, statistics, and export.
- Do not expire automatically.

Example:

```text
Online question:
What are user u-123's latest profile features right now?

Offline question:
What were user u-123's profile features at 2024-01-15T10:00:00Z?
```

## 1.4 Real-World Use Cases

This exact project could support simplified versions of these use cases:

- Fraud detection: fetch user risk features before approving a transaction.
- Recommendation systems: fetch user profile and content features before ranking items.
- Credit scoring: train models using historical point-in-time user features.
- Churn prediction: store customer behavior features and export training datasets.
- Personalization: serve current customer features quickly from Redis.
- Data quality monitoring: compute feature statistics and detect stale online vectors.

## 1.5 High-Level Architecture Of This Project

```text
                       +----------------------+
Client                 | Spring Boot REST API |
                       +----------+-----------+
                                  |
                                  v
                        +-------------------+
                        | Validation/Schema |
                        +----------+--------+
                                   |
                                   v
                              Kafka topic
                            feature-events
                             /          \
                            /            \
                           v              v
                 +--------------+   +----------------+
                 | Redis online |   | Postgres offline |
                 | store        |   | store/history    |
                 +------+-------+   +--------+---------+
                        |                    |
                        |                    |
                        v                    v
                  current reads      asOf reads/export/stats

Observability:

Spring Boot Actuator -> /actuator/prometheus -> Prometheus -> Grafana
```

Important files:

- `src/main/java/com/example/featurestore/controller/FeatureController.java`
- `src/main/java/com/example/featurestore/controller/FeatureGroupController.java`
- `src/main/java/com/example/featurestore/controller/FeatureQualityController.java`
- `src/main/java/com/example/featurestore/service/FeatureRegistryService.java`
- `src/main/java/com/example/featurestore/service/FeatureIngestionService.java`
- `src/main/java/com/example/featurestore/service/FeatureRetrievalService.java`
- `src/main/java/com/example/featurestore/service/OnlineStoreService.java`
- `src/main/java/com/example/featurestore/service/OfflineStoreService.java`
- `src/main/java/com/example/featurestore/service/BackfillService.java`
- `src/main/java/com/example/featurestore/service/FeatureExportService.java`
- `src/main/java/com/example/featurestore/service/FeatureStatisticsService.java`
- `src/main/java/com/example/featurestore/service/StalenessDetectionService.java`
- `src/main/java/com/example/featurestore/service/KafkaConsumerLagMetricsService.java`
- `src/main/resources/db/migration/V1__core_feature_store.sql`
- `src/main/resources/db/migration/V2__depth_advanced_obs.sql`
- `compose.yml`
- `prometheus.yml`
- `grafana/dashboards/feature-store-overview.json`

---

# 2. Complete End-To-End Flow

## 2.1 Flow Summary

The project has several flows:

1. Register a feature group.
2. Update a feature group schema and create a new version.
3. Ingest real-time feature values.
4. Consume Kafka events into Redis and PostgreSQL.
5. Fetch current features.
6. Fetch point-in-time features.
7. Batch-read multiple entities.
8. Multi-group read for one entity.
9. Backfill historical features.
10. Export training data.
11. Compute feature statistics.
12. Observe metrics in Prometheus and Grafana.

## 2.2 Register A Feature Group

Feature registration defines the contract for future ingestions.

Example request:

```bash
curl -X POST http://localhost:8080/feature-groups \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "user_profile",
    "entityType": "user",
    "freshnessTtlSeconds": 300,
    "features": [
      {"name": "age", "type": "INTEGER", "nullable": false, "defaultValue": null},
      {"name": "country", "type": "STRING", "nullable": false, "defaultValue": null},
      {"name": "ltv", "type": "DOUBLE", "nullable": true, "defaultValue": 0.0}
    ]
  }'
```

Internal flow:

```text
POST /feature-groups
  -> FeatureGroupController.register()
  -> FeatureRegistryService.register()
  -> FeatureGroupRepository.existsByName()
  -> ensureUniqueFeatureNames()
  -> ObjectMapper serializes features into schema_json
  -> FeatureGroupRepository.save()
  -> FeatureGroupVersionRepository.save()
  -> FeatureGroupResponse returned
```

Database writes:

- `feature_groups`: current schema row.
- `feature_group_versions`: immutable version snapshot row.

## 2.3 Update A Feature Group

The project supports schema updates:

```bash
curl -X PUT http://localhost:8080/feature-groups/user_profile \
  -H 'Content-Type: application/json' \
  -d '{
    "freshnessTtlSeconds": 300,
    "features": [
      {"name": "age", "type": "INTEGER", "nullable": false, "defaultValue": null},
      {"name": "country", "type": "STRING", "nullable": false, "defaultValue": null},
      {"name": "ltv", "type": "DOUBLE", "nullable": true, "defaultValue": 0.0},
      {"name": "is_premium", "type": "BOOLEAN", "nullable": true, "defaultValue": false}
    ]
  }'
```

Internal flow:

```text
PUT /feature-groups/{name}
  -> FeatureGroupController.update()
  -> FeatureRegistryService.update()
  -> ensureUniqueFeatureNames()
  -> requireGroup(name)
  -> nextVersion = currentVersion + 1
  -> FeatureGroupEntity.updateSchema()
  -> FeatureGroupRepository.save()
  -> FeatureGroupVersionRepository.save()
  -> FeatureGroupResponse returned
```

Important detail:

- Version history is stored.
- Compatibility rules are not implemented. For example, the code does not prevent deleting or changing existing feature types.

## 2.4 Ingest Feature Values

Example:

```bash
curl -X POST http://localhost:8080/features/user_profile/u-123 \
  -H 'Content-Type: application/json' \
  -d '{"age": 31, "country": "IN", "ltv": 42.5}'
```

Internal flow:

```text
POST /features/user_profile/u-123
  -> FeatureController.ingest()
  -> FeatureIngestionService.ingest()
  -> stable LinkedHashMap copy of payload
  -> FeatureValidationService.validate()
       -> FeatureRegistryService.schemaFor()
       -> validate feature names
       -> validate nullability
       -> validate primitive types
  -> create FeatureIngestedEvent
  -> KafkaTemplate.send(topicName, entityId, event)
  -> increment ingestion_events_total
  -> increment acceptedEvents
  -> return FeatureIngestionResponse
```

The response returns HTTP `202 Accepted`, meaning the event was accepted for asynchronous processing.

Important behavior:

- The API publishes to Kafka.
- Redis and PostgreSQL writes happen asynchronously in Kafka consumers.
- The system is eventually consistent.

## 2.5 Store Feature Values

Kafka has two independent consumers:

```text
feature-events topic
       |
       +-> groupId online-store-consumer
       |      -> FeatureEventConsumers.writeOnlineStore()
       |      -> OnlineStoreService.write()
       |      -> Redis
       |
       +-> groupId offline-store-consumer
              -> FeatureEventConsumers.writeOfflineStore()
              -> OfflineStoreService.write()
              -> PostgreSQL
```

Why separate consumer groups matter:

- Each consumer group gets its own copy of each event.
- Redis and PostgreSQL both receive all accepted feature events.

## 2.6 Fetch Current Features

Example:

```bash
curl http://localhost:8080/features/user_profile/u-123
```

Internal flow:

```text
GET /features/user_profile/u-123
  -> FeatureController.read()
  -> asOf parameter is absent
  -> FeatureRetrievalService.readCurrent()
  -> requireGroup(featureGroup)
  -> OnlineStoreService.read()
       -> Redis HGETALL feature:user_profile:u-123
  -> if Redis has values:
       record cache hit
       return FeatureVectorResponse(source="redis")
  -> else:
       record cache miss
       OfflineStoreService.readAsOf(now)
       return FeatureVectorResponse(source="postgres")
```

Response example:

```json
{
  "entityId": "u-123",
  "featureGroup": "user_profile",
  "features": {
    "age": 31,
    "country": "IN",
    "ltv": 42.5
  },
  "asOf": "2026-05-26T10:00:00Z",
  "source": "redis"
}
```

## 2.7 Fetch Point-In-Time Features

Example:

```bash
curl 'http://localhost:8080/features/user_profile/u-123?asOf=2024-01-15T10:00:00Z'
```

Internal flow:

```text
GET /features/user_profile/u-123?asOf=...
  -> FeatureController.read()
  -> asOf parameter is present
  -> FeatureRetrievalService.readAsOf()
  -> requireGroup(featureGroup)
  -> OfflineStoreService.readAsOf()
  -> FeatureValueRepository.findLatestValuesAsOf()
  -> return FeatureVectorResponse(source="postgres")
```

Redis is skipped because Redis contains only the latest online values, not historical versions.

## 2.8 Batch Read

Example:

```bash
curl -X POST http://localhost:8080/features/user_profile/batch \
  -H 'Content-Type: application/json' \
  -d '{"entityIds": ["u-123", "u-456"]}'
```

Internal flow:

```text
POST /features/{featureGroup}/batch
  -> FeatureController.batch()
  -> FeatureRetrievalService.readBatch()
  -> requireGroup(featureGroup)
  -> OnlineStoreService.readBatch()
       -> Redis pipelined HGETALL per entity
  -> for each entity:
       if Redis values found:
          result source = redis
       else:
          OfflineStoreService.readAsOf(now)
          result source = postgres
  -> BatchFeatureResponse returned
```

The batch path reduces Redis network overhead by using pipelining.

## 2.9 Multi-Group Read

Example:

```bash
curl -X POST http://localhost:8080/features/multi-group \
  -H 'Content-Type: application/json' \
  -d '{"entityId": "u-123", "featureGroups": ["user_profile"]}'
```

Internal flow:

```text
POST /features/multi-group
  -> FeatureController.multiGroup()
  -> FeatureRetrievalService.readMultiGroup()
  -> for each feature group:
       readCurrent(featureGroup, entityId)
       merge results into one feature map
       prefix each feature as featureGroup.featureName
  -> MultiGroupFeatureResponse returned
```

Example merged response:

```json
{
  "entityId": "u-123",
  "features": {
    "user_profile.age": 31,
    "user_profile.country": "IN"
  },
  "sources": {
    "user_profile": "redis"
  },
  "asOf": "2026-05-26T10:00:00Z"
}
```

## 2.10 Backfill Historical Data

Backfill writes directly into PostgreSQL. It does not publish to Kafka and does not update Redis.

JSON backfill:

```bash
curl -X POST http://localhost:8080/features/backfill \
  -H 'Content-Type: application/json' \
  -d '{
    "records": [
      {
        "featureGroup": "user_profile",
        "entityId": "u-123",
        "eventTime": "2024-01-15T10:00:00Z",
        "features": {"age": 30, "country": "IN", "ltv": 35.0}
      }
    ]
  }'
```

CSV backfill:

```bash
curl -X POST http://localhost:8080/features/backfill \
  -H 'Content-Type: text/csv' \
  --data-binary $'featureGroup,entityId,eventTime,age,country,ltv\nuser_profile,u-123,2024-01-15T10:00:00Z,30,IN,35.0'
```

Internal flow:

```text
POST /features/backfill
  -> FeatureController.backfillJson() or backfillCsv()
  -> BackfillService
  -> validate feature values
  -> OfflineStoreService.writeDirect()
  -> feature_values rows inserted
  -> BackfillResponse returned
```

## 2.11 Export Training Dataset

Example:

```bash
curl -X POST http://localhost:8080/features/export \
  -H 'Content-Type: application/json' \
  -d '{
    "featureGroups": ["user_profile"],
    "points": [
      {"entityId": "u-123", "timestamp": "2024-01-15T10:00:00Z"}
    ]
  }'
```

Internal flow:

```text
POST /features/export
  -> FeatureController.export()
  -> FeatureExportService.export()
  -> build CSV header from feature group schemas
  -> for each ExportPoint:
       for each feature group:
          OfflineStoreService.readAsOf(group, entityId, timestamp)
       merge values as group.featureName columns
  -> write CSV to build/exports/training-dataset-{millis}.csv
  -> FeatureExportResponse returned
```

The export path is offline-only. It is designed for training dataset generation, not online inference.

## 2.12 Compute Feature Statistics

Example:

```bash
curl -X POST http://localhost:8080/feature-stats/compute
curl http://localhost:8080/feature-stats/user_profile
```

Internal flow:

```text
POST /feature-stats/compute
  -> FeatureQualityController.compute()
  -> FeatureStatisticsService.computeStatistics()
  -> OfflineStoreService.allValues()
  -> group all historical rows by feature group and feature name
  -> compute total count, null count, null rate
  -> for numeric values compute mean, stddev, min, max, p50, p95
  -> FeatureStatisticsRepository.saveAll()
  -> FeatureStatisticsResponse list returned
```

There is also a scheduled daily computation:

```text
@Scheduled(cron = "0 0 2 * * *")
```

That runs every day at 2 AM according to the application scheduler timezone.

---

# 3. Component-By-Component Explanation

## 3.1 Controller Layer

Controllers receive HTTP requests and delegate business logic to services.

### FeatureGroupController

File:

```text
src/main/java/com/example/featurestore/controller/FeatureGroupController.java
```

Purpose:

- Register feature groups.
- Fetch current feature group metadata.
- Update schemas.
- List schema versions.
- Fetch a specific schema version.

Called by:

- HTTP clients.

Calls next:

- `FeatureRegistryService`.

Why it exists:

- Keeps HTTP routing separate from registry business logic.

### FeatureController

File:

```text
src/main/java/com/example/featurestore/controller/FeatureController.java
```

Purpose:

- Ingest features.
- Read current or point-in-time features.
- Batch-read features.
- Multi-group read features.
- Backfill historical data.
- Export training dataset.

Called by:

- HTTP clients.

Calls next:

- `FeatureIngestionService`
- `FeatureRetrievalService`
- `BackfillService`
- `FeatureExportService`

Why it exists:

- Exposes the main feature store API surface.

### FeatureQualityController

File:

```text
src/main/java/com/example/featurestore/controller/FeatureQualityController.java
```

Purpose:

- Trigger feature statistics computation.
- Fetch latest feature statistics for a group.

Called by:

- HTTP clients or operators.

Calls next:

- `FeatureStatisticsService`.

Why it exists:

- Separates feature quality/statistics APIs from serving/ingestion APIs.

### ApiExceptionHandler

File:

```text
src/main/java/com/example/featurestore/controller/ApiExceptionHandler.java
```

Purpose:

- Converts custom exceptions and validation failures into HTTP responses.

Handles:

- `NotFoundException` -> HTTP 404
- `ValidationException` -> HTTP 400
- `MethodArgumentNotValidException` -> HTTP 400

Why it exists:

- Keeps controller methods clean.
- Gives clients consistent error responses using Spring `ProblemDetail`.

## 3.2 Service Layer

Services contain business logic.

### FeatureRegistryService

Purpose:

- Owns schema registration, schema update, schema lookup, and version history.

Input:

- `RegisterFeatureGroupRequest`
- `UpdateFeatureGroupRequest`
- feature group name
- version number

Output:

- `FeatureGroupResponse`
- `FeatureGroupVersionResponse`
- `FeatureGroupEntity`
- list of `FeatureDefinition`

Calls:

- `FeatureGroupRepository`
- `FeatureGroupVersionRepository`
- Jackson `ObjectMapper`

Why it exists:

- Feature schema is a core platform concept. Validation, Redis TTL, export, and serving all depend on it.

### FeatureValidationService

Purpose:

- Validates incoming feature values against the registered schema.

Input:

- feature group name
- `Map<String, Object>` feature payload

Output:

- no return value if valid
- throws `ValidationException` if invalid

Calls:

- `FeatureRegistryService.schemaFor()`

Why it exists:

- Prevents invalid data from entering Kafka, Redis, and PostgreSQL.

### FeatureIngestionService

Purpose:

- Accepts feature payloads, validates them, publishes accepted events to Kafka, and updates ingestion metrics.

Input:

- feature group
- entity ID
- feature map

Output:

- `FeatureIngestionResponse`

Calls:

- `FeatureValidationService`
- `KafkaTemplate`
- Micrometer `Counter` and `Gauge`

Why it exists:

- Keeps ingestion flow separate from controller routing and storage concerns.

### FeatureEventConsumers

Purpose:

- Consumes Kafka feature events and dispatches them to stores.

Input:

- `FeatureIngestedEvent`

Output:

- no direct API response

Calls:

- `OnlineStoreService.write()`
- `OfflineStoreService.write()`

Why it exists:

- Enables event-driven fanout to Redis and PostgreSQL.

### OnlineStoreService

Purpose:

- Reads and writes current feature vectors in Redis.

Input:

- `FeatureIngestedEvent`
- feature group
- entity ID
- list of entity IDs

Output:

- map of feature values
- batch map of entity ID to feature values
- Redis last-updated entries
- Redis online keys

Calls:

- `StringRedisTemplate`
- `FeatureRegistryService`
- Jackson `ObjectMapper`

Why it exists:

- Isolates Redis data layout and serialization details.

### OfflineStoreService

Purpose:

- Writes and reads historical feature values in PostgreSQL.

Input:

- `FeatureIngestedEvent`
- direct backfill values
- point-in-time lookup parameters

Output:

- point-in-time feature map
- feature values list for statistics
- count of direct writes

Calls:

- `FeatureValueRepository`
- Jackson `ObjectMapper`
- Micrometer `Timer`

Why it exists:

- Isolates persistent history logic and point-in-time reads.

### FeatureRetrievalService

Purpose:

- Orchestrates feature serving.

Input:

- feature group
- entity ID
- optional `asOf`
- batch entity IDs
- multi-group request values

Output:

- `FeatureVectorResponse`
- `BatchFeatureResponse`
- `MultiGroupFeatureResponse`

Calls:

- `FeatureRegistryService`
- `OnlineStoreService`
- `OfflineStoreService`
- Micrometer metrics

Why it exists:

- Centralizes read strategy: Redis first, PostgreSQL fallback, PostgreSQL-only for historical reads.

### BackfillService

Purpose:

- Writes historical records directly into PostgreSQL.

Input:

- JSON backfill request
- CSV backfill body

Output:

- `BackfillResponse`

Calls:

- `FeatureValidationService`
- `FeatureRegistryService`
- `OfflineStoreService.writeDirect()`
- Apache Commons CSV

Why it exists:

- Supports offline data loading without touching Kafka or Redis.

### FeatureExportService

Purpose:

- Exports a point-in-time training dataset to a local CSV file.

Input:

- `FeatureExportRequest`

Output:

- `FeatureExportResponse`

Calls:

- `FeatureRegistryService`
- `OfflineStoreService.readAsOf()`
- Java NIO `Files`

Why it exists:

- Demonstrates offline training data generation.

### FeatureStatisticsService

Purpose:

- Computes and stores feature quality/statistical summaries.

Input:

- historical feature values from PostgreSQL

Output:

- `FeatureStatisticsResponse`

Calls:

- `OfflineStoreService`
- `FeatureStatisticsRepository`
- Jackson `ObjectMapper`

Why it exists:

- Supports feature quality monitoring, such as null rate and distribution drift signals.

### StalenessDetectionService

Purpose:

- Periodically detects online feature vectors whose last update is older than `2 * freshnessTtlSeconds`.

Input:

- Redis last-updated entries

Output:

- Prometheus gauge `stale_feature_vectors`
- warning logs

Calls:

- `OnlineStoreService.lastUpdatedEntries()`
- `FeatureRegistryService.requireGroup()`

Why it exists:

- Gives operators visibility into stale online feature data.

### KafkaConsumerLagMetricsService

Purpose:

- Periodically computes Kafka consumer lag for the feature store consumer groups.

Input:

- Kafka admin client offset metadata

Output:

- Prometheus gauge `kafka_consumer_lag`

Calls:

- Kafka `AdminClient`

Why it exists:

- Consumer lag tells whether Redis/PostgreSQL are falling behind ingestion.

## 3.3 Repository Layer

### FeatureGroupRepository

Purpose:

- Query and save current feature group metadata.

Important methods:

- `findByName(String name)`
- `existsByName(String name)`

### FeatureGroupVersionRepository

Purpose:

- Query schema version snapshots.

Important methods:

- `findByFeatureGroupNameOrderByVersionDesc()`
- `findByFeatureGroupNameAndVersion()`

### FeatureValueRepository

Purpose:

- Query historical feature values.

Important methods:

- `findLatestValuesAsOf(featureGroup, entityId, asOf)`
- `findByFeatureGroup(featureGroup)`

### FeatureStatisticsRepository

Purpose:

- Query computed feature statistics.

Important method:

- `findLatestByFeatureGroup(featureGroup)`

## 3.4 Model Layer

### FeatureGroupEntity

Maps to:

```text
feature_groups
```

Stores:

- current schema
- entity type
- TTL
- current version
- created/updated timestamps

### FeatureGroupVersionEntity

Maps to:

```text
feature_group_versions
```

Stores:

- immutable schema snapshot per version

### FeatureValueEntity

Maps to:

```text
feature_values
```

Stores:

- entity ID
- feature group
- feature name
- value JSON
- event time
- created time

### FeatureStatisticsEntity

Maps to:

```text
feature_statistics
```

Stores:

- total count
- null count
- null rate
- mean
- stddev
- min
- max
- p50
- p95

### FeatureType

Supported primitive feature types:

- `STRING`
- `INTEGER`
- `DOUBLE`
- `BOOLEAN`

---

# 4. API Flow

## 4.1 `POST /feature-groups`

Purpose:

- Register a new feature group.

Controller:

- `FeatureGroupController.register()`

Request:

```json
{
  "name": "user_profile",
  "entityType": "user",
  "freshnessTtlSeconds": 300,
  "features": [
    {"name": "age", "type": "INTEGER", "nullable": false, "defaultValue": null},
    {"name": "country", "type": "STRING", "nullable": false, "defaultValue": null}
  ]
}
```

Response:

```json
{
  "name": "user_profile",
  "entityType": "user",
  "features": [
    {"name": "age", "type": "INTEGER", "nullable": false, "defaultValue": null},
    {"name": "country", "type": "STRING", "nullable": false, "defaultValue": null}
  ],
  "freshnessTtlSeconds": 300,
  "version": 1,
  "createdAt": "...",
  "updatedAt": "..."
}
```

Internal call chain:

```text
FeatureGroupController.register()
  -> FeatureRegistryService.register()
     -> FeatureGroupRepository.existsByName()
     -> ensureUniqueFeatureNames()
     -> FeatureGroupRepository.save()
     -> FeatureGroupVersionRepository.save()
     -> toResponse()
```

Storage used:

- PostgreSQL via JPA.

## 4.2 `GET /feature-groups/{name}`

Purpose:

- Fetch current feature group schema.

Controller:

- `FeatureGroupController.get()`

Internal call chain:

```text
FeatureGroupController.get()
  -> FeatureRegistryService.requireGroup()
  -> FeatureRegistryService.toResponse()
```

Storage used:

- PostgreSQL `feature_groups`.

## 4.3 `PUT /feature-groups/{name}`

Purpose:

- Update a feature group schema and create a new schema version.

Controller:

- `FeatureGroupController.update()`

Request:

```json
{
  "freshnessTtlSeconds": 300,
  "features": [
    {"name": "age", "type": "INTEGER", "nullable": false, "defaultValue": null},
    {"name": "country", "type": "STRING", "nullable": false, "defaultValue": null},
    {"name": "is_premium", "type": "BOOLEAN", "nullable": true, "defaultValue": false}
  ]
}
```

Internal call chain:

```text
FeatureGroupController.update()
  -> FeatureRegistryService.update()
     -> ensureUniqueFeatureNames()
     -> requireGroup()
     -> group.updateSchema()
     -> FeatureGroupRepository.save()
     -> FeatureGroupVersionRepository.save()
```

Storage used:

- PostgreSQL `feature_groups`
- PostgreSQL `feature_group_versions`

## 4.4 `GET /feature-groups/{name}/versions`

Purpose:

- List all schema versions for a feature group.

Internal call chain:

```text
FeatureGroupController.versions()
  -> FeatureRegistryService.versions()
     -> requireGroup()
     -> FeatureGroupVersionRepository.findByFeatureGroupNameOrderByVersionDesc()
```

## 4.5 `GET /feature-groups/{name}/versions/{version}`

Purpose:

- Fetch one specific schema version.

Internal call chain:

```text
FeatureGroupController.version()
  -> FeatureRegistryService.version()
     -> requireGroup()
     -> FeatureGroupVersionRepository.findByFeatureGroupNameAndVersion()
```

## 4.6 `POST /features/{featureGroup}/{entityId}`

Purpose:

- Ingest real-time feature values.

Controller:

- `FeatureController.ingest()`

Request:

```json
{
  "age": 31,
  "country": "IN",
  "ltv": 42.5
}
```

Response:

```json
{
  "entityId": "u-123",
  "featureGroup": "user_profile",
  "acceptedFeatures": ["age", "country", "ltv"],
  "timestamp": "..."
}
```

Internal call chain:

```text
FeatureController.ingest()
  -> FeatureIngestionService.ingest()
     -> FeatureValidationService.validate()
        -> FeatureRegistryService.schemaFor()
     -> KafkaTemplate.send()
     -> increment metrics
```

Storage/cache used:

- Kafka immediately.
- Redis and PostgreSQL asynchronously through consumers.

## 4.7 `GET /features/{featureGroup}/{entityId}`

Purpose:

- Read current online feature values.

Controller:

- `FeatureController.read()`

Internal call chain:

```text
FeatureController.read()
  -> FeatureRetrievalService.readCurrent()
     -> FeatureRegistryService.requireGroup()
     -> OnlineStoreService.read()
        -> Redis hash entries
     -> if hit:
          return redis response
        else:
          OfflineStoreService.readAsOf(now)
          return postgres response
```

## 4.8 `GET /features/{featureGroup}/{entityId}?asOf=...`

Purpose:

- Read point-in-time feature values.

Internal call chain:

```text
FeatureController.read()
  -> FeatureRetrievalService.readAsOf()
     -> FeatureRegistryService.requireGroup()
     -> OfflineStoreService.readAsOf()
        -> FeatureValueRepository.findLatestValuesAsOf()
```

## 4.9 `POST /features/{featureGroup}/batch`

Purpose:

- Read one feature group for many entity IDs.

Request:

```json
{
  "entityIds": ["u-123", "u-456"]
}
```

Internal call chain:

```text
FeatureController.batch()
  -> FeatureRetrievalService.readBatch()
     -> OnlineStoreService.readBatch()
        -> Redis pipelined HGETALL
     -> per entity fallback to PostgreSQL if needed
```

## 4.10 `POST /features/multi-group`

Purpose:

- Read multiple feature groups for one entity.

Request:

```json
{
  "entityId": "u-123",
  "featureGroups": ["user_profile", "user_activity"]
}
```

Internal call chain:

```text
FeatureController.multiGroup()
  -> FeatureRetrievalService.readMultiGroup()
     -> readCurrent(group1, entityId)
     -> readCurrent(group2, entityId)
     -> merge as group.featureName
```

## 4.11 `POST /features/backfill`

Purpose:

- Backfill historical feature values into PostgreSQL.

Supported content types:

- `application/json`
- `text/csv`

Internal call chain:

```text
FeatureController.backfillJson()
  -> BackfillService.backfillJson()
     -> FeatureValidationService.validate()
     -> OfflineStoreService.writeDirect()

FeatureController.backfillCsv()
  -> BackfillService.backfillCsv()
     -> CSVParser
     -> parse values based on schema type
     -> FeatureValidationService.validate()
     -> OfflineStoreService.writeDirect()
```

## 4.12 `POST /features/export`

Purpose:

- Export point-in-time feature vectors to local CSV.

Internal call chain:

```text
FeatureController.export()
  -> FeatureExportService.export()
     -> FeatureRegistryService.schemaFor()
     -> OfflineStoreService.readAsOf()
     -> Files.write()
```

Output:

```json
{
  "path": "/absolute/path/build/exports/training-dataset-....csv",
  "rowsWritten": 1
}
```

## 4.13 `POST /feature-stats/compute`

Purpose:

- Compute feature statistics from historical values.

Internal call chain:

```text
FeatureQualityController.compute()
  -> FeatureStatisticsService.computeStatistics()
     -> OfflineStoreService.allValues()
     -> compute counts/null rates/numeric stats
     -> FeatureStatisticsRepository.saveAll()
```

## 4.14 `GET /feature-stats/{featureGroup}`

Purpose:

- Read latest computed statistics for a feature group.

Internal call chain:

```text
FeatureQualityController.latest()
  -> FeatureStatisticsService.latestForGroup()
     -> FeatureStatisticsRepository.findLatestByFeatureGroup()
```

---

# 5. Feature Registry

## 5.1 How Features Are Defined

A feature is defined by `FeatureDefinition`:

```java
public record FeatureDefinition(
        @NotBlank String name,
        @NotNull FeatureType type,
        boolean nullable,
        Object defaultValue
) {}
```

The currently supported types are:

```text
STRING
INTEGER
DOUBLE
BOOLEAN
```

Feature definitions belong to feature groups.

Example feature group:

```json
{
  "name": "user_profile",
  "entityType": "user",
  "freshnessTtlSeconds": 300,
  "features": [
    {"name": "age", "type": "INTEGER", "nullable": false, "defaultValue": null},
    {"name": "country", "type": "STRING", "nullable": false, "defaultValue": null},
    {"name": "ltv", "type": "DOUBLE", "nullable": true, "defaultValue": 0.0}
  ]
}
```

## 5.2 What Metadata Is Stored

The current version is stored in `feature_groups`:

```text
id
name
entity_type
schema_json
freshness_ttl_seconds
version
created_at
updated_at
```

Historical versions are stored in `feature_group_versions`:

```text
id
feature_group_name
entity_type
schema_json
freshness_ttl_seconds
version
created_at
```

The schema is stored as JSON text in the database.

Why JSON instead of separate relational rows?

- It is simple for a prototype.
- The whole schema is read/written as one object.
- It avoids additional join tables.

Trade-off:

- Harder to query individual feature definitions using SQL.
- Harder to enforce feature-level constraints in the database.
- Schema compatibility logic must live in the application.

## 5.3 Feature Groups And Entities

Feature group:

- A named collection of related features.
- Example: `user_profile`.

Entity:

- The object the features describe.
- Example entity type: `user`.
- Example entity ID: `u-123`.

In this project, `entityType` is metadata only. The code does not enforce a separate entity registry or validate entity IDs against another table.

## 5.4 Schema Validation

Validation happens in `FeatureValidationService`.

Rules:

1. Payload cannot be null.
2. Payload cannot be empty.
3. Every feature name in the payload must exist in the registered schema.
4. Null values are allowed only when `nullable=true`.
5. Value runtime type must match the registered `FeatureType`.

Type rules:

```text
STRING  -> Java String
INTEGER -> Java Integer or Long
DOUBLE  -> Java Float, Double, Integer, or Long
BOOLEAN -> Java Boolean
```

Note:

- Numeric JSON values may deserialize into `Integer`, `Long`, or `Double`, depending on value shape and Jackson behavior.
- `DOUBLE` accepts integer-like numeric values too.

## 5.5 Versioning

Versioning is implemented at the schema snapshot level.

On registration:

- `feature_groups.version = 1`
- one row is inserted into `feature_group_versions`.

On update:

- current version increments by 1.
- `feature_groups` row is updated.
- new row is inserted into `feature_group_versions`.

Version lookup APIs:

```text
GET /feature-groups/{name}/versions
GET /feature-groups/{name}/versions/{version}
```

Important limitation:

- Versioned schemas are stored and retrievable.
- Ingested feature values do not store schema version.
- Point-in-time feature reads do not automatically select the schema version valid at that time.
- Compatibility checks are not enforced.

In an interview, say:

```text
The project implements schema version snapshots, but not full schema compatibility management.
```

## 5.6 Duplicate, Missing, Invalid Definitions

Handled:

- Duplicate feature group name -> `ValidationException`
- Duplicate feature names in one group -> `ValidationException`
- Empty `features` list -> Bean Validation error through `@NotEmpty`
- Missing feature group name -> `@NotBlank`
- Missing entity type on registration -> `@NotBlank`
- Missing feature type -> `@NotNull`
- TTL less than 1 -> `@Min(1)`

Not fully handled:

- Duplicate feature definitions across different groups are allowed.
- Schema compatibility rules are not checked.
- `defaultValue` type is not validated against `FeatureType`.
- Feature names do not have a custom regex for allowed characters.

---

# 6. Feature Ingestion Flow

## 6.1 How Feature Values Enter The System

Real-time ingestion enters through:

```text
POST /features/{featureGroup}/{entityId}
```

Example:

```json
{
  "age": 31,
  "country": "IN",
  "ltv": 42.5
}
```

Batch historical ingestion enters through:

```text
POST /features/backfill
```

The project has two ingestion styles:

| Style | Endpoint | Goes through Kafka | Updates Redis | Writes PostgreSQL |
|---|---|---:|---:|---:|
| Real-time ingestion | `POST /features/{group}/{entity}` | yes | yes, async | yes, async |
| Backfill ingestion | `POST /features/backfill` | no | no | yes, direct |

## 6.2 Real-Time Ingestion Flow

```text
Client
  -> FeatureController.ingest()
  -> FeatureIngestionService.ingest()
  -> FeatureValidationService.validate()
  -> KafkaTemplate.send()
  -> HTTP 202 response
  -> Kafka consumers update Redis/PostgreSQL
```

Important implementation:

```java
Map<String, Object> stableFeatures = new LinkedHashMap<>(features);
```

Why `LinkedHashMap`?

- Preserves insertion order for stable response and event payload ordering.
- Not required for correctness, but helpful for deterministic behavior.

## 6.3 Batch Ingestion vs Real-Time Ingestion

The project does not implement a high-throughput streaming batch ingest API for online values.

It implements:

- single HTTP real-time event ingestion
- JSON historical backfill
- CSV historical backfill

Backfill is offline-only. It writes to PostgreSQL history and intentionally does not update Redis.

Why this separation makes sense:

- Backfills usually represent historical corrections or training data.
- Updating Redis with old historical data would corrupt current online serving.

## 6.4 Validation Rules

Before real-time ingestion publishes to Kafka:

```text
FeatureValidationService.validate()
```

Before backfill writes to PostgreSQL:

```text
FeatureValidationService.validate()
```

This means both real-time and historical writes respect the current schema.

Important limitation:

- Backfilled historical records are validated against the current schema, not the schema version at the backfilled timestamp.

## 6.5 Transformation Logic

Real-time ingestion:

- No transformation.
- Values are accepted as JSON types and sent to Kafka.

CSV backfill:

- Parses strings into schema types:
  - `STRING` -> string
  - `INTEGER` -> `Long`
  - `DOUBLE` -> `Double`
  - `BOOLEAN` -> `Boolean.parseBoolean`

There is no feature computation logic such as rolling windows, aggregations, joins, or derived features.

## 6.6 Idempotency Handling

Kafka producer idempotence is enabled:

```java
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
```

This helps reduce duplicate Kafka records caused by producer retries.

But application-level idempotency is not implemented.

If a client sends the same ingestion request twice:

- Kafka gets two events.
- Redis ends with the same latest values if ordering is normal.
- PostgreSQL gets duplicate historical rows with different timestamps.

Missing idempotency features:

- client-provided event ID
- deduplication table
- unique event constraint
- exactly-once end-to-end processing
- idempotent Redis timestamp compare-and-set

## 6.7 Error Handling During Ingestion

Handled:

- Unknown feature group -> 404
- Empty payload -> 400
- Unknown feature name -> 400
- Type mismatch -> 400
- Null for non-nullable feature -> 400
- Duplicate feature group during registration -> 400

Weak spot:

- `kafkaTemplate.send()` is asynchronous and not awaited.
- The method returns after initiating the send.
- If Kafka send fails later, the API path does not currently convert that into an HTTP failure.

Production improvement:

```text
wait for send future or attach callback
record publish failures
return 503 if Kafka is unavailable
use outbox table if stronger reliability is required
```

## 6.8 How Data Is Written To Storage And Cache

Real-time:

```text
HTTP request
  -> Kafka event
     -> Redis write consumer
     -> Postgres write consumer
```

Backfill:

```text
HTTP request
  -> validation
  -> Postgres direct write
```

Redis write:

- One Redis hash per feature group and entity.
- TTL applied to the hash key.
- Last-updated timestamp written to `feature:last-updated`.

PostgreSQL write:

- One row per feature value.
- All historical rows retained.

---

# 7. Feature Serving Flow

## 7.1 Online Feature Lookup

Online lookup is handled by:

```text
FeatureRetrievalService.readCurrent()
```

Flow:

```text
readCurrent(featureGroup, entityId)
  -> check group exists
  -> read Redis hash
  -> if hash non-empty:
       return source="redis"
  -> else:
       read PostgreSQL as of now
       return source="postgres"
```

Why Redis first?

- Online inference needs low latency.
- Redis can return all fields in one hash lookup.

## 7.2 Multiple Features Fetched Together

For one entity and one group, Redis stores a whole feature vector as a hash:

```text
key: feature:user_profile:u-123
fields:
  age     -> 31
  country -> "IN"
  ltv     -> 42.5
```

The read operation fetches all fields:

```text
HGETALL feature:user_profile:u-123
```

This is good for model inference because the model usually needs a vector, not one feature at a time.

## 7.3 Entity Key Resolution

The entity key is directly taken from the URL:

```text
/features/{featureGroup}/{entityId}
```

For example:

```text
featureGroup = user_profile
entityId = u-123
```

Redis key:

```text
feature:user_profile:u-123
```

Kafka key:

```text
u-123
```

PostgreSQL columns:

```text
feature_group = user_profile
entity_id = u-123
```

## 7.4 Cache/Database Query Strategy

Current read:

```text
Redis first
Postgres fallback
```

Historical read:

```text
Postgres only
```

Batch read:

```text
Redis pipelined reads first
Postgres fallback per missing entity
```

Multi-group read:

```text
loop over groups
call readCurrent for each group
merge values with group prefix
```

## 7.5 Missing Feature Values

Current behavior:

- If Redis has no hash fields, it is treated as a cache miss.
- The service falls back to PostgreSQL.
- If PostgreSQL also has no values, the returned `features` map is empty.

Partial missing behavior:

- If Redis has some fields but not all schema-defined fields, the project still treats Redis as a hit.
- It does not fill missing fields from defaults.
- It does not merge Redis partial values with PostgreSQL fallback.
- It does not report missing feature names.

This is an important interview talking point.

## 7.6 Latency Optimization

Implemented:

- Redis online store.
- Redis hash reads.
- Redis pipelining for batch reads.
- Kafka decouples ingestion API from storage writes.
- Online read latency is timed with Micrometer.

Not implemented:

- schema cache in memory
- Redis cluster
- read timeout fallback handling
- request-level parallel multi-group reads
- batch PostgreSQL fallback query
- partial fallback merge

## 7.7 How The Response Is Built

Single read returns `FeatureVectorResponse`:

```java
public record FeatureVectorResponse(
        String entityId,
        String featureGroup,
        Map<String, Object> features,
        Instant asOf,
        String source
) {}
```

The `source` field is important:

- `redis` means online cache hit.
- `postgres` means fallback or point-in-time read.

Batch read returns:

```java
public record BatchFeatureResponse(
        String featureGroup,
        Map<String, FeatureVectorResponse> results
) {}
```

Multi-group read returns:

```java
public record MultiGroupFeatureResponse(
        String entityId,
        Map<String, Object> features,
        Map<String, String> sources,
        Instant asOf
) {}
```

---

# 8. Algorithms And Core Logic

## 8.1 Feature Validation Algorithm

Implemented in:

```text
FeatureValidationService.validate()
```

Algorithm:

```text
Input:
  featureGroup
  features map

Steps:
  if features is null or empty:
      reject

  schemaList = registryService.schemaFor(featureGroup)
  schemaMap = map featureName -> FeatureDefinition

  for each incoming feature:
      definition = schemaMap.get(featureName)
      if definition missing:
          reject
      if value is null and definition nullable is false:
          reject
      if value type does not match definition type:
          reject

  accept
```

Time complexity:

```text
n = number of schema features
m = number of incoming features

Build map: O(n)
Validate payload: O(m)
Total: O(n + m)
```

Space complexity:

```text
O(n) for schema map
```

## 8.2 Feature Lookup Algorithm

Current online lookup:

```text
Input:
  featureGroup
  entityId

Steps:
  require feature group exists
  redisKey = "feature:{featureGroup}:{entityId}"
  values = Redis HGETALL(redisKey)
  if values not empty:
      record hit
      return values from Redis
  else:
      record miss
      return latest values from PostgreSQL as of now
```

Time complexity:

```text
k = number of feature fields in vector

Redis path: O(k)
Postgres fallback: depends on index and historical rows
```

## 8.3 Batch Lookup Algorithm

Implemented in:

```text
OnlineStoreService.readBatch()
FeatureRetrievalService.readBatch()
```

Algorithm:

```text
Input:
  featureGroup
  entityIds

Steps:
  pipeline Redis HGETALL for all entity IDs
  for each entity:
      if Redis values found:
          return source redis
      else:
          query PostgreSQL as of now
          return source postgres
```

Benefit:

- Reduces network round trips to Redis.

Weakness:

- PostgreSQL fallback is still one query per missing entity.

## 8.4 Cache Update Strategy

This project uses event-driven cache update:

```text
HTTP ingest -> Kafka -> online consumer -> Redis
```

This is not read-through cache population. On cache miss, the system reads PostgreSQL and returns it, but it does not repopulate Redis from the fallback result.

## 8.5 Feature Freshness And TTL Logic

TTL is defined per feature group:

```json
"freshnessTtlSeconds": 300
```

Redis write applies:

```text
EXPIRE feature:{group}:{entity} 300
```

Meaning:

- The whole vector expires together.
- Freshness is group-level, not per-feature.

Staleness detection separately checks last update age:

```text
stale if now - lastUpdated > 2 * freshnessTtlSeconds
```

Important nuance:

- Redis feature hash can expire, but the `feature:last-updated` metadata hash does not expire.
- That means stale detection can still see old update timestamps even if the actual feature hash is gone.

## 8.6 Aggregation Or Transformation Logic

Not implemented for feature generation.

Implemented for quality statistics:

- mean
- standard deviation
- min
- max
- p50
- p95
- null rate

These statistics are computed over all historical rows currently returned by `OfflineStoreService.allValues()`.

## 8.7 Conflict Resolution

Redis conflict behavior:

- Later consumed write overwrites previous hash fields.
- There is no timestamp comparison before overwrite.

PostgreSQL conflict behavior:

- Append-only.
- Every write creates rows.
- No deduplication.

Out-of-order risk:

```text
event A timestamp 10:00
event B timestamp 10:05
B reaches Redis first
A reaches Redis second
Redis may end with older values from A
```

Kafka keying by `entityId` reduces this risk for events produced to the same partition, but the Redis write code itself does not enforce timestamp ordering.

## 8.8 Performance Impact

Strong paths:

- Redis current read is fast.
- Redis batch read uses pipelining.
- Kafka absorbs ingestion bursts.
- PostgreSQL has an index for point-in-time lookup.

Potentially expensive paths:

- Feature statistics reads all historical values into memory.
- Export performs point-in-time lookups per export point and feature group.
- Batch fallback queries PostgreSQL per missing entity.
- Schema lookup hits PostgreSQL repeatedly.

---

# 9. Redis Integration

## 9.1 Why Redis Is Used

Redis is used as the online feature store because online inference needs low latency.

Example real-time prediction path:

```text
user request arrives
  -> fetch latest user features
  -> call ML model
  -> return prediction
```

If feature lookup takes too long, the model response is slow. Redis helps because it is in-memory and supports fast hash reads.

## 9.2 What Data Is Stored In Redis

Main feature vector key:

```text
feature:{featureGroup}:{entityId}
```

Example:

```text
feature:user_profile:u-123
```

Stored as Redis hash fields:

```text
age      -> 31
country  -> "IN"
ltv      -> 42.5
```

Values are JSON strings. For example:

```text
age -> 31
country -> "IN"
```

Last-updated metadata key:

```text
feature:last-updated
```

Fields:

```text
user_profile:u-123 -> 2026-05-26T10:00:00Z
```

This supports staleness detection.

## 9.3 Redis Key Naming Pattern

Code:

```java
public String redisKey(String featureGroup, String entityId) {
    return "feature:%s:%s".formatted(featureGroup, entityId);
}
```

Pattern:

```text
feature:<featureGroup>:<entityId>
```

Benefits:

- Easy to inspect manually.
- Namespaced by feature store.
- Separates feature group and entity.

Trade-off:

- If feature group or entity IDs contain colons, parsing can become ambiguous in some contexts.

## 9.4 Value Structure

Redis hash:

```text
HGETALL feature:user_profile:u-123
```

Example output:

```text
1) "age"
2) "31"
3) "country"
4) "\"IN\""
5) "ltv"
6) "42.5"
```

The Java service deserializes each JSON string back to `Object`.

## 9.5 TTL And Expiry Handling

On Redis write:

```text
put all feature fields
expire key using feature group TTL
write last-updated metadata
```

TTL comes from the current feature group metadata:

```text
feature_groups.freshness_ttl_seconds
```

Why TTL is useful:

- Prevents old online values from being served forever.
- Forces fallback to PostgreSQL after expiration.
- Represents a freshness contract.

Limitation:

- TTL is per vector, not per field.
- Backfills do not update Redis.
- Last-updated metadata is not expired with the vector.

## 9.6 Read Flow

Single read:

```text
OnlineStoreService.read()
  -> redisTemplate.opsForHash().entries(key)
  -> deserialize each value
  -> return map
```

Batch read:

```text
OnlineStoreService.readBatch()
  -> redisTemplate.executePipelined()
  -> queue HGETALL for each entity
  -> deserialize each response
  -> return entityId -> feature map
```

## 9.7 Cache Hit/Miss Behavior

Cache hit:

```text
Redis hash entries map is non-empty
```

Cache miss:

```text
Redis hash entries map is empty
```

Metrics:

- `cache_hits_total`
- `cache_misses_total`
- `cache_hit_rate`
- `online_reads_total{result="hit"}`
- `online_reads_total{result="miss"}`

## 9.8 What Happens If Redis Is Unavailable

Current behavior:

- There is no explicit try/catch around Redis reads or writes.
- Redis failures likely propagate as exceptions.
- The current read path does not gracefully fallback to PostgreSQL on Redis exceptions.

Production improvement:

```text
try Redis read
if Redis timeout/connection failure:
    increment redis_errors_total
    fallback to PostgreSQL
    return source="postgres"
```

For writes:

```text
if Redis consumer fails:
    let Kafka retry according to consumer behavior
    use dead-letter topic for poison messages
```

The current code does not configure a dead-letter topic.

## 9.9 Race Conditions And Consistency Issues

Potential issues:

1. Out-of-order Redis writes can overwrite newer values with older values.
2. Redis and PostgreSQL consumers can lag differently.
3. The API returns `202 Accepted` before consumers finish writing stores.
4. Redis TTL can expire while PostgreSQL still has values.
5. Partial updates can leave Redis with only some fields if clients send partial feature maps.
6. Last-updated metadata can outlive actual Redis feature vectors.

Consistency model:

```text
eventual consistency
```

This is acceptable for many ML serving systems, but it must be understood and monitored.

---

# 10. Database And Persistent Storage

## 10.1 Which Database Is Used

The project uses PostgreSQL in Docker Compose:

```yaml
postgres:
  image: postgres:16
```

The app connects through Spring datasource config:

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/feature_store}
```

## 10.2 Database Migrations

Flyway migrations:

```text
src/main/resources/db/migration/V1__core_feature_store.sql
src/main/resources/db/migration/V2__depth_advanced_obs.sql
```

Flyway is enabled:

```yaml
spring:
  flyway:
    enabled: true
```

Hibernate is set to validate:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Meaning:

- Flyway owns schema creation.
- Hibernate checks entity mappings match the schema.
- Hibernate does not auto-create/update production tables.

## 10.3 Tables

### `feature_groups`

Stores current feature group metadata.

Columns:

```text
id
name
entity_type
schema_json
freshness_ttl_seconds
version
created_at
updated_at
```

Important constraints:

- `name` is unique.

### `feature_values`

Stores historical feature values.

Columns:

```text
id
entity_id
feature_group
feature_name
value_json
event_time
created_at
```

One ingested feature map creates one row per feature.

Example ingestion:

```json
{
  "age": 31,
  "country": "IN",
  "ltv": 42.5
}
```

Creates:

```text
u-123 | user_profile | age     | 31
u-123 | user_profile | country | "IN"
u-123 | user_profile | ltv     | 42.5
```

### `feature_group_versions`

Stores schema version snapshots.

Columns:

```text
id
feature_group_name
entity_type
schema_json
freshness_ttl_seconds
version
created_at
```

Constraint:

```text
UNIQUE(feature_group_name, version)
```

### `feature_statistics`

Stores computed statistics.

Columns:

```text
id
feature_group
feature_name
computed_at
total_count
null_count
null_rate
mean
stddev
min
max
p50
p95
```

Constraint:

```text
UNIQUE(feature_group, feature_name, computed_at)
```

## 10.4 Relationships Between Entities

The database does not define foreign keys between tables.

Logical relationships:

```text
feature_groups.name
  -> feature_values.feature_group
  -> feature_group_versions.feature_group_name
  -> feature_statistics.feature_group
```

Why no foreign keys may have been chosen:

- Simpler prototype.
- Avoids constraints during backfill.

Trade-off:

- Database cannot prevent orphan rows if a feature group is deleted.
- Application must enforce consistency.

There is no delete feature group API currently.

## 10.5 Repository Layer

The project uses Spring Data JPA repositories.

Repository interfaces extend:

```java
JpaRepository<Entity, Long>
```

This gives standard methods:

- `save`
- `saveAll`
- `findAll`
- `findById`
- `delete`

Custom query exists for point-in-time lookup.

## 10.6 Point-In-Time Query

Implemented in `FeatureValueRepository.findLatestValuesAsOf()`.

Concept:

```text
For each feature name:
  find the latest value where event_time <= requested asOf timestamp.
```

Why it matters:

- Prevents future data leakage.
- Required for correct training datasets.

Example:

```text
age:
  2024-01-01 -> 29
  2024-02-01 -> 30
  2024-03-01 -> 31

asOf = 2024-02-15
return age = 30
```

## 10.7 Indexes

Important index:

```sql
CREATE INDEX idx_feature_values_lookup
ON feature_values (feature_group, entity_id, feature_name, event_time DESC);
```

This helps point-in-time lookups:

```text
feature_group = ?
entity_id = ?
feature_name = ?
event_time <= ?
order by event_time desc
```

Other indexes:

- `idx_feature_values_event_time`
- `idx_feature_group_versions_lookup`
- `idx_feature_statistics_latest`

## 10.8 Transaction Handling

Transactional methods:

- `FeatureRegistryService.register()`
- `FeatureRegistryService.update()`
- `OfflineStoreService.write()`
- `OfflineStoreService.writeDirect()`
- `OfflineStoreService.readAsOf(readOnly=true)`
- `BackfillService.backfillJson()`
- `BackfillService.backfillCsv()`
- `FeatureStatisticsService.computeStatistics()`

What transactions provide:

- Feature group and version snapshot are saved together.
- Backfill record writes are grouped in a transaction.
- Offline event writes for all features are grouped in a transaction.

Limitation:

- Kafka publish and database writes are not in one distributed transaction.
- Redis and PostgreSQL writes are independent consumers.

## 10.9 How Persistent Storage And Redis Work Together

```text
Redis:
  current, low-latency, expiring online values

PostgreSQL:
  durable, append-only historical values
```

Read strategy:

```text
current read:
  Redis first, PostgreSQL fallback

historical read:
  PostgreSQL only
```

Write strategy:

```text
real-time:
  Kafka fanout to both Redis and PostgreSQL

backfill:
  PostgreSQL only
```

---

# 11. Prometheus Metrics

## 11.1 How Prometheus Is Integrated

Dependencies:

```xml
spring-boot-starter-actuator
micrometer-registry-prometheus
```

Actuator config:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

Metrics endpoint:

```text
http://localhost:8080/actuator/prometheus
```

Prometheus config:

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: feature-store
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["app:8080"]
```

In Docker Compose:

- Prometheus runs on `localhost:9090`.
- It scrapes the app using Docker service name `app`.

## 11.2 Metrics Collected

### Ingestion metrics

Metric:

```text
ingestion_events_total
```

Type:

- Counter

Meaning:

- Total accepted ingestion events.

Useful for:

- Checking whether ingestion traffic is flowing.
- Detecting sudden drops or spikes.

Metric:

```text
ingestion_events_per_sec
```

Type:

- Gauge

Meaning:

- Approximate accepted ingestion events per second.

Implementation:

- `FeatureIngestionService` keeps an atomic count.
- Scheduled method refreshes the rate every second.

### Feature serving metrics

Metric:

```text
online_read_latency_ms
```

Type:

- Timer

Meaning:

- Measures Redis online read latency paths.

Important Micrometer naming note:

- Prometheus may expose timer series with names such as `online_read_latency_ms_seconds_count`, `online_read_latency_ms_seconds_sum`, and histogram bucket names if histogram buckets are available.
- The metric name includes `_ms`, but Micrometer Prometheus timers are usually exported in seconds. The Grafana dashboard multiplies by 1000.

Metric:

```text
online_reads_total{result="hit"}
online_reads_total{result="miss"}
```

Type:

- Function counters

Meaning:

- Total online read results by hit/miss.

### Cache hit/miss metrics

Metric:

```text
cache_hits_total
```

Meaning:

- Redis lookup returned non-empty feature vector.

Metric:

```text
cache_misses_total
```

Meaning:

- Redis lookup returned empty result and service fell back to PostgreSQL.

Metric:

```text
cache_hit_rate
```

Meaning:

- Fraction of online lookups served from Redis.

Formula in code:

```text
hits / (hits + misses)
```

### Offline write latency

Metric:

```text
offline_write_latency_ms
```

Type:

- Timer

Meaning:

- Measures PostgreSQL historical write latency.

Useful for:

- Detecting database slowness.
- Explaining Kafka consumer lag.

### Kafka consumer lag

Metric:

```text
kafka_consumer_lag
```

Type:

- Gauge

Meaning:

- Total lag across `online-store-consumer` and `offline-store-consumer`.

Useful for:

- Detecting whether Redis/PostgreSQL consumers are falling behind.

### Stale feature vectors

Metric:

```text
stale_feature_vectors
```

Type:

- Gauge

Meaning:

- Number of feature vectors whose last update age is greater than `2 * freshnessTtlSeconds`.

Useful for:

- Detecting stale online data.

## 11.3 Error Metrics

Custom error metrics are not currently implemented.

Missing useful metrics:

- validation failures
- unknown feature group errors
- Kafka publish failures
- Redis read/write failures
- PostgreSQL failures
- backfill failures
- export failures

Spring Boot Actuator may expose HTTP request metrics automatically, depending on auto-configuration, such as:

```text
http_server_requests_seconds_count
http_server_requests_seconds_sum
```

But this project does not define custom error counters.

## 11.4 How Metrics Help In Production Debugging

Scenario: reads are slow.

Check:

```text
online_read_latency_ms
cache_hit_rate
cache_misses_total
offline_write_latency_ms
```

Interpretation:

- Low cache hit rate means many reads fall back to PostgreSQL.
- High online latency means Redis path is slow.
- High offline write/read pressure can indicate database bottleneck.

Scenario: new features are not showing up.

Check:

```text
ingestion_events_total
kafka_consumer_lag
stale_feature_vectors
```

Interpretation:

- Ingestion increasing but Redis stale means consumers may lag or fail.
- Kafka lag increasing means Redis/PostgreSQL are not keeping up.

Scenario: training export looks wrong.

Check:

- Backfill response counts.
- PostgreSQL `feature_values`.
- Point-in-time queries.
- Feature group schema versions.

---

# 12. Grafana Dashboard

## 12.1 Grafana In This Project

Grafana is included in `compose.yml`:

```yaml
grafana:
  image: grafana/grafana:11.2.2
  ports:
    - "3000:3000"
```

Login:

```text
username: admin
password: admin
```

The dashboard is provisioned from:

```text
grafana/dashboards/feature-store-overview.json
```

The provisioning config is:

```text
grafana/provisioning/dashboards/dashboards.yml
```

Prometheus datasource provisioning is:

```text
grafana/provisioning/datasources/prometheus.yml
```

## 12.2 How Grafana Connects To Prometheus

Datasource config:

```yaml
url: http://prometheus:9090
```

Inside Docker Compose, service names work as hostnames.

So Grafana connects to:

```text
prometheus:9090
```

Your browser connects to:

```text
http://localhost:3000
```

## 12.3 Dashboard Panels Available

The provisioned dashboard is titled:

```text
Feature Store Overview
```

Panels from the JSON:

1. Ingestion Throughput
2. Cache Hit Rate
3. Online Read Latency
4. Kafka Consumer Lag
5. Stale Feature Vectors
6. Offline Write Latency

## 12.4 Important Panels To Track

### Request rate

The dashboard tracks ingestion throughput:

```promql
ingestion_events_per_sec
```

Additional useful request rate panel:

```promql
sum(rate(http_server_requests_seconds_count[5m])) by (uri, method)
```

### Error rate

Not currently present in the dashboard.

Potential panel:

```promql
sum(rate(http_server_requests_seconds_count{status=~"4..|5.."}[5m])) by (uri, status)
```

### Latency percentiles

Dashboard uses:

```promql
histogram_quantile(0.99, sum(rate(online_read_latency_ms_seconds_bucket[5m])) by (le)) * 1000
histogram_quantile(0.95, sum(rate(online_read_latency_ms_seconds_bucket[5m])) by (le)) * 1000
```

Important code-level caveat:

- `FeatureRetrievalService` configures `online_read_latency_ms` with `publishPercentiles(0.95, 0.99)`.
- It does not currently call `publishPercentileHistogram()` for the online read timer.
- The provisioned Grafana panel queries histogram bucket series.
- If `online_read_latency_ms_seconds_bucket` is not present in Prometheus, either enable percentile histograms for that timer or adjust the panel to use the exported percentile/summary series.

For offline writes:

```promql
histogram_quantile(0.95, sum(rate(offline_write_latency_ms_seconds_bucket[5m])) by (le)) * 1000
```

### Redis cache hit/miss ratio

Dashboard uses:

```promql
cache_hit_rate
```

Alternative:

```promql
rate(cache_hits_total[5m]) /
(rate(cache_hits_total[5m]) + rate(cache_misses_total[5m]))
```

### Feature ingestion count

Useful:

```promql
ingestion_events_total
rate(ingestion_events_total[5m])
```

### Feature serving count

Useful:

```promql
sum by (result) (online_reads_total)
rate(online_reads_total[5m])
```

### System health metrics

Useful JVM/process panels:

```promql
jvm_memory_used_bytes
process_cpu_usage
system_cpu_usage
hikaricp_connections_active
```

Exact availability depends on Spring Boot Actuator and runtime.

## 12.5 How To Interpret Dashboard Data

High ingestion throughput + high Kafka lag:

```text
Producer is accepting events faster than consumers can process them.
Redis/PostgreSQL may be stale.
```

Low cache hit rate:

```text
Redis does not have values for many requests.
Possible causes:
  - TTL too short
  - consumers lagging
  - reads happening before async write completes
  - feature group/entity never ingested online
```

High stale feature vector count:

```text
Online data is older than expected.
Possible causes:
  - ingestion stopped
  - upstream source stopped
  - consumers failing
  - TTL/freshness policy too strict
```

High online read latency:

```text
Redis may be slow, overloaded, or network-limited.
```

High offline write latency:

```text
PostgreSQL may be overloaded.
Kafka offline consumer may start lagging.
```

---

# 13. Spring Boot And Java Architecture

## 13.1 Layered Architecture

The project follows a common Spring Boot backend structure:

```text
Controller layer
  -> HTTP routing and request/response handling

Service layer
  -> business logic and orchestration

Repository layer
  -> database access

Model/entity layer
  -> JPA entity mappings

DTO layer
  -> request, response, and event data shapes

Config layer
  -> Kafka and application wiring
```

## 13.2 Controller Layer

Controllers are annotated with:

```java
@RestController
@RequestMapping(...)
```

They should be thin. This project keeps controllers mostly thin.

Examples:

- `FeatureGroupController`
- `FeatureController`
- `FeatureQualityController`

## 13.3 Service Layer

Services are annotated with:

```java
@Service
```

They hold business logic.

Examples:

- `FeatureRegistryService`
- `FeatureValidationService`
- `FeatureIngestionService`
- `FeatureRetrievalService`
- `OnlineStoreService`
- `OfflineStoreService`
- `BackfillService`
- `FeatureExportService`
- `FeatureStatisticsService`
- `StalenessDetectionService`
- `KafkaConsumerLagMetricsService`

## 13.4 Repository Layer

Repositories extend Spring Data JPA:

```java
JpaRepository<Entity, Long>
```

The project has:

- `FeatureGroupRepository`
- `FeatureGroupVersionRepository`
- `FeatureValueRepository`
- `FeatureStatisticsRepository`

## 13.5 DTOs

DTOs are Java records.

Examples:

- `RegisterFeatureGroupRequest`
- `UpdateFeatureGroupRequest`
- `FeatureDefinition`
- `FeatureGroupResponse`
- `FeatureGroupVersionResponse`
- `FeatureIngestedEvent`
- `FeatureIngestionResponse`
- `FeatureVectorResponse`
- `BatchFeatureRequest`
- `BatchFeatureResponse`
- `MultiGroupFeatureRequest`
- `MultiGroupFeatureResponse`
- `BackfillRequest`
- `BackfillRecord`
- `BackfillResponse`
- `FeatureExportRequest`
- `ExportPoint`
- `FeatureExportResponse`
- `FeatureStatisticsResponse`

Why records are a good fit:

- Immutable data carriers.
- Less boilerplate.
- Clear request/response shapes.

## 13.6 Entity/Model Classes

JPA entities:

- `FeatureGroupEntity`
- `FeatureGroupVersionEntity`
- `FeatureValueEntity`
- `FeatureStatisticsEntity`

Enum:

- `FeatureType`

## 13.7 Configuration Classes

### KafkaConfig

File:

```text
src/main/java/com/example/featurestore/config/KafkaConfig.java
```

Defines:

- producer factory
- Kafka template
- consumer factory
- listener container factory
- Kafka topic

Important settings:

```text
acks = all
enable.idempotence = true
auto.offset.reset = earliest
topic partitions = 6
replication factor = 1
```

Replication factor 1 is fine for local development, not production.

## 13.8 Dependency Injection Flow

Spring injects dependencies using constructors.

Example:

```text
FeatureController needs:
  FeatureIngestionService
  FeatureRetrievalService
  BackfillService
  FeatureExportService

Spring creates those service beans and passes them into the constructor.
```

This is constructor injection, which is a good practice because:

- dependencies are explicit
- fields can be final
- easier to test

## 13.9 Beans Used In The Project

Spring-created beans include:

- controllers
- services
- repositories
- Kafka producer/consumer factories
- Kafka template
- Kafka listener factory
- Kafka topic creation bean
- MeterRegistry
- ObjectMapper
- Redis template
- DataSource
- EntityManager/JPA infrastructure

## 13.10 Exception Handling

Custom exceptions:

- `NotFoundException`
- `ValidationException`

Global handler:

- `ApiExceptionHandler`

HTTP mapping:

```text
NotFoundException -> 404
ValidationException -> 400
MethodArgumentNotValidException -> 400
```

## 13.11 Validation Annotations

Used annotations:

- `@Valid`
- `@NotBlank`
- `@NotNull`
- `@NotEmpty`
- `@Min(1)`

Examples:

```java
public record RegisterFeatureGroupRequest(
        @NotBlank String name,
        @NotBlank String entityType,
        @NotEmpty List<@Valid FeatureDefinition> features,
        @Min(1) long freshnessTtlSeconds
) {}
```

Important limitation:

- `@RequestBody Map<String, Object> features` in real-time ingestion is not annotated with `@Valid`.
- Its validation happens manually in `FeatureValidationService`.

## 13.12 Profiles And Config Files

Main config:

```text
src/main/resources/application.yml
```

Test config:

```text
src/test/resources/application-test.yml
```

The test config uses H2:

```yaml
jdbc:h2:mem:feature_store;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
```

Current tests are mostly Mockito unit tests, so the H2 config is not heavily exercised.

---

# 14. Test Module

## 14.1 Current Test Structure

Test files:

```text
src/test/java/com/example/featurestore/service/FeatureValidationServiceTest.java
src/test/java/com/example/featurestore/service/FeatureRetrievalServiceTest.java
```

Current target reports show:

```text
FeatureValidationServiceTest: 4 tests
FeatureRetrievalServiceTest: 3 tests
Total: 7 tests, 0 failures
```

Note:

- These reports are from `target/surefire-reports`.
- If source code has changed after those reports, re-run tests through Docker or Maven to refresh them.

## 14.2 Unit Tests

### FeatureValidationServiceTest

Covers:

- accepts valid schema-matching payload
- rejects unknown feature names
- rejects wrong types
- rejects null for non-nullable feature

Mocking:

- `FeatureRegistryService` is mocked.

Why this is a unit test:

- It tests validation logic without database, Redis, Kafka, or Spring context.

### FeatureRetrievalServiceTest

Covers:

- current reads use Redis when values are present
- current reads fall back to PostgreSQL when Redis misses
- point-in-time reads always use PostgreSQL

Mocking:

- `FeatureRegistryService`
- `OnlineStoreService`
- `OfflineStoreService`

Uses:

- `SimpleMeterRegistry`

## 14.3 Integration Tests

There are no real integration tests currently.

Not present:

- Spring Boot context tests
- MockMvc controller tests
- repository tests against PostgreSQL
- Redis integration tests
- Kafka integration tests
- Testcontainers

## 14.4 Controller Tests

Not implemented.

Useful controller tests to add:

- `POST /feature-groups` returns 201.
- duplicate feature group returns 400.
- `POST /features/{group}/{entity}` returns 202 for valid payload.
- invalid payload returns 400.
- unknown group returns 404.
- batch endpoint validates non-empty entity list.
- CSV backfill accepts valid CSV.
- export returns path and row count.

## 14.5 Repository Tests

Not implemented.

Important repository test:

- Insert multiple historical values for the same feature.
- Query with `asOf`.
- Assert the latest value before `asOf` is returned.

This is critical because point-in-time correctness is central to feature stores.

## 14.6 Redis-Related Tests

Not implemented.

Useful tests:

- write event stores Redis hash.
- TTL is applied.
- last-updated metadata is written.
- batch read returns values in entity ID order.
- missing Redis key returns empty map.

## 14.7 Kafka Tests

Not implemented.

Useful tests:

- accepted ingestion publishes `FeatureIngestedEvent`.
- Kafka key is entity ID.
- online consumer calls Redis service.
- offline consumer calls PostgreSQL service.

## 14.8 Testcontainers Usage

Testcontainers is not present.

Recommended containers:

- PostgreSQL
- Redis
- Kafka

Why Testcontainers:

- H2 is not identical to PostgreSQL.
- Redis behavior needs real Redis.
- Kafka serialization/listener behavior needs real Kafka.

## 14.9 Missing Important Test Cases

High-value missing tests:

- feature group update creates new version
- version list is sorted descending
- version lookup returns correct schema
- backfill JSON writes direct offline rows
- backfill CSV parses types correctly
- export creates expected CSV content
- statistics compute numeric values correctly
- staleness detection increments stale count
- Kafka lag service handles Kafka unavailable gracefully
- Redis failure fallback behavior, once implemented
- idempotency behavior, if added
- out-of-order event protection, if added

## 14.10 How To Improve Test Coverage

Suggested progression:

1. Add unit tests for new services.
2. Add repository tests using Testcontainers PostgreSQL.
3. Add Redis integration tests using Testcontainers Redis.
4. Add Kafka integration tests using Testcontainers Kafka.
5. Add MockMvc controller tests.
6. Add end-to-end Docker Compose smoke test.

---

# 15. Configuration

## 15.1 Main Application Config

File:

```text
src/main/resources/application.yml
```

Important sections:

```yaml
spring:
  application:
    name: feature-store
```

Sets the app name.

## 15.2 Database Configuration

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/feature_store}
    username: ${SPRING_DATASOURCE_USERNAME:feature_store}
    password: ${SPRING_DATASOURCE_PASSWORD:feature_store}
```

Behavior:

- Use environment variable if present.
- Otherwise use local default.

Docker Compose overrides:

```yaml
SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/feature_store
SPRING_DATASOURCE_USERNAME: feature_store
SPRING_DATASOURCE_PASSWORD: feature_store
```

## 15.3 Flyway And JPA

```yaml
spring:
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
```

Meaning:

- Flyway runs migrations.
- Hibernate validates schema only.
- `open-in-view: false` prevents lazy DB access from leaking into view rendering.

## 15.4 Redis Configuration

```yaml
spring:
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:localhost}
      port: ${SPRING_DATA_REDIS_PORT:6379}
```

Docker Compose uses:

```yaml
SPRING_DATA_REDIS_HOST: redis
SPRING_DATA_REDIS_PORT: 6379
```

## 15.5 Kafka Configuration

```yaml
spring:
  kafka:
    bootstrap-servers: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
```

Docker Compose uses:

```yaml
SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
```

Topic config:

```yaml
feature-store:
  kafka:
    feature-events-topic: ${FEATURE_EVENTS_TOPIC:feature-events}
```

Default topic:

```text
feature-events
```

## 15.6 Prometheus/Actuator Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true
  metrics:
    tags:
      application: feature-store
```

Exposes:

- `/actuator/health`
- `/actuator/info`
- `/actuator/prometheus`

Adds metric tag:

```text
application=feature-store
```

## 15.7 Scheduling Configuration

Scheduling is enabled by:

```java
@EnableScheduling
```

Scheduled jobs:

- ingestion rate refresh every 1 second
- staleness detection every `${feature-store.staleness-check-delay-ms:60000}`
- Kafka lag refresh every `${feature-store.kafka-lag-check-delay-ms:30000}`
- daily feature statistics at `0 0 2 * * *`

The delay properties are referenced in code but not explicitly set in `application.yml`, so defaults apply.

## 15.8 Grafana/Docker Compose Configuration

`compose.yml` includes:

- PostgreSQL
- Redis
- Kafka
- app
- Prometheus
- Grafana

Grafana provisioning:

```text
grafana/provisioning/datasources/prometheus.yml
grafana/provisioning/dashboards/dashboards.yml
grafana/dashboards/feature-store-overview.json
```

## 15.9 Feature TTL Values

TTL is not globally configured.

TTL is per feature group:

```json
"freshnessTtlSeconds": 300
```

Stored in:

- `feature_groups.freshness_ttl_seconds`
- `feature_group_versions.freshness_ttl_seconds`

Used by:

- `OnlineStoreService.write()`
- `StalenessDetectionService.detectStaleFeatures()`

## 15.10 Environment-Specific Configs

Local:

- `compose.yml`

AWS demo:

- `compose.aws-demo.yml`
- `deploy/aws-free-tier-demo/terraform`

Test:

- `src/test/resources/application-test.yml`

## 15.11 Default Values And Override Behavior

Common defaults:

```text
Database URL: jdbc:postgresql://localhost:5432/feature_store
Database user: feature_store
Redis host: localhost
Redis port: 6379
Kafka bootstrap: localhost:9092
Kafka topic: feature-events
Staleness delay: 60000 ms
Kafka lag delay: 30000 ms
```

Environment variables override many of these at runtime.

---

# 16. Docker And Local Setup

## 16.1 How To Run Locally

From the repository root:

```bash
docker compose up --build
```

API:

```text
http://localhost:8080
```

Prometheus:

```text
http://localhost:9090
```

Grafana:

```text
http://localhost:3000
```

Grafana login:

```text
admin / admin
```

## 16.2 Docker Compose Services

### PostgreSQL

Purpose:

- Persistent storage for schema, history, versions, statistics.

Port:

```text
5432
```

Health check:

```text
pg_isready
```

### Redis

Purpose:

- Online low-latency feature store.

Port:

```text
6379
```

Health check:

```text
redis-cli ping
```

### Kafka

Purpose:

- Event stream for feature ingestion.

Port:

```text
9092
```

Internal listener:

```text
kafka:29092
```

### App

Purpose:

- Spring Boot feature store API.

Port:

```text
8080
```

Depends on:

- healthy PostgreSQL
- healthy Redis
- healthy Kafka

### Prometheus

Purpose:

- Scrapes `/actuator/prometheus`.

Port:

```text
9090
```

### Grafana

Purpose:

- Dashboards for metrics.

Port:

```text
3000
```

## 16.3 Application Container

Dockerfile uses a multi-stage build:

```text
Stage 1:
  maven:3.9.9-eclipse-temurin-21
  download dependencies
  run tests
  package jar

Stage 2:
  eclipse-temurin:21-jre
  run jar
```

Important:

```dockerfile
RUN mvn -q test package
```

So image build runs tests.

## 16.4 How Services Connect

Inside Docker Compose:

```text
app -> postgres:5432
app -> redis:6379
app -> kafka:29092
prometheus -> app:8080
grafana -> prometheus:9090
```

From your host browser:

```text
localhost:8080 -> app
localhost:9090 -> prometheus
localhost:3000 -> grafana
```

## 16.5 Useful Local Commands

Check health:

```bash
curl http://localhost:8080/actuator/health
```

Check Prometheus metrics:

```bash
curl http://localhost:8080/actuator/prometheus | grep ingestion
```

Inspect Redis:

```bash
docker compose exec redis redis-cli HGETALL feature:user_profile:u-123
docker compose exec redis redis-cli TTL feature:user_profile:u-123
docker compose exec redis redis-cli HGETALL feature:last-updated
```

Inspect PostgreSQL:

```bash
docker compose exec postgres psql -U feature_store -d feature_store -c "select * from feature_groups;"
docker compose exec postgres psql -U feature_store -d feature_store -c "select entity_id, feature_group, feature_name, value_json, event_time from feature_values order by event_time desc;"
```

View logs:

```bash
docker compose logs -f app
docker compose logs -f kafka
docker compose logs -f prometheus
```

## 16.6 AWS Demo Setup

Additional files:

```text
compose.aws-demo.yml
deploy/aws-free-tier-demo/README.md
deploy/aws-free-tier-demo/terraform/*.tf
```

Purpose:

- Demo the project on one EC2 instance.
- Not a production deployment.

AWS demo stack:

- EC2 instance
- security group for SSH/API/Grafana/Prometheus
- Docker installed through user data
- 2 GB swap file
- memory-limited Compose services

Important warning:

```text
This is for demos only. It is not production architecture.
```

## 16.7 Common Local Setup Issues

Port conflicts:

- 8080 already used
- 5432 already used
- 6379 already used
- 9092 already used
- 9090 already used
- 3000 already used

Kafka startup:

- Kafka can take longer than other services.
- App waits for Kafka health check.

First build:

- Maven dependency download may be slow.

Prometheus target down:

- Open `http://localhost:9090/targets`.
- Confirm `feature-store` target is up.

Grafana dashboard missing:

- Check Grafana provisioning paths.
- Check Docker volume mounts.
- Restart Grafana.

---

# 17. Error Handling And Edge Cases

## 17.1 Invalid Feature Definitions

Handled by Bean Validation:

- blank group name
- blank entity type
- empty features list
- missing feature name
- missing feature type
- TTL less than 1

Handled manually:

- duplicate feature names inside a group
- duplicate feature group name

Not handled:

- invalid default value type
- unsupported feature name characters
- schema compatibility checks during update

## 17.2 Missing Feature Values

Current behavior:

- Missing Redis values cause PostgreSQL fallback.
- Missing PostgreSQL values return empty feature map.
- Missing individual schema features are not explicitly reported.
- Defaults are not applied.

Possible production behavior:

- apply `defaultValue`
- return missing field list
- fail closed for required model features
- allow model-specific fallback policy

## 17.3 Redis Timeout/Failure

Current behavior:

- Redis exceptions are not caught in retrieval service.
- Request may fail instead of fallback to PostgreSQL.

Recommended behavior:

```text
catch Redis exception
record metric
fallback to PostgreSQL
return source="postgres"
```

## 17.4 Database Failure

Current behavior:

- Database exceptions bubble up as Spring errors.
- No custom DB failure response mapping exists.

Impact:

- Registration, versioning, fallback reads, backfills, statistics, and export can fail.

Recommended:

- custom exception handling
- retries for transient errors
- circuit breakers
- connection pool metrics and alerts

## 17.5 Duplicate Feature Registration

Handled:

```text
repository.existsByName()
throw ValidationException
HTTP 400
```

Race condition:

- Two concurrent requests can both pass `existsByName`.
- Database unique constraint ultimately protects the table.
- The resulting database exception is not mapped to a nice 400.

## 17.6 Schema Mismatch

Handled:

- unknown feature name
- wrong primitive type
- null for non-nullable

Not handled:

- missing required features on ingestion.

Important:

The validator allows partial updates. If schema has `age`, `country`, and `ltv`, the client can ingest only:

```json
{"age": 31}
```

This may be intentional for partial feature updates, but it means the system does not enforce complete vectors.

## 17.7 Stale Features

Handled partially:

- Redis TTL expires online feature vectors.
- Staleness detection counts old last-updated timestamps.

Not handled:

- Read response does not include freshness age.
- Read response does not warn if a PostgreSQL fallback is stale.
- Last-updated metadata does not expire with the feature key.

## 17.8 High Traffic Scenarios

Possible behavior:

- Kafka absorbs write bursts.
- Consumers may lag.
- Redis handles many reads if memory and CPU are sufficient.
- PostgreSQL may become bottleneck for fallback reads and writes.

Missing protections:

- rate limiting
- backpressure
- request queue limits
- bulk database writes
- consumer concurrency tuning

## 17.9 Partial Failure While Fetching Multiple Features

Batch read:

- Redis batch read is one pipelined operation.
- If Redis operation fails, there is no per-entity fallback handling around the exception.

Multi-group read:

- It loops through groups sequentially.
- If one group throws an exception, the whole request fails.

Possible improvement:

- Return partial results with error metadata per group/entity.
- Or fail closed for strict model-serving correctness.

## 17.10 Fail-Open vs Fail-Closed

Current behavior:

- Validation failures fail closed.
- Missing Redis values fail open to PostgreSQL fallback.
- Redis exceptions likely fail closed accidentally because they are not caught.
- Missing PostgreSQL feature values return empty results, which is a kind of fail-open behavior.

Production design decision:

- Fraud/credit models may prefer fail-closed.
- Recommendation systems may prefer fail-open with defaults.
- This project does not yet expose per-use-case policy.

---

# 18. Performance And Scalability

## 18.1 Current Performance Strengths

- Redis is used for low-latency current reads.
- Redis hash stores a whole feature vector.
- Batch reads use Redis pipelining.
- Kafka decouples ingestion API from store writes.
- PostgreSQL has indexes for point-in-time queries.
- Metrics expose cache hit rate, latency, lag, and stale vectors.

## 18.2 Possible Bottlenecks

### Redis bottlenecks

- single Redis instance
- hot keys
- large hashes
- memory pressure
- network round trips for non-batch reads
- `KEYS feature:*` in `onlineKeys()` can be dangerous at scale if used frequently

### PostgreSQL bottlenecks

- one row per feature value can grow very large
- no partitioning
- no retention policy
- JPA saves each feature value individually
- point-in-time query can become expensive with high history volume
- feature statistics scans all rows

### Kafka bottlenecks

- one local broker
- replication factor 1
- no DLQ
- consumer concurrency not tuned
- consumer lag is measured but not alerted

### App bottlenecks

- schema lookup can hit DB frequently
- multi-group read is sequential
- export can be slow for many points
- statistics computation loads all values into memory

## 18.3 Network Latency

In Docker Compose:

```text
app, Redis, Postgres, Kafka are on the same Docker network
```

In production:

- Redis should be close to app servers.
- PostgreSQL should be in same region/VPC.
- Kafka latency affects ingestion freshness.
- Cross-region calls would hurt online serving.

## 18.4 Horizontal Scaling

App:

- Can run multiple Spring Boot instances behind a load balancer.

Kafka:

- Topic has 6 partitions.
- Consumer groups can scale up to partition count.

Redis:

- Current setup is single instance.
- Production could use Redis Cluster or managed Redis.

PostgreSQL:

- Writes go to primary.
- Read replicas can support offline reads/exports.
- Partitioning can help history growth.

## 18.5 Cache Warming

Not implemented.

Possible cache warming:

- replay recent Kafka events
- load hot entities from PostgreSQL to Redis
- warm cache during deployment
- populate Redis on PostgreSQL fallback read

## 18.6 Batch Writes

Current PostgreSQL writes:

```text
repository.save() per feature value
```

Better:

- use `saveAll`
- use JDBC batch inserts
- group writes by event
- tune Hibernate batch size

## 18.7 Read Optimization

Current:

- Redis single vector read.
- Redis pipelined batch read.
- PostgreSQL fallback per missing entity.

Improvements:

- batch fallback query for multiple entities
- schema cache
- parallel multi-group reads
- read-through Redis population
- include requested feature list to avoid reading all fields

## 18.8 High-QPS Behavior

At high read QPS:

```text
If cache hit rate is high:
  system performs well.

If cache hit rate drops:
  PostgreSQL fallback can become a bottleneck.
```

At high write QPS:

```text
Kafka absorbs bursts.
Redis/PostgreSQL consumers may lag.
PostgreSQL write latency may increase.
Kafka consumer lag metric should rise.
```

At high export/statistics load:

```text
PostgreSQL and app memory can become bottlenecks.
```

---

# 19. Production Readiness

## 19.1 Strengths

- Clean Spring Boot layering.
- Clear feature registry.
- Schema validation exists.
- Real-time ingestion through Kafka.
- Redis online store.
- PostgreSQL offline store.
- Point-in-time reads.
- Batch reads and multi-group reads.
- Backfill support.
- Export support.
- Schema version snapshots.
- Feature statistics.
- Prometheus metrics.
- Grafana dashboard.
- Docker Compose setup.
- AWS demo runbook.

## 19.2 Weaknesses

- No authentication or authorization.
- No application-level idempotency.
- Kafka send is not awaited.
- No outbox pattern.
- No dead-letter topics.
- No Redis exception fallback.
- No schema compatibility validation.
- No full schema-version-aware historical validation.
- No default value application.
- No complete vector enforcement.
- No per-feature freshness.
- No retention policy for historical rows.
- No data deletion/privacy workflow.
- No integration tests.
- No Testcontainers.
- No tracing.
- No alert rules.

## 19.3 Observability Gaps

Implemented:

- ingestion rate
- cache hits/misses
- cache hit rate
- online read latency
- offline write latency
- Kafka consumer lag
- stale feature vectors
- Grafana dashboard

Missing:

- error counters
- Kafka publish failure metrics
- Redis error metrics
- PostgreSQL query latency metrics by operation
- backfill/export duration metrics
- per-feature-group metrics
- consumer processing failures
- DLQ size
- trace IDs across HTTP -> Kafka -> consumers

## 19.4 Data Consistency Concerns

Main consistency model:

```text
eventual consistency
```

Concerns:

- API can return before Redis/PostgreSQL are updated.
- Redis and PostgreSQL consumers can lag independently.
- Redis can be overwritten by out-of-order events.
- Duplicate ingestion creates duplicate history.
- Feature group updates are not tied to ingested values by version.

## 19.5 Security Concerns

Current local setup:

- no auth
- default database password
- default Grafana admin/admin
- public API if exposed
- no TLS
- no input rate limiting

Production needs:

- authentication
- authorization by feature group/team
- TLS
- secret management
- network isolation
- audit logging
- data classification
- PII handling
- row-level or tenant isolation if multi-tenant

## 19.6 Deployment Concerns

Docker Compose is fine for local/demo.

Production would need:

- Kubernetes or managed container platform
- managed PostgreSQL
- managed Kafka
- managed Redis
- autoscaling
- readiness/liveness probes
- resource requests/limits
- backups
- disaster recovery
- deployment rollbacks
- schema migration process

## 19.7 Improvements Needed Before Production

Top priority:

1. Add auth and authorization.
2. Add idempotency.
3. Wait for or robustly handle Kafka publish result.
4. Add dead-letter topics for consumers.
5. Add Redis fallback on exception.
6. Add schema compatibility checks.
7. Add integration tests with Testcontainers.
8. Add alerting rules.
9. Add out-of-order event protection for Redis.
10. Add retention/partitioning strategy for `feature_values`.

---

# 20. Interview Explanation

## 20.1 Two-Minute Explanation

This project is a simplified feature store for ML systems. It lets clients register feature groups with schemas, ingest feature values, and serve those values for online inference and offline point-in-time usage. The API is built with Spring Boot. Feature schemas are stored in PostgreSQL, and schema updates create version snapshots. Real-time feature ingestion validates payloads and publishes accepted events to Kafka. Two Kafka consumers independently update Redis for low-latency online serving and PostgreSQL for historical storage. Current reads check Redis first and fall back to PostgreSQL. Historical reads use PostgreSQL with an `asOf` timestamp. The project also supports batch reads, multi-group reads, backfills, CSV export for training datasets, feature statistics, and Prometheus/Grafana monitoring.

## 20.2 Five-Minute Explanation

The system has three main planes: registry, ingestion, and serving.

The registry lets users define feature groups like `user_profile`, with feature definitions such as `age` as an integer and `country` as a string. These schemas are stored in PostgreSQL, with version snapshots stored in a separate table.

For ingestion, clients call `POST /features/{featureGroup}/{entityId}` with feature values. The service validates names, types, and nullability against the registered schema. If valid, it creates a `FeatureIngestedEvent` and publishes it to Kafka using the entity ID as the Kafka key, which helps preserve per-entity ordering. Separate Kafka consumer groups process the same event stream: one updates Redis, and another appends values into PostgreSQL.

Redis acts as the online store. It stores one hash per feature group and entity, such as `feature:user_profile:u-123`, with feature names as fields. The hash expires based on the feature group's freshness TTL. PostgreSQL acts as the offline store. It stores one row per feature value with event time, enabling point-in-time lookups and training exports.

For serving, current reads first query Redis. If Redis has data, the service returns it with `source=redis`. If Redis misses, it falls back to PostgreSQL and returns `source=postgres`. If the request includes `asOf`, the service skips Redis and reads historical values from PostgreSQL. The project also implements batch reads using Redis pipelining and multi-group reads that merge multiple feature groups using prefixed names.

Operationally, the app exposes Prometheus metrics through Spring Actuator. It tracks ingestion throughput, cache hit rate, online read latency, offline write latency, Kafka consumer lag, and stale feature vectors. Grafana is provisioned with a Feature Store Overview dashboard.

## 20.3 Deep Technical Explanation

At a deeper level, this project is an eventually consistent, event-driven feature serving platform. The write API validates against the current feature registry and publishes to Kafka. Kafka is the boundary between synchronous request handling and asynchronous materialization. Redis and PostgreSQL are materialized views of the same event stream: Redis is optimized for current low-latency lookup, while PostgreSQL is optimized for durability and point-in-time history.

The registry is stored as JSON schema snapshots in PostgreSQL. The current schema lives in `feature_groups`, while every registration and update writes a snapshot to `feature_group_versions`. This gives reproducibility for schemas, although ingested feature rows do not yet store schema version, so full version-aware historical reconstruction is not implemented.

The online store uses Redis hashes keyed by `feature:{group}:{entity}`. This layout is efficient because model serving typically needs an entire feature vector. Batch reads use Redis pipelining to reduce network round trips. Feature freshness is enforced through Redis TTLs and observed through a scheduled staleness detector that counts vectors older than twice the configured TTL.

The offline store uses PostgreSQL rows at feature granularity: one row per entity, group, feature name, value, and event time. Point-in-time lookup uses a correlated subquery to find the latest value per feature before the requested timestamp. This is essential for avoiding future leakage in training datasets.

The main trade-off is eventual consistency. The API returns after publishing to Kafka, while Redis and PostgreSQL update asynchronously. This improves ingestion latency and decouples storage systems, but requires monitoring consumer lag and handling stale data carefully. The current system has good core architecture but would need idempotency, DLQs, stronger failure handling, auth, schema compatibility checks, and integration tests before production.

## 20.4 Common Interview Questions And Answers

### Why use Redis?

Redis is the online store. ML inference needs fast current feature lookup. This project stores a full entity feature vector in a Redis hash and fetches it with one hash read. That keeps serving latency low.

### Why use PostgreSQL?

PostgreSQL is the durable offline store. It keeps historical feature values with event timestamps, which allows point-in-time reads and training dataset exports.

### Why use Kafka?

Kafka decouples ingestion from storage. The API validates and publishes an event, then independent consumers update Redis and PostgreSQL. This reduces request latency, supports replay, and lets online/offline stores scale separately.

### Is the system strongly consistent?

No. It is eventually consistent. The API returns after publishing to Kafka, while Redis and PostgreSQL are updated asynchronously.

### How is feature freshness handled?

Each feature group has `freshnessTtlSeconds`. Redis keys expire using that TTL. There is also a staleness detector that counts vectors whose last update is older than twice the TTL.

### What happens on a Redis miss?

The service records a cache miss and falls back to PostgreSQL using `readAsOf(now)`.

### What happens on Redis failure?

Currently Redis exceptions are not caught, so the request can fail. A production improvement would catch Redis failures, record a metric, and fall back to PostgreSQL.

### How does point-in-time lookup work?

For each feature, PostgreSQL finds the latest row where `event_time <= asOf`. This avoids using future data during training or historical debugging.

### How does versioning work?

Current schema is stored in `feature_groups`. Every registration and update writes a snapshot to `feature_group_versions`. However, ingested feature values do not store schema version, so full version-aware historical serving is not implemented.

### How would you scale this?

Scale app instances horizontally, increase Kafka partitions and consumers, use Redis Cluster or managed Redis, partition PostgreSQL history tables by time, batch writes, cache schemas, and add monitoring/alerts.

### How would you make it production-ready?

Add auth, idempotency, DLQs, Redis exception fallback, schema compatibility checks, out-of-order event protection, Testcontainers integration tests, tracing, alerts, retention policies, and secure secret management.

## 20.5 Trade-Offs And Design Decisions

### Redis vs PostgreSQL for online reads

Redis is faster but less durable. PostgreSQL is durable but slower. This project uses Redis first and PostgreSQL fallback.

### Kafka async writes vs direct writes

Kafka makes writes eventually consistent but improves decoupling and scalability.

### JSON schema in DB vs normalized schema tables

JSON schema is simple and flexible. Normalized tables would support stronger SQL-level validation and querying.

### One row per feature vs one row per vector

One row per feature makes point-in-time per-feature lookup possible. It can create a lot of rows at scale.

### TTL per group vs per feature

Group TTL is simple. Per-feature TTL would be more precise but more complex.

## 20.6 How To Explain Redis Clearly

Say:

```text
Redis is the online materialized view of feature values. Each feature group/entity pair is stored as one Redis hash. That lets the serving path fetch a full feature vector in one low-latency operation. TTL is used to prevent serving stale online values forever.
```

## 20.7 How To Explain Prometheus And Grafana Clearly

Say:

```text
The Spring Boot app exposes metrics at /actuator/prometheus through Micrometer. Prometheus scrapes that endpoint every 15 seconds. Grafana is provisioned with Prometheus as a datasource and visualizes ingestion throughput, cache hit rate, online read latency, offline write latency, Kafka lag, and stale feature vectors.
```

## 20.8 How To Explain Latency Optimization

Say:

```text
The online read path is optimized by using Redis hashes and fetching the full vector in one call. Batch reads use Redis pipelining to reduce network round trips. Kafka keeps ingestion latency lower by avoiding synchronous writes to both Redis and PostgreSQL in the HTTP request path.
```

---

# 21. Code Walkthrough

## 21.1 `src/main/java/com/example/featurestore/FeatureStoreApplication.java`

Purpose:

- Application entry point.

Important annotations:

```java
@EnableScheduling
@SpringBootApplication
```

Why it matters:

- `@SpringBootApplication` starts component scanning and auto-configuration.
- `@EnableScheduling` enables scheduled metrics/statistics/staleness jobs.

## 21.2 `src/main/java/com/example/featurestore/controller/FeatureGroupController.java`

Purpose:

- HTTP interface for feature group registry.

Important methods:

- `register()`
- `get()`
- `update()`
- `versions()`
- `version()`

Who calls it:

- HTTP clients.

What it calls next:

- `FeatureRegistryService`.

How it fits:

- Entry point for schema lifecycle.

## 21.3 `src/main/java/com/example/featurestore/controller/FeatureController.java`

Purpose:

- HTTP interface for feature value operations.

Important methods:

- `ingest()`
- `read()`
- `batch()`
- `multiGroup()`
- `backfillJson()`
- `backfillCsv()`
- `export()`

Who calls it:

- HTTP clients.

What it calls next:

- `FeatureIngestionService`
- `FeatureRetrievalService`
- `BackfillService`
- `FeatureExportService`

How it fits:

- Main API surface for online and offline feature operations.

## 21.4 `src/main/java/com/example/featurestore/controller/FeatureQualityController.java`

Purpose:

- HTTP interface for feature statistics.

Important methods:

- `compute()`
- `latest()`

What it calls:

- `FeatureStatisticsService`.

How it fits:

- Gives operators visibility into feature quality.

## 21.5 `src/main/java/com/example/featurestore/controller/ApiExceptionHandler.java`

Purpose:

- Global error response mapping.

Important methods:

- `notFound()`
- `validation()`
- `requestValidation()`

How it fits:

- Converts internal exceptions into clear HTTP responses.

## 21.6 `src/main/java/com/example/featurestore/service/FeatureRegistryService.java`

Purpose:

- Owns registry operations.

Important methods:

- `register()`
- `update()`
- `requireGroup()`
- `schemaFor()`
- `versions()`
- `version()`
- `toResponse()`
- `writeSchema()`
- `readSchema()`

Important logic:

- duplicate group check
- duplicate feature name check
- schema JSON serialization
- schema version snapshot creation

How it fits:

- Every validation, TTL lookup, export, and registry API depends on it.

## 21.7 `src/main/java/com/example/featurestore/service/FeatureValidationService.java`

Purpose:

- Validate incoming feature values.

Important methods:

- `validate()`
- `matchesType()`

Important logic:

- feature payload must not be empty
- feature names must exist
- nullability enforced
- primitive type matching

How it fits:

- Protects Kafka and PostgreSQL from invalid feature payloads.

## 21.8 `src/main/java/com/example/featurestore/service/FeatureIngestionService.java`

Purpose:

- Real-time ingestion orchestration.

Important methods:

- `ingest()`
- `refreshIngestionRate()`

Important logic:

- validates payload
- creates `FeatureIngestedEvent`
- sends to Kafka with entity ID as key
- increments `ingestion_events_total`
- refreshes `ingestion_events_per_sec`

How it fits:

- Connects HTTP ingestion to Kafka event pipeline.

## 21.9 `src/main/java/com/example/featurestore/service/FeatureEventConsumers.java`

Purpose:

- Kafka listeners for online and offline stores.

Important methods:

- `writeOnlineStore()`
- `writeOfflineStore()`

Important logic:

- same topic, different consumer groups.

How it fits:

- Fanout from Kafka to Redis and PostgreSQL.

## 21.10 `src/main/java/com/example/featurestore/service/OnlineStoreService.java`

Purpose:

- Redis online store operations.

Important methods:

- `write()`
- `read()`
- `readBatch()`
- `lastUpdatedEntries()`
- `onlineKeys()`
- `redisKey()`

Important logic:

- stores feature vector as Redis hash
- applies TTL
- records last update timestamp
- uses pipelining for batch reads

How it fits:

- Provides low-latency current feature serving.

## 21.11 `src/main/java/com/example/featurestore/service/OfflineStoreService.java`

Purpose:

- PostgreSQL historical store operations.

Important methods:

- `write()`
- `writeDirect()`
- `readAsOf()`
- `valuesForGroup()`
- `allValues()`

Important logic:

- one row per feature value
- JSON serialization for values
- point-in-time read through repository
- timer for offline write latency

How it fits:

- Durable history for training, export, fallback, and statistics.

## 21.12 `src/main/java/com/example/featurestore/service/FeatureRetrievalService.java`

Purpose:

- Serving orchestration.

Important methods:

- `readCurrent()`
- `readAsOf()`
- `readBatch()`
- `readMultiGroup()`

Important logic:

- Redis first for current reads
- PostgreSQL fallback on cache miss
- PostgreSQL only for historical reads
- batch read uses Redis batch operation
- multi-group read prefixes fields as `group.feature`
- records cache hit/miss metrics

How it fits:

- Core read path for the feature store.

## 21.13 `src/main/java/com/example/featurestore/service/BackfillService.java`

Purpose:

- Offline historical ingestion.

Important methods:

- `backfillJson()`
- `backfillCsv()`
- `parseFeatureColumns()`
- `parseValue()`

Important logic:

- validates records
- parses CSV values based on schema
- writes directly to PostgreSQL

How it fits:

- Supports offline historical data loading.

## 21.14 `src/main/java/com/example/featurestore/service/FeatureExportService.java`

Purpose:

- Point-in-time CSV export.

Important methods:

- `export()`
- `featureColumns()`
- `pointInTimeVector()`
- `csvLine()`
- `escapeCsv()`

Important logic:

- builds columns from schema
- reads offline values as of each export timestamp
- writes CSV file to `build/exports`

How it fits:

- Demonstrates training dataset generation.

## 21.15 `src/main/java/com/example/featurestore/service/FeatureStatisticsService.java`

Purpose:

- Feature quality statistics.

Important methods:

- `computeDailyStatistics()`
- `computeStatistics()`
- `latestForGroup()`
- `computeOne()`
- `percentile()`

Important logic:

- groups all historical values by feature
- computes null rate
- computes numeric statistics
- stores statistics in PostgreSQL

How it fits:

- Supports monitoring and data quality analysis.

## 21.16 `src/main/java/com/example/featurestore/service/StalenessDetectionService.java`

Purpose:

- Detect stale online feature vectors.

Important method:

- `detectStaleFeatures()`

Important logic:

- reads Redis `feature:last-updated`
- compares age to `2 * freshnessTtlSeconds`
- logs warning
- updates `stale_feature_vectors` gauge

How it fits:

- Observability for online feature freshness.

## 21.17 `src/main/java/com/example/featurestore/service/KafkaConsumerLagMetricsService.java`

Purpose:

- Track Kafka consumer lag.

Important methods:

- `refreshConsumerLag()`
- `close()`

Important logic:

- reads committed consumer group offsets
- reads latest topic offsets
- computes total lag
- updates `kafka_consumer_lag`

How it fits:

- Observability for async ingestion pipeline health.

## 21.18 `src/main/java/com/example/featurestore/config/KafkaConfig.java`

Purpose:

- Kafka producer/consumer configuration.

Important methods/beans:

- `producerFactory()`
- `kafkaTemplate()`
- `consumerFactory()`
- `kafkaListenerContainerFactory()`
- `featureTopics()`

Important logic:

- JSON serialization/deserialization
- producer acks all
- producer idempotence
- topic creation with 6 partitions

How it fits:

- Enables event-driven architecture.

## 21.19 DTO Files

Purpose:

- Define data contracts for API requests, responses, and Kafka events.

Important groups:

Registry DTOs:

- `RegisterFeatureGroupRequest`
- `UpdateFeatureGroupRequest`
- `FeatureDefinition`
- `FeatureGroupResponse`
- `FeatureGroupVersionResponse`

Serving DTOs:

- `FeatureVectorResponse`
- `BatchFeatureRequest`
- `BatchFeatureResponse`
- `MultiGroupFeatureRequest`
- `MultiGroupFeatureResponse`

Ingestion DTOs:

- `FeatureIngestedEvent`
- `FeatureIngestionResponse`

Backfill/export DTOs:

- `BackfillRecord`
- `BackfillRequest`
- `BackfillResponse`
- `ExportPoint`
- `FeatureExportRequest`
- `FeatureExportResponse`

Statistics DTO:

- `FeatureStatisticsResponse`

## 21.20 Model Files

Purpose:

- Map Java objects to database tables.

Files:

- `FeatureGroupEntity.java`
- `FeatureGroupVersionEntity.java`
- `FeatureValueEntity.java`
- `FeatureStatisticsEntity.java`
- `FeatureType.java`

## 21.21 Repository Files

Purpose:

- Database access.

Files:

- `FeatureGroupRepository.java`
- `FeatureGroupVersionRepository.java`
- `FeatureValueRepository.java`
- `FeatureStatisticsRepository.java`

## 21.22 Migration Files

### `V1__core_feature_store.sql`

Creates:

- `feature_groups`
- `feature_values`
- indexes for feature value lookup

### `V2__depth_advanced_obs.sql`

Creates:

- `feature_group_versions`
- `feature_statistics`
- indexes for versions/statistics

Also backfills existing feature groups into `feature_group_versions`.

## 21.23 `compose.yml`

Purpose:

- Local development stack.

Services:

- PostgreSQL
- Redis
- Kafka
- app
- Prometheus
- Grafana

## 21.24 `prometheus.yml`

Purpose:

- Tells Prometheus where to scrape metrics.

Target:

```text
app:8080/actuator/prometheus
```

## 21.25 Grafana Files

Datasource:

```text
grafana/provisioning/datasources/prometheus.yml
```

Dashboard provider:

```text
grafana/provisioning/dashboards/dashboards.yml
```

Dashboard:

```text
grafana/dashboards/feature-store-overview.json
```

## 21.26 AWS Demo Files

Purpose:

- Demo the app stack on one EC2 instance.

Files:

- `compose.aws-demo.yml`
- `deploy/aws-free-tier-demo/README.md`
- Terraform files under `deploy/aws-free-tier-demo/terraform`

Important:

- This is demo-only, not production.

---

# 22. Best Practices And Improvement Roadmap

## 22.1 Immediate Engineering Improvements

1. Add integration tests with Testcontainers.
2. Handle Kafka send failures.
3. Add Redis exception fallback to PostgreSQL.
4. Add custom error metrics.
5. Add DLQ for Kafka consumers.
6. Add schema compatibility checks.
7. Add default value application.
8. Add request logging with correlation IDs.
9. Add OpenAPI documentation.
10. Add controller tests.

## 22.2 ML Platform Improvements

1. Store schema version with each feature value.
2. Support feature views or model-specific feature sets.
3. Add offline training dataset materialization jobs.
4. Add per-feature freshness.
5. Add drift detection from statistics.
6. Add missing-feature policy.
7. Add data lineage metadata.
8. Add feature ownership/team metadata.
9. Add feature deprecation lifecycle.
10. Add registry search/list APIs.

## 22.3 Scalability Improvements

1. Batch PostgreSQL writes.
2. Partition `feature_values` by event time.
3. Add retention policy.
4. Cache schemas in memory.
5. Parallelize multi-group reads.
6. Add batch PostgreSQL fallback.
7. Use Redis Cluster.
8. Increase Kafka replication factor and broker count.
9. Tune Kafka consumer concurrency.
10. Add read replicas for offline workloads.

## 22.4 Production Hardening

1. Authentication and authorization.
2. TLS.
3. Secret management.
4. Rate limiting.
5. Audit logs.
6. Alerts for lag, low hit rate, high latency, stale vectors.
7. Backup and restore.
8. Deployment health checks.
9. Disaster recovery plan.
10. Data privacy controls.

## 22.5 Final Mental Model

Remember the project like this:

```text
Registry:
  defines allowed feature groups, schemas, TTLs, and versions.

Ingestion:
  validates real-time features and emits Kafka events.

Online materialization:
  Kafka consumer writes latest feature vectors to Redis.

Offline materialization:
  Kafka consumer writes historical feature rows to PostgreSQL.

Serving:
  current reads use Redis first and PostgreSQL fallback.
  historical reads use PostgreSQL point-in-time lookup.

Offline tooling:
  backfill writes historical rows.
  export builds point-in-time CSV datasets.
  statistics compute quality metrics.

Observability:
  Micrometer exposes metrics.
  Prometheus scrapes the app.
  Grafana visualizes throughput, latency, cache hit rate, lag, and staleness.
```

That is the complete story you can confidently explain in an SDE-2 interview.
