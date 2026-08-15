package com.vincent.tools.common.core.schema;

public final class SchemaValidationException extends RuntimeException {
    public static final String SCHEMA_MISSING = "SCHEMA_MISSING";
    public static final String SCHEMA_VERSION_MISMATCH = "SCHEMA_VERSION_MISMATCH";

    private final String errorCode;

    public SchemaValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
