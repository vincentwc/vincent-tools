package com.vincent.tools.dict.application;

import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;

public final class DictLimits {
    private static final int DEFAULT_MAX_EFFECTIVE_ITEMS = 2000;

    private final int maxEffectiveItems;

    public DictLimits(int maxEffectiveItems) {
        if (maxEffectiveItems <= 0) {
            throw new DictException(DictErrorCode.CONFIGURATION_INVALID, "maxEffectiveItems must be positive");
        }
        this.maxEffectiveItems = maxEffectiveItems;
    }

    public static DictLimits defaults() {
        return new DictLimits(DEFAULT_MAX_EFFECTIVE_ITEMS);
    }

    public int getMaxEffectiveItems() {
        return maxEffectiveItems;
    }
}
