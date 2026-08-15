# Vincent Tools 工具包路线图设计

> **需求单一入口**（已交付 / 可选 / 未启动 / 不做）：[docs/REQUIREMENTS.md](../../REQUIREMENTS.md)

## 1. 背景与目标

`vincent-tools` 是可复用的 Java 工具仓库。业务系统开发时导入 `vincent-tools-bom`，按需引入 Starter 或纯 Java 库，即可获得通用能力，避免每个项目重复实现字典、审计、编号、导出等基础设施。

首个工具 `vincent-dict` 已完成并验证了交付模式。本文档定义：

- 工具包的两层分类与完整清单；
- 从 `vincent-dict` 抽取的公共模块；
- 下一个嵌入式组件 `vincent-audit` 的设计；
- 分阶段实施顺序。

目标业务场景：**多租户 SaaS**。工具包不拥有用户、租户、认证和 RBAC 数据，通过宿主端口接入；工具只负责各自领域的数据存储、Java API、可选管理页和 Starter 自动装配。

## 2. 兼容基线

与 `vincent-dict` 保持一致：

```text
Java                 8
Spring Boot          2.2.6.RELEASE
MyBatis-Plus         3.3.2
MySQL                5.7+
前端（内嵌管理页）    Vue 3 + TypeScript
```

首版只实现 Boot 2 Starter。领域层和应用层保持框架无关，为后续 Boot 3 适配保留边界。

## 3. 制品与接入方式

维持现有三种制品，禁止混用：

| 制品 | 坐标 | 用途 |
| --- | --- | --- |
| 根父 POM | `com.vincent.tools:vincent-tools` | 仓库内部父工程与源码聚合，业务系统不得依赖 |
| BOM | `com.vincent.tools:vincent-tools-bom` | 业务系统在 `dependencyManagement` 中 `import`，对齐版本 |
| 功能 Starter / 库 | 例如 `vincent-dict-boot2-starter`、`vincent-id-core` | 业务系统按需引入的真正功能依赖 |

## 4. 两层工具分类

### 4.1 纯 Java 库

**特征**：无数据库表、无内嵌 UI、弱 Spring 依赖或无 Spring 依赖；引入即可调用 API。

| 优先级 | 模块 | 职责 |
| --- | --- | --- |
| P0（Phase 0） | `vincent-host-ports` | 宿主扩展端口：`TenantProvider`、`OperatorProvider`、`PermissionProvider`、`VincentPermission` |
| P0（Phase 0） | `vincent-common-web` | 统一 API 响应体 `ApiResponse`、异常映射基类、SPA HTML 注入工具 |
| P0（Phase 0） | `vincent-common-core` | `PageResult` 分页模型、编码校验、Schema 版本只读校验、`VincentInfrastructureResolver` |
| P0（Phase 0） | `vincent-common-cache-redis` | 版本号失效 Redis 缓存、故障降级、限流日志（从 dict Redis Starter 抽象） |
| P1 | `vincent-id-core` | 分布式 ID 与业务编号生成（雪花、号段、带前缀格式化） | ✅ 已完成 Phase 3（PR #9） |
| P1 | `vincent-export-core` | 基于 **EasyExcel** 的 Excel 流式导入导出（BOM 锁定 Java 8 兼容版本） | ✅ 已完成 Phase 3（PR #9） |

### 4.2 嵌入式组件

**特征**：独立 Starter、本地 MySQL 表、版本化手工 SQL、Schema 只读校验、可选内嵌管理页；镜像 `vincent-dict` 的 DDD 分层。

| 优先级 | 模块 | 职责 | 状态 |
| --- | --- | --- | --- |
| — | `vincent-dict` | 字典与字典项查询、默认项与租户追加 | ✅ 已完成（PR #1） |
| P0 | `vincent-audit` | 操作审计记录与检索 | ✅ 已完成 Phase 1–2（PR #4–#8） |
| P1 | `vincent-region` | 省市区树查询与只读管理 API | ✅ 已完成 Phase 4（PR #10） |
| P2 | `vincent-file` | 附件元数据与存储端口（本地/OSS 由宿主实现） | 🔮 已概要规划，未启动 |

