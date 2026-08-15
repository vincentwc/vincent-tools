# Vincent Tools Phase 3 — ID / Export 纯库实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Status:** ✅ 已完成（PR #9 merged）

**Goal:** 在 `vincent-common` 下交付两个纯 Java 库：`vincent-id-core`（雪花 ID + 业务编号格式化 + 号段端口）与 `vincent-export-core`（EasyExcel 流式读写封装）；无表、无 Spring 自动装配。

**Architecture:** 模块挂于现有 `vincent-common` 聚合 POM；BOM 锁定 `easyexcel` 3.3.2（Java 8 兼容）；各模块独立 README 说明用法；不进嵌入式 Starter 模式。

**Tech Stack:** Java 8、EasyExcel 3.3.2、JUnit 5、AssertJ。

**Spec 来源：** `docs/superpowers/specs/2026-08-15-vincent-tools-roadmap-design.md` §8.2、§8.3、§10 Phase 3。

## Global Constraints

- **纯库**：不得依赖 Spring Boot autoconfigure 或 MyBatis。
- 雪花 `workerId` / `datacenterId` 由宿主构造函数传入（0–31），不提供 YAML/`WorkerIdProvider`。
- 号段持久化由宿主实现 `SegmentAllocator`，工具包不做内置持久化。
- Export 只暴露 EasyExcel 封装，不额外暴露原生 POI API。
- 全量验证：`mvn -P '!jdk-17' verify`。

## Phase 3 验收 checklist

- [x] `SnowflakeIdGenerator` 单调递增、唯一、时钟回拨检测
- [x] `BusinessNumberFormatter` 支持 `{date}`（yyyyMMdd）、`{seq}` 占位符
- [x] `SegmentAllocator` 端口定义
- [x] `VincentExcelExporter.write/read` 流式读写
- [x] BOM 注册 `vincent-id-core`、`vincent-export-core`、`easyexcel`
- [x] 模块 README + 根 README 说明
- [x] 全量 reactor 测试全绿

---

### Task 1: vincent-id-core

**Files:**
- Create: `vincent-common/vincent-id-core/pom.xml`
- Create: `SnowflakeIdGenerator.java` — 41+5+5+12 bit 布局，epoch 2020-01-01
- Create: `BusinessNumberFormatter.java`
- Create: `SegmentAllocator.java`, `IdGenerationException.java`
- Test: `SnowflakeIdGeneratorTest`, `BusinessNumberFormatterTest`
- Create: `vincent-id-core/README.md`

- [x] `vincent-common/pom.xml` 注册模块
- [x] 包名 `com.vincent.tools.common.id.*`
- [x] Commit: 合入 PR #9

---

### Task 2: vincent-export-core

**Files:**
- Create: `vincent-common/vincent-export-core/pom.xml`
- Create: `VincentExcelExporter.java` — `write(OutputStream, Class, Iterable)` / `read(InputStream, Class, Consumer)`
- Test: `VincentExcelExporterTest`（DTO 需 public 无参构造）
- Create: `vincent-export-core/README.md`

- [x] 根 POM `easyexcel.version=3.3.2` + `dependencyManagement`
- [x] Commit: 合入 PR #9

---

### Task 3: BOM 与文档

**Files:**
- Modify: `vincent-tools-bom/pom.xml`, 根 `pom.xml`
- Modify: 根 `README.md` — Vincent ID / Export 小节

- [x] `mvn -P '!jdk-17' verify` 全绿
- [x] Commit: 合入 PR #9

---

## Spec Coverage Self-Review

| Checklist | Task |
| --- | --- |
| 雪花 ID | 1 |
| 业务编号格式化 | 1 |
| SegmentAllocator 端口 | 1 |
| EasyExcel 读写 | 2 |
| BOM | 3 |
| 文档 | 1, 2, 3 |

## 相关 PR

| PR | 说明 |
| --- | --- |
| #9 | feat(common): vincent-id-core + vincent-export-core |

## 后续可选

- `BusinessNumberFormatter` 扩展更多占位符（如 `{yyyyMM}`）
- Export CSV 支持、大文件分批 write 回调
