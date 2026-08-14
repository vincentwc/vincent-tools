package com.vincent.tools.dict.application;

import com.vincent.tools.dict.domain.DictItemSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class ApplicationFixtures {
    static final String DICT_CODE = "ORDER_STATUS";
    static final String TENANT_A = "tenant-a";
    static final String TENANT_B = "tenant-b";

    private ApplicationFixtures() {
    }

    static EffectiveDictData enabled(EffectiveItemData... items) {
        return new EffectiveDictData(true, Arrays.asList(items));
    }

    static EffectiveDictData disabled() {
        return new EffectiveDictData(false, Collections.<EffectiveItemData>emptyList());
    }

    static EffectiveItemData item(long id, String code, int sortNo, DictItemSource source) {
        return new EffectiveItemData(id, code, code + " name", code + " description", sortNo, source);
    }

    static List<EffectiveItemData> items(int count) {
        EffectiveItemData[] items = new EffectiveItemData[count];
        for (int index = 0; index < count; index++) {
            items[index] = item(index + 1L, String.format("ITEM_%04d", index), index, DictItemSource.DEFAULT);
        }
        return Arrays.asList(items);
    }
}
