package com.vincent.tools.dict.application.admin.query;

public final class DictPageQuery {
    private final String code;
    private final String name;
    private final Boolean enabled;
    private final boolean includeDeleted;
    private final int page;
    private final int size;

    public DictPageQuery(String code, String name, Boolean enabled, boolean includeDeleted, int page, int size) {
        this.code = code;
        this.name = name;
        this.enabled = enabled;
        this.includeDeleted = includeDeleted;
        this.page = page;
        this.size = size;
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
