# Vincent Common 架构

> ID / Export 接入说明见 [INTEGRATION.md](INTEGRATION.md)。

## 1. 模块全景

```mermaid
flowchart TB
    subgraph embedded["嵌入式组件复用"]
        HP["vincent-host-ports<br/>宿主端口定义"]
        CC["vincent-common-core<br/>Schema / 基础设施解析"]
        CW["vincent-common-web<br/>ApiResponse / SPA 注入"]
        CR["vincent-common-cache-redis<br/>Redis 缓存基座"]
    end

    subgraph pure["纯 Java 库（Phase 3）"]
        ID["vincent-id-core<br/>雪花 ID / 业务编号"]
        EX["vincent-export-core<br/>EasyExcel 封装"]
    end

    DICT["vincent-dict"] --> HP & CC & CW & CR
    AUDIT["vincent-audit"] --> HP & CC & CW
    REGION["vincent-region"] --> CC & CW & HP
    HOST["宿主业务系统"] --> ID & EX
```

| 模块 | 类型 | 消费者 |
| --- | --- | --- |
| `vincent-host-ports` | 端口接口 | dict、audit、region（部分） |
| `vincent-common-core` | 共享基础设施 | dict、audit、region Starter |
| `vincent-common-web` | Web 公共类型 | dict、audit、region web 层 |
| `vincent-common-cache-redis` | Redis 缓存 | dict cache starter |
| `vincent-id-core` | 纯库 | 任意 Java 8 项目 |
| `vincent-export-core` | 纯库 | 任意 Java 8 项目 |

---

## 2. vincent-host-ports

```mermaid
flowchart LR
    subgraph ports["端口接口"]
        TP["TenantProvider"]
        OP["OperatorProvider"]
        PP["PermissionProvider"]
        TD["TenantDirectory"]
        ACP["AuditContextProvider"]
    end

    HOST["宿主实现"] --> ports
    ports --> DICT["dict"]
    ports --> AUDIT["audit"]
    ports --> REGION["region admin"]
```

宿主在 Spring `@Configuration` 中注册 Bean；各组件通过构造注入使用，**不**提供默认实现。

---

## 3. vincent-common-core

```mermaid
flowchart TB
    CC["vincent-common-core"]
    SV["VincentSchemaValidator<br/>information_schema + meta 表"]
    IR["VincentInfrastructureResolver<br/>DataSource / SqlSessionFactory 选择"]

    CC --> SV & IR
    SV --> DICT & AUDIT & REGION
    IR --> DICT & AUDIT & REGION
```

统一 Schema 只读校验与多数据源解析逻辑，避免各组件重复实现。

---

## 4. vincent-id-core（Phase 3）

```mermaid
flowchart LR
    subgraph id["vincent-id-core"]
        SF["SnowflakeIdGenerator<br/>41+5+5+12, epoch 2020-01-01"]
        BN["BusinessNumberFormatter<br/>{date} {seq}"]
        SA["SegmentAllocator 端口"]
    end

    HOST["宿主实现 SegmentAllocator"] --> SA
    SF --> OUT1["long nextId()"]
    BN --> OUT2["String 业务单号"]
```

**无 Spring、无持久化**；多实例 worker/datacenter ID 由宿主分配。

---

## 5. vincent-export-core（Phase 3）

```mermaid
flowchart LR
    IN["InputStream"] --> EE["EasyExcel 3.3.2"]
    EE --> VEE["VincentExcelExporter.read<br/>Consumer 逐行"]
    VEE --> BIZ["宿主业务逻辑"]

    BIZ2["Iterable rows"] --> VEW["VincentExcelExporter.write"]
    VEW --> EE2["EasyExcel"]
    EE2 --> OUT["OutputStream"]
```

薄封装：统一读写入口，不暴露 POI；DTO 使用 `@ExcelProperty`。

---

## 6. 依赖原则

- `vincent-host-ports`：零 Spring 依赖，可被 domain 层间接引用。
- `vincent-id-core` / `vincent-export-core`：独立发布，不依赖 host-ports。
- 嵌入式组件**不得**反向依赖 id/export（除非未来 explicit 集成场景）。
