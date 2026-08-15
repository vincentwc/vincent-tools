# Vincent Dict 架构

> 接入说明见 [INTEGRATION.md](INTEGRATION.md)。领域规则详见 [设计 spec](../../docs/superpowers/specs/2026-08-14-vincent-dict-design.md)。

## 1. 模块结构

```mermaid
flowchart TB
    subgraph publish["业务系统引入"]
        BS["vincent-dict-boot2-starter"]
        RS["vincent-dict-cache-redis-boot2-starter<br/>可选"]
    end

    subgraph internal["内部模块（勿直接依赖）"]
        DOM["vincent-dict-domain"]
        APP["vincent-dict-application"]
        INF["vincent-dict-infra-mybatis"]
        WEB["vincent-dict-web"]
        UI["vincent-dict-admin-ui<br/>Vue SPA → jar 静态资源"]
    end

    BS --> APP & INF & WEB & UI
    RS --> APP
    WEB --> APP
    INF --> APP
    APP --> DOM
```

| 模块 | 职责 |
| --- | --- |
| `domain` | 字典/条目聚合、编码规则、领域异常 |
| `application` | `DictQueryService`、管理命令服务、仓储/缓存端口 |
| `infra-mybatis` | PO、Mapper、仓储实现、Schema 校验 |
| `web` | 管理 REST API、SPA 路由、异常映射 |
| `admin-ui` | Vue 3 管理端，打包进 Starter |
| `boot2-starter` | 自动装配、条件 Bean、UI 资源聚合 |
| `cache-redis-boot2-starter` | 可选 Redis 旁路缓存 |

---

## 2. 运行时数据流（查询）

```mermaid
sequenceDiagram
    participant Biz as 业务代码
    participant QS as DictQueryService
    participant Cache as DictCache 端口
    participant Repo as MybatisDictRepository
    participant DB as MySQL vin_dict_*

    Biz->>QS: listEffectiveItems(dictCode)
    QS->>Cache: get / load
    alt 缓存命中
        Cache-->>QS: 有效项列表
    else 缓存未命中
        Cache->>Repo: load from DB
        Repo->>DB: SELECT
        DB-->>Repo: rows
        Repo-->>Cache: DictItemView 列表
    end
    QS-->>Biz: 有效项（默认 + 租户追加）
```

多租户：`TenantProvider` 提供当前租户；批处理使用显式租户 API。

---

## 3. 管理端数据流

```mermaid
sequenceDiagram
    participant Browser as 浏览器
    participant Ctrl as DictAdminController
    participant Admin as DictAdminService
    participant Repo as Repository
    participant Cache as DictCache evict

    Browser->>Ctrl: REST /vincent/dict/admin/api/v1/...
    Ctrl->>Ctrl: PermissionProvider 校验
    Ctrl->>Admin: 命令/查询
    Admin->>Repo: 持久化
    Admin->>Cache: evict 版本
    Repo-->>Admin: 结果
    Admin-->>Ctrl: DTO
    Ctrl-->>Browser: ApiResponse
```

---

## 4. 与 vincent-common 的关系

```mermaid
flowchart LR
    CC["vincent-common-core<br/>SchemaValidator<br/>InfrastructureResolver"]
    CW["vincent-common-web<br/>ApiResponse / SPA HTML"]
    CR["vincent-common-cache-redis"]
    HP["vincent-host-ports"]

    BS["dict-boot2-starter"] --> CC & CW & HP
    RS["cache-redis-starter"] --> CR
```

---

## 5. 关键设计约束

- 依赖只能由外向内；Controller/Mapper 不得复制领域规则。
- Schema 版本 `1`，手工 SQL 初始化，启动只读校验。
- 管理端需 `OperatorProvider` + `PermissionProvider`；查询端仅需 `TenantProvider`（多租户时）。
- Redis 缓存为可选旁路；未启用时直读 MySQL。
