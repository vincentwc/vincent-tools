# Vincent Region 架构

> 接入说明见 [INTEGRATION.md](INTEGRATION.md)。

## 1. 模块结构

```mermaid
flowchart TB
    subgraph publish["业务系统引入"]
        BS["vincent-region-boot2-starter"]
    end

    subgraph internal["内部模块（勿直接依赖）"]
        DOM["vincent-region-domain"]
        APP["vincent-region-application"]
        INF["vincent-region-infra-mybatis"]
        WEB["vincent-region-web<br/>仅 REST，无 SPA"]
    end

    BS --> APP & INF
    WEB --> APP
    INF --> APP
    APP --> DOM
```

| 模块 | 职责 |
| --- | --- |
| `domain` | 字段约束、`RegionErrorCode`、`RegionException` |
| `application` | `RegionQueryService`（`findByCode` / `listChildren`） |
| `infra-mybatis` | `vin_region` 表读写、按 `parent_code` 索引查询 |
| `web` | 只读管理 REST（`REGION_VIEW`） |
| `boot2-starter` | Schema 校验、DataSource 解析、条件 Bean |

**与 audit/dict 的差异**

- 无 `TenantProvider` / `OperatorProvider` 依赖（核心查询）
- 无写入 API、无 admin-ui SPA
- 数据由 DBA 执行 `001-init.sql` + `001-data.sql` 维护

---

## 2. 查询数据流

```mermaid
sequenceDiagram
    participant Biz as 业务代码 / Admin API
    participant QS as RegionQueryService
    participant Repo as MybatisRegionRepository
    participant DB as MySQL vin_region

    Biz->>QS: findByCode("440100")
    QS->>Repo: findByCode
    Repo->>DB: SELECT BY code PK
    DB-->>Repo: row
    Repo-->>QS: RegionView
    QS-->>Biz: Optional RegionView

    Biz->>QS: listChildren("440000")
    QS->>QS: normalizeParentCode
    QS->>Repo: listChildren
    Repo->>DB: SELECT WHERE parent_code=?
    DB-->>Repo: rows
    Repo-->>QS: List RegionView
    QS-->>Biz: 子级列表
```

`parentCode` 为 `null`、空串或 `"0"` 时归一化为省级查询。

---

## 3. 只读管理 API

```mermaid
flowchart LR
    Client["HTTP Client / 前端"]
    Ctrl["RegionAdminController"]
    PP["PermissionProvider<br/>REGION_VIEW"]
    QS["RegionQueryService"]

    Client --> Ctrl
    Ctrl --> PP
    Ctrl --> QS
    QS --> DB["vin_region"]
```

| 端点 | 说明 |
| --- | --- |
| `GET /{code}` | 单条查询 |
| `GET /children?parentCode=` | 子级列表 |

---

## 4. 数据模型

```mermaid
erDiagram
    vin_region_meta {
        bigint id PK
        varchar schema_version
        datetime updated_at
    }
    vin_region {
        varchar code PK
        varchar name
        tinyint level
        varchar parent_code
    }
    vin_region ||--o{ vin_region : "parent_code → code"
```

| level | 含义 |
| --- | --- |
| 1 | 省/直辖市 |
| 2 | 市 |
| 3 | 区/县 |

`RegionView` 对外只暴露 `code/name/level/parentCode`，不暴露 DB  surrogate key。

---

## 5. 与 vincent-common 的关系

```mermaid
flowchart LR
    CC["vincent-common-core<br/>VincentSchemaValidator<br/>VincentInfrastructureResolver"]
    CW["vincent-common-web<br/>ApiResponse"]
    HP["vincent-host-ports<br/>PermissionProvider"]

    BS["region-boot2-starter"] --> CC & CW
    WEB["region-web"] --> CW & HP
```

---

## 6. 关键设计约束

- 领域层无 Spring/MyBatis 依赖。
- 启动只读 Schema 校验；应用永不执行 DDL。
- 首版样本数据（北京、广东）；全量国标需后续扩展 `001-data.sql`。
- UTF-8：JDBC `characterEncoding=UTF-8` + SQL 导入 `utf8mb4`。