### 4.3 明确不做

以下能力留给业务系统或后续独立评估，不在本路线图 Phase 0–4 范围内：

- 用户、租户、RBAC 数据维护；
- 认证、会话、CSRF；
- 工作流与审批；
- 消息通知（邮件、短信、Webhook）；
- 独立部署的远程服务中心；
- Spring Boot 3 Starter（仅保留边界，不提前实现）。

## 5. 目标仓库结构

```text
vincent-tools
├── pom.xml
├── vincent-tools-bom
├── vincent-common/
│   ├── vincent-host-ports
│   ├── vincent-common-web
│   ├── vincent-common-core
│   ├── vincent-id-core
│   ├── vincent-export-core
│   └── vincent-common-cache-redis
├── vincent-dict/                    # 已有，Phase 0 重构依赖 common
├── vincent-audit/                   # Phase 1–2
│   ├── vincent-audit-domain
│   ├── vincent-audit-application
│   ├── vincent-audit-infra-mybatis
│   ├── vincent-audit-web
│   ├── vincent-audit-admin-ui       # 可选，与 dict 同样条件装配
│   ├── vincent-audit-boot2-starter
│   ├── vincent-audit-aop-boot2-starter   # 可选 @Audited 糖层
│   └── vincent-audit-example-boot2       # 仓库内验收，不进 BOM
└── vincent-region/                  # Phase 4，结构同 audit
```

`vincent-tools-bom` 随每个新模块扩展 `dependencyManagement` 条目。示例宿主模块（如 `vincent-audit-example-boot2`）只在仓库内存在，不进入公开 BOM。

## 6. 从 vincent-dict 抽取的公共能力

Phase 0 **一次性**迁出以下全部 6 项（与 §5 仓库结构中的 4 个 common 模块对应），从 dict 零行为变化地重构依赖，避免后续工具复制粘贴：

| 现状位置 | 目标模块 | 说明 |
| --- | --- | --- |
| `dict.application.TenantProvider` 等 | `vincent-host-ports` | dict、audit、region 共用同一端口定义 |
| `dict.web.ApiResponse` | `vincent-common-web` | 泛型响应体，各组件 web 层复用 |
| `dict.web.DictWebExceptionHandler` 模式 | `vincent-common-web` | 抽象基类 + 各组件 ErrorCode 枚举 |
| `dict.web.DictAdminSpaHtml` | `vincent-common-web` | 通用 SPA `<base href>` 与 `window.__CONFIG__` 注入 |
| `dict.application.admin.PageResult` | `vincent-common-core` | 泛型分页结果，dict/audit 共用 |
| `dict.boot2.DictSchemaValidator` 模式 | `vincent-common-core` | 可配置的表存在性与 meta 版本校验 |
| `dict.boot2.DictInfrastructureResolver` | `vincent-common-core` | 泛化为 `VincentInfrastructureResolver`，供 dict/audit 等同库 DataSource 解析 |
| dict Redis 缓存适配器模式 | `vincent-common-cache-redis` | 版本号失效、Noop 回退、限流 warn 日志 |

抽取后 `vincent-dict` 依赖上述 common 模块，对外 Starter 坐标与行为不变。

Java 包名遵循现有惯例：`com.vincent.tools.host.*`（ports）、`com.vincent.tools.common.*`（web/core/cache）。

### 6.1 共用权限端口（VincentPermission）

`PermissionProvider` 抽到 `vincent-host-ports`，签名接受标记接口而非 dict 专用枚举：

```java
public interface VincentPermission {
    /** 稳定权限码；第一版约定返回 enum.name()，如 DICT_VIEW、AUDIT_VIEW */
    String code();
}

public interface PermissionProvider {
    boolean hasPermission(VincentPermission permission, Optional<String> targetTenantId);
}
```

各工具在**自身模块**定义权限枚举并实现 `VincentPermission`：

- dict：`DictAdminPermission implements VincentPermission`
- audit：`AuditPermission implements VincentPermission`（首版权限码 `AUDIT_VIEW`）

