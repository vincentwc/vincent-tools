# Vincent Audit 接入使用说明

本文面向**业务系统宿主**，说明如何把 Vincent Audit 作为嵌入式组件接入 Spring Boot 2.2 / Java 8 应用，并完成数据库初始化、显式审计写入与只读检索。

> 产品概览见 [`../README.md`](../README.md)。仓库内可运行示例见 `vincent-audit-example-boot2`（非发布制品）。

---

## 1. 适用场景

| 场景 | 是否需要 Web | 是否需要管理端 | 说明 |
| --- | --- | --- | --- |
| 业务代码注入 `AuditService.record()` 写审计 | 否 | 否 | **Phase 1 核心路径**；批处理/消息消费用显式 tenant |
| 运维/合规在浏览器检索审计 | 是（Servlet） | 是 | 只读；需 `spring-boot-starter-web` + 宿主权限适配器 |
| `@Audited` 注解糖层 | 否/是均可 | 否 | **Phase 2**；第一版请显式调用 `record()` |

**不提供**：Flyway/Liquibase 自动建表、匿名公共查询 HTTP API、审计记录的创建/修改/删除管理 API、自动 TTL 清理。

---

## 2. 环境要求

| 项 | 要求 |
| --- | --- |
| JDK | 8（与宿主一致；编译目标 1.8） |
| Spring Boot | 2.2.x（组件在 2.2.6 验证） |
| MyBatis-Plus | 3.3.2 |
| MySQL | 5.7+（或兼容 5.7 的发行版） |
| Web（仅管理端） | Servlet + Spring MVC（`DispatcherServlet`） |

可与 `vincent-dict` 共用同一 MySQL 库与 `@Primary` DataSource。

---

## 3. 快速开始（写入 + 检索）

### 3.1 Maven 依赖

**不要**依赖根父 POM `vincent-tools`，也**不要**直接依赖 `vincent-audit-domain` / `application` / `infra-mybatis`。

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
        <artifactId>vincent-audit-boot2-starter</artifactId>
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
  < vincent-audit/sql/mysql/1.0.0/001-init.sql
```

脚本创建 `vin_audit_meta`、`vin_audit_log`，写入 schema 版本 `1`。

启动时组件**只读校验**：表存在且 `vin_audit_meta.id = 1` 的版本为 `1`。缺表 → `SCHEMA_MISSING`；版本不符 → `SCHEMA_VERSION_MISMATCH`。

### 3.3 最小配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/your_db?useSSL=false&characterEncoding=utf8
    username: your_user
    password: your_password
    driver-class-name: com.mysql.jdbc.Driver

vincent:
  audit:
    enabled: true
    fail-fast: true   # false：写入失败只打 error 日志，不抛异常
```

### 3.4 宿主端口（必需 / 可选）

| 端口 | 必需性 | 说明 |
| --- | --- | --- |
| `OperatorProvider` | **record 必需** | `operator_id` 始终来自 Provider，不可由 command 伪造 |
| `PermissionProvider` | 管理端 / `search()` 必需 | 权限码 `AUDIT_VIEW` |
| `TenantProvider` | 可选 | 无显式 tenant 的 record/search 场景 |
| `AuditContextProvider` | 可选 | 提供 `client_ip` / `user_agent` / `trace_id` |

```java
@Configuration
public class AuditAdapters {

    @Bean
    OperatorProvider operatorProvider() {
        return () -> currentLoginUserId(); // 非空，trim 后非空，最长 64 字符
    }

    @Bean
    PermissionProvider permissionProvider() {
        return (permission, targetTenantId) ->
                yourAuthzService.isAllowed(permission.name(), targetTenantId.orElse(null));
    }

    @Bean
    TenantProvider tenantProvider() {
        return () -> Optional.of(currentTenantIdFromContext());
    }

    @Bean
    AuditContextProvider auditContextProvider() {
        return new AuditContextProvider() {
            public String clientIp() { return requestClientIp(); }
            public String userAgent() { return requestUserAgent(); }
            public String traceId() { return currentTraceId(); }
        };
    }
}
```

定时任务/消息消费无登录用户时，宿主应在任务上下文中让 `OperatorProvider` 返回系统标识（如 `SYSTEM`），audit **不做 fallback**。

### 3.5 在业务代码中使用

```java
@Service
public class OrderService {
    private final AuditService auditService;

    public OrderService(AuditService auditService) {
        this.auditService = auditService;
    }

    @Transactional
    public void confirmOrder(long orderId) {
        // ... 业务逻辑 ...
        auditService.record(new AuditRecordCommand(
                "UPDATE",
                "ORDER",
                String.valueOf(orderId),
                Optional.empty(),              // Web 线程：走 TenantProvider
                "{\"status\":\"NEW\"}",
                "{\"status\":\"CONFIRMED\"}"
        ));
    }

    public void auditBatchJob(String tenantId, long orderId) {
        auditService.record(new AuditRecordCommand(
                "UPDATE",
                "ORDER",
                String.valueOf(orderId),
                Optional.of(tenantId),         // 批处理：显式 tenant，不走 Provider
                null,
                "{\"status\":\"DONE\"}"
        ));
    }
}
```

**事务语义**：`record()` 加入当前 Spring 事务（若存在），与业务同 commit/rollback。

**JSON 契约**：`before_json` / `after_json` 为 opaque JSON；只记变更字段或摘要，不塞完整实体图；超出 MySQL TEXT（约 64KB）时写入失败 → `INVALID_ARGUMENT`。

---

## 4. 启用管理端（可选，只读）

