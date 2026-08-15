-- vincent-region MySQL 5.7 schema 1.0.0
-- Schema version written to vin_region_meta is '1'.
-- Apply once against an empty database, then optionally apply 001-data.sql.

CREATE TABLE vin_region_meta (
    id BIGINT NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE vin_region (
    code VARCHAR(12) NOT NULL,
    name VARCHAR(64) NOT NULL,
    level TINYINT NOT NULL,
    parent_code VARCHAR(12) NOT NULL,
    PRIMARY KEY (code),
    KEY idx_vin_region_parent (parent_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO vin_region_meta (id, schema_version, updated_at)
VALUES (1, '1', CURRENT_TIMESTAMP(3));
