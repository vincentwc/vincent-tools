# Vincent Dict 设计文档

## 1. 背景与目标

`vincent-tools` 是可持续扩展的通用工具仓库。首个工具 `vincent-dict` 为 Java/Spring Boot 业务系统提供可嵌入的字典能力。

业务系统导入 `vincent-tools-bom`、引入 `vincent-dict-boot2-starter`、执行版本化手工 SQL 并提供少量宿主适配后，即可获得：

- 统一的 Java 字典查询接口；
- 默认字典项与租户追加项；
- Web 宿主中的内嵌后台管理页面和管理 API；
- 创建人、最后修改人和创建/修改时间；
- 独立依赖、按需启用的 Redis 缓存。

首版兼容 Java 8 与 Spring Boot 2.2.6 技术基线。所有发布坐标、包名、配置、表名和业务代码保持通用，不包含客户业务语义。

## 2. 范围

### 2.1 第一版负责

- 字典定义和字典项管理；
- 默认项与租户追加规则；
- 基于当前租户或显式租户的 Java 查询服务；
- 条件装配的管理 API 与内嵌管理页面；
- MySQL 5.7+ 初始化、升级和示例数据 SQL；
- Schema 版本只读校验；
- Spring Boot 2 自动装配；
- 可选 Redis 缓存 Starter；
- 最小 Boot 2.2.6 示例宿主和接入文档。

### 2.2 第一版不负责

- 完整操作审计和修改前后快照；
- 自动建表、Flyway 或其他自动迁移框架；
- 默认项覆盖或屏蔽；
- 租户私有字典定义；
- 字段映射和系统间值转换；
- 组织树、地区树和字典树；
- 用户、租户和权限数据维护；
- 国际化；
- 审批和发布流程；
- 导入导出；
- 独立部署的字典中心或远程查询服务；
- Spring Boot 3；
- 修改或迁移任何现有业务系统。

第一版不会随发布物预置任何业务字典。示例宿主可以包含演示数据，但演示数据不得进入 Starter。

## 3. 仓库、发布物与版本

```text
vincent-tools
├── pom.xml
├── vincent-tools-bom
└── vincent-dict
    ├── vincent-dict-domain
    ├── vincent-dict-application
    ├── vincent-dict-infra-mybatis
    ├── vincent-dict-web
    ├── vincent-dict-admin-ui
    ├── vincent-dict-boot2-starter
    ├── vincent-dict-cache-redis-boot2-starter
    └── vincent-dict-example-boot2
```

根 POM 同时承担内部父工程与源码模块聚合职责，并与其他模块一起发布：

```text
com.vincent.tools:vincent-tools:1.0.0
com.vincent.tools:vincent-tools-bom:1.0.0
```

`vincent-tools-bom` 只管理已发布 Vincent 工具的兼容版本，不包含代码或构建插件。开发版本从 `1.0.0-SNAPSHOT` 起步，正式版本遵循语义化版本。

业务宿主按需使用：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.vincent.tools</groupId>
            <artifactId>vincent-tools-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependency>
    <groupId>com.vincent.tools</groupId>
    <artifactId>vincent-dict-boot2-starter</artifactId>
</dependency>
```

需要 Redis 时额外引入：

```xml
<dependency>
    <groupId>com.vincent.tools</groupId>
    <artifactId>vincent-dict-cache-redis-boot2-starter</artifactId>
