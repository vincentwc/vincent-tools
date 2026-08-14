package com.vincent.tools.dict.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EffectiveDictData {
    private final boolean enabled;
    private final List<EffectiveItemData> items;

    public EffectiveDictData(boolean enabled, List<EffectiveItemData> items) {
        this.enabled = enabled;
        this.items = Collections.unmodifiableList(new ArrayList<EffectiveItemData>(items));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<EffectiveItemData> getItems() {
        return items;
    }
}
