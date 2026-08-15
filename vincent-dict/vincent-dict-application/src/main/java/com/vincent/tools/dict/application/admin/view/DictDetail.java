package com.vincent.tools.dict.application.admin.view;

import java.time.Instant;

public final class DictDetail {
    private final long id;
    private final String code;
    private final String name;
    private final String description;
    private final boolean enabled;
    private final int sortNo;
    private final int version;
    private final boolean deleted;
    private final String createdBy;
    private final Instant createdAt;
    private final String updatedBy;
    private final Instant updatedAt;

    public DictDetail(long id, String code, String name, String description, boolean enabled, int sortNo, int version,
                      boolean deleted, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.sortNo = sortNo;
        this.version = version;
        this.deleted = deleted;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
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

    public int getVersion() {
        return version;
    }

    public boolean isDeleted() {
        return deleted;
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
