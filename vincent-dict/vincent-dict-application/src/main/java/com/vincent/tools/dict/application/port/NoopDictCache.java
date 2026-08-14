package com.vincent.tools.dict.application.port;

import com.vincent.tools.dict.application.DictItemView;

import java.util.List;
import java.util.function.Supplier;

public final class NoopDictCache implements DictCache {
    @Override
    public List<DictItemView> load(String dictCode, String tenantId, Supplier<List<DictItemView>> databaseLoader) {
        return databaseLoader.get();
    }

    @Override
    public void evictAll(String dictCode) {
    }

    @Override
    public void evictTenant(String dictCode, String tenantId) {
    }
}
