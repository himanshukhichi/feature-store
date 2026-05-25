# feature-store

High-throughput feature serving platform with a dual-store architecture.

## Feature coverage

This implementation covers the core feature-store loop plus the depth, advanced,
and observability features from the roadmap:

- Register feature groups with schemas and freshness TTLs.
- Version feature group schemas on every update.
- Ingest features with `POST /features/{featureGroup}/{entityId}`.
- Validate feature names and primitive types against the registered schema.
- Publish accepted events to Kafka with `entityId` as the message key for per-entity ordering.
- Consume the same event stream into Redis for online serving.
- Consume the same event stream into PostgreSQL for full historical offline storage.
- Retrieve current online features from Redis with PostgreSQL fallback.
- Retrieve point-in-time feature values from PostgreSQL with `?asOf=...`.
- Batch-read one group for many entity IDs from Redis with PostgreSQL fallback.
- Multi-group read for one entity into a merged feature vector.
- Backfill historical feature values directly into PostgreSQL without touching Redis or Kafka.
- Export point-in-time training datasets to local CSV.
- Compute feature statistics and expose latest stats by group.
- Detect stale Redis vectors when last update age exceeds `2 * freshnessTtlSeconds`.
- Expose Prometheus metrics at `/actuator/prometheus`.

## Run locally

```bash
docker compose up --build
```

The API runs on `http://localhost:8080`.

Register a feature group:

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

Ingest feature values:

```bash
curl -X POST http://localhost:8080/features/user_profile/u-123 \
  -H 'Content-Type: application/json' \
  -d '{"age": 31, "country": "IN", "ltv": 42.5}'
```

Read current values:

```bash
curl http://localhost:8080/features/user_profile/u-123
```

Read point-in-time values:

```bash
curl 'http://localhost:8080/features/user_profile/u-123?asOf=2024-01-15T10:00:00Z'
```

Update a feature group schema. This creates a new version while preserving older
versions for reproducibility:

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

List schema versions:

```bash
curl http://localhost:8080/feature-groups/user_profile/versions
curl http://localhost:8080/feature-groups/user_profile/versions/1
```

Batch-read a feature group:

```bash
curl -X POST http://localhost:8080/features/user_profile/batch \
  -H 'Content-Type: application/json' \
  -d '{"entityIds": ["u-123", "u-456"]}'
```

Read multiple groups for one entity:

```bash
curl -X POST http://localhost:8080/features/multi-group \
  -H 'Content-Type: application/json' \
  -d '{"entityId": "u-123", "featureGroups": ["user_profile"]}'
```

Backfill historical data with JSON:

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

Backfill with CSV. Required columns are `featureGroup`, `entityId`, `eventTime`,
followed by feature columns:

```bash
curl -X POST http://localhost:8080/features/backfill \
  -H 'Content-Type: text/csv' \
  --data-binary $'featureGroup,entityId,eventTime,age,country,ltv\nuser_profile,u-123,2024-01-15T10:00:00Z,30,IN,35.0'
```

Export a point-in-time training dataset to `build/exports/*.csv`:

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

Compute and query feature statistics:

```bash
curl -X POST http://localhost:8080/feature-stats/compute
curl http://localhost:8080/feature-stats/user_profile
```

## Metrics

Prometheus scrapes the app through `/actuator/prometheus`. The main feature-store
metrics are:

- `ingestion_events_per_sec`
- `online_read_latency_ms`
- `cache_hit_rate`
- `offline_write_latency_ms`
- `kafka_consumer_lag`
- `stale_feature_vectors`

Prometheus is available at `http://localhost:9090`.

Grafana is available at `http://localhost:3000` with username `admin` and
password `admin`. It is provisioned with Prometheus as the default datasource and
a `Feature Store Overview` dashboard.

## AWS EC2 demo

For a demo account where cost matters most, use `deploy/aws-free-tier-demo`.
It creates one EC2 instance and runs this project with Docker Compose.

Start with the runbook in `deploy/aws-free-tier-demo/README.md`.
