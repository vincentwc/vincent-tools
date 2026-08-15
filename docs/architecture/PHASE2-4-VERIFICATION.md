# Phase 2–4 计划 vs 代码验证报告

> 验证时间：2026-08-15  
> 验证命令：`mvn -P '!jdk-17' verify`（exit 0，全 reactor 含 Testcontainers IT）

本文对照各 Phase 实施计划 checklist，逐项核对代码与测试证据。

---

## Phase 2 — `@Audited` AOP

计划文档：[2026-08-15-vincent-audit-phase2-aop.md](../superpowers/plans/2026-08-15-vincent-audit-phase2-aop.md)

| 计划项 | 代码证据 | 测试证据 | 结论 |
| --- | --- | --- | --- |
| `@Audited` + SpEL `resourceId` / `targetTenantId` | `vincent-audit-aop/@Audited.java`、`AuditedSpelEvaluator.java` | `AuditedAspectTest` | ✅ |
| `AuditPayloadExtractor` 按 resourceType 匹配 | `AuditedAspect.resolvePayload()` | `AuditedAspectTest` | ✅ |
| `afterCommit` 默认 false / true 提交后写入 | `AuditedRecordPublisher.java` | `AuditedRecordPublisherTest` | ✅ |
| 独立 artifact，不合并 core Starter | `vincent-audit-aop-boot2-starter` 独立模块 | `AuditAopAutoConfigurationTest` | ✅ |
| `@ConditionalOnBean(AuditService)` + `vincent.audit.aop.enabled` | `AuditAopAutoConfiguration.java` | `AuditAopAutoConfigurationTest` | ✅ |
| 方法成功返回后才写审计；异常不写 | `AuditedAspect.aroundAudited()` 先 `proceed()` | `AuditedAspectTest` | ✅ |
| example 端到端 | `ExampleAuditedService` + `AuditExampleIT.auditedAnnotationRecordsThroughAuditService` | IT 通过（verify） | ✅ |
| 文档 §3.6 | `vincent-audit/docs/INTEGRATION.md` | — | ✅ |

---

## Phase 3 — ID / Export 纯库

计划文档：[2026-08-15-vincent-common-phase3-id-export.md](../superpowers/plans/2026-08-15-vincent-common-phase3-id-export.md)

| 计划项 | 代码证据 | 测试证据 | 结论 |
| --- | --- | --- | --- |
| 雪花 ID 41+5+5+12，epoch 2020-01-01 | `SnowflakeIdGenerator.java` | `SnowflakeIdGeneratorTest` | ✅ |
| 时钟回拨检测 | `nextId()` throws `IdGenerationException` | `SnowflakeIdGeneratorTest` | ✅ |
| workerId/datacenterId 0–31 构造传入 | 构造函数校验 | `SnowflakeIdGeneratorTest` | ✅ |
| `BusinessNumberFormatter` `{date}` `{seq}` | `BusinessNumberFormatter.java` | `BusinessNumberFormatterTest` | ✅ |
| `SegmentAllocator` 端口 | `SegmentAllocator.java` | — | ✅ |
| 无 Spring 自动装配 | 模块无 spring.factories | — | ✅ |
| `VincentExcelExporter.write/read` | `VincentExcelExporter.java` | `VincentExcelExporterTest` | ✅ |
| EasyExcel 3.3.2 BOM 锁定 | 根 POM `easyexcel.version=3.3.2` | — | ✅ |
| 模块 README | `vincent-id-core/README.md`、`vincent-export-core/README.md` | — | ✅ |
| 接入文档（dict 风格） | `vincent-common/docs/INTEGRATION.md` | — | ✅ 本次补充 |

---

## Phase 4 — vincent-region

计划文档：[2026-08-15-vincent-region-phase4-query.md](../superpowers/plans/2026-08-15-vincent-region-phase4-query.md)

| 计划项 | 代码证据 | 测试证据 | 结论 |
| --- | --- | --- | --- |
| `001-init.sql` + `001-data.sql` | `vincent-region/sql/mysql/1.0.0/` | `MybatisRegionRepositoryIT` | ✅ |
| Schema 只读校验 version=1 | `RegionSchemaValidator` | Starter IT | ✅ |
| `findByCode` / `listChildren` | `DefaultRegionQueryService` | `DefaultRegionQueryServiceTest` | ✅ |
| parentCode null/空/`0` → 省级 | `normalizeParentCode()` | `DefaultRegionQueryServiceTest` | ✅ |
| `RegionView` 不暴露 DB id | 仅 code/name/level/parentCode | — | ✅ |
| 只读管理 REST + `REGION_VIEW` | `RegionAdminController` | `RegionExampleIT` | ✅ |
| 无 admin-ui SPA | 无 admin-ui 模块 | — | ✅ |
| 无 tenant / 无写入 API | 无 TenantProvider 依赖 | — | ✅ |
| UTF-8 中文 | JDBC `characterEncoding=UTF-8` | `RegionExampleIT` 断言「广州市」「荔湾区」 | ✅ |
| example IT 3 例 + 403 | `RegionExampleIT` | verify 通过 | ✅ |
| 接入文档（dict 风格） | `vincent-region/docs/INTEGRATION.md` | — | ✅ 本次补充 |

---

## 全量构建证据

```bash
cd vincent-tools
git checkout main && git pull
mvn -P '!jdk-17' verify
# exit code: 0
```

关键 IT 模块：

- `vincent-audit-example-boot2` — `@Audited` + admin API
- `vincent-region-example-boot2` — 查询 + admin API + 403
- `vincent-audit-aop-boot2-starter` — 单元测试
- `vincent-common/vincent-id-core`、`vincent-export-core` — 单元测试

---

## 文档缺口（本次已补）

| 缺口 | 补充文件 |
| --- | --- |
| region 无 INTEGRATION | `vincent-region/docs/INTEGRATION.md` |
| id/export 无 dict 风格接入文档 | `vincent-common/docs/INTEGRATION.md` |
| 无整体架构图 | `docs/architecture/README.md` |
| 各工具无架构图 | `*/docs/ARCHITECTURE.md`（dict/audit/region/common） |
