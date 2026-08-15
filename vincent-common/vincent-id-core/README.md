# vincent-id-core

纯 Java 库：分布式 ID 与业务编号辅助。

## API

| 类 | 说明 |
| --- | --- |
| `SnowflakeIdGenerator` | 雪花 ID；构造时传入 `workerId` / `datacenterId`（0–31） |
| `BusinessNumberFormatter` | 模板格式化，支持 `{date}`（yyyyMMdd）、`{seq}` |
| `SegmentAllocator` | 号段端口，宿主实现持久化与多实例安全 |

```java
SnowflakeIdGenerator ids = new SnowflakeIdGenerator(1, 1);
long id = ids.nextId();

String orderNo = BusinessNumberFormatter.format("ORD-{date}-{seq}", segmentAllocator.nextSegment("order"));
```

## 验收

```bash
mvn -P '!jdk-17' test -pl vincent-common/vincent-id-core
```