调用处保持 enum 类型安全；宿主只实现一个 `PermissionProvider` Bean，按 `permission.code()` 查 RBAC。`code()` 第一版使用 `name()`，与 dict 现有权限字符串兼容，宿主 RBAC **零迁移**。

已拒绝：字符串权限码端口（编译期无检查）、每工具各自定义 `PermissionProvider`（宿主重复实现）。

## 7. vincent-audit 设计

### 7.1 设计原则

操作审计与业务相关，但可拆分职责：

- **工具负责**：审计记录的存储、索引、分页查询、可选管理页、Starter 装配；
- **业务负责**：哪些操作要记、action/resourceType 命名、before/after JSON 内容与序列化时机。

**保留策略（第一版）**：永久保留，不提供自动清理或 TTL 定时任务；运维需清理时手工 SQL。自动保留策略留待后续版本。

**数据源**：`vin_audit_*` 与 `vin_dict_*` 默认在同一 MySQL 库，共用宿主 `@Primary` DataSource；配置镜像 dict（`vincent.audit.data-source-bean-name`、`sql-session-factory-bean-name`、`transaction-manager-bean-name` 三件套同配同缺）。多数据源场景由 `VincentInfrastructureResolver` 解析。

**启用开关**：`vincent.audit.enabled` 默认 `true`（与 dict 一致）；`vincent.audit.admin.enabled` 默认 `false`。

写入采用**混合方案**：

1. **核心路径（方案 1）**：显式调用 `AuditService.record()`，适用于复杂业务、多表操作、批处理、消息消费；
2. **可选糖层（方案 2）**：`@Audited` 注解 + AOP，内部仍调用 `AuditService.record()`，适用于简单 CRUD。

两种场景比例相当，因此 core 与 AOP 分模块交付：Phase 1 只做 core，Phase 2 加 AOP Starter。

### 7.2 审计记录模型

表名前缀 `vin_audit_`，首版单表 `vin_audit_log`：

```text
id              BIGINT AUTO_INCREMENT PRIMARY KEY
tenant_id       VARCHAR(64) NOT NULL
operator_id     VARCHAR(64) NOT NULL
action          VARCHAR(64) NOT NULL
resource_type   VARCHAR(64) NOT NULL
resource_id     VARCHAR(128) NOT NULL
before_json     TEXT
after_json      TEXT
client_ip       VARCHAR(64)
user_agent      VARCHAR(256)
trace_id        VARCHAR(128)
created_at      DATETIME(3) NOT NULL
```

规则：

- `action`、`resource_type`、`resource_id` 由业务传入，工具不做枚举校验；
- `before_json`、`after_json` 为 opaque JSON，工具只存只查，不解析字段语义；列类型 `TEXT`（MySQL 上限约 64KB），应用层不做 byte 上限校验；超出时写入失败并映射为 `INVALID_ARGUMENT`；
- **宿主 JSON 写入契约**：只记变更字段或业务摘要，不塞完整实体图；批量/导出类操作允许 `before_json`/`after_json` 为 null；大对象只记 ID/哈希，不记内容本身；
- 索引：`(tenant_id, created_at DESC)`、`(tenant_id, resource_type, resource_id)`；
- Schema 版本写入 `vin_audit_meta`（与 dict 相同 meta 表模式），当前版本 `1`；
- Vincent Audit never runs DDL at application startup.

### 7.3 Java API

```java
public interface AuditService {
    void record(AuditRecordCommand command);
    PageResult<AuditRecordView> search(AuditSearchQuery query);
}
```

`AuditRecordCommand` 字段：

```java
String action;
String resourceType;
String resourceId;
Optional<String> targetTenantId;   // 显式租户；无参时走 TenantProvider
String beforeJson;                 // 可 null
String afterJson;                  // 可 null
```

- 无 `targetTenantId` 且已注册 `TenantProvider` 时，从 Provider 解析 tenant；缺少租户上下文时抛 `TENANT_CONTEXT_MISSING`；
- 显式 `targetTenantId` 用于定时任务、消息消费，不调用 `TenantProvider`；
- `operator_id` 始终来自 `OperatorProvider`，不由 command 传入，防止伪造；定时任务/消息消费等无登录用户场景，由宿主在任务上下文中设置 `OperatorProvider` 返回系统标识（如 `SYSTEM`），audit 不做 fallback；
- `client_ip`、`user_agent`、`trace_id` 来自 `AuditContextProvider`（可选 Bean，缺省为空）。

