package com.vincent.tools.audit.application;

import java.util.Optional;

public final class AuditRecordCommand {
    private final String action;
    private final String resourceType;
    private final String resourceId;
    private final Optional<String> targetTenantId;
    private final String beforeJson;
    private final String afterJson;

    public AuditRecordCommand(String action, String resourceType, String resourceId,
                              Optional<String> targetTenantId, String beforeJson, String afterJson) {
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.targetTenantId = targetTenantId;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
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

    public Optional<String> getTargetTenantId() {
        return targetTenantId;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }
}
