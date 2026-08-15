package com.vincent.tools.audit.application;

import java.time.Instant;
import java.util.Optional;

public final class AuditSearchQuery {
    private final Optional<String> tenantId;
    private final Optional<String> operatorId;
    private final Optional<String> action;
    private final Optional<String> resourceType;
    private final Optional<String> resourceId;
    private final Optional<Instant> createdFrom;
    private final Optional<Instant> createdTo;
    private final int page;
    private final int size;

    public AuditSearchQuery(Optional<String> tenantId, Optional<String> operatorId, Optional<String> action,
                            Optional<String> resourceType, Optional<String> resourceId,
                            Optional<Instant> createdFrom, Optional<Instant> createdTo, int page, int size) {
        this.tenantId = tenantId;
        this.operatorId = operatorId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.createdFrom = createdFrom;
        this.createdTo = createdTo;
        this.page = page;
        this.size = size;
    }

    public Optional<String> getTenantId() {
        return tenantId;
    }

    public Optional<String> getOperatorId() {
        return operatorId;
    }

    public Optional<String> getAction() {
        return action;
    }

    public Optional<String> getResourceType() {
        return resourceType;
    }

    public Optional<String> getResourceId() {
        return resourceId;
    }

    public Optional<Instant> getCreatedFrom() {
        return createdFrom;
    }

    public Optional<Instant> getCreatedTo() {
        return createdTo;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }
}
