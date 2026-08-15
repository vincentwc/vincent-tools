# Vincent Tools 需求清单

本文是 **vincent-tools 当前需求的单一入口**：已交付能力、明确不做项、后续可选增强、未启动规划。设计细节见各 spec / plan / INTEGRATION 文档。

> 最后更新：2026-08-15（Phase 0–4 代码与文档均已合入 `main`）

---

## 1. 项目定位（不变）

| 项 | 说明 |
| --- | --- |
| 目标 | 可复用 Java 工具仓库；业务系统 `import vincent-tools-bom` 后按需引入 Starter 或纯库 |
| 目标场景 | **多租户 SaaS** |
| 工具边界 | 不拥有用户/租户/RBAC/认证；通过 **宿主端口** 接入 |
| 兼容基线 | Java 8、Spring Boot 2.2.6、MyBatis-Plus 3.3.2、MySQL 5.7+ |
| 嵌入式契约 | 手工 SQL 初始化 → 启动 **只读 Schema 校验** → 宿主实现 Provider |

**路线图设计**：[superpowers/specs/2026-08-15-vincent-tools-roadmap-design.md](superpowers/specs/2026-08-15-vincent-tools-roadmap-design.md)

---

## 2. 需求总览

### 2.1 已交付（✅）

| ID | 模块 | 类型 | 核心能力 | 文档 |
| --- | --- | --- | --- | --- |
| R-DICT | `vincent-dict` | 嵌入式 | 字典查询、租户追加项、管理端 SPA、可选 Redis 缓存 | [INTEGRATION](../vincent-dict/docs/INTEGRATION.md) · [ARCHITECTURE](../vincent-dict/docs/ARCHITECTURE.md) · [设计 spec](superpowers/specs/2026-08-14-vincent-dict-design.md) |
| R-COMMON-0 | `vincent-host-ports` 等 4 模块 | 共享库 | 宿主端口、Schema 校验、ApiResponse、Redis 缓存基座 | [ARCHITECTURE](../vincent-common/docs/ARCHITECTURE.md) · [Phase 0 计划](superpowers/plans/2026-08-15-vincent-tools-phase0-common.md) |
| R-AUDIT-1 | `vincent-audit` core | 嵌入式 | `AuditService.record/search`、只读管理 SPA | [INTEGRATION](../vincent-audit/docs/INTEGRATION.md) · [Phase 1 计划](superpowers/plans/2026-08-15-vincent-audit-phase1-core.md) |
| R-AUDIT-2 | `vincent-audit-aop` | 可选 Starter | `@Audited` + SpEL + `AuditPayloadExtractor` | [INTEGRATION §3.6](../vincent-audit/docs/INTEGRATION.md) · [Phase 2 计划](superpowers/plans/2026-08-15-vincent-audit-phase2-aop.md) |
| R-ID | `vincent-id-core` | 纯库 | 雪花 ID、业务编号格式化、号段端口 | [INTEGRATION](../vincent-common/docs/INTEGRATION.md) · [Phase 3 计划](superpowers/plans/2026-08-15-vincent-common-phase3-id-export.md) |
| R-EXPORT | `vincent-export-core` | 纯库 | EasyExcel 流式读写 | 同上 |
| R-REGION | `vincent-region` | 嵌入式 | 省市区三级树查询、只读管理 REST（无 SPA） | [INTEGRATION](../vincent-region/docs/INTEGRATION.md) · [Phase 4 计划](superpowers/plans/2026-08-15-vincent-region-phase4-query.md) |

**验证**：Phase 2–4 计划 vs 代码对照见 [architecture/PHASE2-4-VERIFICATION.md](architecture/PHASE2-4-VERIFICATION.md)。全量构建：`mvn -P '!jdk-17' verify`。

### 2.2 后续可选（📋 未排期，非当前任务）

来自各 Phase 计划「后续可选」小节，**不影响第一版验收**：

