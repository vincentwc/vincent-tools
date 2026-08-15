package com.vincent.tools.audit.boot2;

import com.vincent.tools.common.core.schema.SchemaExpectation;
import com.vincent.tools.common.core.schema.SchemaValidationException;
import com.vincent.tools.common.core.schema.VincentSchemaValidator;
import com.vincent.tools.audit.domain.AuditErrorCode;
import com.vincent.tools.audit.domain.AuditException;

import javax.sql.DataSource;

public class AuditSchemaValidator {
    public static final String INIT_SQL_PATH = "sql/mysql/1.0.0/001-init.sql";
    public static final String REQUIRED_VERSION = "1";
    private static final String[] REQUIRED_TABLES = new String[] {
            "vin_audit_meta", "vin_audit_log"
    };
    private static final SchemaExpectation AUDIT_SCHEMA_EXPECTATION = new SchemaExpectation(
            REQUIRED_TABLES,
            "vin_audit_meta",
            "id",
            1L,
            "schema_version",
            REQUIRED_VERSION,
            INIT_SQL_PATH);

    public void validate(DataSource dataSource) {
        try {
            new VincentSchemaValidator().validate(dataSource, AUDIT_SCHEMA_EXPECTATION);
        } catch (SchemaValidationException ex) {
            throw toAuditException(ex);
        }
    }

    private static AuditException toAuditException(SchemaValidationException ex) {
        return new AuditException(AuditErrorCode.valueOf(ex.errorCode()), auditMessage(ex));
    }

    private static String auditMessage(SchemaValidationException ex) {
        String message = ex.getMessage();
        if (SchemaValidationException.SCHEMA_VERSION_MISMATCH.equals(ex.errorCode())) {
            return "audit schema version must be " + REQUIRED_VERSION + ", apply " + INIT_SQL_PATH;
        }
        if (SchemaValidationException.SCHEMA_MISSING.equals(ex.errorCode())) {
            if (message != null && message.startsWith("missing table ")) {
                String table = message.substring("missing table ".length(), message.indexOf(", apply "));
                return "missing audit table " + table + ", apply " + INIT_SQL_PATH;
            }
            if (message != null && message.startsWith("failed to read schema, apply ")) {
                return "failed to read audit schema, apply " + INIT_SQL_PATH;
            }
        }
        return message;
    }
}
