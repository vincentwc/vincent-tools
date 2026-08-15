package com.vincent.tools.region.boot2;

import com.vincent.tools.common.core.schema.SchemaExpectation;
import com.vincent.tools.common.core.schema.SchemaValidationException;
import com.vincent.tools.common.core.schema.VincentSchemaValidator;
import com.vincent.tools.region.domain.RegionErrorCode;
import com.vincent.tools.region.domain.RegionException;

import javax.sql.DataSource;

public class RegionSchemaValidator {
    public static final String INIT_SQL_PATH = "sql/mysql/1.0.0/001-init.sql";
    public static final String REQUIRED_VERSION = "1";
    private static final String[] REQUIRED_TABLES = new String[] {
            "vin_region_meta", "vin_region"
    };
    private static final SchemaExpectation REGION_SCHEMA_EXPECTATION = new SchemaExpectation(
            REQUIRED_TABLES,
            "vin_region_meta",
            "id",
            1L,
            "schema_version",
            REQUIRED_VERSION,
            INIT_SQL_PATH);

    public void validate(DataSource dataSource) {
        try {
            new VincentSchemaValidator().validate(dataSource, REGION_SCHEMA_EXPECTATION);
        } catch (SchemaValidationException ex) {
            throw toRegionException(ex);
        }
    }

    private static RegionException toRegionException(SchemaValidationException ex) {
        return new RegionException(RegionErrorCode.valueOf(ex.errorCode()), regionMessage(ex));
    }

    private static String regionMessage(SchemaValidationException ex) {
        String message = ex.getMessage();
        if (SchemaValidationException.SCHEMA_VERSION_MISMATCH.equals(ex.errorCode())) {
            return "region schema version must be " + REQUIRED_VERSION + ", apply " + INIT_SQL_PATH;
        }
        if (SchemaValidationException.SCHEMA_MISSING.equals(ex.errorCode())) {
            if (message != null && message.startsWith("missing table ")) {
                String table = message.substring("missing table ".length(), message.indexOf(", apply "));
                return "missing region table " + table + ", apply " + INIT_SQL_PATH;
            }
            if (message != null && message.startsWith("failed to read schema, apply ")) {
                return "failed to read region schema, apply " + INIT_SQL_PATH;
            }
        }
        return message;
    }
}
