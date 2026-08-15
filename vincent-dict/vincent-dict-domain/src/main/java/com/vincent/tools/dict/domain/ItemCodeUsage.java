package com.vincent.tools.dict.domain;

public final class ItemCodeUsage {
    private final boolean defaultUsed;
    private final boolean sameTenantUsed;
    private final boolean otherTenantUsed;

    private ItemCodeUsage(boolean defaultUsed, boolean sameTenantUsed, boolean otherTenantUsed) {
        this.defaultUsed = defaultUsed;
        this.sameTenantUsed = sameTenantUsed;
        this.otherTenantUsed = otherTenantUsed;
    }

    public static ItemCodeUsage none() { return new ItemCodeUsage(false, false, false); }

    public static ItemCodeUsage defaultAndTenant(boolean sameTenantUsed, boolean defaultUsed) {
        return new ItemCodeUsage(defaultUsed, sameTenantUsed, false);
    }

    public static ItemCodeUsage tenantOnly(boolean sameTenant) {
        return new ItemCodeUsage(false, sameTenant, !sameTenant);
    }

    static ItemCodeUsage of(boolean defaultUsed, boolean sameTenantUsed, boolean otherTenantUsed) {
        return new ItemCodeUsage(defaultUsed, sameTenantUsed, otherTenantUsed);
    }

    boolean hasAnyUse() { return defaultUsed || sameTenantUsed || otherTenantUsed; }
    boolean hasDefaultUse() { return defaultUsed; }
    boolean hasSameTenantUse() { return sameTenantUsed; }
}
