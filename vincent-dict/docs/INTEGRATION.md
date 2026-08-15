# Vincent Dict 接入使用说明

本文面向**业务系统宿主**，说明如何把 Vincent Dict 作为嵌入式组件接入 Spring Boot 2.2 / Java 8 应用，并完成数据库初始化、查询 API 与管理端配置。

> 产品概览与编码规则见 [`../README.md`](../README.md)。仓库内可运行示例见 `vincent-dict-example-boot2`（非发布制品）。

---

## 1. 适用场景

| 场景 | 是否需要 Web | 是否需要管理端 | 说明 |
| --- | --- | --- | --- |
| 业务代码注入 `DictQueryService` 读字典 | 否 | 否 | 最常见；定时任务、消息消费可用显式租户 API |
| 运维/运营在浏览器维护字典 | 是（Servlet） | 是 | 需 `spring-boot-starter-web` + 宿主权限适配器 |
| 多实例共享查询缓存 | 否/是均可 | 否 | 额外引入 Redis Starter |

**不提供**：Flyway/Liquibase 自动建表、公共查询 HTTP API、登录/CSRF 实现。

---

## 2. 环境要求

| 项 | 要求 |
| --- | --- |
| JDK | 8（与宿主一致；编译目标 1.8） |
| Spring Boot | 2.2.x（组件在 2.2.6 验证） |
| MyBatis-Plus | 3.3.2 |
| MySQL | 5.7+（或兼容 5.7 的发行版） |
| Web（仅管理端） | Servlet + Spring MVC（`DispatcherServlet`） |

---

## 3. 快速开始（查询能力）

### 3.1 Maven 依赖

**不要**依赖根父 POM `vincent-tools`，也**不要**直接依赖 `vincent-dict-domain` / `application` / `infra-mybatis`。

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
    <!-- 功能 Starter：唯一需要的 Dict 制品 -->
    <dependency>
        <groupId>com.vincent.tools</groupId>
        <artifactId>vincent-dict-boot2-starter</artifactId>
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

对**空库**执行（发布/DBA 流程，**不要**在应用启动时执行）：

```bash
mysql --default-character-set=utf8mb4 -u <user> -p <database> \
  < vincent-dict/sql/mysql/1.0.0/001-init.sql
```

脚本创建 `vin_dict_meta`、`vin_dict`、`vin_dict_item`，写入 schema 版本 `1`。

启动时组件**只读校验**：表存在且 `vin_dict_meta.id = 1` 的版本为 `1`。缺表 → `SCHEMA_MISSING`；版本不符 → `SCHEMA_VERSION_MISMATCH`。

### 3.3 最小配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/your_db?useSSL=false&characterEncoding=utf8
    username: your_user
    password: your_password
    driver-class-name: com.mysql.jdbc.Driver

vincent:
  dict:
    enabled: true
```

### 3.4 注册租户（多租户宿主）

```java
@Bean
TenantProvider tenantProvider() {
    return () -> Optional.of(currentTenantIdFromContext());
}
```

- 返回的租户 ID：非空、最长 64 字符、**不能**是 `"0"`。
- 未注册 `TenantProvider` 时：单租户模式，只查默认项（内部哨兵 `"0"`）。
- 已注册但当前无租户：无参查询抛 `TENANT_CONTEXT_MISSING`。

### 3.5 在业务代码中使用

```java
@Service
public class OrderService {
    private final DictQueryService dictQueryService;

    public OrderService(DictQueryService dictQueryService) {
        this.dictQueryService = dictQueryService;
    }

    public void renderStatus(String tenantId) {
        // Web 请求线程：用无参 API（走 TenantProvider）
        dictQueryService.listEffectiveItems("ORDER_STATUS");

        // 定时任务 / 消息：用显式租户（不走 TenantProvider）
        dictQueryService.listEffectiveItems("ORDER_STATUS", tenantId);
        dictQueryService.findEffectiveItem("ORDER_STATUS", "CREATED", tenantId);
    }
}
```

**有效项规则**：启用的默认项 + 该租户启用的追加项；按 `sortNo`、`code`、内部 id 升序。字典不存在 → `DICT_NOT_FOUND`；字典停用 → 空列表。

**返回字段**（`DictItemView`）：`code`、`name`、`description`、`sortNo`、`source`（`DEFAULT` / `TENANT`）。

---

## 4. 启用管理端（可选）

### 4.1 额外依赖与条件

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

须同时满足：

1. Servlet Web 应用（`spring.main.web-application-type` 不为 `none`）
2. `vincent.dict.admin.enabled=true`
3. 宿主注册 `OperatorProvider`、`PermissionProvider`
4. （推荐）注册 `TenantDirectory`，否则租户页与租户条目 API 不可用

```yaml
vincent:
  dict:
    admin:
      enabled: true
      base-path: /dict-admin
      api-path: /vincent/dict/admin/api/v1
```

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `base-path` | `/dict-admin` | SPA 页面与静态资源路径 |
| `api-path` | `/vincent/dict/admin/api/v1` | 管理 REST API 前缀 |

### 4.2 宿主适配器示例

```java
@Configuration
public class DictAdminAdapters {

