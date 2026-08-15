package com.vincent.tools.audit.domain;

public class AuditException extends RuntimeException {
    private final AuditErrorCode code;

    public AuditException(AuditErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AuditException(AuditErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public AuditErrorCode getCode() {
        return code;
    }
}
