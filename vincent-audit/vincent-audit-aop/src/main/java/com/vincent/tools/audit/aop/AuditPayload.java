package com.vincent.tools.audit.aop;

public final class AuditPayload {
    private final String beforeJson;
    private final String afterJson;

    public AuditPayload(String beforeJson, String afterJson) {
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }
}