| 来源 | 增强项 | 说明 |
| --- | --- | --- |
| Audit AOP | `afterCommit` 与 `@Transactional` 顺序的更多 IT | 边界场景测试补全 |
| Audit AOP | `@Audited` + `fail-fast=false` 组合行为文档 | 运维/排错指南 |
| ID | `BusinessNumberFormatter` 扩展占位符（如 `{yyyyMM}`） | 模板能力增强 |
| Export | CSV 支持、大文件分批 write 回调 | 导出场景扩展 |
| Region | `001-data.sql` 扩展全国全量国标 | 数据交付，非代码 |
| Region | `vincent-region-admin-ui` 只读 SPA | 镜像 audit/dict 管理页体验 |
| Region | 管理端按名称精确匹配搜索 | spec 首版明确不做模糊搜索 |
| Dict | 管理操作自动埋点 audit | 在 `DefaultDictAdminService` 可选注入 `AuditService` |
| 全局 | Spring Boot 3 Starter | 路线图保留边界，未实现 |

### 2.3 已规划未启动（🔮 Phase 5+）

路线图 §8.4 概要，**尚无实施计划**：

| ID | 模块 | 概要需求 | 参考 |
| --- | --- | --- | --- |
| R-FILE | `vincent-file` | 附件元数据表 + `FileStorage` 端口（本地/OSS 宿主实现）；可选管理页；不做图片处理/病毒扫描 | [路线图 §8.4](superpowers/specs/2026-08-15-vincent-tools-roadmap-design.md) |

### 2.4 明确不做（🚫 路线图范围外）

汇总自路线图 §4.3、§13 及各组件 spec：

| 类别 | 不做项 |
| --- | --- |
| IAM | 用户/租户/RBAC 维护、认证、会话、CSRF |
| 平台能力 | 工作流审批、消息通知、独立远程服务中心 |
| 数据迁移 | Flyway/Liquibase 自动建表、应用启动执行 DDL |
| 技术栈 | Spring Boot 3 Starter（第一版） |
| Audit | 内置 action/resource 枚举、自动 TTL 清理、operator fallback、仅 AOP 无显式 API |
| ID | 内置号段持久化、Spring 自动分配 workerId |
| Export | 双库（EasyExcel + 原生 POI API） |
| Region | 国际地区、业务自定义层级、模糊搜索、应用内增删改、数据打进 Starter |
| Dict（首版） | 组织树/地区树（已由 region 独立承担）、导入导出（export 纯库单独提供） |

---

## 3. 分 Phase 需求与验收

### Phase 0 — Common 抽取 ✅

| 需求 | 验收 |
| --- | --- |
| 从 dict 抽取 host-ports、common-web、common-core、common-cache-redis | dict 行为零变化 |
| `VincentPermission` + 统一 `PermissionProvider` | 宿主 RBAC 零迁移 |
| BOM 注册 common 坐标 | `mvn -P '!jdk-17' test` 全绿 |

计划：[phase0-common.md](superpowers/plans/2026-08-15-vincent-tools-phase0-common.md) · PR #3

### Phase 1 — Audit Core ✅

| 需求 | 验收 |
| --- | --- |
| `vin_audit_*` 表 + Schema 校验 | 手工 SQL + 启动校验 |
| `AuditService.record()` / `search()` | example IT |
| fail-fast、事务语义、分页限制 | 单测 + IT |
| 只读管理 SPA + API | `/audit-admin` |
| `AUDIT_VIEW` 权限 | 403 IT |

计划：[audit-phase1-core.md](superpowers/plans/2026-08-15-vincent-audit-phase1-core.md) · PR #4–#7

### Phase 2 — Audit AOP ✅

| 需求 | 验收 |
| --- | --- |
| 独立 `vincent-audit-aop-boot2-starter` | 不修改 Phase 1 契约 |
| `@Audited` + SpEL + Extractor | 单测 + example IT |
| `afterCommit` 可配置 | `AuditedRecordPublisherTest` |

