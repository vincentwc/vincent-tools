# Vincent Dict Redis 缓存实施计划

> **面向智能体执行者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans，按任务逐项实施本计划。步骤使用复选框（`- [ ]`）语法跟踪。

**目标：** 新增一个可独立引入的 Redis 缓存 Starter，以加速有效字典项查询、在应用实例间安全失效，并在 Redis 故障时回退至 MySQL。

**架构：** Redis 模块将应用层 `DictCache` 端口实现为旁路缓存加载器。全局字典/默认项变更推进全局版本；租户项变更推进租户版本。比较并设置 Lua 脚本可防止与失效操作竞争的读取在新版本下写入陈旧数据。

**技术栈：** 核心与管理端计划的产物、Java 8、Spring Boot 2.2.6.RELEASE、由 Boot 管理的 Spring Data Redis、`StringRedisTemplate`、由 Boot 管理的 Jackson、通过 Testcontainers 1.19.8 运行的 Redis 7.2 集成测试。

## 全局约束

- 先完成核心计划；在测试写入触发的失效前完成管理端计划。
- 核心 Starter 不得依赖本模块，且不得含有传递性的 Redis 依赖。
- 仅当本 Starter 存在且 `vincent.dict.cache.enabled=true` 时启用 Redis。
- 适配器复用宿主提供的 `StringRedisTemplate`，绝不创建 Redis 连接工厂。
- 未提供 `StringRedisTemplate` 时启用缓存应导致启动失败；禁用缓存时不得创建 Redis 支持的 `DictCache`。
- 数据库结果始终是权威数据；Redis 故障绝不能导致业务读取失败或回滚已提交的写入。
- 正常失效应在跨实例场景下即时生效；Redis 故障时，陈旧数据最多保留配置的 TTL，默认 60 秒。
- Redis 键不得包含原始租户 ID；应使用不带填充的 URL 安全 Base64 编码租户 ID。
- 缓存载荷只能包含 `DictItemView` 字段和载荷格式版本；绝不序列化 PO/领域对象。
- 不得使用 Redis `KEYS`、通配扫描、发布订阅、分布式事务或本地 Caffeine 缓存。

---

### Task 1: 定义竞态安全的缓存端口语义与 Redis 自动配置

**文件：**
- 验证：`vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/port/DictCache.java`
- 验证：`vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/port/NoopDictCache.java`
- 验证：`vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/DefaultDictQueryService.java`
- 修改：`vincent-dict/vincent-dict-cache-redis-boot2-starter/pom.xml`
- 创建：`vincent-dict/vincent-dict-cache-redis-boot2-starter/src/main/java/com/vincent/tools/dict/cache/redis/DictRedisProperties.java`
- 创建：`vincent-dict/vincent-dict-cache-redis-boot2-starter/src/main/java/com/vincent/tools/dict/cache/redis/DictRedisAutoConfiguration.java`
- 创建：`vincent-dict/vincent-dict-cache-redis-boot2-starter/src/main/resources/META-INF/spring.factories`
- 测试：`vincent-dict/vincent-dict-application/src/test/java/com/vincent/tools/dict/application/DefaultDictQueryCacheTest.java`
- 测试：`vincent-dict/vincent-dict-cache-redis-boot2-starter/src/test/java/com/vincent/tools/dict/cache/redis/DictRedisAutoConfigurationTest.java`

**接口：**
- 使用：`DictItemView`、应用仓储加载器、宿主 `StringRedisTemplate`。
- 产出：供查询/管理服务使用的一个旁路缓存端口，以及条件化的 Redis 适配器 Bean。

- [ ] **步骤 1：编写预期失败的旁路缓存应用测试**

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

- [ ] **步骤 2：确认核心缓存接缝是竞态安全的旁路缓存端口**

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

`NoopDictCache.load` 恰好调用一次 supplier；其失效方法不执行任何操作。`DefaultDictQueryService` 在调用缓存前完成校验，并提供一个返回既有不可变且已排序结果的仓储 lambda。

- [ ] **步骤 3：运行应用缓存测试**

```bash
mvn -q -pl vincent-dict/vincent-dict-application -am test
```

预期结果：查询缓存接缝及既有查询语义均通过。

- [ ] **步骤 4：编写预期失败的 Redis 自动配置测试**

测试以下精确上下文：

```text
property absent/false                           -> no Redis DictCache
enabled + StringRedisTemplate                   -> one Redis DictCache
enabled + missing StringRedisTemplate           -> context failure CONFIGURATION_INVALID
core Starter without Redis Starter dependency  -> NoopDictCache only
```

