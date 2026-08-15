# vincent-tools

可持续扩展的通用 Java 工具仓库。当前已交付 `vincent-dict`（字典）、`vincent-audit`（操作审计）、`vincent-region`（省市区查询）与 `vincent-common` 纯库（ID / Export）；共享模块 `vincent-host-ports`、`vincent-common-core`、`vincent-common-web`、`vincent-common-cache-redis` 供各嵌入式组件复用。

**整体架构**见 [docs/architecture/README.md](docs/architecture/README.md)。

## 三种制品，不要混用

| 制品 | 坐标 | 用途 |
| --- | --- | --- |
| 根父 POM | `com.vincent.tools:vincent-tools` | 本仓库的内部父工程与源码聚合。只给本仓库构建使用。 |
| BOM | `com.vincent.tools:vincent-tools-bom` | 业务系统在 `dependencyManagement` 中 `import`，对齐已发布 Vincent 制品版本。 |
| 功能 Starter | 例如 `com.vincent.tools:vincent-dict-boot2-starter` | 业务系统真正引入的功能依赖。 |

业务系统不要把根父 POM 当作功能依赖，也不要继承它来接入某个工具。接入方式是：导入 BOM，再按需添加对应功能 Starter。

## Vincent Dict

字典查询组件。消费者说明见 [vincent-dict/README.md](vincent-dict/README.md)，接入指南见 [vincent-dict/docs/INTEGRATION.md](vincent-dict/docs/INTEGRATION.md)，架构见 [vincent-dict/docs/ARCHITECTURE.md](vincent-dict/docs/ARCHITECTURE.md)。

兼容性基线：Java 8、Spring Boot `2.2.6.RELEASE`、MyBatis-Plus `3.3.2`、MySQL 5.7+。核心查询不依赖 Redis；跨实例缓存是按需启用的额外 Starter，需要宿主提供 `StringRedisTemplate`，默认 TTL 60 秒。Redis 健康时写入后立即可见；Redis 不可用时回退 MySQL，第一版不提供强一致性。

## Vincent Audit

操作审计组件（Phase 1–2）：显式 `AuditService.record()` 写入、分页检索、可选只读管理页、可选 `@Audited` AOP。消费者说明见 [vincent-audit/README.md](vincent-audit/README.md)，接入指南见 [vincent-audit/docs/INTEGRATION.md](vincent-audit/docs/INTEGRATION.md)，架构见 [vincent-audit/docs/ARCHITECTURE.md](vincent-audit/docs/ARCHITECTURE.md)。

与 dict 共用 `vincent-host-ports`（`TenantProvider`、`OperatorProvider`、`PermissionProvider`），宿主只需实现一次 Provider。表前缀 `vin_audit_*`，可与 `vin_dict_*` 同库。可选 `@Audited` AOP 见 `vincent-audit-aop-boot2-starter`。

## Vincent ID / Export（Phase 3 纯库）

| 模块 | 说明 |
| --- | --- |
| `vincent-id-core` | 雪花 ID、`BusinessNumberFormatter`、`SegmentAllocator` 端口 |
| `vincent-export-core` | EasyExcel 流式读写封装 |

接入指南见 [vincent-common/docs/INTEGRATION.md](vincent-common/docs/INTEGRATION.md)，架构见 [vincent-common/docs/ARCHITECTURE.md](vincent-common/docs/ARCHITECTURE.md)。各模块 API 摘要见 `vincent-common/vincent-id-core/README.md`、`vincent-common/vincent-export-core/README.md`。

## Vincent Region

中国省市区三级树查询（Phase 4）：`RegionQueryService.findByCode` / `listChildren`、可选只读管理 API。说明见 [vincent-region/README.md](vincent-region/README.md)，接入指南见 [vincent-region/docs/INTEGRATION.md](vincent-region/docs/INTEGRATION.md)，架构见 [vincent-region/docs/ARCHITECTURE.md](vincent-region/docs/ARCHITECTURE.md)。
