# Vincent Dict

嵌入式字典组件。业务系统导入 BOM、只引入核心 Starter、手工执行 SQL，并提供宿主 `DataSource` 与可选 `TenantProvider` 后，即可注入 `DictQueryService` 查询默认项与租户生效项。

**详细接入步骤、配置说明、管理端 API 与验收清单见 [docs/INTEGRATION.md](docs/INTEGRATION.md)。**

Vincent Dict never runs DDL at application startup.

## 根父 POM、BOM 与功能 Starter

- **根父 POM** `com.vincent.tools:vincent-tools` 是本仓库的内部父工程，不是功能依赖。不要在业务系统中继承或依赖它来接入 Dict。
- **BOM** `com.vincent.tools:vincent-tools-bom` 只管理已发布 Vincent 制品的兼容版本。业务系统应在 `dependencyManagement` 中 `import` 它。
- **功能 Starter** `com.vincent.tools:vincent-dict-boot2-starter` 才是业务系统应引入的 Dict 依赖。不要直接依赖 `vincent-dict-domain`、`vincent-dict-application` 或 `vincent-dict-infra-mybatis`。

`vincent-dict-example-boot2` 是仓库内的 Boot 2.2.6 兼容性证明，不是发布制品，也不在公开 BOM 中。

## 接入

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
        <artifactId>vincent-dict-boot2-starter</artifactId>
    </dependency>
</dependencies>
```

宿主还需自行提供 Spring Boot 数据源与 MyBatis `SqlSessionFactory`（常见组合：`spring-boot-starter-jdbc` 或已有 JDBC 起步依赖，加上 `mybatis-plus-boot-starter` 3.3.2）以及 MySQL 驱动。核心 Starter 不创建连接池，不读取独立数据库账号密码，也不引入 Redis。查询缓存默认是进程内空操作；需要跨实例失效时再按需引入 Redis Starter，见下方「可选 Redis 缓存」。

非 Web 宿主可以只使用 Java 查询 API。不要为了查询去引入 Spring MVC；管理端默认关闭。没有 Web 容器时，请用手工 SQL 或已有运维流程维护字典，不要把示例适配器或管理页面当成生产写入通道。

## 手工 SQL

在应用启动前，对空库执行一次：

```bash
mysql --default-character-set=utf8mb4 -u <user> -p <database> \
  < vincent-dict/sql/mysql/1.0.0/001-init.sql
```

脚本创建 `vin_dict_meta`、`vin_dict`、`vin_dict_item`，并把 Schema 版本写成 `1`。不要使用 `CREATE TABLE IF NOT EXISTS` 掩盖结构不一致。MySQL DDL 可能隐式提交，不要把该脚本放进业务事务。

Vincent Dict never runs DDL at application startup. 启动时只读校验表是否存在以及 `vin_dict_meta.id = 1` 的版本是否为 `1`。缺表抛 `SCHEMA_MISSING`，版本不匹配抛 `SCHEMA_VERSION_MISMATCH`，消息中会给出 `sql/mysql/1.0.0/001-init.sql`。

## 所需 MySQL 权限

运行期查询账号至少需要：

```sql
GRANT SELECT ON information_schema.TABLES TO 'app'@'%';
GRANT SELECT ON <database>.vin_dict_meta TO 'app'@'%';
GRANT SELECT ON <database>.vin_dict TO 'app'@'%';
GRANT SELECT ON <database>.vin_dict_item TO 'app'@'%';
```

初始化与升级由 DBA 或发布流程用更高权限手工执行，不要交给应用启动过程。

## TenantProvider

无参查询通过宿主 `TenantProvider` 解析当前租户：

```java
@Bean
TenantProvider tenantProvider() {
    return () -> Optional.of("tenant-a");
}
```

已注册 Provider 但当前上下文没有租户时，无参查询抛 `TENANT_CONTEXT_MISSING`。Provider 返回的租户 ID 必须是最长 64 个字符的非空字符串，且不能是保留值 `"0"`。

未提供 `TenantProvider` 时，组件以单租户模式运行：内置 `SingleTenantProvider` 只查询默认项（内部哨兵 `"0"`）。这是唯一允许使用 `"0"` 的路径。

## 显式租户批量 API

定时任务、消息消费和批处理应使用显式租户重载，它们不调用 `TenantProvider`：

```java
queryService.listEffectiveItems("ORDER_STATUS", tenantId);
queryService.findEffectiveItem("ORDER_STATUS", "CREATED", tenantId);
```

显式租户方法不接受保留值 `"0"`。跨租户授权由宿主业务代码负责。公共查询 API 不提供 HTTP 端点。

有效项 = 启用的默认项 + 当前/显式租户的启用追加项，按 `sortNo`、`code`、内部 `id` 升序。字典不存在或已删除抛 `DICT_NOT_FOUND`；字典停用则列表为空。

## 数据源选择

- 宿主只有一个 `DataSource` 时自动使用。
- 多个 `DataSource` 且存在唯一 `@Primary` 时使用主数据源。
- 多个且没有唯一主数据源时启动失败（`CONFIGURATION_INVALID`）。
- 可用以下三项显式选择，且必须三个一起配置：

```yaml
vincent:
  dict:
    data-source-bean-name: dataSource
    sql-session-factory-bean-name: sqlSessionFactory
    transaction-manager-bean-name: transactionManager
