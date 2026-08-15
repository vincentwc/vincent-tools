# vincent-region

中国省市区三级树查询组件：Java `RegionQueryService`、MySQL 参考数据、Boot 2 Starter、可选只读管理 API。

## 模块

| 模块 | 说明 |
| --- | --- |
| `vincent-region-domain` | 异常与字段约束 |
| `vincent-region-application` | `RegionQueryService` |
| `vincent-region-infra-mybatis` | MyBatis 持久化 |
| `vincent-region-web` | 只读管理 REST API |
| `vincent-region-boot2-starter` | Spring Boot 2 自动装配 |
| `vincent-region-example-boot2` | 仓库内端到端验收（不进 BOM） |

## 快速开始

1. 执行 `sql/mysql/1.0.0/001-init.sql`
2. 可选：执行 `sql/mysql/1.0.0/001-data.sql` 导入参考数据
3. 引入 BOM + `vincent-region-boot2-starter`
4. 启用管理 API 时实现 `PermissionProvider`（权限码 `REGION_VIEW`）

```yaml
vincent:
  region:
    enabled: true
    admin:
      enabled: true
```

管理 API 默认 `/vincent/region/admin/api/v1`。

## 验收

```bash
mvn -P '!jdk-17' verify -pl vincent-region/vincent-region-example-boot2 -am
```
