# Vincent Dict Redis Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an independently consumable Redis cache Starter that accelerates effective-item queries, invalidates safely across application instances, and falls back to MySQL during Redis failures.

**Architecture:** The Redis module implements the application `DictCache` port as a cache-aside loader. Global dict/default changes advance a global version; tenant-item changes advance a tenant version. A compare-and-set Lua script prevents a read racing with invalidation from publishing stale data under the new version.

**Tech Stack:** Core and admin plan outputs, Java 8, Spring Boot 2.2.6.RELEASE, Spring Data Redis managed by Boot, `StringRedisTemplate`, Jackson managed by Boot, Redis 7.2 integration tests through Testcontainers 1.19.8.

## Global Constraints

- Complete the core plan first; complete the admin plan before testing write-triggered invalidation.
- The core Starter must not depend on this module and must have no transitive Redis dependency.
- Redis is enabled only when this Starter is present and `vincent.dict.cache.enabled=true`.
- The adapter reuses the host `StringRedisTemplate`; it never creates a Redis connection factory.
- Enabling cache without `StringRedisTemplate` fails startup; disabled cache creates no Redis-backed `DictCache`.
- Database results remain authoritative; Redis failures never fail a business read or roll back a committed write.
- Normal invalidation is cross-instance immediate; Redis failures allow stale data for at most the configured TTL, default 60 seconds.
- Redis keys must not contain raw tenant IDs; encode tenant IDs with URL-safe Base64 without padding.
- Cache payloads contain only `DictItemView` fields and a payload format version; never serialize PO/domain objects.
- Do not use Redis `KEYS`, wildcard scans, pub/sub, distributed transactions, or local Caffeine caches.

---

### Task 1: Define race-safe cache port semantics and Redis auto-configuration

**Files:**
- Verify: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/port/DictCache.java`
- Verify: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/port/NoopDictCache.java`
- Verify: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/DefaultDictQueryService.java`
- Modify: `vincent-dict/vincent-dict-cache-redis-boot2-starter/pom.xml`
- Create: `vincent-dict/vincent-dict-cache-redis-boot2-starter/src/main/java/com/vincent/tools/dict/cache/redis/DictRedisProperties.java`
- Create: `vincent-dict/vincent-dict-cache-redis-boot2-starter/src/main/java/com/vincent/tools/dict/cache/redis/DictRedisAutoConfiguration.java`
- Create: `vincent-dict/vincent-dict-cache-redis-boot2-starter/src/main/resources/META-INF/spring.factories`
- Test: `vincent-dict/vincent-dict-application/src/test/java/com/vincent/tools/dict/application/DefaultDictQueryCacheTest.java`
- Test: `vincent-dict/vincent-dict-cache-redis-boot2-starter/src/test/java/com/vincent/tools/dict/cache/redis/DictRedisAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `DictItemView`, application repository loader, host `StringRedisTemplate`.
- Produces: one cache-aside port used by query/admin services and conditional Redis adapter bean.

- [ ] **Step 1: Write failing cache-aside application tests**

```java
@Test void query_delegates_loading_to_cache_port() {
    cache.answer = Arrays.asList(item("CACHED"));
    assertThat(service.listEffectiveItems("ORDER_STATUS", "tenant-a"))
        .extracting(DictItemView::getCode).containsExactly("CACHED");
    assertThat(repository.calls()).isZero();
}

@Test void noop_cache_executes_loader_once() {
    AtomicInteger calls = new AtomicInteger();
    List<DictItemView> result = new NoopDictCache().load(
        "ORDER_STATUS", "tenant-a", () -> {
            calls.incrementAndGet();
            return Arrays.asList(item("DB"));
        });
    assertThat(calls).hasValue(1);
    assertThat(result).extracting(DictItemView::getCode).containsExactly("DB");
}
```

- [ ] **Step 2: Confirm the core cache seam is the race-safe cache-aside port**

```java
public interface DictCache {
    List<DictItemView> load(
        String dictCode,
        String tenantId,
        Supplier<List<DictItemView>> databaseLoader
    );

    void evictAll(String dictCode);
    void evictTenant(String dictCode, String tenantId);
}
```

`NoopDictCache.load` invokes the supplier exactly once; its eviction methods do nothing. `DefaultDictQueryService` performs validation before calling cache and supplies a repository lambda that returns the already-defined immutable, sorted result.

- [ ] **Step 3: Run application cache tests**

