package com.vincent.tools.dict.application.admin.command;

public final class UpdateDictCommand {
    private final String name;
    private final String description;
    private final int sortNo;

    public UpdateDictCommand(String name, String description, int sortNo) {
        this.name = name;
        this.description = description;
        this.sortNo = sortNo;
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
