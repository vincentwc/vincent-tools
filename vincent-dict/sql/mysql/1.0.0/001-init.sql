-- vincent-dict MySQL 5.7 schema 1.0.0
-- Schema version written to vin_dict_meta is '1'.
-- MySQL DDL may implicitly commit. Do not wrap this script in an application transaction.
-- Apply once against an empty database. Do not add business seed data.

CREATE TABLE vin_dict_meta (
    id BIGINT NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE vin_dict (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NOT NULL,
    status TINYINT NOT NULL COMMENT '0=disabled, 1=enabled',
    sort_no INT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL COMMENT '0=present, 1=deleted',
    created_by VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vin_dict_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE vin_dict_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dict_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NOT NULL,
    status TINYINT NOT NULL COMMENT '0=disabled, 1=enabled',
    sort_no INT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL COMMENT '0=present, 1=deleted',
    created_by VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vin_dict_item_scope_code (dict_id, tenant_id, code),
    KEY idx_vin_dict_item_effective (dict_id, tenant_id, status, deleted, sort_no),
    KEY idx_vin_dict_item_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO vin_dict_meta (id, schema_version, updated_at)
VALUES (1, '1', CURRENT_TIMESTAMP(3));
