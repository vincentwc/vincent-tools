package com.vincent.tools.dict.domain;

import java.util.Objects;

public final class TenantId {
    public static final String DEFAULT_VALUE = "0";

    private final String value;
    private final boolean defaultItem;

    private TenantId(String value, boolean defaultItem) {
        this.value = value;
        this.defaultItem = defaultItem;
    }

    public static TenantId of(String value) {
        if (value == null || value.trim().isEmpty() || value.length() > 64
                || DEFAULT_VALUE.equals(value) || !value.equals(value.trim())) {
            throw new DictException(DictErrorCode.INVALID_ARGUMENT, "invalid tenantId");
        }
        return new TenantId(value, false);
    }

    public static TenantId defaultItem() {
        return new TenantId(DEFAULT_VALUE, true);
    }

    public String value() {
        return value;
    }

    public boolean isDefault() {
        return defaultItem;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TenantId)) {
            return false;
        }
        TenantId that = (TenantId) other;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
