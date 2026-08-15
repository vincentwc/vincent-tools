package com.vincent.tools.dict.application.admin.query;

public final class ItemPageQuery {
    private final String tenantId;
    private final String code;
    private final String name;
    private final Boolean enabled;
    private final boolean includeDeleted;
    private final int page;
    private final int size;

    public ItemPageQuery(String tenantId, String code, String name, Boolean enabled, boolean includeDeleted, int page,
                         int size) {
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.enabled = enabled;
        this.includeDeleted = includeDeleted;
        this.page = page;
        this.size = size;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public boolean isIncludeDeleted() {
        return includeDeleted;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }
}