</dependency>
```

README 必须明确根父 POM、BOM 与功能 Starter 的差异，禁止建议业务系统把根 POM作为功能依赖。

## 4. DDD 架构与依赖

### 4.1 领域层

`vincent-dict-domain` 是纯 Java 模块，包含聚合、值对象、领域规则和领域异常，不依赖 Spring、MyBatis、Redis、HTTP 或前端代码。

### 4.2 应用层

`vincent-dict-application` 包含查询和维护用例、事务语义、入站服务接口，以及仓储、缓存和宿主上下文端口。应用层不依赖具体基础设施。

### 4.3 外层适配器

- `vincent-dict-infra-mybatis`：PO、Mapper、仓储、数据库锁和 Schema 校验；
- `vincent-dict-web`：管理 API、异常映射和页面路由；
- `vincent-dict-admin-ui`：Vue 3 + TypeScript 管理端源码；
- `vincent-dict-boot2-starter`：配置、条件 Bean、自动装配和 UI 静态资源聚合；
- `vincent-dict-cache-redis-boot2-starter`：可选 Redis 缓存适配器和自动装配。

依赖只能由外向内。领域规则只能存在于领域层；Controller、Starter、Mapper 和缓存适配器不得复制领域规则。

## 5. 领域模型

### 5.1 Dict

`Dict` 是字典定义聚合根：

```text
id, code, name, description, status, sortNo,
version, deleted, createdBy, createdAt, updatedBy, updatedAt
```

规则：

- `id` 是数据库生成的 `Long` 自增主键，仅供组件内部使用；
- `code` 全局唯一，创建后不可修改，逻辑删除后不可复用；
- 名称、描述、排序和状态可修改；
- 停用只影响业务有效查询，不限制管理维护；
- 存在未删除字典项时禁止删除，返回 `DICT_NOT_EMPTY`；
- 删除后可以恢复。

### 5.2 DictItem

`DictItem` 是独立聚合根，避免读取一个字典时加载全部租户项：

```text
id, dictId, code, name, tenantId, description,
status, sortNo, version, deleted,
createdBy, createdAt, updatedBy, updatedAt
```

规则：

- `id` 是数据库生成的 `Long` 自增主键；
- `tenantId = "0"` 表示默认项，其他非空字符串表示租户追加项；
- `code`、`dictId` 和 `tenantId` 创建后不可修改；
- 名称、描述、排序和状态可修改；
- 字典项只支持扁平结构；
- 删除后可以恢复；恢复前所属字典必须存在且未删除。

### 5.3 编码与文本规范

`Dict.code` 和 `DictItem.code` 必须匹配：

```regex
^[A-Z][A-Z0-9_]{0,63}$
```

- 只允许大写英文、数字和下划线；
- 最长 64 字符；
- 接口不自动转大写或裁剪空格，不合法时直接拒绝；
- 数据库编码列使用明确的 ASCII/二进制排序规则；
- 名称最长 128 字符；
- 描述最长 500 字符；
- `tenantId` 最长 64 字符，`"0"` 是组件保留值。
- `operatorId` 必须非空且最长 64 字符；
- 创建和更新时间按 UTC 生成并持久化，展示时由宿主或浏览器转换时区。

### 5.4 多租户不变量

- 租户只能在已有字典下追加字典项，不能创建字典定义；
- 租户不能覆盖、修改、停用、删除或屏蔽默认项；
- 默认项已使用某编码时，任何租户不能新增相同编码；
- 某编码已被任意租户使用时，不能再创建相同编码的默认项；
- 同一字典、同一租户下字典项编码唯一；
- 不同租户允许使用相同的租户项编码；
- 所有冲突检查都包含逻辑删除数据；
- 字典和字典项逻辑删除后，编码永久不可由新记录复用；
- 恢复字典不自动恢复字典项；
- 单个字典最多 1,000 个未删除默认项；
- 单个租户在单个字典下最多 1,000 个未删除追加项；
- 一次有效查询最多返回 2,000 项。

## 6. 宿主扩展端口

```java
public interface TenantProvider {
    Optional<String> currentTenantId();
}

public interface OperatorProvider {
    String currentOperatorId();
}

public interface PermissionProvider {
    boolean hasPermission(
        DictAdminPermission permission,
        Optional<String> targetTenantId
    );
}

