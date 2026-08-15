package com.vincent.tools.region.application;

public final class RegionView {
    private final String code;
    private final String name;
    private final int level;
    private final String parentCode;

    public RegionView(String code, String name, int level, String parentCode) {
        this.code = code;
        this.name = name;
        this.level = level;
        this.parentCode = parentCode;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }

    public String getParentCode() {
        return parentCode;
    }
}