### 4.1 额外依赖与条件

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

须同时满足：

1. Servlet Web 应用
2. `vincent.audit.admin.enabled=true`
3. 宿主注册 `OperatorProvider`、`PermissionProvider`

```yaml
vincent:
  audit:
    admin:
      enabled: true
      base-path: /audit-admin
      api-path: /vincent/audit/admin/api/v1
```

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `base-path` | `/audit-admin` | SPA 页面与静态资源路径 |
| `api-path` | `/vincent/audit/admin/api/v1` | 管理 REST API 前缀 |

### 4.2 访问地址

| 用途 | URL |
| --- | --- |
| 管理页面 | `GET {base-path}`，默认 `http://host:port/audit-admin` |
| 检索 API | `GET {api-path}/records` |

页面 HTML 会在脚本前注入：

```html
<base href="/audit-admin/">
<script>window.__VIN_AUDIT_CONFIG__={"apiPath":"/vincent/audit/admin/api/v1","historyBase":"/audit-admin"};</script>
```

### 4.3 管理端 REST API

前缀：`{api-path}`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/records` | 分页检索（只读） |

**查询参数**（均可选，除分页外）：

| 参数 | 说明 |
| --- | --- |
| `tenantId` | 有值：只查该租户；无值：查全部（由 RBAC 决定是否允许） |
| `operatorId` | 操作人精确匹配 |
| `action` | 动作精确匹配 |
| `resourceType` | 资源类型精确匹配 |
| `resourceId` | 资源 ID 精确匹配 |
| `createdFrom` / `createdTo` | ISO-8601 时间范围 |
| `page` / `size` | 分页；默认 size=20，最大 100 |

**权限**：`search()` 与 API 均校验 `PermissionProvider.hasPermission(AUDIT_VIEW, tenantScope)`；无 `tenantId` 时 scope 为 `Optional.empty()`。

### 4.4 安全（宿主负责）

Starter **不实现**登录、会话、CSRF。生产环境须由宿主保护 `{base-path}/**` 与 `{api-path}/**`。

---

## 5. 配置参考

```yaml
vincent:
  audit:
    enabled: true
    fail-fast: true
    data-source-bean-name:           # 多数据源时与下面两项一起配置
    sql-session-factory-bean-name:
    transaction-manager-bean-name:
    admin:
      enabled: false
      base-path: /audit-admin
      api-path: /vincent/audit/admin/api/v1
    limits:
      default-page-size: 20
      max-page-size: 100
```

**数据源选择**：规则与 dict 相同（单 DataSource / `@Primary` / 显式三件套）。

### MySQL 运行期权限（应用账号）

```sql
GRANT SELECT ON information_schema.TABLES TO 'app'@'%';
GRANT SELECT ON your_db.vin_audit_meta TO 'app'@'%';
GRANT SELECT, INSERT ON your_db.vin_audit_log TO 'app'@'%';
```

DDL 由 DBA/发布流程执行；应用账号不需要 CREATE/ALTER/DROP。

---

## 6. 异常代码

```text
INVALID_ARGUMENT
TENANT_CONTEXT_MISSING
PERMISSION_DENIED
SCHEMA_MISSING
SCHEMA_VERSION_MISMATCH
CONFIGURATION_INVALID
```

---

## 7. 常见问题

### 7.1 启动报 SCHEMA_MISSING / SCHEMA_VERSION_MISMATCH

未执行或未完成 `001-init.sql`。按第 3.2 节补跑脚本。

### 7.2 record() 报 TENANT_CONTEXT_MISSING

未传 `targetTenantId` 且未注册 `TenantProvider`，或 Provider 当前无租户。批处理请显式传 tenant。

### 7.3 record() 报 invalid operator

`OperatorProvider.currentOperatorId()` 为 null、空串或超长。批处理请在任务上下文中设置系统操作人。

### 7.4 fail-fast=false 仍看到 error 日志

预期行为：异常被吞掉并打 error 日志，业务不中断。

### 7.5 管理页空白 / assets 404

第一版请保持默认 `base-path: /audit-admin`。查看页面源码应含 `<base href="/audit-admin/">`；Network 中脚本为 `/audit-admin/assets/*.js` 且 200。

---

## 8. 接入验收清单

**核心能力**

- [ ] BOM + `vincent-audit-boot2-starter` 引入，未直接依赖内部模块
- [ ] 已执行 `001-init.sql`
- [ ] 应用启动无 schema 错误
- [ ] `OperatorProvider` 已注册，`record()` 可写入
- [ ] `search()` / 管理 API 需 `AUDIT_VIEW` 权限

**管理端（若启用）**

- [ ] 已引入 `spring-boot-starter-web`
- [ ] `GET /audit-admin` 页面正常
- [ ] `GET {api-path}/records` 返回 200（经宿主鉴权后）

---

## 9. 本地演示

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS vincent_audit DEFAULT CHARACTER SET utf8mb4;"
mysql -u root -p vincent_audit < vincent-audit/sql/mysql/1.0.0/001-init.sql

mvn -pl vincent-audit/vincent-audit-example-boot2 -am package -DskipTests
# IDEA 运行 AuditExampleApplication
```

访问：`http://localhost:8080/audit-admin`

全量测试（需 Docker）：

```bash
mvn -P '!jdk-17' verify
```

---

## 10. Schema 升级

- 当前版本：`1`，脚本 `sql/mysql/1.0.0/001-init.sql`
- **应用永不执行 DDL**；版本不匹配时启动失败并提示所需 SQL 路径
- 第一版永久保留审计数据，无自动清理
