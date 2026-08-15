# Vincent Audit 架构

> 接入说明见 [INTEGRATION.md](INTEGRATION.md)。

## 1. 模块结构

```mermaid
flowchart TB
    subgraph publish["业务系统引入"]
        BS["vincent-audit-boot2-starter<br/>Phase 1 核心"]
        AS["vincent-audit-aop-boot2-starter<br/>Phase 2 可选"]
    end

    subgraph internal["内部模块（勿直接依赖）"]
        AOP["vincent-audit-aop<br/>@Audited 注解"]
        DOM["vincent-audit-domain"]
        APP["vincent-audit-application"]
        INF["vincent-audit-infra-mybatis"]
        WEB["vincent-audit-web"]
        UI["vincent-audit-admin-ui<br/>只读 SPA"]
    end

    BS --> APP & INF & WEB & UI
    AS --> AOP & BS
    WEB --> APP
    INF --> APP
    APP --> DOM
    AOP -.->|无 Spring| APP
```

| 模块 | 职责 |
| --- | --- |
| `domain` | 审计字段约束、异常码 |
| `application` | `AuditService.record()` / `search()`、`AuditRecordCommand` |
| `infra-mybatis` | 审计日志 PO、Mapper、Schema 校验 |
| `web` | 只读管理 REST + SPA 路由 |
| `admin-ui` | 只读检索 Vue SPA |
| `aop` | `@Audited`、`AuditPayloadExtractor` 端口（纯 Java） |
| `aop-boot2-starter` | `AuditedAspect`、SpEL、`afterCommit` 事务同步 |

---

## 2. 显式写入路径（Phase 1）

```mermaid
sequenceDiagram
    participant Biz as 业务代码
    participant AS as AuditService
    participant OP as OperatorProvider
    participant Repo as AuditRepository
    participant DB as MySQL vin_audit_log

    Biz->>AS: record(AuditRecordCommand)
    AS->>OP: currentOperatorId()
    OP-->>AS: operator_id
    AS->>AS: 校验 JSON / tenant / 权限
    AS->>Repo: insert
    Repo->>DB: INSERT
    Note over AS,DB: 加入当前 Spring 事务（若存在）
```

`operator_id` **始终**来自 `OperatorProvider`，不可由 command 伪造。

---

## 3. @Audited AOP 路径（Phase 2）

```mermaid
sequenceDiagram
    participant Biz as 业务方法
    participant Asp as AuditedAspect
    participant SpEL as AuditedSpelEvaluator
    participant Ext as AuditPayloadExtractor
    participant Pub as AuditedRecordPublisher
    participant AS as AuditService

    Biz->>Asp: @Audited 方法调用
    Asp->>Biz: proceed()
    Biz-->>Asp: result（成功才继续）
    Asp->>SpEL: 解析 resourceId / targetTenantId
    Asp->>Ext: supports(resourceType)?
    Ext-->>Asp: before/after JSON
    alt afterCommit=false
        Asp->>Pub: publish
        Pub->>AS: record()
    else afterCommit=true
        Asp->>Pub: register afterCommit
        Pub->>AS: record() 于事务提交后
    end
```

| 约束 | 说明 |
| --- | --- |
| 异常时不写审计 | 仅方法成功返回后触发 |
| 复杂场景 | 仍建议显式 `record()` |
| Extractor | 按 `resourceType` 匹配；无则 JSON 为 null |
| 开关 | `vincent.audit.aop.enabled=false` 关闭切面 |

---

## 4. 只读检索与管理端

```mermaid
flowchart LR
    SPA["audit-admin SPA"]
    API["GET /records"]
    AS["AuditService.search()"]
    PP["PermissionProvider<br/>AUDIT_VIEW"]

    SPA --> API
    API --> PP
    API --> AS
    AS --> DB["vin_audit_log"]
```

管理端**只读**：无创建/修改/删除审计记录的 API。

---

## 5. 与 vincent-common / dict 共用

```mermaid
flowchart LR
    HP["vincent-host-ports<br/>Tenant / Operator / Permission"]
    CC["vincent-common-core"]
    CW["vincent-common-web"]

    BS["audit-boot2-starter"] --> HP & CC & CW
    DICT["vincent-dict"] --> HP
```

Audit 与 Dict 可共用同一 MySQL 库（`vin_audit_*` / `vin_dict_*`）与同一套 Provider 实现。

---

## 6. 关键设计约束

- Phase 1 与 Phase 2 **独立 artifact**；AOP 不修改 `AuditService` 契约。
- `@ConditionalOnBean(AuditService.class)` 注册 AOP。
- Schema 版本 `1`；无自动 TTL 清理（第一版永久保留）。
- `fail-fast` 控制写入失败是否抛异常。