```

所选 `SqlSessionFactory` 与事务管理器必须绑定到同一 `DataSource`。Starter 不覆盖宿主 Bean，也不修改宿主全局 MyBatis 配置。

## 可选 Redis 缓存

核心 Starter 不引入 Redis 客户端。只有业务系统额外依赖 BOM 管理的 `vincent-dict-cache-redis-boot2-starter`，并提供宿主 `StringRedisTemplate` 时，才会启用跨实例查询缓存。常见做法是同时引入 `spring-boot-starter-data-redis`（或等价配置）来创建该模板；Dict Redis Starter 不会自己建立连接工厂。

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
      key-prefix: vin:dict
      ttl: 60s
```

- **按需启用**：`vincent.dict.cache.enabled` 默认为 `false`。未启用或未引入 Redis Starter 时，查询走 MySQL，写入后的 `evict` 是空操作。
- **宿主模板**：`enabled=true` 时必须已有 `StringRedisTemplate`，否则启动失败（`CONFIGURATION_INVALID`）。
- **键前缀隔离**：`key-prefix` 必须非空，默认 `vin:dict`。不同环境或不同宿主应使用不同前缀，避免共用一个 Redis 时互相覆盖。
- **TTL**：载荷默认 60 秒。命中保留不可变、已排序的查询 DTO，不暴露 PO 或领域对象。
- **失效**：默认项变更递增全局版本，使所有租户缓存失效；租户项变更只递增该租户版本。不使用 `KEYS`、通配扫描或发布订阅。
- **正常路径**：Redis 健康时，一次成功写入后的下一次查询应立即看到新值，不必等待 TTL。
- **故障回退**：Redis 读/写失败时回退到 MySQL，并按故障类别限流打日志。已缓存的陈旧值最多再保留一个 TTL。
- **一致性**：第一版在 Redis 不可用时不提供强一致性。多实例在 Redis 故障期间可能读到最多一个 TTL 的陈旧数据。

## 管理端

管理控制台打进同一个核心 Starter，默认关闭。只有同时满足以下条件才会暴露页面和 API：

- 宿主是 Servlet Web 应用（提供 Spring MVC / `DispatcherServlet`，通常再引入 `spring-boot-starter-web`）
- `vincent.dict.admin.enabled=true`
- 宿主注册了 `OperatorProvider` 和 `PermissionProvider`

```yaml
vincent:
  dict:
    admin:
      enabled: true
      base-path: /dict-admin
      api-path: /vincent/dict/admin/api/v1
```

