package com.vincent.tools.dict.application.admin.view;

import com.vincent.tools.dict.domain.DictItemSource;

import java.time.Instant;

public final class DictItemDetail {
    private final long id;
    private final long dictId;
    private final String code;
    private final String name;
    private final String tenantId;
    private final String description;
    private final boolean enabled;
    private final int sortNo;
    private final boolean deleted;
    private final DictItemSource source;
    private final int version;
    private final String createdBy;
    private final Instant createdAt;
    private final String updatedBy;
    private final Instant updatedAt;

    public DictItemDetail(long id, long dictId, String code, String name, String tenantId, String description,
                          boolean enabled, int sortNo, boolean deleted, DictItemSource source, int version,
                          String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
        this.id = id;
        this.dictId = dictId;
        this.code = code;
        this.name = name;
        this.tenantId = tenantId;
        this.description = description;
        this.enabled = enabled;
        this.sortNo = sortNo;
        this.deleted = deleted;
        this.source = source;
        this.version = version;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public long getDictId() {
        return dictId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSortNo() {
        return sortNo;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public DictItemSource getSource() {
        return source;
    }

    public int getVersion() {
        return version;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
