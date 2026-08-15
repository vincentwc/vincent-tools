# Vincent Audit Phase 2 — @Audited AOP 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Status:** ✅ 已完成（PR #8 merged）

**Goal:** 交付可选 `@Audited` 注解糖层：独立 `vincent-audit-aop` + `vincent-audit-aop-boot2-starter`，内部仍调用 Phase 1 的 `AuditService.record()`；复杂场景继续显式 API。

**Architecture:** 镜像 `vincent-dict-cache-redis-boot2-starter` 可选 Starter 模式——宿主按需额外引入 AOP artifact；`@ConditionalOnBean(AuditService.class)` + `vincent.audit.aop.enabled` 开关。SpEL 解析 `resourceId` / `targetTenantId`；`AuditPayloadExtractor` 按 `resourceType` 提供 before/after JSON；`afterCommit=true` 时事务提交后写入。

**Tech Stack:** Java 8、Spring Boot 2.2.6.RELEASE、Spring AOP + AspectJ、Spring Expression Language。

**Spec 来源：** `docs/superpowers/specs/2026-08-15-vincent-tools-roadmap-design.md` §7.1、§7.4、§10 Phase 2。

## Global Constraints

- 不得修改 Phase 1 `AuditService` 契约与默认行为。
- AOP Starter **独立 artifact**，不合并进 `vincent-audit-boot2-starter`。
- `@Audited` 仅在方法**成功返回**后触发；异常时不写审计。
- `operator_id` 仍来自 `OperatorProvider`（经 `AuditService.record()`），不由注解传入。
- `vincent.audit.aop.enabled` 默认 `true`（Starter 在 classpath 且存在 `AuditService` 时生效）。
- 全量验证：`mvn -P '!jdk-17' verify`。

## Phase 2 验收 checklist

- [x] `@Audited` 注解 + SpEL `resourceId` / 可选 `targetTenantId`
- [x] `AuditPayloadExtractor` 注册与按 `resourceType` 匹配
- [x] `afterCommit=false`（默认，同事务）与 `afterCommit=true`（提交后写入）
- [x] `vincent.audit.aop.enabled=false` 可关闭切面
- [x] example-boot2 覆盖 `@Audited` 端到端场景
- [x] 全量 reactor 测试全绿

## 前置（Phase 1 完成后）

- [x] Starter/Web 单测补全（PR #7，`AuditCoreAutoConfigurationTest`、`AuditSchemaValidatorIT`、Web 三层测试）

---

### Task 1: vincent-audit-aop 纯 Java 模块

**Files:**
- Create: `vincent-audit/vincent-audit-aop/pom.xml`
- Create: `@Audited.java` — `action`, `resourceType`, `resourceId`（SpEL）, `targetTenantId`（SpEL，可选）, `afterCommit`
- Create: `AuditPayload.java`, `AuditPayloadExtractor.java`

- [x] 包名 `com.vincent.tools.audit.aop.*`，无 Spring 依赖
- [x] 父 POM `vincent-audit/pom.xml` 注册模块
- [x] Commit: `feat(audit): add @Audited annotation module`

---

### Task 2: vincent-audit-aop-boot2-starter

**Files:**
- Create: `AuditAopAutoConfiguration.java`, `AuditAopProperties.java`
- Create: `AuditedAspect.java`, `AuditedSpelEvaluator.java`, `AuditedRecordPublisher.java`
- Create: `META-INF/spring.factories`
- Test: `AuditedAspectTest`, `AuditedRecordPublisherTest`, `AuditAopAutoConfigurationTest`

- [x] `@AutoConfigureAfter(AuditCoreAutoConfiguration)`
- [x] `@EnableAspectJAutoProxy` + `@Aspect` 注册
- [x] `ObjectProvider<AuditPayloadExtractor>` 收集宿主 Extractor
- [x] BOM + 根 POM `dependencyManagement` 增加 aop 坐标
- [x] Commit: `feat(audit): add optional @Audited AOP starter`

---

### Task 3: example-boot2 与文档

**Files:**
- Modify: `vincent-audit-example-boot2/pom.xml` — 依赖 aop-boot2-starter
- Create: `ExampleAuditedService.java`
- Modify: `AuditExampleIT.java` — `@Audited` 写入 + admin 检索
- Modify: `vincent-audit/docs/INTEGRATION.md` §3.6、`README.md`、根 `README.md`

- [x] `mvn -P '!jdk-17' verify` 全绿
- [x] Commit: 合入 PR #8

---

## Spec Coverage Self-Review

| Checklist | Task |
| --- | --- |
| @Audited + SpEL | 1, 2 |
| AuditPayloadExtractor | 1, 2 |
| afterCommit 时机 | 2 |
| 可选 Starter 模式 | 2 |
| example IT | 3 |
| 文档 | 3 |

## 相关 PR

| PR | 说明 |
| --- | --- |
| #7 | test(audit): starter/web 单测补全（Phase 2 前置） |
| #8 | feat(audit): @Audited AOP starter（本 Phase） |

## 后续可选

- AOP `afterCommit` 与业务 `@Transactional` 顺序的更多集成测试
- `@Audited` 失败场景与 `fail-fast=false` 组合行为文档化
