-- vincent-audit MySQL 5.7 schema 1.0.0
-- Schema version written to vin_audit_meta is '1'.
-- MySQL DDL may implicitly commit. Do not wrap this script in an application transaction.
-- Apply once against an empty database. Do not add business seed data.

CREATE TABLE vin_audit_meta (
    id BIGINT NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE vin_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    operator_id VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    before_json TEXT,
    after_json TEXT,
    client_ip VARCHAR(64),
    user_agent VARCHAR(256),
    trace_id VARCHAR(128),
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_vin_audit_log_tenant_created (tenant_id, created_at),
    KEY idx_vin_audit_log_tenant_resource (tenant_id, resource_type, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO vin_audit_meta (id, schema_version, updated_at)
VALUES (1, '1', CURRENT_TIMESTAMP(3));
