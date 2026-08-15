# Vincent Audit Phase 1 — Core 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 交付 `vincent-audit` 嵌入式组件第一版：显式 `AuditService.record/search`、MySQL 持久化、Starter 自动装配、只读管理 API 与内嵌 SPA、`vincent-audit-example-boot2` 端到端验收。不含 AOP（Phase 2）。

**Architecture:** 镜像 `vincent-dict` DDD 分层；复用 `vincent-host-ports`、`vincent-common-core`（PageResult、Schema、InfrastructureResolver）、`vincent-common-web`（ApiResponse、SPA 注入）。领域层纯 Java；MyBatis 持久化；条件装配 Web 管理端。

**Tech Stack:** Java 8、Spring Boot 2.2.6.RELEASE、MyBatis-Plus 3.3.2、MySQL 5.7+、Vue 3 + TypeScript + Vite + Element Plus（管理 UI）。

## Global Constraints

- groupId `com.vincent.tools`；版本 `1.0.0-SNAPSHOT`。
- 包名前缀 `com.vincent.tools.audit.*`。
- `vincent-audit-domain` 不得依赖 Spring/MyBatis/HTTP。
- Vincent Audit never runs DDL at application startup；手工 SQL `vincent-audit/sql/mysql/1.0.0/001-init.sql`。
- 表：`vin_audit_meta`、`vin_audit_log`；Schema 版本 `1`。
- `vincent.audit.enabled` 默认 `true`；`vincent.audit.admin.enabled` 默认 `false`。
- 管理端默认 `base-path: /audit-admin`，`api-path: /vincent/audit/admin/api/v1`。
- `vincent.audit.fail-fast` 默认 `true`；`record()` 加入当前 Spring 事务。
- 分页：`default-page-size=20`，`max-page-size=100`。
- 权限：`AuditPermission.AUDIT_VIEW`；search 时 `tenantId` 可选。
- 全量验证：`mvn -P '!jdk-17' test`。
- Phase 1 不得修改 `vincent-dict` 行为。

## Phase 1 验收 checklist（§10.1）

1. SQL 初始化 + Schema 只读校验
2. `record()` + `search()` 功能完整
3. 可选 tenant search + `AUDIT_VIEW` 权限
4. fail-fast 可配置
5. 事务内 rollback 与 audit 一致
6. 管理页 `/audit-admin` 可检索
7. example-boot2 IT 通过
8. audit reactor 测试全绿

---

### Task 1: Maven Reactor、SQL 与 BOM

**Files:**
- Modify: `pom.xml`, `vincent-tools-bom/pom.xml`
- Create: `vincent-audit/pom.xml` 及 7 个子模块 pom（domain/application/infra-mybatis/web/admin-ui/boot2-starter/example-boot2）
- Create: `vincent-audit/sql/mysql/1.0.0/001-init.sql`

**Interfaces:**
- Produces: 空 reactor 可 `mvn install`；SQL 创建 meta+log 表。

- [ ] 根 POM 增加 `<module>vincent-audit</module>`
- [ ] 创建聚合与子模块 POM，依赖方向：domain ← application ← infra ← starter；web/admin-ui ← starter
- [ ] SQL：`vin_audit_meta`（id=1, schema_version='1'）+ `vin_audit_log`（spec §7.2 字段与索引）
- [ ] BOM 增加 audit 模块坐标
- [ ] `mvn -P '!jdk-17' -q -DskipTests install` BUILD SUCCESS
- [ ] Commit: `build(audit): add vincent-audit module skeleton and init SQL`

---

### Task 2: audit-domain — 异常与校验

**Files:**
- Create: `AuditErrorCode.java`, `AuditException.java`
- Create: 字段长度常量/校验 helper（action/resource 非空、operator 规则在 application）
- Test: `AuditExceptionTest.java`

- [ ] 错误码：INVALID_ARGUMENT, TENANT_CONTEXT_MISSING, PERMISSION_DENIED, SCHEMA_MISSING, SCHEMA_VERSION_MISMATCH, CONFIGURATION_INVALID
- [ ] Commit: `feat(audit): add domain exceptions`

---

### Task 3: audit-application — AuditService（TDD）