- [ ] **步骤 5：实现缓存属性与条件化自动配置**

使用前缀 `vincent.dict.cache` 及默认值 `enabled=false`、`key-prefix=vin:dict`、`ttl=60s`。校验前缀非空且 TTL 为正数。通过 Boot 2 的 `spring.factories` 和 `@ConditionalOnClass(StringRedisTemplate.class)` 注册，不得修改核心 Starter。

- [ ] **步骤 6：运行上下文与依赖测试**

```bash
mvn -q -pl vincent-dict/vincent-dict-cache-redis-boot2-starter -am test
mvn -q -pl vincent-dict/vincent-dict-boot2-starter dependency:tree
```

预期结果：自动配置测试通过；核心 Starter 的依赖树不包含 `spring-data-redis`。

- [ ] **步骤 7：提交缓存契约与模块接线**

```bash
git add vincent-dict/vincent-dict-application vincent-dict/vincent-dict-cache-redis-boot2-starter
git commit -m "feat(dict): add optional redis cache contract"
```

---

### Task 2: 实现带版本的缓存加载、失效与故障回退

**文件：**
- 创建：`vincent-dict/vincent-dict-cache-redis-boot2-starter/src/main/java/com/vincent/tools/dict/cache/redis/RedisDictCache.java`
- 创建：`vincent-dict/vincent-dict-cache-redis-boot2-starter/src/main/java/com/vincent/tools/dict/cache/redis/DictCacheKeyFactory.java`
- 创建：`vincent-dict/vincent-dict-cache-redis-boot2-starter/src/main/java/com/vincent/tools/dict/cache/redis/DictCachePayload.java`
- 创建：`vincent-dict/vincent-dict-cache-redis-boot2-starter/src/main/java/com/vincent/tools/dict/cache/redis/RateLimitedCacheLogger.java`
- 创建：`vincent-dict/vincent-dict-cache-redis-boot2-starter/src/main/resources/com/vincent/tools/dict/cache/redis/put-if-version-unchanged.lua`
- 测试：`vincent-dict/vincent-dict-cache-redis-boot2-starter/src/test/java/com/vincent/tools/dict/cache/redis/DictCacheKeyFactoryTest.java`
- 测试：`vincent-dict/vincent-dict-cache-redis-boot2-starter/src/test/java/com/vincent/tools/dict/cache/redis/RedisDictCacheIT.java`

**接口：**
- 使用：`DictCache`、`StringRedisTemplate`、`ObjectMapper`、TTL/前缀属性。
- 产出：具有全局和按租户代际失效能力的跨实例缓存。

- [ ] **步骤 1：编写预期失败的键与租户编码测试**

```java
assertThat(keys.globalVersion("ORDER_STATUS"))
    .isEqualTo("vin:dict:gv:ORDER_STATUS");
assertThat(keys.tenantVersion("ORDER_STATUS", "tenant:a"))
    .doesNotContain("tenant:a")
    .startsWith("vin:dict:tv:ORDER_STATUS:");
```

载荷键格式必须为：

```text
{prefix}:v1:{dictCode}:{globalVersion}:{tenantVersion}:{base64TenantId}
```

- [ ] **步骤 2：编写预期失败的 Redis 集成测试**

使用 `GenericContainer("redis:7.2.5-alpine")`。覆盖未命中/加载/写入、无需加载器的命中、TTL、`evictAll`、`evictTenant`、仅默认项租户、载荷损坏回退，以及两个共享 Redis 的缓存实例。

- [ ] **步骤 3：编写预期失败的读取/失效竞态测试**

在读取版本后阻塞数据库加载器，从第二个缓存实例调用 `evictTenant`，然后释放加载器。断言正在执行的调用方会收到陈旧结果，但该结果不会在新的租户版本下写入；下一次调用将重新加载数据库。

- [ ] **步骤 4：运行 Redis 测试并确认失败**

```bash
mvn -q -pl vincent-dict/vincent-dict-cache-redis-boot2-starter -am -Dtest=DictCacheKeyFactoryTest,RedisDictCacheIT test
```

预期结果：因 Redis 适配器和 Lua 脚本尚不存在而失败。

- [ ] **步骤 5：实现全局与租户版本读取**

缺失的版本键表示版本 `0`。`evictAll` 执行 `INCR globalVersionKey`；`evictTenant` 执行 `INCR tenantVersionKey`。每次递增后，将两个版本键的 TTL 设为至少 `max(payloadTtl * 2, 120s)`，使废弃元数据能够过期且寿命不会短于载荷。

- [ ] **步骤 6：实现比较并设置的缓存填充**

