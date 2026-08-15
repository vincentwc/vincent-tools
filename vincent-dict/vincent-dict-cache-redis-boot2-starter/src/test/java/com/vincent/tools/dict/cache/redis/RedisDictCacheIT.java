package com.vincent.tools.dict.cache.redis;

import com.vincent.tools.dict.application.DictItemView;
import com.vincent.tools.dict.domain.DictItemSource;
import com.vincent.tools.dict.domain.TenantId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisDictCacheIT {
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2.5-alpine"))
                    .withExposedPorts(6379)
                    .withStartupTimeout(Duration.ofMinutes(2));

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static final DictCacheKeyFactory KEYS = new DictCacheKeyFactory("vin:dict");

    private RedisDictCache cache;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        REDIS.stop();
    }

    @BeforeEach
    void flushAndCreateCache() {
        redisTemplate.execute(new RedisCallback<Void>() {
            @Override
            public Void doInRedis(RedisConnection connection) {
                connection.flushDb();
                return null;
            }
        });
        cache = newCache(Duration.ofSeconds(60));
    }

    @Test
    void miss_loads_from_database_once_and_writes_payload() {
        AtomicInteger calls = new AtomicInteger();
        List<DictItemView> loaded = cache.load("ORDER_STATUS", "tenant-a", countingLoader(calls, item("PAID")));

        assertThat(calls).hasValue(1);
        assertThat(loaded).extracting(DictItemView::getCode).containsExactly("PAID");
        String payload = redisTemplate.opsForValue().get(KEYS.payload("ORDER_STATUS", 0L, 0L, "tenant-a"));
        assertThat(payload).contains("\"formatVersion\":1");
        assertThat(payload).contains("\"code\":\"PAID\"");
        assertThat(payload).doesNotContain("DictItemView");
        assertThat(payload).doesNotContain("@class");
        assertThat(payload).doesNotContain("DictItemPo");
    }

    @Test
    void hit_does_not_invoke_loader() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<List<DictItemView>> loader = countingLoader(calls, item("PAID"));

        cache.load("ORDER_STATUS", "tenant-a", loader);
        List<DictItemView> hit = cache.load("ORDER_STATUS", "tenant-a", loader);

        assertThat(calls).hasValue(1);
        assertThat(hit).extracting(DictItemView::getCode).containsExactly("PAID");
        assertThat(hit.get(0).getName()).isEqualTo("PAID name");
        assertThat(hit.get(0).getDescription()).isEqualTo("PAID description");
        assertThat(hit.get(0).getSortNo()).isEqualTo(1);
        assertThat(hit.get(0).getSource()).isEqualTo(DictItemSource.TENANT);
    }

    @Test
    void payload_uses_configured_ttl() {
        cache = newCache(Duration.ofSeconds(8));
        cache.load("ORDER_STATUS", "tenant-a", loader(item("PAID")));

        Long ttlMs = redisTemplate.getExpire(KEYS.payload("ORDER_STATUS", 0L, 0L, "tenant-a"), TimeUnit.MILLISECONDS);
        assertThat(ttlMs).isGreaterThan(0L).isLessThanOrEqualTo(8_000L);
    }

    @Test
    void evict_all_forces_reload_for_every_tenant() {
        AtomicInteger tenantA = new AtomicInteger();
        AtomicInteger tenantB = new AtomicInteger();
        cache.load("ORDER_STATUS", "tenant-a", countingLoader(tenantA, item("A1")));
        cache.load("ORDER_STATUS", "tenant-b", countingLoader(tenantB, item("B1")));

        cache.evictAll("ORDER_STATUS");
        assertThat(redisTemplate.opsForValue().get(KEYS.globalVersion("ORDER_STATUS"))).isEqualTo("1");
        assertThat(redisTemplate.getExpire(KEYS.globalVersion("ORDER_STATUS"), TimeUnit.SECONDS))
                .isGreaterThan(100L)
                .isLessThanOrEqualTo(120L);

        List<DictItemView> reloadedA = cache.load("ORDER_STATUS", "tenant-a", countingLoader(tenantA, item("A2")));
        List<DictItemView> reloadedB = cache.load("ORDER_STATUS", "tenant-b", countingLoader(tenantB, item("B2")));

        assertThat(tenantA).hasValue(2);
        assertThat(tenantB).hasValue(2);
        assertThat(reloadedA).extracting(DictItemView::getCode).containsExactly("A2");
        assertThat(reloadedB).extracting(DictItemView::getCode).containsExactly("B2");
    }

    @Test
    void evict_tenant_only_invalidates_that_tenant() {
        AtomicInteger tenantA = new AtomicInteger();
        AtomicInteger tenantB = new AtomicInteger();
        cache.load("ORDER_STATUS", "tenant-a", countingLoader(tenantA, item("A1")));
        cache.load("ORDER_STATUS", "tenant-b", countingLoader(tenantB, item("B1")));

        cache.evictTenant("ORDER_STATUS", "tenant-a");
        assertThat(redisTemplate.opsForValue().get(KEYS.tenantVersion("ORDER_STATUS", "tenant-a"))).isEqualTo("1");
        assertThat(redisTemplate.getExpire(KEYS.tenantVersion("ORDER_STATUS", "tenant-a"), TimeUnit.SECONDS))
                .isGreaterThan(100L)
                .isLessThanOrEqualTo(120L);
        assertThat(redisTemplate.getExpire(KEYS.globalVersion("ORDER_STATUS"), TimeUnit.SECONDS))
                .isGreaterThan(100L)
                .isLessThanOrEqualTo(120L);

        List<DictItemView> reloadedA = cache.load("ORDER_STATUS", "tenant-a", countingLoader(tenantA, item("A2")));
        List<DictItemView> cachedB = cache.load("ORDER_STATUS", "tenant-b", countingLoader(tenantB, item("B2")));

        assertThat(tenantA).hasValue(2);
        assertThat(tenantB).hasValue(1);
        assertThat(reloadedA).extracting(DictItemView::getCode).containsExactly("A2");
        assertThat(cachedB).extracting(DictItemView::getCode).containsExactly("B1");
    }

    @Test
    void default_item_tenant_is_encoded_and_cacheable() {
        String tenantId = TenantId.DEFAULT_VALUE;
        AtomicInteger calls = new AtomicInteger();
        cache.load("ORDER_STATUS", tenantId, countingLoader(calls, item("DEFAULT")));

        assertThat(KEYS.tenantVersion("ORDER_STATUS", tenantId)).doesNotEndWith(":" + tenantId);
        assertThat(KEYS.payload("ORDER_STATUS", 0L, 0L, tenantId)).doesNotEndWith(":" + tenantId);
        assertThat(redisTemplate.opsForValue().get(KEYS.payload("ORDER_STATUS", 0L, 0L, tenantId)))
                .isNotNull()
                .doesNotContain("\"" + tenantId + "\"");
        List<DictItemView> hit = cache.load("ORDER_STATUS", tenantId, countingLoader(calls, item("OTHER")));
        assertThat(calls).hasValue(1);
        assertThat(hit).extracting(DictItemView::getCode).containsExactly("DEFAULT");
    }

    @Test
    void corrupt_payload_is_deleted_and_reloaded() {
        AtomicInteger calls = new AtomicInteger();
        cache.load("ORDER_STATUS", "tenant-a", countingLoader(calls, item("PAID")));
        String payloadKey = KEYS.payload("ORDER_STATUS", 0L, 0L, "tenant-a");
        redisTemplate.opsForValue().set(payloadKey, "{not-json");

        List<DictItemView> reloaded = cache.load("ORDER_STATUS", "tenant-a", countingLoader(calls, item("FRESH")));

        assertThat(calls).hasValue(2);
        assertThat(reloaded).extracting(DictItemView::getCode).containsExactly("FRESH");
        assertThat(redisTemplate.opsForValue().get(payloadKey)).contains("\"code\":\"FRESH\"");
        assertThat(redisTemplate.opsForValue().get(payloadKey)).doesNotContain("{not-json");
    }

    @Test
    void unknown_format_version_is_treated_as_miss() {
        AtomicInteger calls = new AtomicInteger();
        cache.load("ORDER_STATUS", "tenant-a", countingLoader(calls, item("PAID")));
        String payloadKey = KEYS.payload("ORDER_STATUS", 0L, 0L, "tenant-a");
        redisTemplate.opsForValue().set(payloadKey, "{\"formatVersion\":2,\"items\":[]}");

        List<DictItemView> reloaded = cache.load("ORDER_STATUS", "tenant-a", countingLoader(calls, item("FRESH")));

        assertThat(calls).hasValue(2);
        assertThat(reloaded).extracting(DictItemView::getCode).containsExactly("FRESH");
    }

    @Test
    void two_cache_instances_share_redis_payload() {
        RedisDictCache cacheA = newCache(Duration.ofSeconds(60));
        RedisDictCache cacheB = newCache(Duration.ofSeconds(60));
        AtomicInteger callsA = new AtomicInteger();
        AtomicInteger callsB = new AtomicInteger();

        cacheA.load("ORDER_STATUS", "tenant-a", countingLoader(callsA, item("FROM_A")));
        List<DictItemView> fromB = cacheB.load("ORDER_STATUS", "tenant-a", countingLoader(callsB, item("FROM_B")));

        assertThat(callsA).hasValue(1);
        assertThat(callsB).hasValue(0);
        assertThat(fromB).extracting(DictItemView::getCode).containsExactly("FROM_A");
    }

    @Test
    void blocked_loader_then_evict_tenant_does_not_write_stale_payload() throws Exception {
        RedisDictCache cacheA = newCache(Duration.ofSeconds(60));
        RedisDictCache cacheB = newCache(Duration.ofSeconds(60));
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        AtomicInteger firstLoads = new AtomicInteger();
        AtomicReference<List<DictItemView>> firstResult = new AtomicReference<List<DictItemView>>();

        Thread blocked = new Thread(new Runnable() {
            @Override
            public void run() {
                firstResult.set(cacheA.load("ORDER_STATUS", "tenant-a", new Supplier<List<DictItemView>>() {
                    @Override
                    public List<DictItemView> get() {
                        firstLoads.incrementAndGet();
                        loaderStarted.countDown();
                        await(releaseLoader);
                        return items(item("STALE"));
                    }
                }));
            }
        });
        blocked.start();
        assertThat(loaderStarted.await(10, TimeUnit.SECONDS)).isTrue();

        cacheB.evictTenant("ORDER_STATUS", "tenant-a");
        releaseLoader.countDown();
        blocked.join(10_000L);
        assertThat(blocked.isAlive()).isFalse();

        assertThat(firstLoads).hasValue(1);
        assertThat(firstResult.get()).extracting(DictItemView::getCode).containsExactly("STALE");
        assertThat(redisTemplate.opsForValue().get(KEYS.payload("ORDER_STATUS", 0L, 1L, "tenant-a"))).isNull();
        assertThat(redisTemplate.opsForValue().get(KEYS.payload("ORDER_STATUS", 0L, 0L, "tenant-a"))).isNull();

        AtomicInteger secondLoads = new AtomicInteger();
        List<DictItemView> second = cacheA.load("ORDER_STATUS", "tenant-a",
                countingLoader(secondLoads, item("FRESH")));
        assertThat(secondLoads).hasValue(1);
        assertThat(second).extracting(DictItemView::getCode).containsExactly("FRESH");
        assertThat(redisTemplate.opsForValue().get(KEYS.payload("ORDER_STATUS", 0L, 1L, "tenant-a")))
                .contains("\"code\":\"FRESH\"");
    }

    @Test
    void redis_read_failure_returns_database_data() {
        RedisDictCache failing = new RedisDictCache(brokenTemplate(), properties(Duration.ofSeconds(60)));
        AtomicInteger calls = new AtomicInteger();

        List<DictItemView> loaded = failing.load("ORDER_STATUS", "tenant-a", countingLoader(calls, item("DB")));

        assertThat(calls).hasValue(1);
        assertThat(loaded).extracting(DictItemView::getCode).containsExactly("DB");
    }

    @Test
    void redis_failure_does_not_swallow_database_exception() {
        RedisDictCache failing = new RedisDictCache(brokenTemplate(), properties(Duration.ofSeconds(60)));

        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                failing.load("ORDER_STATUS", "tenant-a", new Supplier<List<DictItemView>>() {
                    @Override
                    public List<DictItemView> get() {
                        throw new IllegalStateException("db down");
                    }
                });
            }
        }).isInstanceOf(IllegalStateException.class).hasMessage("db down");
    }

    @Test
    void evict_swallows_redis_failures() {
        final RedisDictCache failing = new RedisDictCache(brokenTemplate(), properties(Duration.ofSeconds(60)));
        assertThatCode(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                failing.evictAll("ORDER_STATUS");
                failing.evictTenant("ORDER_STATUS", "tenant-a");
            }
        }).doesNotThrowAnyException();
    }

    private static RedisDictCache newCache(Duration ttl) {
        return new RedisDictCache(redisTemplate, properties(ttl));
    }

    private static DictRedisProperties properties(Duration ttl) {
        DictRedisProperties properties = new DictRedisProperties();
        properties.setEnabled(true);
        properties.setKeyPrefix("vin:dict");
        properties.setTtl(ttl);
        return properties;
    }

    private static Supplier<List<DictItemView>> loader(DictItemView item) {
        return countingLoader(new AtomicInteger(), item);
    }

    private static Supplier<List<DictItemView>> countingLoader(final AtomicInteger calls, final DictItemView item) {
        return new Supplier<List<DictItemView>>() {
            @Override
            public List<DictItemView> get() {
                calls.incrementAndGet();
                return items(item);
            }
        };
    }

    private static List<DictItemView> items(DictItemView item) {
        List<DictItemView> values = new ArrayList<DictItemView>();
        values.add(item);
        return values;
    }

    private static DictItemView item(String code) {
        return new DictItemView(code, code + " name", code + " description", 1, DictItemSource.TENANT);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting to release loader");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting to release loader", ex);
        }
    }

    private static StringRedisTemplate brokenTemplate() {
        return new StringRedisTemplate(new BrokenRedisConnectionFactory());
    }

    private static final class BrokenRedisConnectionFactory implements RedisConnectionFactory {
        @Override
        public RedisConnection getConnection() {
            throw new UnsupportedOperationException("redis is unavailable");
        }

        @Override
        public RedisClusterConnection getClusterConnection() {
            throw new UnsupportedOperationException("redis is unavailable");
        }

        @Override
        public boolean getConvertPipelineAndTxResults() {
            return false;
        }

        @Override
        public RedisSentinelConnection getSentinelConnection() {
            throw new UnsupportedOperationException("redis is unavailable");
        }

        @Override
        public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
            return null;
        }
    }
}
