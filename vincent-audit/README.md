# vincent-audit

嵌入式操作审计组件：显式 `AuditService.record()` / `search()`、MySQL 持久化、Boot 2 Starter、可选只读管理页。

**宿主接入说明见 [docs/INTEGRATION.md](docs/INTEGRATION.md)。**

## 模块

| 模块 | 说明 |
| --- | --- |
| `vincent-audit-domain` | 异常与字段约束 |
| `vincent-audit-application` | `AuditService` 应用层 |
| `vincent-audit-infra-mybatis` | MyBatis 持久化 |
| `vincent-audit-web` | 只读管理 API 与 SPA 注入 |
| `vincent-audit-admin-ui` | 管理页静态资源（Vite 构建） |
| `vincent-audit-boot2-starter` | Spring Boot 2 自动装配 |
| `vincent-audit-example-boot2` | 仓库内端到端验收（不进 BOM） |

## 快速开始

1. 执行 `sql/mysql/1.0.0/001-init.sql`
2. 引入 BOM + `vincent-audit-boot2-starter`
3. 实现 `OperatorProvider`；启用管理页时需 `PermissionProvider`
4. 可选：`TenantProvider`、`AuditContextProvider`

```yaml
vincent:
  audit:
    enabled: true
    admin:
      enabled: true
```

管理页默认 `/audit-admin`，API 默认 `/vincent/audit/admin/api/v1`。

## 验收

```bash
mvn -P '!jdk-17' verify
```

详见 `vincent-audit-example-boot2` 集成测试与 [docs/INTEGRATION.md](docs/INTEGRATION.md)。
