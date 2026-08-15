package com.vincent.tools.dict.application.admin;

public final class TenantOption {
    private final String tenantId;
    private final String name;

    public TenantOption(String tenantId, String name) {
        this.tenantId = tenantId;
        this.name = name;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }
}
