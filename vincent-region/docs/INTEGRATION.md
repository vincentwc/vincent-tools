# Vincent Region 接入使用说明

本文面向**业务系统宿主**，说明如何把 Vincent Region 作为嵌入式组件接入 Spring Boot 2.2 / Java 8 应用，并完成数据库初始化、Java 查询 API 与可选只读管理 REST API。

> 产品概览见 [`../README.md`](../README.md)。架构说明见 [`ARCHITECTURE.md`](ARCHITECTURE.md)。仓库内可运行示例见 `vincent-region-example-boot2`（非发布制品）。

---

## 1. 适用场景

| 场景 | 是否需要 Web | 是否需要管理端 | 说明 |
| --- | --- | --- | --- |
| 业务代码注入 `RegionQueryService` 查省市区 | 否 | 否 | **最常见**；表单级联、地址解析 |
| 运维/前端通过 HTTP 只读查询 | 是（Servlet） | 是 | 仅 REST，**首版无内嵌 SPA** |
| 维护国标数据 | — | — | **首版不提供**写入 API；数据由 DBA 执行 SQL |

**不提供**：Flyway/Liquibase 自动建表、模糊搜索、国际地区、管理端增删改、内嵌 Vue 管理页。

---

## 2. 环境要求

| 项 | 要求 |
| --- | --- |
| JDK | 8（与宿主一致；编译目标 1.8） |
| Spring Boot | 2.2.x（组件在 2.2.6 验证） |
| MyBatis-Plus | 3.3.2 |
| MySQL | 5.7+（或兼容 5.7 的发行版） |
| Web（仅管理 API） | Servlet + Spring MVC（`DispatcherServlet`） |

可与 `vincent-dict`、`vincent-audit` 共用同一 MySQL 库与 `@Primary` DataSource。

---

## 3. 快速开始（查询能力）

### 3.1 Maven 依赖

**不要**依赖根父 POM `vincent-tools`，也**不要**直接依赖 `vincent-region-domain` / `application` / `infra-mybatis`。

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.vincent.tools</groupId>
            <artifactId>vincent-tools-bom</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.vincent.tools</groupId>
        <artifactId>vincent-region-boot2-starter</artifactId>
    </dependency>

    <!-- 宿主自备：数据源 + MyBatis + MySQL 驱动 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-boot-starter</artifactId>
        <version>3.3.2</version>
    </dependency>
    <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
    </dependency>
</dependencies>
```

### 3.2 初始化数据库（手工，仅一次）

对**空库**或已有业务库执行（发布/DBA 流程，**不要**在应用启动时执行）：

```bash
mysql --default-character-set=utf8mb4 -u <user> -p <database> \
  < vincent-region/sql/mysql/1.0.0/001-init.sql

# 可选：导入参考样本数据（北京、广东及下属市/区）
mysql --default-character-set=utf8mb4 -u <user> -p <database> \
  < vincent-region/sql/mysql/1.0.0/001-data.sql
```

脚本创建 `vin_region_meta`、`vin_region`，写入 schema 版本 `1`。

启动时组件**只读校验**：表存在且 `vin_region_meta.id = 1` 的版本为 `1`。缺表 → `SCHEMA_MISSING`；版本不符 → `SCHEMA_VERSION_MISMATCH`。

### 3.3 最小配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/your_db?useSSL=false&characterEncoding=UTF-8
    username: your_user
    password: your_password
    driver-class-name: com.mysql.jdbc.Driver

vincent:
  region:
    enabled: true
```

### 3.4 在业务代码中使用

Region **不依赖** `TenantProvider` / `OperatorProvider`；核心查询无需宿主上下文。

```java
@Service
public class AddressService {
    private final RegionQueryService regionQueryService;

    public AddressService(RegionQueryService regionQueryService) {
        this.regionQueryService = regionQueryService;
    }

    public List<RegionView> provinces() {
        // parentCode 为 null、空串或 "0" 均表示省级
        return regionQueryService.listChildren("0");
    }

    public RegionView cityDetail(String cityCode) {
        return regionQueryService.findByCode(cityCode)
                .orElseThrow(() -> new RegionException(RegionErrorCode.REGION_NOT_FOUND, "not found"));
    }

    public List<RegionView> districts(String cityCode) {
        return regionQueryService.listChildren(cityCode);
    }
}
```

**返回字段**（`RegionView`）：`code`、`name`、`level`（1=省、2=市、3=区）、`parentCode`。不暴露数据库自增 ID。

**编码规则**：国标行政区划代码（如 `440000` 广东省、`440100` 广州市）。

---

## 4. 启用只读管理 REST API（可选）

### 4.1 额外依赖与条件

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

须同时满足：

1. Servlet Web 应用（`spring.main.web-application-type` 不为 `none`）
2. `vincent.region.admin.enabled=true`
3. 宿主注册 `PermissionProvider`（权限码 `REGION_VIEW`）

