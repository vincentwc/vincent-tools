package com.vincent.tools.audit.domain;

public final class AuditFieldLimits {
    public static final int MAX_TENANT_ID_LENGTH = 64;
    public static final int MAX_OPERATOR_ID_LENGTH = 64;
    public static final int MAX_ACTION_LENGTH = 64;
    public static final int MAX_RESOURCE_TYPE_LENGTH = 64;
    public static final int MAX_RESOURCE_ID_LENGTH = 128;
    public static final int MAX_CLIENT_IP_LENGTH = 64;
    public static final int MAX_USER_AGENT_LENGTH = 256;
    public static final int MAX_TRACE_ID_LENGTH = 128;

    private AuditFieldLimits() {
    }

    public static String requireNonBlank(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty() || !value.equals(value.trim()) || value.length() > maxLength) {
            throw new AuditException(AuditErrorCode.INVALID_ARGUMENT, "invalid " + fieldName);
        }
        return value;
    }

    public static String optionalBounded(String value, String fieldName, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.trim().isEmpty() || !value.equals(value.trim()) || value.length() > maxLength) {
            throw new AuditException(AuditErrorCode.INVALID_ARGUMENT, "invalid " + fieldName);
        }
        return value;
    }
}