`AuditSearchQuery` 中 `tenantId` **可选**：

- 有值：只查该租户；校验 `PermissionProvider.hasPermission(AuditPermission.AUDIT_VIEW, Optional.of(tenantId))`；
- 无值：查全部租户；校验 `PermissionProvider.hasPermission(AuditPermission.AUDIT_VIEW, Optional.empty())`；是否允许跨租户由宿主 RBAC 决定。

查询 API 只读，支持按 tenant、operator、action、resourceType、resourceId、时间范围分页；不暴露 HTTP 公共查询端点给匿名调用，管理页 API 受 `PermissionProvider` 保护。

**写入失败策略**：`vincent.audit.fail-fast` 默认 `true` — `record()` 失败抛异常；`false` 时 catch 后打 error 日志，不抛。宿主在 never-fail 场景显式设为 `false`。

**事务语义**：`record()` 加入当前 Spring 事务（若存在）；与业务同 commit/rollback。无事务时 auto-commit。AOP `@Audited(afterCommit = true)` 在业务事务提交后再调用 `record()`。

**operator 校验**：镜像 dict `requireOperator()` — `OperatorProvider.currentOperatorId()` 必须非 null、非空、trim 后非空、最长 64 字符；否则抛 `INVALID_ARGUMENT`。

**分页限制**：镜像 dict — `vincent.audit.limits.default-page-size=20`，`max-page-size=100`；Starter 启动时校验 `max-page-size >= default-page-size`；超出抛 `INVALID_ARGUMENT`。

### 7.4 宿主端口

| 端口 | 模块 | 必需性 |
| --- | --- | --- |
| `TenantProvider` | `vincent-host-ports` | 可选；未注册时仅支持显式 tenant 的 record/search |
| `OperatorProvider` | `vincent-host-ports` | record 必需 |
| `PermissionProvider` | `vincent-host-ports` | 启用管理页时必需 |
| `AuditContextProvider` | `vincent-audit-application` | 可选 |
| `AuditPayloadExtractor` | `vincent-audit-aop` | 仅 AOP Starter 需要 |

`AuditPayloadExtractor` 签名：

```java
public interface AuditPayloadExtractor {
    boolean supports(String resourceType);
    AuditPayload extract(Object[] args, Object result, Method method);
}
```

业务可为每种 `resourceType` 注册 Extractor；AOP 在方法成功返回后调用，默认 `afterCommit = false`，复杂场景可配 `afterCommit = true` 绑定事务提交后写入。

### 7.5 管理端

条件装配，规则镜像 dict：

- `vincent.audit.admin.enabled=true`；
- 默认 `base-path: /audit-admin`，`api-path: /vincent/audit/admin/api/v1`（第一版请保持默认，自定义路径需与前端静态资源前缀一致）；
- 宿主为 Servlet Web 应用；
- 存在 `OperatorProvider` 与 `PermissionProvider`；
- 权限码：`AUDIT_VIEW`（只读检索）。

不提供审计记录的创建、修改、删除管理 API；审计写入只通过 `AuditService.record()` 或 `@Audited`。

### 7.6 与 vincent-dict 协作

- dict 不硬依赖 audit；audit Starter 存在时，宿主或 dict 应用层可自行编排审计调用；
- 第一版不在 dict 内部自动埋点；后续若 dict 管理操作需要审计，在 `DefaultDictAdminService` 中显式调用 `AuditService`（可选依赖注入）；
- 共用 `vincent-host-ports`，宿主只需实现一次 Provider。

### 7.7 异常码

```text
INVALID_ARGUMENT
TENANT_CONTEXT_MISSING
PERMISSION_DENIED
SCHEMA_MISSING
SCHEMA_VERSION_MISMATCH
CONFIGURATION_INVALID
```

## 8. 其他待做组件概要

### 8.1 vincent-region（Phase 4）