```yaml
vincent:
  region:
    admin:
      enabled: true
      api-path: /vincent/region/admin/api/v1
```

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `api-path` | `/vincent/region/admin/api/v1` | 只读 REST API 前缀 |

**注意**：与 dict/audit 不同，首版**没有**内嵌 SPA 管理页，仅提供 REST API。

### 4.2 宿主适配器示例

```java
@Configuration
public class RegionAdminAdapters {

    @Bean
    PermissionProvider permissionProvider() {
        return (permission, targetTenantId) ->
                yourAuthzService.isAllowed(permission.name(), targetTenantId.orElse(null));
    }
}
```

**权限枚举**：`REGION_VIEW`（`RegionPermission.REGION_VIEW`）。Region 无租户概念，scope 始终为 `Optional.empty()`。

### 4.3 管理端 REST API

前缀：`{api-path}`（下表省略前缀）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/{code}` | 按编码查询单条；不存在 → `REGION_NOT_FOUND` |
| GET | `/children?parentCode=` | 列出子级；`parentCode` 省略或 `0` → 省级 |

响应包装为 `ApiResponse<T>`（来自 `vincent-common-web`）。

### 4.4 安全（宿主负责）

Starter **不实现**登录、会话、CSRF。生产环境须由宿主保护 `{api-path}/**`（网关、Spring Security 等）。

---

## 5. 配置参考

```yaml
vincent:
  region:
    enabled: true
    data-source-bean-name:           # 多数据源时与下面两项一起配置
    sql-session-factory-bean-name:
    transaction-manager-bean-name:
    admin:
      enabled: false
      api-path: /vincent/region/admin/api/v1
```

**数据源选择**：规则与 dict/audit 相同（单 DataSource / `@Primary` / 显式三件套）。

### MySQL 运行期权限（应用账号）

```sql
GRANT SELECT ON information_schema.TABLES TO 'app'@'%';
GRANT SELECT ON your_db.vin_region_meta TO 'app'@'%';
GRANT SELECT ON your_db.vin_region TO 'app'@'%';
```

DDL 与数据导入由 DBA/发布流程执行；应用账号不需要 INSERT/UPDATE/DELETE。

---

## 6. 异常代码

业务代码可捕获 `RegionException`，按 `errorCode` 分支：

```text
INVALID_ARGUMENT
PERMISSION_DENIED
SCHEMA_MISSING
SCHEMA_VERSION_MISMATCH
CONFIGURATION_INVALID
REGION_NOT_FOUND
```

---

## 7. 常见问题

### 7.1 启动报 SCHEMA_MISSING / SCHEMA_VERSION_MISMATCH

未执行或未完成 `001-init.sql`。按第 3.2 节补跑脚本。

### 7.2 启动报 CONFIGURATION_INVALID（DataSource）

与 dict/audit 相同：未配置数据源、多数据源无唯一 `@Primary`、或显式 bean 名未三项齐配。

### 7.3 中文乱码

JDBC URL 须含 `characterEncoding=UTF-8`（或 `utf8mb4`）；导入 SQL 时使用 `--default-character-set=utf8mb4`。

### 7.4 admin.enabled=true 但启动失败

缺少 `PermissionProvider`，或宿主不是 Servlet Web。

### 7.5 需要全国全量国标数据

首版 `001-data.sql` 仅含样本（北京、广东）。全量数据需 DBA 另行导入，或等待后续版本扩展。

---

## 8. 接入验收清单

**查询能力**

- [ ] BOM + `vincent-region-boot2-starter` 引入，未直接依赖内部模块
- [ ] 已执行 `001-init.sql`；可选执行 `001-data.sql`
- [ ] 应用启动无 schema 错误
- [ ] 注入 `RegionQueryService`，`listChildren("0")` 返回省级列表
- [ ] `findByCode` 可查到已知编码

**管理 API（若启用）**

- [ ] 已引入 `spring-boot-starter-web`
- [ ] `PermissionProvider` 已注册
- [ ] `GET {api-path}/children` 返回 200（经宿主鉴权后）
- [ ] 无权限时返回 403 / `PERMISSION_DENIED`

---

## 9. 本地演示

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS vincent_region DEFAULT CHARACTER SET utf8mb4;"
mysql -u root -p vincent_region < vincent-region/sql/mysql/1.0.0/001-init.sql
mysql -u root -p vincent_region < vincent-region/sql/mysql/1.0.0/001-data.sql

mvn -pl vincent-region/vincent-region-example-boot2 -am package -DskipTests
# IDEA 运行 RegionExampleApplication
```

端到端 IT（需 Docker）：

```bash
mvn -P '!jdk-17' verify -pl vincent-region/vincent-region-example-boot2 -am
```

---

## 10. Schema 升级

- 当前版本：`1`，脚本 `sql/mysql/1.0.0/001-init.sql`
- **应用永不执行 DDL**；版本不匹配时启动失败并提示所需 SQL 路径