    @Bean
    OperatorProvider operatorProvider() {
        return () -> currentLoginUserId(); // 非空，最长 64 字符
    }

    @Bean
    PermissionProvider permissionProvider() {
        return (permission, targetTenantId) ->
                yourAuthzService.isAllowed(permission.name(), targetTenantId.orElse(null));
    }

    @Bean
    TenantDirectory tenantDirectory() {
        return new YourTenantDirectory(); // 搜索 / 校验租户 ID
    }
}
```

**权限枚举**（`PermissionProvider` 第一个参数）：

```text
DICT_VIEW, DICT_CREATE, DICT_UPDATE, DICT_ENABLE_DISABLE, DICT_DELETE, DICT_RESTORE
ITEM_CREATE, ITEM_UPDATE, ITEM_ENABLE_DISABLE, ITEM_DELETE, ITEM_RESTORE
```

- 默认字典/默认条目：第二个参数传 `Optional.empty()`。
- 租户条目：必须传目标 `tenantId`。

### 4.3 访问地址

| 用途 | URL |
| --- | --- |
| 管理页面 | `GET {base-path}`，默认 `http://host:port/dict-admin` |
| 管理 API | `{api-path}/...`，默认 `/vincent/dict/admin/api/v1/...` |

页面 HTML 会在脚本前注入：

```html
<base href="/dict-admin/">
<script>window.__VIN_DICT_CONFIG__={"apiPath":"/vincent/dict/admin/api/v1","historyBase":"/dict-admin"};</script>
```

### 4.4 管理端 REST API 概览

前缀：`{api-path}`（下表省略前缀）

**字典**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/dicts` | 分页列表（`code`/`name` 精确匹配） |
| POST | `/dicts` | 创建 |
| GET | `/dicts/{dictId}` | 详情 |
| PUT | `/dicts/{dictId}` | 更新 |
| PATCH | `/dicts/{dictId}/status` | 启停 |
| DELETE | `/dicts/{dictId}` | 软删 |
| POST | `/dicts/{dictId}/restore` | 恢复 |
| GET | `/capabilities?tenantId=` | 当前操作人权限 |

**条目**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/dicts/{dictId}/items` | 分页（`scope=default\|tenant`） |
| POST | `/dicts/{dictId}/items/default` | 创建默认项 |
| POST | `/dicts/{dictId}/items/tenant` | 创建租户项 |
| PUT | `/items/{itemId}` | 更新 |
| PATCH | `/items/{itemId}/status` | 启停 |
| DELETE | `/items/{itemId}` | 软删 |
| POST | `/items/{itemId}/restore` | 恢复 |