- **SPA**：`GET {base-path}`，默认 `/dict-admin`。页面 HTML 会在应用脚本之前注入 `<base href="{base-path}/">` 与 `window.__VIN_DICT_CONFIG__`（`apiPath`、`historyBase`）。**第一版请保持默认 `base-path: /dict-admin`**；自定义页面路径需与前端静态资源前缀一致，否则可能出现白屏。
- **API**：`{api-path}`，默认 `/vincent/dict/admin/api/v1`。
- **操作人**：`OperatorProvider.currentOperatorId()` 必须返回非空、无需去空白、最长 64 字符的标识，写入元数据使用该值。
- **权限**：每次管理操作都调用 `PermissionProvider.hasPermission(permission, targetTenantId)`。租户条目的创建/修改/启停/删除/恢复必须带目标租户 ID；默认字典/默认条目使用空 `Optional`。
- **租户目录**：`TenantDirectory` 可选。缺少时默认条目管理仍可用，租户条目读写和租户搜索返回 `CONFIGURATION_INVALID`，UI 会隐藏租户页。
- **认证与 CSRF**：Starter 不实现登录、会话或 CSRF。宿主必须自行保护 `/dict-admin/**` 与管理 API，并在浏览器会话场景下处理 CSRF。
- **非 Web**：`spring.main.web-application-type=none` 或没有 `DispatcherServlet` 时，不会创建控制器、`DispatcherServlet` 或嵌入式服务器。查询专用宿主不要为了管理端去引入 MVC。

示例模块 `vincent-dict-example-boot2` 提供演示用适配器（操作人 `example-admin`、全放行权限、内存租户目录），仅用于仓库内验收，不要照搬到生产。

## 编码规则

字典编码与字典项编码必须匹配 `^[A-Z][A-Z0-9_]{0,63}$`：只允许大写英文、数字和下划线，最长 64 字符。接口不自动转大写或裁剪空格，不合法时直接拒绝。

- 名称最长 128 字符，描述最长 500 字符。
- `tenantId` 最长 64 字符；`"0"` 表示默认项，不能当作普通外部租户 ID。
- 数据库 ID 是内部值，不属于查询契约。

## 字典项上限

| 配置 | 默认 | 含义 |
| --- | --- | --- |
| `vincent.dict.limits.default-items-per-dict` | 1000 | 单个字典未删除默认项上限 |
| `vincent.dict.limits.tenant-items-per-dict` | 1000 | 单个租户在单个字典下的未删除追加项上限 |
| `vincent.dict.limits.max-effective-items` | 2000 | 一次有效查询最多返回的项数 |

所有限制必须是正整数，且满足 `max-effective-items >= default-items-per-dict + tenant-items-per-dict`。不合法配置在启动时失败，运行时不会静默修正。查询结果超过 `max-effective-items` 时抛 `CONFIGURATION_INVALID`。

## 异常代码

Java API 抛出带稳定错误码的 `DictException`：

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

## Schema 升级策略

- 当前产品 Schema 版本为 `1`，初始化脚本为 `sql/mysql/1.0.0/001-init.sql`。
- 只提供前向升级脚本（未来放在 `sql/mysql/upgrade/`），不提供自动回滚。
- 升级前由宿主备份；执行前检查 `vin_dict_meta` 当前版本，完成后更新版本。
- 不要使用 `CREATE TABLE IF NOT EXISTS` 掩盖结构不一致。
- Vincent Dict never runs DDL at application startup. 版本不兼容时启动失败并提示所需 SQL 路径，由发布流程手工执行。

## 最小示例

`vincent-dict-example-boot2` 是仓库内的 Boot 2.2.6 / Java 8 演示宿主：依赖核心 Starter + Web，默认开启管理端，便于本地验收。演示适配器（操作人 `example-admin`、全放行权限、内存租户目录）**仅供仓库内试用**，不要照搬到生产。演示 SQL 见 `vincent-dict-example-boot2/src/test/resources/demo-data.sql`。Redis Starter 仅出现在示例模块测试作用域。
