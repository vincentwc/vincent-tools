# Vincent Common（ID / Export）接入使用说明

本文面向**业务系统宿主**，说明如何引入 `vincent-id-core` 与 `vincent-export-core` 两个**纯 Java 库**。它们无 Spring 自动装配、无数据库表、无 HTTP API。

> 架构说明见 [`ARCHITECTURE.md`](ARCHITECTURE.md)。各模块 API 摘要见 [`../vincent-id-core/README.md`](../vincent-id-core/README.md)、[`../vincent-export-core/README.md`](../vincent-export-core/README.md)。

---

## 1. 适用场景

| 场景 | 推荐模块 | 说明 |
| --- | --- | --- |
| 分布式唯一 ID（雪花） | `vincent-id-core` | 宿主自行分配 `workerId` / `datacenterId` |
| 业务单号（日期 + 序号） | `vincent-id-core` | 宿主实现 `SegmentAllocator` 持久化号段 |
| Excel 导出/导入 | `vincent-export-core` | EasyExcel 流式读写封装 |

**不提供**：Spring Boot Starter、号段数据库实现、WorkerId 自动分配、CSV/PDF 导出。

---

## 2. 环境要求

| 项 | 要求 |
| --- | --- |
| JDK | 8（编译目标 1.8） |
| Spring Boot | **不强制**；纯库可在非 Spring 项目使用 |
| EasyExcel | 3.3.2（由 BOM 锁定，仅 export 模块传递依赖） |

---

## 3. Maven 依赖

通过 BOM 对齐版本（推荐）：

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
    <!-- 按需二选一或同时引入 -->
    <dependency>
        <groupId>com.vincent.tools</groupId>
        <artifactId>vincent-id-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.vincent.tools</groupId>
        <artifactId>vincent-export-core</artifactId>
    </dependency>
</dependencies>
```

**不要**依赖根父 POM `vincent-tools` 作为功能依赖。

---

## 4. vincent-id-core

### 4.1 SnowflakeIdGenerator

41+5+5+12 bit 布局，epoch 为 2020-01-01 UTC。构造时传入 `workerId` / `datacenterId`（各 0–31）。

```java
SnowflakeIdGenerator ids = new SnowflakeIdGenerator(1, 1);
long id = ids.nextId();
```

| 行为 | 说明 |
| --- | --- |
| 单调递增 | 同一 JVM 内按时间戳 + sequence 递增 |
| 时钟回拨 | 抛出 `IdGenerationException("clock moved backwards")` |
| 多实例 | 各实例 **必须** 使用不同 `(datacenterId, workerId)` 组合 |

**宿主职责**：在 K8s/多机部署时为每个实例分配唯一 worker/datacenter ID（配置文件、注册中心或 DB 租约等），工具包不提供 `WorkerIdProvider`。

### 4.2 BusinessNumberFormatter

模板占位符：

| 占位符 | 含义 |
| --- | --- |
| `{date}` | 当前 UTC 日期 `yyyyMMdd` |
| `{seq}` | 号段内序号（由 `SegmentAllocator` 提供） |

```java
public class DbSegmentAllocator implements SegmentAllocator {
    @Override
    public long nextSegment(String bizKey) {
        // 宿主实现：DB/Redis 原子递增，保证多实例安全
        return segmentRepository.next(bizKey);
    }
}

String orderNo = BusinessNumberFormatter.format(
        "ORD-{date}-{seq}",
        segmentAllocator.nextSegment("order"));
// 示例：ORD-20260815-10001
```

### 4.3 SegmentAllocator 端口

```java
public interface SegmentAllocator {
    long nextSegment(String bizKey);
}
```

工具包**只定义端口**，不内置持久化。常见宿主实现：MySQL `UPDATE ... SET seq=seq+1`、Redis `INCR`、号段批量预取等。

---

## 5. vincent-export-core

基于 EasyExcel 3.3.2 的流式读写，不额外暴露 POI API。

### 5.1 写入 Excel

```java
@Data
public class OrderRow {
    @ExcelProperty("订单号")
    private String orderNo;
    @ExcelProperty("金额")
    private BigDecimal amount;

    public OrderRow() { }  // EasyExcel 需要 public 无参构造
}

try (OutputStream out = response.getOutputStream()) {
    VincentExcelExporter.write(out, OrderRow.class, rows);
}
```

### 5.2 读取 Excel

```java
VincentExcelExporter.read(inputStream, OrderRow.class, row -> {
    orderService.importRow(row);
});
```

每解析一行调用一次 `rowConsumer`；大文件不会一次性加载到内存（EasyExcel 流式解析）。

### 5.3 DTO 要求

- 使用 `@ExcelProperty` 标注列名
- **public 无参构造**（Lombok `@Data` 默认满足）
- 字段类型须为 EasyExcel 支持的 Java 类型

---

## 6. 与 Spring Boot 集成（可选）

纯库无 `@Configuration`。宿主可自行注册 Bean：

```java
@Configuration
public class IdConfiguration {

    @Bean
    SnowflakeIdGenerator snowflakeIdGenerator(
            @Value("${app.snowflake.worker-id}") long workerId,
            @Value("${app.snowflake.datacenter-id}") long datacenterId) {
        return new SnowflakeIdGenerator(workerId, datacenterId);
    }

    @Bean
    SegmentAllocator segmentAllocator(SegmentRepository repo) {
        return bizKey -> repo.nextSegment(bizKey);
    }
}
```

Export 通常无需 Bean，直接静态调用 `VincentExcelExporter`。

---

## 7. 异常

| 模块 | 异常 | 场景 |
| --- | --- | --- |
| id-core | `IdGenerationException` | worker/datacenter 越界、时钟回拨 |
| export-core | EasyExcel 运行时异常 | DTO 缺无参构造、列映射错误等 |

---

## 8. 接入验收清单

**vincent-id-core**

- [ ] BOM 引入 `vincent-id-core`
- [ ] 各实例 worker/datacenter ID 唯一
- [ ] `nextId()` 在压测下无重复（抽样验证）
- [ ] `SegmentAllocator` 多实例并发安全（宿主实现验证）

**vincent-export-core**

- [ ] BOM 引入 `vincent-export-core`
- [ ] DTO 有 public 无参构造 + `@ExcelProperty`
- [ ] 导出文件可被 Excel/WPS 正常打开
- [ ] 导入大文件内存稳定（流式消费）

---

## 9. 本地验证

```bash
mvn -P '!jdk-17' test -pl vincent-common/vincent-id-core
mvn -P '!jdk-17' test -pl vincent-common/vincent-export-core
```

全量 reactor：

```bash
mvn -P '!jdk-17' verify
```
