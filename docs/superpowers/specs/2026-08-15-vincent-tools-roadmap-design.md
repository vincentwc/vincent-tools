# Vincent Tools 工具包路线图设计

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
| P0 | `vincent-host-ports` | 宿主扩展端口：`TenantProvider`、`OperatorProvider`、`PermissionProvider` 及通用权限枚举基类 |
| P0 | `vincent-common-web` | 统一 API 响应体 `ApiResponse`、异常映射基类、SPA HTML 注入工具 |
| P1 | `vincent-id-core` | 分布式 ID 与业务编号生成（雪花、号段、带前缀格式化） |
| P1 | `vincent-export-core` | Excel/CSV 批量导入导出 |
| P2 | `vincent-common-core` | 分页模型、编码校验、Schema 版本只读校验框架 |
| P2 | `vincent-common-cache-redis` | 版本号失效 Redis 缓存、故障降级、限流日志（从 dict Redis Starter 抽象） |

### 4.2 嵌入式组件

**特征**：独立 Starter、本地 MySQL 表、版本化手工 SQL、Schema 只读校验、可选内嵌管理页；镜像 `vincent-dict` 的 DDD 分层。

| 优先级 | 模块 | 职责 | 状态 |
| --- | --- | --- | --- |
| — | `vincent-dict` | 字典与字典项查询、默认项与租户追加 | 已完成 |
| P0 | `vincent-audit` | 操作审计记录与检索 | 下一个 |
| P1 | `vincent-region` | 省市区树查询与管理 | 待做 |
| P2 | `vincent-file` | 附件元数据与存储端口（本地/OSS 由宿主实现） | 待做 |

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
│   └── vincent-audit-aop-boot2-starter   # 可选 @Audited 糖层
└── vincent-region/                  # Phase 4，结构同 audit
```

`vincent-tools-bom` 随每个新模块扩展 `dependencyManagement` 条目。示例宿主模块（如 `vincent-audit-example-boot2`）只在仓库内存在，不进入公开 BOM。

## 6. 从 vincent-dict 抽取的公共能力

Phase 0 从 dict 零行为变化地迁出以下实现，避免后续工具复制粘贴：

| 现状位置 | 目标模块 | 说明 |
| --- | --- | --- |
| `dict.application.TenantProvider` 等 | `vincent-host-ports` | dict、audit、region 共用同一端口定义 |
| `dict.web.ApiResponse` | `vincent-common-web` | 泛型响应体，各组件 web 层复用 |
| `dict.web.DictWebExceptionHandler` 模式 | `vincent-common-web` | 抽象基类 + 各组件 ErrorCode 枚举 |
| `dict.web.DictAdminSpaHtml` | `vincent-common-web` | 通用 SPA `<base href>` 与 `window.__CONFIG__` 注入 |
| `dict.boot2.DictSchemaValidator` 模式 | `vincent-common-core` | 可配置的表存在性与 meta 版本校验 |
| dict Redis 缓存适配器模式 | `vincent-common-cache-redis` | 版本号失效、Noop 回退、限流 warn 日志 |

抽取后 `vincent-dict` 依赖上述 common 模块，对外 Starter 坐标与行为不变。

## 7. vincent-audit 设计

### 7.1 设计原则

操作审计与业务相关，但可拆分职责：

- **工具负责**：审计记录的存储、索引、分页查询、保留策略、可选管理页、Starter 装配；
- **业务负责**：哪些操作要记、action/resourceType 命名、before/after JSON 内容与序列化时机。

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
- `before_json`、`after_json` 为 opaque JSON，工具只存只查，不解析字段语义；
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
- `operator_id` 始终来自 `OperatorProvider`，不由 command 传入，防止伪造；
- `client_ip`、`user_agent`、`trace_id` 来自 `AuditContextProvider`（可选 Bean，缺省为空）。

查询 API 只读，支持按 tenant、operator、action、resourceType、resourceId、时间范围分页；不暴露 HTTP 公共查询端点给匿名调用，管理页 API 受 `PermissionProvider` 保护。

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

- 省市区三级树，只读查询 + 可选管理页；
- 复用 dict 分层与 Schema 校验模式；
- 首版不做国际地区、不做业务自定义层级。

### 8.2 vincent-id-core（Phase 3）

- 纯库，无表；
- 提供雪花 ID、号段模式、带前缀格式化（如 `ORD-{date}-{seq}`）；
- 号段持久化若需可视化管理，再评估 `vincent-sequence` 嵌入式组件（P3 待定）。

### 8.3 vincent-export-core（Phase 3）

- 纯库，基于 Apache POI 或 EasyExcel（版本由 BOM 锁定）；
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

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| Phase 0 | 抽取 `vincent-host-ports`、`vincent-common-web`；dict 重构依赖 common，零行为变化 | dict 全量测试通过；BOM 新增 common 坐标 |
| Phase 1 | `vincent-audit` core：domain/application/infra/starter/SQL/管理页 | 显式 record + search；Schema 校验；示例宿主 IT 通过 |
| Phase 2 | `vincent-audit-aop-boot2-starter` | `@Audited` 在简单 CRUD 场景可用；复杂场景仍走显式 API |
| Phase 3 | `vincent-id-core`、`vincent-export-core` | 纯库单测；示例用法写入 README |
| Phase 4 | `vincent-region` | 三级树查询与管理；复用 common 模块 |

每个 Phase 独立可发布，不阻塞已有 dict 消费者。

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
- **Phase 1 与 Phase 0 并行**：common 未稳定前开 audit 会复制 dict 内联代码，增加返工。
