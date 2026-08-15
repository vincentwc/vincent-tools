package com.vincent.tools.dict.cache.redis;

import com.vincent.tools.dict.application.DictItemView;
import com.vincent.tools.dict.application.port.DictCache;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class RedisDictCache implements DictCache {
    private final StringRedisTemplate redisTemplate;
    private final DictRedisProperties properties;

    public RedisDictCache(StringRedisTemplate redisTemplate, DictRedisProperties properties) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

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