**租户目录**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/tenants` | 需 `TenantDirectory` + `DICT_VIEW` |

### 4.5 安全（宿主负责）

Starter **不实现**登录、会话、CSRF。生产环境须由宿主：

- 保护 `{base-path}/**` 与 `{api-path}/**`（网关、Spring Security 等）
- 浏览器会话场景自行处理 CSRF
- `PermissionProvider` 对接真实 RBAC/ABAC

---

## 5. 配置参考

### 5.1 核心开关

```yaml
vincent:
  dict:
    enabled: true                    # false：不创建 DictQueryService、不校验 schema
    data-source-bean-name:           # 多数据源时与下面两项一起配置
    sql-session-factory-bean-name:
    transaction-manager-bean-name:
    admin:
      enabled: false
      base-path: /dict-admin
      api-path: /vincent/dict/admin/api/v1
    limits:
      default-items-per-dict: 1000
      tenant-items-per-dict: 1000
      max-effective-items: 2000
      default-page-size: 20
      max-page-size: 100
```

**数据源选择**：

- 单个 `DataSource` → 自动使用
- 多个且唯一 `@Primary` → 使用主数据源
- 多个且无唯一主数据源 → 启动失败 `CONFIGURATION_INVALID`
- 显式 bean 名：三项必须**同时**配置，且绑定同一数据源

### 5.2 MySQL 运行期权限（查询账号）

```sql
GRANT SELECT ON information_schema.TABLES TO 'app'@'%';
GRANT SELECT ON your_db.vin_dict_meta TO 'app'@'%';
GRANT SELECT ON your_db.vin_dict TO 'app'@'%';
GRANT SELECT ON your_db.vin_dict_item TO 'app'@'%';
```

管理端写入账号还需对应表的 INSERT/UPDATE/DELETE（或更高权限）。DDL 由 DBA/发布流程执行。

### 5.3 可选 Redis 缓存

```xml
<dependency>
    <groupId>com.vincent.tools</groupId>
    <artifactId>vincent-dict-cache-redis-boot2-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

```yaml
vincent:
  dict:
    cache:
      enabled: true
      key-prefix: vin:dict    # 不同环境/宿主应不同
      ttl: 60s
```

`enabled=true` 时宿主须已有 `StringRedisTemplate`。未启用时查询直读 MySQL，写入后的 evict 为空操作。

---

## 6. 编码与数据规则

| 规则 | 说明 |
| --- | --- |
| 字典/项编码 | `^[A-Z][A-Z0-9_]{0,63}$`，不自动转大写 |
| 名称 | 最长 128 字符 |
| 描述 | 最长 500 字符 |
| 租户 ID | 最长 64 字符；`"0"` 仅内部表示默认项 |
| 数据库 id | 内部使用，不属于查询契约 |

---

## 7. 异常代码

业务代码可捕获 `DictException`，按 `errorCode` 分支：

```text
INVALID_ARGUMENT
DICT_NOT_FOUND
DICT_CODE_CONFLICT
DICT_NOT_EMPTY
DICT_ITEM_NOT_FOUND
DICT_ITEM_CODE_CONFLICT
DICT_ITEM_LIMIT_EXCEEDED
TENANT_CONTEXT_MISSING
TENANT_NOT_FOUND
DEFAULT_ITEM_PROTECTED
PERMISSION_DENIED
OPTIMISTIC_LOCK_CONFLICT
SCHEMA_MISSING
SCHEMA_VERSION_MISMATCH
CONFIGURATION_INVALID
```

---

## 8. 常见问题

### 8.1 管理页空白

**现象**：打开 `/dict-admin` 白屏，Network 里 `/assets/*.js` 404。

**原因（已修复）**：旧版前端资源用相对路径，在 `/dict-admin`（无尾斜杠）下会解析到站点根 `/assets/*`。

**业务接入是否还会遇到**：

| 接入方式 | 是否受影响 |
| --- | --- |
| 仅 `DictQueryService` 查询 | **否** |
| 管理端 + 默认 `base-path: /dict-admin` | **否**（使用含 `<base href>` 注入的 Starter 版本） |
| 管理端 + **自定义** `base-path` | **可能**：当前发布的前端静态资源前缀固定为 `/dict-admin/`，修改 `base-path` 但未同步前端构建时，JS/CSS 仍可能 404。**第一版请保持默认路径**；改 API 前缀（`api-path`）不受影响。 |

**自检**：查看页面源码应含 `<base href="/dict-admin/">`；Network 中脚本 URL 应为 `/dict-admin/assets/*.js` 且 200。

### 8.2 启动报 SCHEMA_MISSING / SCHEMA_VERSION_MISMATCH

未执行或未完成 `001-init.sql`。按第 3.2 节补跑脚本。

### 8.3 启动报 CONFIGURATION_INVALID（DataSource）

- 未配置 `spring.datasource.*`
- 或多个数据源且无唯一 `@Primary`
- 或显式 bean 名未三项齐配

### 8.4 无参查询 TENANT_CONTEXT_MISSING

已注册 `TenantProvider` 但当前线程取不到租户。Web 请求须在鉴权/filter 中设置租户上下文；批处理请用显式租户 API。

### 8.5 admin.enabled=true 但启动失败

缺少 `OperatorProvider` 或 `PermissionProvider`，或宿主不是 Servlet Web。

### 8.6 非 Web 宿主误开管理端

`spring.main.web-application-type=none` 时不应开启 admin；查询专用进程不要引入 `spring-boot-starter-web`。

---

## 9. 接入验收清单

**查询能力**

- [ ] BOM + `vincent-dict-boot2-starter` 引入，未直接依赖内部模块
- [ ] 空库已执行 `001-init.sql`
- [ ] 应用启动无 schema 错误
- [ ] 注入 `DictQueryService` 可列出有效项
- [ ] 多租户：`TenantProvider` 或无参/显式 API 行为符合预期

**管理端（若启用）**

- [ ] 已引入 `spring-boot-starter-web`
- [ ] `OperatorProvider`、`PermissionProvider` 已注册
- [ ] `GET /dict-admin` 页面正常、Network 无 `/assets` 404
- [ ] `GET {api-path}/dicts` 返回 200（经宿主鉴权后）
- [ ] 网关/Security 已保护 admin 路径

**可选 Redis**

- [ ] 引入 cache starter + `StringRedisTemplate`
- [ ] 写入后另一实例下一次查询读到新值

---

## 10. 本地演示

仓库示例模块 `vincent-dict-example-boot2`（**不要**当生产模板）：

```bash
# 建库 + 初始化 + 演示数据
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS vincent_dict DEFAULT CHARACTER SET utf8mb4;"
mysql -u root -p vincent_dict < vincent-dict/sql/mysql/1.0.0/001-init.sql
mysql -u root -p vincent_dict < vincent-dict/vincent-dict-example-boot2/src/test/resources/demo-data.sql

# 编译并启动
mvn -pl vincent-dict/vincent-dict-example-boot2 -am package -DskipTests
# IDEA 运行 DictExampleApplication
```

演示数据：`ORDER_STATUS` 字典，默认项 `CREATED`，租户 `tenant-a` 追加项 `WAIT_CONFIRM`。

访问：`http://localhost:8080/dict-admin`

---

## 11. Schema 升级

- 当前版本：`1`，脚本 `sql/mysql/1.0.0/001-init.sql`
- 未来升级脚本：`sql/mysql/upgrade/`（前向升级，无自动回滚）
- **应用永不执行 DDL**；版本不匹配时启动失败并提示所需 SQL 路径
