package com.vincent.tools.dict.application.admin.command;

public final class CreateItemCommand {
    private final String code;
    private final String name;
    private final String description;
    private final int sortNo;

    public CreateItemCommand(String code, String name, String description, int sortNo) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.sortNo = sortNo;
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
}
