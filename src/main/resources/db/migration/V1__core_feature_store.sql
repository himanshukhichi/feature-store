CREATE TABLE feature_groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL UNIQUE,
    entity_type VARCHAR(64) NOT NULL,
    schema_json TEXT NOT NULL,
    freshness_ttl_seconds BIGINT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE feature_values (
    id BIGSERIAL PRIMARY KEY,
    entity_id VARCHAR(256) NOT NULL,
    feature_group VARCHAR(128) NOT NULL,
    feature_name VARCHAR(128) NOT NULL,
    value_json TEXT NOT NULL,
    event_time TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_feature_values_lookup
    ON feature_values (feature_group, entity_id, feature_name, event_time DESC);

CREATE INDEX idx_feature_values_event_time
    ON feature_values (event_time DESC);