`load` 读取两个版本及由此得到的载荷键。未命中时调用一次数据库 supplier。Lua 脚本接收预期的全局/租户版本，重新读取两个键并将缺失视为 `0`，仅当二者仍匹配时才按 PX TTL 写入载荷。这可防止任一种失效后发生陈旧数据回填。

- [ ] **步骤 7：实现载荷与故障处理行为**

序列化一个仅含 DTO 字段且带有 `formatVersion=1` 的包装对象。未知格式或格式错误的 JSON 视为未命中，并尽力删除坏键。任何 Redis 读/写/脚本异常按故障类别最多每 30 秒记录一次 WARN 并返回数据库数据；绝不捕获数据库 supplier 的异常。

- [ ] **步骤 8：运行 Redis 集成测试**

```bash
mvn -q -pl vincent-dict/vincent-dict-cache-redis-boot2-starter -am verify
```

预期结果：所有命中/未命中/版本/竞态/回退测试均通过。

- [ ] **步骤 9：提交 Redis 适配器**

```bash
git add vincent-dict/vincent-dict-cache-redis-boot2-starter
git commit -m "feat(dict): implement versioned redis cache"
```

---

### Task 3: 验证写入失效、消费者依赖与 Redis 文档

**文件：**
- 创建：`vincent-dict/vincent-dict-example-boot2/src/test/java/com/vincent/tools/dict/example/DictRedisIntegrationIT.java`
- 修改：`vincent-dict/vincent-dict-example-boot2/pom.xml`
- 修改：`vincent-dict/vincent-dict-example-boot2/src/main/resources/application.yml`
- 修改：`vincent-dict/README.md`
- 修改：`README.md`

**接口：**
- 使用：管理端写服务、Redis Starter、示例宿主和共享 Redis。
- 产出：跨实例失效的端到端证明及文档化的按需启用用法。

- [ ] **步骤 1：编写预期失败的双实例端到端测试**

启动 MySQL 和 Redis 容器，以及两个共享二者的 Spring 上下文。在上下文 B 中预热 `tenant-a` 的 `ORDER_STATUS`，经由上下文 A 更新一个租户项，然后断言 B 的下一次查询无需等待 TTL 即可看到更新。以默认项更新重复此流程，并断言租户 A 和租户 B 的缓存均刷新。

- [ ] **步骤 2：运行端到端测试并确认失败**

```bash
mvn -q -pl vincent-dict/vincent-dict-example-boot2 -am -Dtest=DictRedisIntegrationIT test
```

预期结果：在启用示例 Redis 依赖/配置前失败。

- [ ] **步骤 3：在示例测试配置中接入按需启用的 Redis**

仅在示例模块的测试作用域/配置中添加 Redis Starter，并设置：

```yaml
vincent:
  dict:
    cache:
      enabled: true
      key-prefix: vin:dict:test
      ttl: 60s
```

不得让 Redis 成为常规示例配置的必需项。

- [ ] **步骤 4：记录 Redis 按需启用方式与一致性**

记录额外依赖、所需的宿主 `StringRedisTemplate`、键前缀隔离、60 秒默认 TTL、全局/租户失效、数据库回退、正常情况下的即时可见性，以及 Redis 故障期间有界的陈旧读取。明确说明第一版在 Redis 不可用时不提供强一致性。

- [ ] **步骤 5：验证依赖隔离与完整 Reactor**

```bash
mvn -q clean verify
mvn -q -pl vincent-dict/vincent-dict-boot2-starter dependency:tree -Dincludes=org.springframework.data,io.lettuce,redis.clients
mvn -q -pl vincent-dict/vincent-dict-cache-redis-boot2-starter dependency:tree -Dincludes=org.springframework.data:spring-data-redis
```

预期结果：Reactor 通过；核心依赖树不含 Redis 客户端；Redis Starter 依赖树包含 Spring Data Redis。

- [ ] **步骤 6：提交 Redis 示例与文档**

```bash
git add README.md vincent-dict
git commit -m "docs(dict): document optional redis caching"
```

## Redis 计划退出标准

- 核心消费者不会获得 Redis 类或客户端库。
- Redis 消费者通过额外引入一个由 BOM 管理的 Starter 按需启用。
- 缓存命中保留不可变、已排序的查询结果，且绝不暴露 PO/领域类型。
- 全局和租户版本失效可在没有 `KEYS` 或发布订阅的情况下跨实例工作。
- 与失效竞争的读取不能在新版本下重新填充陈旧数据。
- Redis 故障会降级至 MySQL，日志受速率限制，且陈旧值最多保留一个 TTL。
