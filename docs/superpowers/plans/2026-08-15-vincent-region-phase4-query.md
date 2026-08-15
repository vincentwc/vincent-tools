# Vincent Region Phase 4 — 省市区查询实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Status:** ✅ 已完成（PR #10 merged）

**Goal:** 交付 `vincent-region` 嵌入式组件：中国省市区三级树 Java 查询 API、MySQL 参考数据、Boot 2 Starter、可选只读管理 REST API、`vincent-region-example-boot2` 端到端验收。首版不含内嵌 SPA 管理页。

**Architecture:** 镜像 `vincent-audit` DDD 分层（简化版）：无 tenant、无写入 API、无 AOP；复用 `vincent-common-core` Schema 校验与 `VincentInfrastructureResolver`；复用 `vincent-common-web` `ApiResponse`。参考数据手工 SQL 交付（init + data 分离）。

**Tech Stack:** Java 8、Spring Boot 2.2.6.RELEASE、MyBatis-Plus 3.3.2、MySQL 5.7+。

**Spec 来源：** `docs/superpowers/specs/2026-08-15-vincent-tools-roadmap-design.md` §8.1、§10 Phase 4。

## Global Constraints

- groupId `com.vincent.tools`；版本 `1.0.0-SNAPSHOT`。
- 包名前缀 `com.vincent.tools.region.*`。
- `vincent-region-domain` 不得依赖 Spring/MyBatis/HTTP。
- Vincent Region never runs DDL at application startup。
- 表：`vin_region_meta`、`vin_region`；Schema 版本 `1`。
- `vincent.region.enabled` 默认 `true`；`vincent.region.admin.enabled` 默认 `false`。
- 管理 API 默认 `/vincent/region/admin/api/v1`；权限码 `REGION_VIEW`。
- 首版不做：国际地区、模糊搜索、管理端增删改、内嵌 Vue SPA。
- 全量验证：`mvn -P '!jdk-17' verify`。

## Phase 4 验收 checklist

- [x] `001-init.sql` + `001-data.sql` 手工执行；Schema 只读校验
- [x] `RegionQueryService.findByCode` / `listChildren`（`parentCode` 空或 `"0"` → 省级）
- [x] `RegionView` 暴露 code/name/level/parentCode，不暴露 DB 自增 ID
- [x] 只读管理 REST API + `REGION_VIEW` 权限
- [x] `vincent-region-example-boot2` IT（查询 + admin API + 403）
- [x] UTF-8 中文数据（Testcontainers `characterEncoding=UTF-8`）
- [x] 全量 reactor 测试全绿

---

### Task 1: Maven Reactor、SQL 与 BOM

**Files:**
- Modify: `pom.xml`, `vincent-tools-bom/pom.xml`
- Create: `vincent-region/pom.xml` 及子模块 pom
- Create: `sql/mysql/1.0.0/001-init.sql`, `001-data.sql`

**表结构：**
```text
vin_region_meta   — schema_version
vin_region        — code PK, name, level (1/2/3), parent_code, idx(parent_code)
```

- [x] 根 POM 增加 `<module>vincent-region</module>`
- [x] 样本数据：北京、广东及下属市/区（可扩展全量国标）
- [x] Commit: 合入 PR #10

---

### Task 2: region-domain + application

**Files:**
- Create: `RegionErrorCode`, `RegionException`, `RegionFieldLimits`
- Create: `RegionQueryService`, `DefaultRegionQueryService`, `RegionView`, `RegionPermission`
- Create: `RegionRepository` port
- Test: `RegionExceptionTest`, `DefaultRegionQueryServiceTest`

- [x] 错误码含 `REGION_NOT_FOUND`
- [x] 核心查询**不**依赖 `TenantProvider` / `OperatorProvider`
- [x] Commit: 合入 PR #10

---

### Task 3: region-infra-mybatis

**Files:**
- Create: `RegionPo`, `RegionMapper` + XML, `MybatisRegionRepository`
- Test: `MybatisRegionRepositoryIT`（init + data SQL，UTF-8）
- Create: `docker-java.properties`（`api.version=1.44`）

- [x] `listChildren` 按 `parent_code` 索引查询
- [x] Commit: 合入 PR #10

---

### Task 4: region-boot2-starter

**Files:**
- Create: `RegionProperties`, `RegionCoreAutoConfiguration`, `RegionSchemaValidator`, `RegionInfrastructureResolver`
- Create: `META-INF/spring.factories`
- Create: `docker-java.properties`（test）

- [x] Mapper 包 `com.vincent.tools.region.infra.mybatis.mapper`
- [x] `@ConditionalOnProperty(vincent.region.enabled)` 默认 true
- [x] Commit: 合入 PR #10

---

### Task 5: region-web — 只读管理 REST API

**Files:**
- Create: `RegionAdminController` — `GET /{code}`, `GET /children`
- Create: `RegionWebExceptionHandler`, `RegionAdminWebAutoConfiguration`, `RegionAdminWebProperties`
- 注：首版**无** admin-ui SPA（与 audit/dict 不同，仅 REST）

- [x] `@ConditionalOnProperty(vincent.region.admin.enabled=true)` + `PermissionProvider`
- [x] Commit: 合入 PR #10

---

### Task 6: example-boot2 与文档

**Files:**
- Create: `RegionExampleApplication`, `ExamplePermissionAdapter`, `ExampleMysqlSupport`
- Test: `RegionExampleIT`（3 例）
- Create: `vincent-region/README.md`
- Modify: 根 `README.md`

- [x] `mvn -P '!jdk-17' verify -pl vincent-region/vincent-region-example-boot2 -am`
- [x] Commit: 合入 PR #10

---

## Spec Coverage Self-Review

| Checklist | Task |
| --- | --- |
| SQL + Schema | 1, 4 |
| findByCode / listChildren | 2, 3 |
| REGION_VIEW 权限 | 5 |
| 只读、无写入 API | 2, 5 |
| example IT | 6 |
| 全量 verify | 6 |

## 相关 PR

| PR | 说明 |
| --- | --- |
| #10 | feat(region): vincent-region Phase 4 query module |

## 后续可选

- `001-data.sql` 扩展为全国全量国标数据
- `vincent-region-admin-ui` 内嵌只读 SPA（镜像 audit admin-ui）
- 管理端按名称精确匹配搜索（spec 首版明确不做模糊搜索）
