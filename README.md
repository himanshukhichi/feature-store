# feature-store

High-throughput feature serving platform with a dual-store architecture.

## Core slice

This first implementation covers the core feature-store loop:

- Register feature groups with schemas and freshness TTLs.
- Ingest features with `POST /features/{featureGroup}/{entityId}`.
- Validate feature names and primitive types against the registered schema.
- Publish accepted events to Kafka with `entityId` as the message key for per-entity ordering.
- Consume the same event stream into Redis for online serving.
- Consume the same event stream into PostgreSQL for full historical offline storage.
- Retrieve current online features from Redis with PostgreSQL fallback.
- Retrieve point-in-time feature values from PostgreSQL with `?asOf=...`.
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

Prometheus is available at `http://localhost:9090`.
