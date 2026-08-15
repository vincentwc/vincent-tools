package com.vincent.tools.audit.application;

import java.time.Instant;

public final class AuditRecord {
    private final String tenantId;
    private final String operatorId;
    private final String action;
    private final String resourceType;
    private final String resourceId;
    private final String beforeJson;
    private final String afterJson;
    private final String clientIp;
    private final String userAgent;
    private final String traceId;
    private final Instant createdAt;

    public AuditRecord(String tenantId, String operatorId, String action, String resourceType, String resourceId,
                       String beforeJson, String afterJson, String clientIp, String userAgent, String traceId,
                       Instant createdAt) {
        this.tenantId = tenantId;
        this.operatorId = operatorId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.traceId = traceId;
        this.createdAt = createdAt;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
