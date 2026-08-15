package com.vincent.tools.common.core.infrastructure;

public final class InfrastructureConfigurationException extends RuntimeException {
    public static final String CONFIGURATION_INVALID = "CONFIGURATION_INVALID";

    private final String errorCode;

    public InfrastructureConfigurationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