- 中国省市区**三级树**，Java 查询 API + 可选**只读**管理页（浏览/搜索，不提供增删改）；
- 复用 dict 分层与 Schema 校验模式；数据变更靠重新执行 SQL，不在应用内维护；
- 首版不做国际地区、不做业务自定义层级、不做模糊搜索。

**数据交付**（镜像 dict 手工 SQL 模式）：

- `sql/mysql/1.0.0/001-init.sql` — 表结构 + `vin_region_meta` Schema 版本；
- `sql/mysql/1.0.0/001-data.sql` — 国标省市区静态参考数据；
- 两份脚本均**手工执行**，不进 Starter JAR；不需要地区功能的宿主可只 init 不导 data。

**Java API（第一版最小集）**：

```java
public interface RegionQueryService {
    Optional<RegionView> findByCode(String code);
    List<RegionView> listChildren(String parentCode);  // parentCode 为空或 "0" 时返回省级
}
```

`RegionView` 字段：`code`、`name`、`level`（1=省/2=市/3=区）、`parentCode`；不暴露数据库 ID。

**管理端（可选，只读）**：条件装配；权限码 `REGION_VIEW`；无写 API。

### 8.2 vincent-id-core（Phase 3）

- **纯 Java 库**，无表、无 Spring 自动装配；
- 第一版提供：
  1. **雪花 ID** — `SnowflakeIdGenerator(workerId, datacenterId)` 构造；宿主自行传参（0–31），工具包不提供 YAML 或 `WorkerIdProvider`；
  2. **前缀格式化** — `BusinessNumberFormatter.format(template, value)`，如 `ORD-{date}-{seq}`；
  3. **号段端口** — 宿主实现 `SegmentAllocator`，持久化由宿主 DB/Redis 负责。

```java
public interface SegmentAllocator {
    /** 返回下一个序号（单调递增，宿主保证多实例安全） */
    long nextSegment(String bizKey);
}
```

- 第一版**不做**内置号段持久化、不做 `vincent-sequence` 嵌入式组件；
- 典型用法：技术主键用雪花；业务流水号用 `SegmentAllocator` + `BusinessNumberFormatter` 组合。

### 8.3 vincent-export-core（Phase 3）

- 纯库，基于 **EasyExcel**（BOM 锁定 Java 8 / Boot 2.2.6 兼容版本；不额外暴露原生 POI API）；
- 提供流式读写，不绑定 Web；
- 业务系统自行定义 DTO 与列映射。

### 8.4 vincent-file（Phase 2 之后）

- 附件元数据表 + `FileStorage` 端口（本地磁盘、OSS 由宿主实现）；
- 不做图片处理、病毒扫描；
- 管理页可选。

## 9. 业务系统典型接入

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.vincent.tools</groupId>
            <artifactId>vincent-tools-bom</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.vincent.tools</groupId>
        <artifactId>vincent-dict-boot2-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.vincent.tools</groupId>
        <artifactId>vincent-audit-boot2-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.vincent.tools</groupId>
        <artifactId>vincent-audit-aop-boot2-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.vincent.tools</groupId>
        <artifactId>vincent-id-core</artifactId>
    </dependency>