public interface TenantDirectory {
    PageResult<TenantOption> search(String keyword, int page, int size);
    boolean exists(String tenantId);
}
```

规则：

- 未提供 `TenantProvider` 时，组件以单租户模式运行，无参查询只返回默认项；
- 已注册 `TenantProvider` 但当前上下文缺少租户时，无参多租户查询抛出 `TENANT_CONTEXT_MISSING`；
- 启用管理端时 `OperatorProvider` 与 `PermissionProvider` 必须存在，否则启动失败；
- 管理字典定义和默认项时权限目标租户为空；
- 查询或维护租户项时权限校验必须携带目标租户 ID；
- `TenantDirectory` 只读搜索和校验租户，不保存租户资料；
- 缺少 `TenantDirectory` 时隐藏并拒绝租户项管理，但默认项管理和查询仍可用；
- 新增租户项时后端必须再次执行 `TenantDirectory.exists`；租户目录故障时拒绝写入，不降级为未校验写入。

## 7. 权限

稳定权限集合：

```text
DICT_VIEW
DICT_CREATE
DICT_UPDATE
DICT_ENABLE_DISABLE
DICT_DELETE
DICT_RESTORE
ITEM_CREATE
ITEM_UPDATE
ITEM_ENABLE_DISABLE
ITEM_DELETE
ITEM_RESTORE
```

管理页面根据权限隐藏按钮，管理 API 对每个请求独立校验权限和目标租户范围。组件不负责身份认证、用户会话、CSRF 或宿主角色体系；这些由宿主负责，`PermissionProvider` 是组件的授权边界。

## 8. Java 查询契约

```java
public interface DictQueryService {
    List<DictItemView> listEffectiveItems(String dictCode);
    List<DictItemView> listEffectiveItems(String dictCode, String tenantId);
    Optional<DictItemView> findEffectiveItem(String dictCode, String itemCode);
    Optional<DictItemView> findEffectiveItem(
        String dictCode,
        String itemCode,
        String tenantId
    );
}
```

- 无租户参数的方法通过 `TenantProvider` 解析当前租户；
- 显式租户方法用于定时任务、消息消费和批处理，不调用 `TenantProvider`；
- 显式租户方法不接受保留值 `"0"`；
- 显式租户 API 只作为 Java API，不开放公共 HTTP 接口；
- 调用显式租户 API 的跨租户授权由宿主业务代码负责；
- 两类入口复用同一查询用例。

`DictItemView` 是不可变结果：

```java
public final class DictItemView {
    private final String code;
    private final String name;
    private final String description;
    private final int sortNo;
    private final DictItemSource source; // DEFAULT | TENANT
}
```

业务查询结果不暴露数据库 ID、`dictId`、`tenantId`、状态、删除标识和维护元数据。管理 API 使用独立 DTO。

### 8.1 查询语义

- 字典不存在或已删除：抛 `DICT_NOT_FOUND`；
- 字典停用：列表返回空列表，单项查询返回 `Optional.empty()`；
- 字典存在但没有有效项：列表返回空列表；
- 字典项不存在、停用、删除或不属于指定租户：返回 `Optional.empty()`；
- 有效项等于启用默认项加当前/显式租户的启用追加项；
- 按 `sortNo`、`code`、`id` 依次升序，保证顺序稳定。

## 9. 写入、并发和恢复

新增字典项时，应用服务在同一事务中：

1. 锁定所属字典记录；
2. 校验字典存在且未删除；
3. 校验租户存在、编码冲突和数量上限；
4. 执行领域规则；
5. 保存字典项和基础维护元数据；
6. 提交事务；
7. 事务提交后通知缓存端口失效。

锁定字典记录可序列化同一字典下互相冲突的默认项和租户项创建。基础数据库唯一约束作为最后防线。

恢复规则：

- 列表支持显式“包含已删除数据”筛选；
- 已删除记录只允许查看和恢复，不能编辑或启停；
- 恢复字典不自动恢复其字典项；
- 恢复字典项前所属字典必须已恢复；
- 永久占用的唯一键保证恢复时不会与新记录冲突。

## 10. 数据库和手工 SQL

第一版只有三张表：

```text
vin_dict
vin_dict_item
vin_dict_meta
```

`vin_dict` 和 `vin_dict_item` 使用 `BIGINT AUTO_INCREMENT` 主键。关键约束和索引：

```text
vin_dict:      UNIQUE (code)
vin_dict_item: UNIQUE (dict_id, tenant_id, code)
vin_dict_item: INDEX  (dict_id, tenant_id, status, deleted, sort_no)
vin_dict_item: INDEX  (tenant_id, deleted)
```

`vin_dict_meta` 保存唯一的 `schema_version`。Starter 启动时只读校验表存在性和 Schema 版本，不执行任何 DDL；表不存在或版本不兼容时启动失败，并提示所需 SQL 路径。

SQL 目录：

```text
sql/mysql
├── 1.0.0
│   └── 001-init.sql
└── upgrade
    ├── 1.0.0-to-1.1.0.sql
    └── 1.1.0-to-1.2.0.sql