计划：[audit-phase2-aop.md](superpowers/plans/2026-08-15-vincent-audit-phase2-aop.md) · PR #8

### Phase 3 — ID / Export ✅

| 需求 | 验收 |
| --- | --- |
| 雪花 ID（epoch 2020-01-01，worker 0–31） | 单测 |
| `BusinessNumberFormatter` + `SegmentAllocator` 端口 | 单测 |
| `VincentExcelExporter` 流式读写 | 单测 |
| 无 Spring 自动装配 | 纯库 |

计划：[common-phase3-id-export.md](superpowers/plans/2026-08-15-vincent-common-phase3-id-export.md) · PR #9

### Phase 4 — Region ✅

| 需求 | 验收 |
| --- | --- |
| `001-init.sql` + `001-data.sql`（样本数据） | MySQL IT |
| `findByCode` / `listChildren` | 单测 + example IT |
| 只读 REST + `REGION_VIEW` | example IT + 403 |
| 无 SPA、无写入 API、无 tenant | 模块结构 |

计划：[region-phase4-query.md](superpowers/plans/2026-08-15-vincent-region-phase4-query.md) · PR #10

---

## 4. 宿主适配需求（接入任一嵌入式组件）

业务系统**一次性**实现（详见各 INTEGRATION）：

| 端口 | Dict | Audit core | Audit AOP | Region 查询 | Region admin |
| --- | --- | --- | --- | --- | --- |
| `TenantProvider` | 可选/常用 | 可选 | 同左 | 不需要 | 不需要 |
| `OperatorProvider` | 管理端 | **record 必需** | 同左 | 不需要 | 不需要 |
| `PermissionProvider` | 管理端 | 管理/search | 不需要 | 不需要 | **必需** |
| `AuditContextProvider` | — | 可选 | 可选 | — | — |
| `AuditPayloadExtractor` | — | — | 可选 | — | — |
| `DataSource` + MySQL | 必需 | 必需 | 同左 | 必需 | 同左 |
| 手工 SQL | 必需 | 必需 | 同左 | 必需 | 同左 |

权限码一览：

```text
Dict:   DICT_VIEW, DICT_CREATE, DICT_UPDATE, ...（见 dict INTEGRATION）
Audit:  AUDIT_VIEW
Region: REGION_VIEW
```

---

## 5. 文档索引

| 类型 | 路径 |
| --- | --- |
| **需求清单（本文）** | [docs/REQUIREMENTS.md](REQUIREMENTS.md) |
| 整体架构 | [docs/architecture/README.md](architecture/README.md) |
| Phase 2–4 验证报告 | [docs/architecture/PHASE2-4-VERIFICATION.md](architecture/PHASE2-4-VERIFICATION.md) |
| 路线图设计 | [docs/superpowers/specs/2026-08-15-vincent-tools-roadmap-design.md](superpowers/specs/2026-08-15-vincent-tools-roadmap-design.md) |
| Dict 设计 | [docs/superpowers/specs/2026-08-14-vincent-dict-design.md](superpowers/specs/2026-08-14-vincent-dict-design.md) |
| 实施计划 | [docs/superpowers/plans/](superpowers/plans/) |
| 接入指南 | 各模块 `docs/INTEGRATION.md` |
| 架构图 | 各模块 `docs/ARCHITECTURE.md` |

---

## 6. 第一版路线图验收（§12）— 全部满足 ✅

1. 设计文档 committed，BOM 与结构文档化
2. Phase 0 后 dict 无回归
3. Phase 1 后业务可仅引入 audit Starter + 手工 SQL
4. 审计写入不依赖 AOP
5. 嵌入式组件遵循「手工 SQL + 只读校验 + 宿主端口」
6. 业务系统不必内置 IAM

**当前状态**：第一版路线图（Phase 0–4）**需求已闭合**；无 open PR；下一步为 §2.2 可选增强或 §2.3 `vincent-file` 规划，需单独立项。