```bash
mvn -q -pl vincent-dict/vincent-dict-application -am test
```

Expected: query cache seam and existing query semantics pass.

- [ ] **Step 4: Write failing Redis auto-configuration tests**

Test exact contexts:

```text
property absent/false                           -> no Redis DictCache
enabled + StringRedisTemplate                   -> one Redis DictCache
enabled + missing StringRedisTemplate           -> context failure CONFIGURATION_INVALID
core Starter without Redis Starter dependency  -> NoopDictCache only
```

- [ ] **Step 5: Implement cache properties and conditional auto-configuration**

Use prefix `vincent.dict.cache` and defaults `enabled=false`, `key-prefix=vin:dict`, `ttl=60s`. Validate nonblank prefix and positive TTL. Register through Boot 2 `spring.factories` and `@ConditionalOnClass(StringRedisTemplate.class)` without altering the core Starter.

- [ ] **Step 6: Run context and dependency tests**

```bash
mvn -q -pl vincent-dict/vincent-dict-cache-redis-boot2-starter -am test
mvn -q -pl vincent-dict/vincent-dict-boot2-starter dependency:tree
```

Expected: auto-config tests pass; core Starter tree contains no `spring-data-redis`.

- [ ] **Step 7: Commit the cache contract and module wiring**

```bash
git add vincent-dict/vincent-dict-application vincent-dict/vincent-dict-cache-redis-boot2-starter
git commit -m "feat(dict): add optional redis cache contract"
```

---

### Task 2: Implement versioned cache loading, invalidation, and fault fallback

**Files:**
- Create: `vincent-dict/vincent-dict-cache-redis-boot2-starter/src/main/java/com/vincent/tools/dict/cache/redis/RedisDictCache.java`
- Create: `vincent-dict/vincent-dict-cache-redis-boot2-starter/src/main/java/com/vincent/tools/dict/cache/redis/DictCacheKeyFactory.java`
- Create: `vincent-dict/vincent-dict-cache-redis-boot2-starter/src/main/java/com/vincent/tools/dict/cache/redis/DictCachePayload.java`
- Create: `vincent-dict/vincent-dict-cache-redis-boot2-starter/src/main/java/com/vincent/tools/dict/cache/redis/RateLimitedCacheLogger.java`
- Create: `vincent-dict/vincent-dict-cache-redis-boot2-starter/src/main/resources/com/vincent/tools/dict/cache/redis/put-if-version-unchanged.lua`
- Test: `vincent-dict/vincent-dict-cache-redis-boot2-starter/src/test/java/com/vincent/tools/dict/cache/redis/DictCacheKeyFactoryTest.java`
- Test: `vincent-dict/vincent-dict-cache-redis-boot2-starter/src/test/java/com/vincent/tools/dict/cache/redis/RedisDictCacheIT.java`

**Interfaces:**
- Consumes: `DictCache`, `StringRedisTemplate`, `ObjectMapper`, TTL/prefix properties.
- Produces: cross-instance cache with global and per-tenant generation invalidation.

- [ ] **Step 1: Write failing key and tenant-encoding tests**

```java
assertThat(keys.globalVersion("ORDER_STATUS"))
    .isEqualTo("vin:dict:gv:ORDER_STATUS");
assertThat(keys.tenantVersion("ORDER_STATUS", "tenant:a"))
    .doesNotContain("tenant:a")
    .startsWith("vin:dict:tv:ORDER_STATUS:");
```

The payload key format must be:

```text
{prefix}:v1:{dictCode}:{globalVersion}:{tenantVersion}:{base64TenantId}
```

- [ ] **Step 2: Write failing Redis integration tests**

Use `GenericContainer("redis:7.2.5-alpine")`. Cover miss/load/set, hit without loader, TTL, `evictAll`, `evictTenant`, default-only tenant, payload corruption fallback, and two cache instances sharing Redis.

- [ ] **Step 3: Write the failing read/invalidation race test**

Block the database loader after versions are read, call `evictTenant` from a second cache instance, then release the loader. Assert the stale result is returned to the in-flight caller but is not written under the new tenant version; the next call reloads the database.

- [ ] **Step 4: Run Redis tests and verify failure**

```bash
mvn -q -pl vincent-dict/vincent-dict-cache-redis-boot2-starter -am -Dtest=DictCacheKeyFactoryTest,RedisDictCacheIT test
```

Expected: FAIL because Redis adapter and Lua script are absent.

- [ ] **Step 5: Implement global and tenant version reads**

