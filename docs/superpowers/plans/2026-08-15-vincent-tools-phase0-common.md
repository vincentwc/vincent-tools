# Vincent Tools Phase 0 — Common 模块抽取实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从 `vincent-dict` 零行为变化地抽取 4 个 common 模块（host-ports、common-web、common-core、common-cache-redis），更新 BOM 与 dict 依赖，全量测试通过。

**Architecture:** 新建 `vincent-common/` 聚合模块；将端口、分页、Schema 校验、基础设施解析、Web 响应体、SPA 注入、Redis 限流日志等迁入 common；dict 各层改为依赖 common 并删除重复类。`DictAdminPermission` 实现 `VincentPermission`；`PermissionProvider` 签名改为接受 `VincentPermission`。

**Tech Stack:** Java 8、Maven 3.6+、Spring Boot 2.2.6.RELEASE、MyBatis-Plus 3.3.2、Spring Data Redis（Boot 2.2.6 BOM）、JUnit 5、AssertJ。

## Global Constraints

- 所有 Maven 坐标 groupId 为 `com.vincent.tools`；开发版本 `1.0.0-SNAPSHOT`。
- Java 源码/目标严格 1.8；不得使用 Java 8 之后 API。
- `vincent-host-ports` 与 `vincent-common-core` 不得依赖 Spring、MyBatis、Redis、HTTP。
- `vincent-common-web` 可依赖 Spring Web MVC（optional/provided 由 consumer 传递）。
- `vincent-common-cache-redis` 可依赖 Spring Data Redis；不得依赖 dict 领域类型。
- 包名：`com.vincent.tools.host.*`、`com.vincent.tools.common.web.*`、`com.vincent.tools.common.core.*`、`com.vincent.tools.common.cache.redis.*`。
- Phase 0 **不得**改变 dict 对外 Starter 坐标、配置键、HTTP 路径、错误码字符串或运行时行为。
- 全量验证命令：`mvn -P '!jdk-17' test`（本机 Maven settings 有冲突 profile 时必须加 `-P '!jdk-17'`）。
- `VincentPermission.code()` 第一版返回 `enum.name()`；宿主 RBAC 零迁移。

---

### Task 1: Maven Reactor 与 common 模块骨架

**Files:**
- Modify: `pom.xml`
- Modify: `vincent-tools-bom/pom.xml`
- Create: `vincent-common/pom.xml`
- Create: `vincent-common/vincent-host-ports/pom.xml`
- Create: `vincent-common/vincent-common-web/pom.xml`
- Create: `vincent-common/vincent-common-core/pom.xml`
- Create: `vincent-common/vincent-common-cache-redis/pom.xml`

**Interfaces:**
- Consumes: 无。
- Produces: 4 个空模块可被 `mvn install`，BOM 含新坐标。

- [ ] **Step 1: 根 POM 增加 `vincent-common` 模块**

在 `pom.xml` 的 `<modules>` 中加入 `<module>vincent-common</module>`（位于 `vincent-tools-bom` 与 `vincent-dict` 之间）。

- [ ] **Step 2: 创建 `vincent-common/pom.xml` 聚合 POM**

```xml
<artifactId>vincent-common</artifactId>
<packaging>pom</packaging>
<modules>
    <module>vincent-host-ports</module>
    <module>vincent-common-core</module>
    <module>vincent-common-web</module>
    <module>vincent-common-cache-redis</module>
</modules>
```

- [ ] **Step 3: 创建叶子 POM 并锁定依赖方向**

```text
vincent-host-ports          （无内部 Vincent 依赖）
vincent-common-core         → host-ports（若需要则不加，保持纯 Java）
vincent-common-web          → host-ports（optional，仅 ApiResponse 不需）
vincent-common-cache-redis  （仅 Spring Data Redis + slf4j）
```

`vincent-host-ports/pom.xml`：仅 JUnit + AssertJ test 依赖。

`vincent-common-core/pom.xml`：无 Spring 依赖。

`vincent-common-web/pom.xml`：

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-webmvc</artifactId>
    <optional>true</optional>
</dependency>
```

`vincent-common-cache-redis/pom.xml`：

```xml
<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-redis</artifactId>
    <optional>true</optional>
