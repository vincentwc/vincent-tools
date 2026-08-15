package com.vincent.tools.dict.application;

import com.vincent.tools.dict.domain.DictItemSource;

public final class EffectiveItemData {
    private final long id;
    private final String code;
    private final String name;
    private final String description;
    private final int sortNo;
    private final DictItemSource source;

    public EffectiveItemData(long id, String code, String name, String description, int sortNo, DictItemSource source) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.sortNo = sortNo;
        this.source = source;
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

    public int getSortNo() {
        return sortNo;
    }

    public DictItemSource getSource() {
        return source;
    }
}