</dependencies>
```

宿主一次性适配：

```java
@Bean TenantProvider tenantProvider() { ... }
@Bean OperatorProvider operatorProvider() { ... }
@Bean PermissionProvider permissionProvider() { ... }
```

## 10. 分阶段实施

| 阶段 | 内容 | 验收 | 实施计划 |
| --- | --- | --- | --- |
| Phase 0 | 一次性抽取 §6 全部 6 项至 4 个 common 模块；dict 重构依赖 common，零行为变化 | `mvn -P '!jdk-17' test` 全量通过；BOM 新增 common 坐标；dict 对外 Starter 行为不变 | [Phase 0 计划](plans/2026-08-15-vincent-tools-phase0-common.md) |
| Phase 1 | `vincent-audit` core + `vincent-audit-example-boot2` | 见 §10.1 checklist | [Phase 1 计划](plans/2026-08-15-vincent-audit-phase1-core.md) |
| Phase 2 | `vincent-audit-aop-boot2-starter` | `@Audited` 在简单 CRUD 场景可用；复杂场景仍走显式 API | [Phase 2 计划](plans/2026-08-15-vincent-audit-phase2-aop.md) |
| Phase 3 | `vincent-id-core`、`vincent-export-core`（EasyExcel） | 纯库单测；示例用法写入 README | [Phase 3 计划](plans/2026-08-15-vincent-common-phase3-id-export.md) |
| Phase 4 | `vincent-region` | 三级树查询与管理；复用 common 模块 | [Phase 4 计划](plans/2026-08-15-vincent-region-phase4-query.md) |

每个 Phase 独立可发布，不阻塞已有 dict 消费者。

### 10.1 Phase 1 验收 checklist

1. `vin_audit_meta` + `vin_audit_log` 手工 SQL 可初始化，Starter 只读 Schema 校验通过；
2. `AuditService.record()` 显式写入成功，`search()` 按 tenant/operator/action/resource/时间分页返回；
3. `tenantId` 可选 search + `AUDIT_VIEW` 权限校验（含跨租户空 Optional 场景）；
4. `fail-fast=true` 时写入失败抛异常；`fail-fast=false` 时吞掉并打日志；
5. `record()` 与业务在同一 `@Transactional` 内同 rollback；
6. 管理页默认 `/audit-admin` + API `/vincent/audit/admin/api/v1` 可检索（需 `admin.enabled=true`）；
7. `vincent-audit-example-boot2` 端到端 IT 通过；
8. `mvn -P '!jdk-17' test` 在 audit reactor 全绿。

## 11. 测试策略

- **common 模块**：纯单元测试，不启 Spring；
- **嵌入式组件**：镜像 dict 测试矩阵（domain / application fake 端口 / MySQL IT / Starter 条件装配 / Web 权限 / UI 冒烟）；
- **audit AOP**：额外覆盖事务前后时机、`AuditPayloadExtractor` 注册与 `afterCommit` 配置；
- **Phase 0 重构**：dict 回归测试零失败作为 gate。

## 12. 第一版路线图验收标准

1. 设计文档 committed，BOM 与仓库结构文档化；
2. Phase 0 完成后 dict 行为与测试无回归；
3. Phase 1 完成后业务系统可仅引入 audit Starter 并手工 SQL 接入；
4. 审计写入不依赖 AOP；AOP 为可选增强；
5. 所有嵌入式组件遵循「手工 SQL + 启动只读校验 + 宿主端口」契约；
6. 业务系统不因引入工具而必须实现用户/租户/RBAC 维护。

## 13. 已拒绝方案

- **审计只做 AOP、不做显式 API**：复杂场景和批处理无法可靠覆盖；
- **审计工具内置 action/resource 枚举**：与业务耦合，违背工具包定位；
- **在 vincent-tools 内做完整 IAM**：超出工具包边界，与 dict 设计原则冲突；
- **每个工具各自定义 TenantProvider**：导致宿主多次适配，Phase 0 抽取统一端口；
- **Phase 1 与 Phase 0 并行**：common 未稳定前开 audit 会复制 dict 内联代码，增加返工；
- **Phase 0 最小抽取（仅 host-ports + common-web）**：audit Schema 校验与 Redis 模式会再次复制；
- **PermissionProvider 字符串权限码**：编译期无检查，放弃；
- **PermissionProvider 字符串命名空间前缀（`dict:view`）**：与 dict 现有 RBAC 映射不兼容，第一版零迁移优先；
- **审计第一版自动 TTL 清理**：合规策略因客户而异，第一版不做；
- **JSON 字段 MEDIUMTEXT / 应用层 byte 上限**：64KB 对变更摘要足够，第一版不增加复杂度；
- **audit 内置 operator fallback**：批处理上下文由宿主负责，工具不猜测；
- **search 强制 tenantId / 仅 TenantProvider 范围**：不支持平台管理员跨租户检索场景；
- **export-core 双库（EasyExcel + 原生 POI API）**：第一版 YAGNI，只暴露 EasyExcel；
- **audit 无独立 example-boot2**：与 dict 验收模式不一致，不利于端到端验证；
- **id-core 内置号段持久化 / 内存号段**：第一版纯库无表，号段由 SegmentAllocator 端口交给宿主；
- **id-core Spring 自动装配 workerId**：纯库分类，宿主构造函数传参；
- **region 数据打进 Starter 自动导入**：违背手工 SQL 原则；
- **region 第一版可编辑管理端**：国标参考数据第一版只读，变更靠 SQL。

## 14. Ambiguity Report

### 第一轮（2026-08-15）

```
Ambiguity Report (Round 1):
  Goals:        0.25  ⚠ Phase 3+ 组件仍仅为概要
  Acceptance:   0.25  ⚠ Phase 1+ 缺逐项 checklist
  Boundaries:   0.0   ✓ clear
  Alternatives: 0.25  ✓ 已拒绝方案已扩充
  Assumptions:  0.25  ⚠ export 库选型待定
  Aggregate:    0.20  ✓ at threshold (0.2 spec)
