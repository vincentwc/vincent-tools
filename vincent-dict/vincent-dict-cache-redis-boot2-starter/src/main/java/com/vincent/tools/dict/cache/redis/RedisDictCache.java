package com.vincent.tools.dict.cache.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vincent.tools.dict.application.DictItemView;
import com.vincent.tools.dict.application.port.DictCache;
import com.vincent.tools.dict.domain.DictItemSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class RedisDictCache implements DictCache {
    private static final String PUT_SCRIPT_PATH =
            "com/vincent/tools/dict/cache/redis/put-if-version-unchanged.lua";
    private static final Duration MIN_VERSION_TTL = Duration.ofSeconds(120);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DictRedisProperties properties;
    private final DictCacheKeyFactory keys;
    private final DefaultRedisScript<Long> putIfUnchanged;
    private final RateLimitedCacheLogger logger;

    public RedisDictCache(StringRedisTemplate redisTemplate, DictRedisProperties properties) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.objectMapper = cacheObjectMapper();
        this.properties = Objects.requireNonNull(properties, "properties");
        this.keys = new DictCacheKeyFactory(properties.getKeyPrefix());
        this.putIfUnchanged = loadPutScript();
        this.logger = new RateLimitedCacheLogger();
    }

    @Override
    public List<DictItemView> load(String dictCode, String tenantId, Supplier<List<DictItemView>> databaseLoader) {
        Objects.requireNonNull(databaseLoader, "databaseLoader");
        Snapshot snapshot;
        try {
            snapshot = readSnapshot(dictCode, tenantId);
            if (snapshot.items != null) {
                return snapshot.items;
            }
        } catch (RuntimeException ex) {
            logger.warn("redis-read", "dict redis cache read failed; falling back to database", ex);
            return databaseLoader.get();
        }

        List<DictItemView> loaded = databaseLoader.get();
        try {
            writeIfUnchanged(dictCode, tenantId, snapshot.globalVersion, snapshot.tenantVersion, loaded);
        } catch (JsonProcessingException ex) {
            logger.warn("redis-write", "dict redis cache write failed; returning database data", ex);
        } catch (RuntimeException ex) {
            logger.warn("redis-script", "dict redis cache script failed; returning database data", ex);
        }
        return loaded;
    }

    @Override
    public void evictAll(String dictCode) {
        try {
            String globalKey = keys.globalVersion(dictCode);
            redisTemplate.opsForValue().increment(globalKey);
            refreshVersionTtl(globalKey, null);
        } catch (RuntimeException ex) {
            logger.warn("redis-evict", "dict redis cache evictAll failed", ex);
        }
    }

    @Override
    public void evictTenant(String dictCode, String tenantId) {
        try {
            String globalKey = keys.globalVersion(dictCode);
            String tenantKey = keys.tenantVersion(dictCode, tenantId);
            redisTemplate.opsForValue().increment(tenantKey);
            refreshVersionTtl(globalKey, tenantKey);
        } catch (RuntimeException ex) {
            logger.warn("redis-evict", "dict redis cache evictTenant failed", ex);
        }
    }

    private Snapshot readSnapshot(String dictCode, String tenantId) {
        long globalVersion = readVersion(keys.globalVersion(dictCode));
        long tenantVersion = readVersion(keys.tenantVersion(dictCode, tenantId));
        String payloadKey = keys.payload(dictCode, globalVersion, tenantVersion, tenantId);
        String json = redisTemplate.opsForValue().get(payloadKey);
        if (json == null) {
            return Snapshot.miss(globalVersion, tenantVersion);
        }
        List<DictItemView> items = decode(json);
        if (items != null) {
            return Snapshot.hit(items);
        }
        deleteQuietly(payloadKey);
        return Snapshot.miss(globalVersion, tenantVersion);
    }

    private long readVersion(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || value.length() == 0) {
            return 0L;
        }
        return Long.parseLong(value.trim());
    }

    private void writeIfUnchanged(String dictCode, String tenantId, long globalVersion, long tenantVersion,
                                  List<DictItemView> items) throws JsonProcessingException {
        String json = encode(items);
        redisTemplate.execute(putIfUnchanged,
                Arrays.asList(
                        keys.globalVersion(dictCode),
                        keys.tenantVersion(dictCode, tenantId),
                        keys.payload(dictCode, globalVersion, tenantVersion, tenantId)),
                Long.toString(globalVersion),
                Long.toString(tenantVersion),
                json,
                Long.toString(properties.getTtl().toMillis()));
    }

    private String encode(List<DictItemView> items) throws JsonProcessingException {
        DictCachePayload payload = new DictCachePayload();
        payload.setFormatVersion(DictCachePayload.CURRENT_FORMAT_VERSION);
        List<DictCachePayload.Item> encoded = new ArrayList<DictCachePayload.Item>(items.size());
        for (int index = 0; index < items.size(); index++) {
            DictItemView item = items.get(index);
            DictCachePayload.Item row = new DictCachePayload.Item();
            row.setCode(item.getCode());
            row.setName(item.getName());
            row.setDescription(item.getDescription());
            row.setSortNo(item.getSortNo());
            row.setSource(item.getSource() == null ? null : item.getSource().name());
            encoded.add(row);
        }
        payload.setItems(encoded);
        return objectMapper.writeValueAsString(payload);
    }

    private List<DictItemView> decode(String json) {
        try {
            DictCachePayload payload = objectMapper.readValue(json, DictCachePayload.class);
            if (payload == null
                    || payload.getFormatVersion() != DictCachePayload.CURRENT_FORMAT_VERSION
                    || payload.getItems() == null) {
                return null;
            }
            List<DictItemView> views = new ArrayList<DictItemView>(payload.getItems().size());
            for (int index = 0; index < payload.getItems().size(); index++) {
                DictCachePayload.Item item = payload.getItems().get(index);
                if (item == null || item.getCode() == null || item.getName() == null || item.getSource() == null) {
                    return null;
                }
                DictItemSource source;
                try {
                    source = DictItemSource.valueOf(item.getSource());
                } catch (IllegalArgumentException ex) {
                    return null;
                }
                views.add(new DictItemView(item.getCode(), item.getName(), item.getDescription(), item.getSortNo(),
                        source));
            }
            return views;
        } catch (Exception ex) {
            return null;
        }
    }

    private void deleteQuietly(String payloadKey) {
        try {
            redisTemplate.delete(payloadKey);
        } catch (RuntimeException ex) {
            logger.warn("redis-delete", "dict redis cache failed to delete corrupt payload", ex);
        }
    }

    private void refreshVersionTtl(String globalKey, String tenantKey) {
        Duration ttl = versionTtl();
        if (tenantKey != null) {
            redisTemplate.opsForValue().setIfAbsent(globalKey, "0");
            expire(globalKey, ttl);
            expire(tenantKey, ttl);
            return;
        }
        expire(globalKey, ttl);
    }

    private void expire(String key, Duration ttl) {
        redisTemplate.expire(key, ttl.getSeconds(), TimeUnit.SECONDS);
    }

    private Duration versionTtl() {
        long seconds = Math.max(properties.getTtl().getSeconds() * 2L, MIN_VERSION_TTL.getSeconds());
        return Duration.ofSeconds(seconds);
    }

    private static ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    private static DefaultRedisScript<Long> loadPutScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource(PUT_SCRIPT_PATH));
        script.setResultType(Long.class);
        return script;
    }

    private static final class Snapshot {
        private final long globalVersion;
        private final long tenantVersion;
        private final List<DictItemView> items;

        private Snapshot(long globalVersion, long tenantVersion, List<DictItemView> items) {
            this.globalVersion = globalVersion;
            this.tenantVersion = tenantVersion;
            this.items = items;
        }

        private static Snapshot hit(List<DictItemView> items) {
            return new Snapshot(0L, 0L, items);
        }

        private static Snapshot miss(long globalVersion, long tenantVersion) {
            return new Snapshot(globalVersion, tenantVersion, null);
        }
    }
}