```

规则：

- 初始化 SQL 创建三张表并写入 Schema 版本；
- 升级 SQL 执行前检查当前版本，完成后更新版本；
- 不使用 `CREATE TABLE IF NOT EXISTS` 掩盖结构不一致；
- 脚本注明 MySQL DDL 可能隐式提交；
- 只提供前向升级，不提供自动回滚；
- 宿主执行升级前负责备份；
- 示例数据 SQL 与产品 SQL 分开，绝不进入 Starter 自动流程。

## 11. 数据源和 MyBatis

- 宿主只有一个 `DataSource` 时自动使用；
- 多数据源且有唯一 `@Primary` 时默认使用主数据源；
- 多数据源且没有唯一主数据源时启动失败；
- 可用 `vincent.dict.data-source-bean-name`、`sql-session-factory-bean-name` 和 `transaction-manager-bean-name` 显式选择；
- 多套数据库基础设施并存且需要显式选择时，三个 Bean 名称必须一起配置；
- 配置的 Bean 不存在或类型错误时启动失败；
- 事务管理器和 `SqlSessionFactory` 必须与选中 DataSource 匹配；
- 组件不创建连接池，不读取独立数据库账号密码；
- Starter 不覆盖宿主 Bean，也不修改宿主全局 MyBatis 配置；
- Mapper 显式处理逻辑删除字段，不依赖宿主 MyBatis-Plus 全局逻辑删除配置。

## 12. Redis 缓存

核心 Starter 不传递任何 Redis 依赖。只有宿主额外引入 `vincent-dict-cache-redis-boot2-starter` 并启用配置后才装配缓存。

```yaml
vincent:
  dict:
    cache:
      enabled: true
      key-prefix: vin:dict
      ttl: 60s
```

- Redis Starter 复用宿主 `StringRedisTemplate`，不维护独立连接；
- 启用缓存但缺少 `StringRedisTemplate` 时启动失败；
- 字典或默认项变化时递增字典级缓存版本；
- 某租户项变化时递增该字典、该租户的租户级缓存版本；
- 缓存键包含字典编码、字典版本、租户版本和租户；
- 租户 ID 使用 URL-safe Base64 编码后进入 Key，不直接拼接原文；
- 缓存未命中后的写入必须比较读取前后的字典/租户版本，仅在版本未变化时写入，防止查询与失效并发时回填旧数据；
- Redis 正常时，事务提交后立即失效缓存；
- Redis 查询故障时降级到数据库并限频告警；
- Redis 失效失败不回滚已提交数据库事务；
- 故障期间最多允许返回 TTL 范围内的旧数据；
- 数据库是最终事实来源，第一版不承诺 Redis 故障期间强一致。

建议键格式：

```text
vin:dict:v1:{dictCode}:{dictVersion}:{tenantVersion}:{encodedTenantId}
vin:dict:gv:{dictCode}
vin:dict:tv:{dictCode}:{encodedTenantId}
```

## 13. 管理页面和 API

Vue 3 + TypeScript 前端作为独立源码模块构建，产物内嵌进核心 Boot 2 Starter。管理端不拆成独立 Starter。

管理端只在以下条件全部满足时装配：

- 宿主是 Servlet Web 应用；
- classpath 存在 Spring MVC；
- `vincent.dict.admin.enabled=true`；
- `OperatorProvider` 和 `PermissionProvider` 存在。

Spring MVC 依赖不得强制传递给非 Web 宿主。非 Web 项目只获得 Java 查询能力；维护由宿主依据组件 SQL 规范手工完成。

页面包括：

- 字典列表：编码、名称、状态、是否删除筛选，新增、编辑、启停、删除和恢复；
- 字典详情：基本信息、默认项、租户项；
- 默认项和租户项：分页、排序、状态、是否删除筛选和维护；
- 租户项通过 `TenantDirectory` 提供的可搜索选择器选择租户；
- 编码冲突、数量上限和非空字典删除即时提示。

分页默认 20 条，单页最大 100 条。默认页面路径 `/dict-admin`，管理 API 前缀 `/vincent/dict/admin/api/v1`。页面只调用组件管理 API，不访问宿主 Mapper。

## 14. 配置

```yaml
vincent:
  dict:
    enabled: true
    data-source-bean-name:
    sql-session-factory-bean-name:
    transaction-manager-bean-name:
    admin:
      enabled: false
      base-path: /dict-admin
      api-path: /vincent/dict/admin/api/v1
    limits:
      default-items-per-dict: 1000
      tenant-items-per-dict: 1000
      max-effective-items: 2000
      default-page-size: 20
      max-page-size: 100
