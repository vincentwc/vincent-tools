package com.vincent.tools.dict.application.admin.view;

public final class DictSummary {
    private final long id;
    private final String code;
    private final String name;
    private final String description;
    private final boolean enabled;
    private final int sortNo;
    private final boolean deleted;

    public DictSummary(long id, String code, String name, String description, boolean enabled, int sortNo,
                       boolean deleted) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.sortNo = sortNo;
        this.deleted = deleted;
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

    public boolean isDeleted() {
        return deleted;
    }
}