</dependency>
```

- [ ] **Step 4: 更新根 POM `dependencyManagement` 与 BOM**

新增条目：

```text
vincent-host-ports
vincent-common-core
vincent-common-web
vincent-common-cache-redis
```

- [ ] **Step 5: 验证空 Reactor**

```bash
mvn -P '!jdk-17' -q -DskipTests install
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add pom.xml vincent-tools-bom/pom.xml vincent-common/
git commit -m "build(common): add vincent-common module skeleton and BOM entries"
```

---

### Task 2: vincent-host-ports — 宿主端口与 VincentPermission

**Files:**
- Create: `vincent-common/vincent-host-ports/src/main/java/com/vincent/tools/host/VincentPermission.java`
- Create: `vincent-common/vincent-host-ports/src/main/java/com/vincent/tools/host/TenantProvider.java`
- Create: `vincent-common/vincent-host-ports/src/main/java/com/vincent/tools/host/OperatorProvider.java`
- Create: `vincent-common/vincent-host-ports/src/main/java/com/vincent/tools/host/PermissionProvider.java`
- Create: `vincent-common/vincent-host-ports/src/test/java/com/vincent/tools/host/VincentPermissionTest.java`
- Delete（Task 6）: `vincent-dict/.../application/TenantProvider.java` 等旧文件

**Interfaces:**
- Consumes: 无。
- Produces:

```java
public interface VincentPermission { String code(); }
public interface TenantProvider { Optional<String> currentTenantId(); }
public interface OperatorProvider { String currentOperatorId(); }
public interface PermissionProvider {
    boolean hasPermission(VincentPermission permission, Optional<String> targetTenantId);
}
```

- [ ] **Step 1: 写入四个端口接口**

从 dict 复制方法签名，调整包名与 `PermissionProvider` 参数类型为 `VincentPermission`。

- [ ] **Step 2: 写测试验证 VincentPermission 约定**

```java
enum SamplePermission implements VincentPermission {
    DICT_VIEW;
    @Override public String code() { return name(); }
}
@Test void codeReturnsEnumName() {
    assertThat(SamplePermission.DICT_VIEW.code()).isEqualTo("DICT_VIEW");
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn -P '!jdk-17' -pl vincent-common/vincent-host-ports test
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add vincent-common/vincent-host-ports/
git commit -m "feat(common): add host port interfaces and VincentPermission"
```

---

### Task 3: vincent-common-core — PageResult 与 Schema 校验

**Files:**
- Create: `vincent-common/vincent-common-core/src/main/java/com/vincent/tools/common/core/PageResult.java`
- Create: `vincent-common/vincent-common-core/src/main/java/com/vincent/tools/common/core/schema/SchemaExpectation.java`
- Create: `vincent-common/vincent-common-core/src/main/java/com/vincent/tools/common/core/schema/VincentSchemaValidator.java`
- Create: `vincent-common/vincent-common-core/src/main/java/com/vincent/tools/common/core/schema/SchemaValidationException.java`
- Create: `vincent-common/vincent-common-core/src/test/java/com/vincent/tools/common/core/schema/VincentSchemaValidatorTest.java`
- Modify（Task 7）: `vincent-dict/.../DictSchemaValidator.java` 改为委托

**Interfaces:**
- Consumes: 无。
- Produces:

```java
public final class PageResult<T> { /* 从 dict 原样迁移 */ }

public final class SchemaExpectation {
    public SchemaExpectation(String[] requiredTables, String metaTable,
            String metaIdColumn, long metaRowId, String versionColumn,
            String requiredVersion, String initSqlPath);
}

public final class VincentSchemaValidator {
    public void validate(DataSource dataSource, SchemaExpectation expectation);
}

public final class SchemaValidationException extends RuntimeException {
    public String errorCode(); // "SCHEMA_MISSING" | "SCHEMA_VERSION_MISMATCH"
}
```

- [ ] **Step 1: 迁移 `PageResult`**

从 `dict.application.admin.PageResult` 复制到 common-core，包名 `com.vincent.tools.common.core`。

- [ ] **Step 2: 写 failing Schema 校验测试**

使用 H2 或 Testcontainers MySQL（与 dict IT 一致优先 MySQL Testcontainers）。测试缺表抛 `SchemaValidationException`，errorCode=`SCHEMA_MISSING`。

- [ ] **Step 3: 实现 `VincentSchemaValidator`**

从 `DictSchemaValidator` 提取逻辑；用 `SchemaExpectation` 参数化表名、meta 表、版本值、SQL 路径提示。SQL 查询保持与 dict 相同（`information_schema.TABLES`、`DATABASE()`）。

- [ ] **Step 4: 运行 common-core 测试**

```bash
mvn -P '!jdk-17' -pl vincent-common/vincent-common-core test
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add vincent-common/vincent-common-core/
git commit -m "feat(common): add PageResult and VincentSchemaValidator"
```

---

### Task 4: vincent-common-core — VincentInfrastructureResolver

**Files:**
- Create: `vincent-common/vincent-common-core/src/main/java/com/vincent/tools/common/core/infrastructure/InfrastructureBeanNames.java`
- Create: `vincent-common/vincent-common-core/src/main/java/com/vincent/tools/common/core/infrastructure/VincentInfrastructureResolver.java`
- Create: `vincent-common/vincent-common-core/src/main/java/com/vincent/tools/common/core/infrastructure/InfrastructureConfigurationException.java`
- Create: `vincent-common/vincent-common-core/src/test/java/com/vincent/tools/common/core/infrastructure/VincentInfrastructureResolverTest.java`
- Modify（Task 7）: `DictInfrastructureResolver.java`

**Interfaces:**
- Consumes: 无（测试用 Spring `StaticApplicationContext` 或 mock `ConfigurableListableBeanFactory`）。
- Produces:

```java
public final class InfrastructureBeanNames {
    public final String dataSourceBeanName;
    public final String sqlSessionFactoryBeanName;
    public final String transactionManagerBeanName;
}

public final class VincentInfrastructureResolver {
    public InfrastructureBeanNames resolve(ConfigurableListableBeanFactory beanFactory,
            Optional<String> dataSourceBeanName,
            Optional<String> sqlSessionFactoryBeanName,
            Optional<String> transactionManagerBeanName);

    public void validateMatching(ApplicationContext context, InfrastructureBeanNames names);

    public DataSource getDataSource(ApplicationContext context, InfrastructureBeanNames names);
    // 同理 SqlSessionFactory、PlatformTransactionManager
}
```

- [ ] **Step 1: 从 `DictInfrastructureResolver` 提取 `uniqueOrPrimary`、`requireType`、`validateMatching` 到 `VincentInfrastructureResolver`**

dict 特有逻辑（mapper scanner、`vincent.dict.enabled`、MapperScannerConfigurer）**保留**在 `DictInfrastructureResolver`。

- [ ] **Step 2: 写单元测试**

覆盖：0 个 DataSource → 异常；2 个无 @Primary → 异常；1 个 → 成功；3 个显式 bean name → 成功。

- [ ] **Step 3: 运行测试**

```bash
mvn -P '!jdk-17' -pl vincent-common/vincent-common-core test
```

- [ ] **Step 4: Commit**

```bash
git add vincent-common/vincent-common-core/
git commit -m "feat(common): add VincentInfrastructureResolver"
```

---

### Task 5: vincent-common-web — ApiResponse 与 SPA 注入

**Files:**
- Create: `vincent-common/vincent-common-web/src/main/java/com/vincent/tools/common/web/ApiResponse.java`
- Create: `vincent-common/vincent-common-web/src/main/java/com/vincent/tools/common/web/VincentAdminSpaHtml.java`
- Create: `vincent-common/vincent-common-web/src/test/java/com/vincent/tools/common/web/VincentAdminSpaHtmlTest.java`
- Modify（Task 7）: `DictAdminSpaHtml.java`、`ApiResponse.java`（删除或改为 type alias 委托）

**Interfaces:**
- Consumes: 无。
- Produces:

```java
public final class ApiResponse<T> {
    public static <T> ApiResponse<T> ok(T data);
    public static <T> ApiResponse<T> error(String code, String message);
}

public final class VincentAdminSpaHtml {
    public static String inject(String html, String configGlobalName,
            String apiPath, String historyBase);
    public static byte[] injectBytes(Resource resource, String configGlobalName,
            String apiPath, String historyBase) throws IOException;
}
```

`configGlobalName` 对 dict 传 `"__VIN_DICT_CONFIG__"`，保持现有前端契约。

- [ ] **Step 1: 迁移 `ApiResponse`**

从 dict.web 复制，包名改为 common.web。

- [ ] **Step 2: 迁移 SPA 注入测试**

将 `DictAdminSpaHtml` 现有测试逻辑改为测 `VincentAdminSpaHtml`，覆盖 `<head>` 注入、`base href`、JSON escape。

- [ ] **Step 3: 实现 `VincentAdminSpaHtml`**

从 `DictAdminSpaHtml` 提取；`window.__VIN_DICT_CONFIG__` 改为参数 `configGlobalName`。

- [ ] **Step 4: 运行测试**

```bash
mvn -P '!jdk-17' -pl vincent-common/vincent-common-web test
```

- [ ] **Step 5: Commit**

```bash
git add vincent-common/vincent-common-web/
git commit -m "feat(common): add ApiResponse and VincentAdminSpaHtml"
```

---

### Task 6: vincent-common-cache-redis — 限流日志与脚本工具

**Files:**
- Create: `vincent-common/vincent-common-cache-redis/src/main/java/com/vincent/tools/common/cache/redis/RateLimitedCacheLogger.java`
- Create: `vincent-common/vincent-common-cache-redis/src/main/java/com/vincent/tools/common/cache/redis/RedisScriptLoader.java`
- Create: `vincent-common/vincent-common-cache-redis/src/test/java/com/vincent/tools/common/cache/redis/RateLimitedCacheLoggerTest.java`
- Modify（Task 7）: `dict/cache/redis/RateLimitedCacheLogger.java`、`RedisDictCache.java`

**Interfaces:**
- Consumes: 无。
- Produces:

```java
public final class RateLimitedCacheLogger {
    public RateLimitedCacheLogger(Class<?> logCategory);
    public void warn(String failureClass, String message, Throwable error);
}

public final class RedisScriptLoader {
    public static DefaultRedisScript<Long> loadLongScript(String classpathLocation);
}
```

- [ ] **Step 1: 迁移 `RateLimitedCacheLogger`**

从 dict 模块复制；构造函数增加 `Class<?>` 参数以便各组件指定 log category。

- [ ] **Step 2: 写测试验证 30s 限流**

30s 内相同 `failureClass` 只 log 一次。

- [ ] **Step 3: 提取 `RedisScriptLoader`**

从 `RedisDictCache.loadPutScript()` 抽取通用 classpath 脚本加载。

- [ ] **Step 4: 运行测试**

```bash
mvn -P '!jdk-17' -pl vincent-common/vincent-common-cache-redis test
```

- [ ] **Step 5: Commit**

```bash
git add vincent-common/vincent-common-cache-redis/
git commit -m "feat(common): add RateLimitedCacheLogger and RedisScriptLoader"
```

---

### Task 7: dict 迁移 — application 与 host-ports

**Files:**
- Modify: `vincent-dict/vincent-dict-application/pom.xml`
- Modify: `vincent-dict/.../DictAdminPermission.java`
- Modify: 所有 `import com.vincent.tools.dict.application.TenantProvider` → `com.vincent.tools.host.*`
- Modify: 所有 `import ...PageResult` → `com.vincent.tools.common.core.PageResult`
- Delete: `TenantProvider.java`, `OperatorProvider.java`, `PermissionProvider.java`, `PageResult.java`（原路径）

**Interfaces:**
- Consumes: Task 2–3 产出。
- Produces: `DictAdminPermission implements VincentPermission`；编译通过。

- [ ] **Step 1: application POM 增加依赖**

```xml
<dependency>
    <groupId>com.vincent.tools</groupId>
    <artifactId>vincent-host-ports</artifactId>
</dependency>
<dependency>
    <groupId>com.vincent.tools</groupId>
    <artifactId>vincent-common-core</artifactId>
</dependency>
```

- [ ] **Step 2: 更新 `DictAdminPermission`**

```java
public enum DictAdminPermission implements VincentPermission {
    DICT_VIEW, /* ... */;
    @Override public String code() { return name(); }
}
```

- [ ] **Step 3: 全局替换 import 并删除旧端口类**

- [ ] **Step 4: 运行 application 测试**

```bash
mvn -P '!jdk-17' -pl vincent-dict/vincent-dict-application test
```

Expected: PASS（测试 mock 仍用 `DictAdminPermission`）

- [ ] **Step 5: Commit**

```bash
git add vincent-dict/vincent-dict-application/
git commit -m "refactor(dict): depend on host-ports and common-core PageResult"
```

---

### Task 8: dict 迁移 — web、boot2、redis

**Files:**
- Modify: `vincent-dict-web/pom.xml`、`vincent-dict-boot2-starter/pom.xml`、`vincent-dict-cache-redis-boot2-starter/pom.xml`
- Modify: `DictWebExceptionHandler.java`（import common ApiResponse）
- Modify: `DictAdminSpaHtml.java` → 薄包装委托 `VincentAdminSpaHtml`
- Modify: `DictSchemaValidator.java` → 委托 `VincentSchemaValidator`
- Modify: `DictInfrastructureResolver.java` → 委托 `VincentInfrastructureResolver`
- Modify: `RedisDictCache.java` → 使用 common `RateLimitedCacheLogger`

**Interfaces:**
- Consumes: Task 3–6 产出。
- Produces: dict 全模块编译通过。

- [ ] **Step 1: 更新各模块 POM 依赖**

```text
vincent-dict-web              → vincent-common-web
vincent-dict-boot2-starter    → vincent-common-core
vincent-dict-cache-redis      → vincent-common-cache-redis
```

- [ ] **Step 2: `DictSchemaValidator` 薄包装**

```java
public void validate(DataSource dataSource) {
    new VincentSchemaValidator().validate(dataSource, DICT_SCHEMA_EXPECTATION);
}
// DICT_SCHEMA_EXPECTATION 使用现有 REQUIRED_TABLES、版本 1、001-init.sql 路径
```

捕获 `SchemaValidationException` 转为 `DictException`（保持错误码字符串不变）。

- [ ] **Step 3: `DictInfrastructureResolver` 委托**

用 `VincentInfrastructureResolver` 替换内部 `uniqueOrPrimary` 等私有方法；mapper scanner 注册逻辑不变。

- [ ] **Step 4: `DictAdminSpaHtml` 委托**

```java
static String inject(String html, String apiPath, String historyBase) {
    return VincentAdminSpaHtml.inject(html, "__VIN_DICT_CONFIG__", apiPath, historyBase);
}
```

- [ ] **Step 5: 删除 dict 内重复的 `ApiResponse.java`；web 层改 import**

- [ ] **Step 6: Redis 模块改用 common logger/script loader**

- [ ] **Step 7: Commit**

```bash
git add vincent-dict/
git commit -m "refactor(dict): migrate web, boot2, and redis to common modules"
```

---

### Task 9: 全量回归与文档

**Files:**
- Modify: `README.md`（可选，一行说明 common 模块存在）
- Modify: `vincent-dict/README.md`（可选，宿主端口已迁至 host-ports）

**Interfaces:**
- Consumes: Task 1–8 全部完成。
- Produces: Phase 0 验收通过。

- [ ] **Step 1: 全量测试**

```bash
mvn -P '!jdk-17' test
```

Expected: 全部 PASS，0 failures（与 Phase 0 前测试数量一致或仅新增 common 模块测试）。

- [ ] **Step 2: 验证 dict Starter 行为不变**

确认以下仍成立：
- `vincent-dict-boot2-starter` 坐标不变；
- `PermissionProvider.hasPermission(DictAdminPermission.DICT_VIEW, ...)` 调用方式不变；
- 管理页 `/dict-admin` 与 API 路径不变；
- Schema 校验错误码仍为 `SCHEMA_MISSING` / `SCHEMA_VERSION_MISMATCH`。

- [ ] **Step 3: 更新 README（最小）**

根 README 工具清单增加 common 模块一句说明。

- [ ] **Step 4: Commit**

```bash
git add README.md vincent-dict/README.md
git commit -m "docs: note vincent-common modules after Phase 0 extraction"
```

---

## Spec Coverage Self-Review

| Spec 要求 | 对应 Task |
| --- | --- |
| Phase 0 抽取 host-ports | Task 2, 7 |
| Phase 0 抽取 common-web（ApiResponse、SPA） | Task 5, 8 |
| Phase 0 抽取 common-core（PageResult、Schema、InfrastructureResolver） | Task 3, 4, 7, 8 |
| Phase 0 抽取 common-cache-redis | Task 6, 8 |
| VincentPermission + PermissionProvider 签名 | Task 2, 7 |
| dict 零行为变化 | Task 9 回归 |
| BOM 新增 common 坐标 | Task 1 |
| `mvn -P '!jdk-17' test` gate | Task 9 |

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-15-vincent-tools-phase0-common.md`. Two execution options:

**1. Subagent-Driven (recommended)** — 每个 Task 派发独立 subagent，Task 间人工/审查卡点

**2. Inline Execution** — 本会话用 executing-plans 按 Task 批量执行并在 checkpoint 暂停

Which approach?