**Files:**
- Create: `AuditService`, `DefaultAuditService`, `AuditRecordCommand`, `AuditSearchQuery`, `AuditRecordView`
- Create: `AuditContextProvider`（optional port）
- Create: `AuditPermission` enum（AUDIT_VIEW implements VincentPermission）
- Create: `AuditRepository` port
- Test: `DefaultAuditServiceTest`（fake 端口覆盖 record/search/tenant/operator/fail-fast/permission）

- [ ] record：解析 tenant（Provider 或显式）、operator 校验、context 字段、fail-fast 行为
- [ ] search：分页限制、AUDIT_VIEW 权限、可选 tenantId
- [ ] Commit: `feat(audit): add AuditService application layer`

---

### Task 4: audit-infra-mybatis — 持久化

**Files:**
- Create: PO、Mapper、MybatisAuditRepository
- Test: `MybatisAuditRepositoryIT`（Testcontainers MySQL）

- [ ] insert + 分页 search（tenant/operator/action/resource/time 过滤）
- [ ] Commit: `feat(audit): add MyBatis audit repository`

---

### Task 5: audit-boot2-starter — 自动装配

**Files:**
- Create: `AuditProperties`, `AuditCoreAutoConfiguration`, `AuditSchemaValidator`（委托 VincentSchemaValidator）
- Create: `AuditInfrastructureResolver`（委托 common，mapper package 配置）
- Create: `META-INF/spring.factories`
- Test: `AuditCoreAutoConfigurationTest`, `AuditSchemaValidatorIT`

- [ ] 条件 Bean、enabled 开关、limits 校验、Schema 校验
- [ ] Commit: `feat(audit): add Boot 2 starter auto-configuration`

---

### Task 6: audit-web — 只读管理 API

**Files:**
- Create: `AuditAdminController`（GET search only）
- Create: `AuditAdminPageController`, `AuditWebExceptionHandler`, properties
- Create: `AuditAdminWebAutoConfiguration`, disabled 配置
- Test: controller + auto-config tests

- [ ] API 路径 `${vincent.audit.admin.api-path}`；权限 AUDIT_VIEW
- [ ] Commit: `feat(audit): add read-only admin web layer`

---

### Task 7: audit-admin-ui — 只读 SPA

**Files:**
- Create: Vue 3 项目（镜像 dict admin-ui 构建链）
- Views: 审计列表 + 筛选（tenant/operator/action/resource/时间）
- Test: vitest 冒烟

- [ ] 打包进 boot2-starter `META-INF/resources/audit-admin/`
- [ ] Commit: `feat(audit): add read-only audit admin UI`

---

### Task 8: example-boot2 与文档

**Files:**
- Create: `vincent-audit-example-boot2`（Web + audit starter + 演示 Provider）
- Test: `AuditExampleIT`（record + admin search 端到端）
- Create: `vincent-audit/README.md`, `docs/INTEGRATION.md`
- Update: 根 README、BOM 说明

- [ ] `mvn -P '!jdk-17' test` 全量通过
- [ ] Commit: `feat(audit): add example host and integration docs`

---

## Spec Coverage Self-Review

| Checklist | Task |
| --- | --- |
| SQL + Schema | 1, 5 |
| record/search | 3, 4 |
| 权限 + 可选 tenant | 3, 6 |
| fail-fast | 3, 5 |
| 事务 rollback | 3, 4 IT |
| 管理页 | 6, 7 |
| example IT | 8 |
| 全量 test | 8 |

---

## 后续 Phase 计划文档

| Phase | 计划 |
| --- | --- |
| Phase 2 `@Audited` AOP | [2026-08-15-vincent-audit-phase2-aop.md](2026-08-15-vincent-audit-phase2-aop.md) |
| Phase 3 ID / Export | [2026-08-15-vincent-common-phase3-id-export.md](2026-08-15-vincent-common-phase3-id-export.md) |
| Phase 4 Region | [2026-08-15-vincent-region-phase4-query.md](2026-08-15-vincent-region-phase4-query.md) |

路线图总览见 [../specs/2026-08-15-vincent-tools-roadmap-design.md](../specs/2026-08-15-vincent-tools-roadmap-design.md)。