```

所有限制必须是正整数，并满足：

```text
max-effective-items >= default-items-per-dict + tenant-items-per-dict
max-page-size >= default-page-size
```

不合法配置在启动时失败，不在运行时静默修正。

## 15. 错误处理

稳定错误码至少包括：

```text
INVALID_ARGUMENT
DICT_NOT_FOUND
DICT_CODE_CONFLICT
DICT_NOT_EMPTY
DICT_ITEM_NOT_FOUND
DICT_ITEM_CODE_CONFLICT
DICT_ITEM_LIMIT_EXCEEDED
TENANT_CONTEXT_MISSING
TENANT_NOT_FOUND
DEFAULT_ITEM_PROTECTED
PERMISSION_DENIED
OPTIMISTIC_LOCK_CONFLICT
SCHEMA_MISSING
SCHEMA_VERSION_MISMATCH
CONFIGURATION_INVALID
```

领域和应用层不依赖宿主响应类型。Java API 暴露组件异常；管理 API 使用组件响应结构并提供异常映射扩展点。数据库、领域校验或维护元数据生成失败时写事务整体回滚。

## 16. 兼容基线

```text
Java                 8
Spring Boot          2.2.6.RELEASE
MyBatis-Plus         3.3.2
MySQL                5.7+
Spring Data Redis    Spring Boot 2.2.6 管理版本
前端                 Vue 3 + TypeScript
```

首版只实现 Boot 2 Starter。领域层和应用层保持框架无关，为以后新增 Boot 3 Starter保留边界，但不提前实现未使用适配器。

## 17. 测试策略

- 领域测试：纯 JUnit 覆盖编码、状态、不变量、恢复、删除和数量限制；
- 应用测试：Fake 端口覆盖租户解析、显式租户查询、权限范围、租户目录、事务后缓存失效和异常语义；
- MySQL 集成测试：DDL、Schema 版本、唯一约束、逻辑删除、乐观锁、并发锁、分页和事务回滚；
- Redis 集成测试：可选装配、命中、未命中、TTL、版本失效、租户失效和故障降级；
- Starter 测试：单/多数据源选择、条件 Bean、配置校验、非 Web 宿主和宿主 Bean 共存；
- Web 测试：权限和目标租户、租户目录、输入校验、恢复、错误映射和分页；
- UI 测试：字典、默认项、租户项、恢复、权限展示、冲突和上限提示；
- 兼容测试：最小 Boot 2.2.6 示例宿主端到端验证；
- 规模测试：每字典 1,000 个默认项和每租户 1,000 个追加项，验证无 N+1 查询和稳定排序。

## 18. 第一版验收标准

1. Maven Reactor 可以构建、测试和发布父 POM、BOM、核心 Starter 和 Redis Starter；
2. 业务系统可以导入 BOM 并只引入核心 Starter；
3. 手工初始化 SQL 创建三张表和 Schema 版本，Starter 只读校验成功；
4. 缺表或 Schema 版本错误时启动失败并给出明确升级提示；
5. 宿主可以适配当前租户、显式租户、操作人、范围权限和租户目录；
6. Web 宿主可以启用内嵌页面完成字典、默认项、租户项、删除和恢复；
7. 非 Web 宿主不会启动 Web 容器，仍可通过 `DictQueryService` 查询；
8. 默认项与租户追加项规则、编码永久占用、数量限制和非空删除规则全部通过自动化测试；
9. `DictQueryService` 按已定义语义返回不可变、稳定排序的结果；
10. 不引入 Redis Starter 时 classpath 不增加 Redis 依赖；引入并启用后缓存、失效和故障降级正确；
11. 全部领域、应用、MySQL、Starter、Web、UI、Redis 和兼容测试通过；
12. README 提供核心接入、Web 管理、Redis、手工 SQL、升级和多数据源示例；
13. 不修改任何现有业务系统，不随发布物附带业务字典数据。

## 19. 已拒绝方案

- 中心化字典服务：不符合“引入依赖、创建本地表即可使用”的交付目标；
- 单体模块：领域、持久化、Web 和缓存耦合，不利于测试和未来 Boot 3 适配；
- 全面插件化：首版测试矩阵和配置组合过大；
- 默认项覆盖模型：会破坏平台基线语义；
- Caffeine：多实例失效不可协调，用户选择仅在显式配置 Redis 时缓存；
- 自动 Flyway：宿主明确要求只执行手工 SQL；
- 完整操作审计：延后到未来版本；第一版只保留创建人和最后修改人；
- 独立管理 Starter：宿主选择单 Starter，非 Web 环境通过条件装配禁用页面；
- 同期改造现有业务系统：与通用组件首版解耦，另立接入任务。
