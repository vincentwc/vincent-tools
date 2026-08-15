package com.vincent.tools.dict.boot2;

import com.vincent.tools.common.core.schema.SchemaExpectation;
import com.vincent.tools.common.core.schema.SchemaValidationException;
import com.vincent.tools.common.core.schema.VincentSchemaValidator;
import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;

import javax.sql.DataSource;

public class DictSchemaValidator {
    public static final String INIT_SQL_PATH = "sql/mysql/1.0.0/001-init.sql";
    public static final String REQUIRED_VERSION = "1";
    private static final String[] REQUIRED_TABLES = new String[] {
            "vin_dict_meta", "vin_dict", "vin_dict_item"
    };
    private static final SchemaExpectation DICT_SCHEMA_EXPECTATION = new SchemaExpectation(
            REQUIRED_TABLES,
            "vin_dict_meta",
            "id",
            1L,
            "schema_version",
            REQUIRED_VERSION,
            INIT_SQL_PATH);

    public void validate(DataSource dataSource) {
        try {
            new VincentSchemaValidator().validate(dataSource, DICT_SCHEMA_EXPECTATION);
        } catch (SchemaValidationException ex) {
            throw toDictException(ex);
        }
    }

    private static DictException toDictException(SchemaValidationException ex) {
        return new DictException(DictErrorCode.valueOf(ex.errorCode()), ex.getMessage());
    }
}