```

Resolved: VincentPermission、Phase 0 全量抽取、保留策略、DataSource、JSON 契约、批处理 operator、search 范围、权限码格式。

### 第二轮（2026-08-15）

```
Ambiguity Report (Round 2):
  Goals:        0.25  ⚠ id-core / region 仍为概要（不阻塞 Phase 0–1）
  Acceptance:   0.0   ✓ Phase 1 checklist 已补 §10.1
  Boundaries:   0.0   ✓ clear
  Alternatives: 0.25  ✓ 已拒绝方案已扩充
  Assumptions:  0.0   ✓ EasyExcel、PageResult、example-boot2 已明确
  ──────────────────────────────
  Aggregate:    0.10  ✓ below threshold (0.2 spec)

Resolved in Round 2:
  - 写入失败 → fail-fast 可配置，默认 true（方案 C）
  - 事务语义 → 加入当前 Spring 事务（方案 A）
  - PageResult → Phase 0 抽入 common-core（方案 A）
  - 管理端路径 → /audit-admin + /vincent/audit/admin/api/v1（方案 A）
  - export-core → EasyExcel only（方案 A）
  - 示例宿主 → vincent-audit-example-boot2（方案 A）
  - 分页限制 → default=20, max=100，镜像 dict（方案 A）
```

**结论**：Phase 0–1 已具备零上下文可执行性；Phase 3+ 概要粒度可接受，待进入对应 Phase 时再 grill。

### 第三轮（2026-08-15）

```
Ambiguity Report (Round 3):
  Goals:        0.0   ✓ id-core / region 第一版边界已明确
  Acceptance:   0.25  ⚠ Phase 3/4 缺逐项 checklist（进入 Phase 前再补）
  Boundaries:   0.0   ✓ clear
  Alternatives: 0.25  ✓ 已拒绝方案已扩充
  Assumptions:  0.0   ✓ 雪花构造、region 数据交付、API 形状已明确
  ──────────────────────────────
  Aggregate:    0.10  ✓ below threshold (0.2 spec)

Resolved in Round 3:
  - id-core → 雪花 + 前缀格式化 + SegmentAllocator 端口（方案 B）
  - region 数据 → init.sql + data.sql 手工执行（方案 A）
  - region 管理端 → 纯只读浏览，无 CRUD（方案 A）
  - 雪花 workerId → 纯 Java 构造，宿主传参（方案 C）
  - region API → findByCode + listChildren 最小集（方案 A）
```

**结论**：路线图 Phase 0–4 全部组件第一版边界已闭合；Phase 0–4 **已于 2026-08-15 全部交付**（见 [需求清单](../../REQUIREMENTS.md)）。后续可选增强与 `vincent-file` 待单独立项。

## 15. 交付状态（2026-08-15 更新）

| Phase | 状态 | PR |
| --- | --- | --- |
| Phase 0 common 抽取 | ✅ | #3 |
| Phase 1 audit core | ✅ | #4–#7 |
| Phase 2 audit AOP | ✅ | #8 |
| Phase 3 id / export | ✅ | #9 |
| Phase 4 region | ✅ | #10 |
| 计划文档 Phase 2–4 | ✅ | #11 |
| 接入/架构/验证文档 | ✅ | #12 |

**需求单一入口**：[docs/REQUIREMENTS.md](../../REQUIREMENTS.md)
