CREATE TABLE feature_group_versions (
    id BIGSERIAL PRIMARY KEY,
    feature_group_name VARCHAR(128) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    schema_json TEXT NOT NULL,
    freshness_ttl_seconds BIGINT NOT NULL,
    version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (feature_group_name, version)
);

CREATE TABLE feature_statistics (
    id BIGSERIAL PRIMARY KEY,
    feature_group VARCHAR(128) NOT NULL,
    feature_name VARCHAR(128) NOT NULL,
    computed_at TIMESTAMPTZ NOT NULL,
    total_count BIGINT NOT NULL,
    null_count BIGINT NOT NULL,
    null_rate DOUBLE PRECISION NOT NULL,
    mean DOUBLE PRECISION,
    stddev DOUBLE PRECISION,
    min DOUBLE PRECISION,
    max DOUBLE PRECISION,
    p50 DOUBLE PRECISION,
    p95 DOUBLE PRECISION,
    UNIQUE (feature_group, feature_name, computed_at)
);

INSERT INTO feature_group_versions (
    feature_group_name,
    entity_type,
    schema_json,
    freshness_ttl_seconds,
    version,
    created_at
)
SELECT
    name,
    entity_type,
    schema_json,
    freshness_ttl_seconds,
    version,
    created_at
FROM feature_groups
ON CONFLICT (feature_group_name, version) DO NOTHING;

CREATE INDEX idx_feature_group_versions_lookup
    ON feature_group_versions (feature_group_name, version DESC);

CREATE INDEX idx_feature_statistics_latest
    ON feature_statistics (feature_group, feature_name, computed_at DESC);
