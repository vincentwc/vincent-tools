package com.vincent.tools.dict.application.port;

import com.vincent.tools.dict.application.DictItemView;

import java.util.List;
import java.util.function.Supplier;

public interface DictCache {
    List<DictItemView> load(String dictCode, String tenantId, Supplier<List<DictItemView>> databaseLoader);

    void evictAll(String dictCode);

    void evictTenant(String dictCode, String tenantId);
}