Missing version keys mean version `0`. `evictAll` runs `INCR globalVersionKey`; `evictTenant` runs `INCR tenantVersionKey`. Set the two version keys' TTL to at least `max(payloadTtl * 2, 120s)` after each increment so abandoned metadata expires without becoming shorter-lived than payloads.

- [ ] **Step 6: Implement compare-and-set cache population**

`load` reads both versions and the resulting payload key. On miss it calls the database supplier once. The Lua script receives expected global/tenant versions, re-reads both keys treating missing as `0`, and writes the payload with PX TTL only when both still match. This prevents stale repopulation after either invalidation type.

- [ ] **Step 7: Implement payload and fault behavior**

Serialize a wrapper with `formatVersion=1` and only DTO fields. Unknown format or malformed JSON is a miss and deletes the bad key best-effort. Any Redis read/write/script exception logs at WARN at most once per 30 seconds per failure category and returns database data; never catch the database supplier exception.

- [ ] **Step 8: Run Redis integration tests**

```bash
mvn -q -pl vincent-dict/vincent-dict-cache-redis-boot2-starter -am verify
```

Expected: all hit/miss/version/race/fallback tests pass.

- [ ] **Step 9: Commit the Redis adapter**

```bash
git add vincent-dict/vincent-dict-cache-redis-boot2-starter
git commit -m "feat(dict): implement versioned redis cache"
```

---

### Task 3: Verify write invalidation, consumer dependencies, and Redis documentation

**Files:**
- Create: `vincent-dict/vincent-dict-example-boot2/src/test/java/com/vincent/tools/dict/example/DictRedisIntegrationIT.java`
- Modify: `vincent-dict/vincent-dict-example-boot2/pom.xml`
- Modify: `vincent-dict/vincent-dict-example-boot2/src/main/resources/application.yml`
- Modify: `vincent-dict/README.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: admin write service, Redis Starter, example host and shared Redis.
- Produces: end-to-end proof of cross-instance invalidation and documented opt-in usage.

- [ ] **Step 1: Write the failing two-instance end-to-end test**

Start MySQL and Redis containers plus two Spring contexts sharing both. Prime `ORDER_STATUS` for `tenant-a` in context B, update a tenant item through context A, then assert B's next query sees the update without waiting for TTL. Repeat with a default-item update and assert both tenant A and tenant B caches refresh.

- [ ] **Step 2: Run the end-to-end test and verify failure**

```bash
mvn -q -pl vincent-dict/vincent-dict-example-boot2 -am -Dtest=DictRedisIntegrationIT test
```

Expected: FAIL until example Redis dependency/configuration is enabled.

- [ ] **Step 3: Wire opt-in Redis in the example test profile**

Add Redis Starter only in the example module's test scope/profile and set:

```yaml
vincent:
  dict:
    cache:
      enabled: true
      key-prefix: vin:dict:test
      ttl: 60s
```

Do not make Redis mandatory for the normal example profile.

- [ ] **Step 4: Document Redis opt-in and consistency**

Document the extra dependency, required host `StringRedisTemplate`, key prefix isolation, 60-second default TTL, global/tenant invalidation, database fallback, normal immediate visibility, and bounded stale reads during Redis failure. State explicitly that first version does not provide strong consistency while Redis is unavailable.

- [ ] **Step 5: Verify dependency isolation and the complete reactor**

```bash
mvn -q clean verify
mvn -q -pl vincent-dict/vincent-dict-boot2-starter dependency:tree -Dincludes=org.springframework.data,io.lettuce,redis.clients
mvn -q -pl vincent-dict/vincent-dict-cache-redis-boot2-starter dependency:tree -Dincludes=org.springframework.data:spring-data-redis
```

Expected: reactor passes; core tree has no Redis client; Redis Starter tree contains Spring Data Redis.

- [ ] **Step 6: Commit Redis example and documentation**

```bash
git add README.md vincent-dict
git commit -m "docs(dict): document optional redis caching"
```

## Redis Plan Exit Criteria

- Core consumers receive no Redis classes or client libraries.
- Redis consumers opt in with one additional BOM-managed Starter.
- Cache hits preserve immutable sorted query results and never expose PO/domain types.
- Global and tenant version invalidation works across instances without `KEYS` or pub/sub.
- A read racing with invalidation cannot repopulate stale data under a new version.
- Redis faults degrade to MySQL, are rate-limited in logs, and allow stale values for at most TTL.
