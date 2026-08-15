package com.vincent.tools.dict.application;

import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;

public final class DictLimits {
    private static final int DEFAULT_MAX_EFFECTIVE_ITEMS = 2000;
    private static final int DEFAULT_DEFAULT_ITEMS_PER_DICT = 1000;
    private static final int DEFAULT_TENANT_ITEMS_PER_DICT = 1000;

    private final int maxEffectiveItems;
    private final int defaultItemsPerDict;
    private final int tenantItemsPerDict;

    public DictLimits(int maxEffectiveItems) {
        if (maxEffectiveItems <= 0) {
            throw new DictException(DictErrorCode.CONFIGURATION_INVALID, "maxEffectiveItems must be positive");
        }
        this.maxEffectiveItems = maxEffectiveItems;
        this.defaultItemsPerDict = DEFAULT_DEFAULT_ITEMS_PER_DICT;
        this.tenantItemsPerDict = DEFAULT_TENANT_ITEMS_PER_DICT;
    }

    public DictLimits(int maxEffectiveItems, int defaultItemsPerDict, int tenantItemsPerDict) {
        if (maxEffectiveItems <= 0 || defaultItemsPerDict <= 0 || tenantItemsPerDict <= 0) {
            throw new DictException(DictErrorCode.CONFIGURATION_INVALID, "all limits must be positive");
        }
        this.maxEffectiveItems = maxEffectiveItems;
        this.defaultItemsPerDict = defaultItemsPerDict;
        this.tenantItemsPerDict = tenantItemsPerDict;
    }

    public static DictLimits defaults() {
        return new DictLimits(DEFAULT_MAX_EFFECTIVE_ITEMS);
    }

    public int getMaxEffectiveItems() {
        return maxEffectiveItems;
    }

    public int getDefaultItemsPerDict() {
        return defaultItemsPerDict;
    }

    public int getTenantItemsPerDict() {
        return tenantItemsPerDict;
    }
}
