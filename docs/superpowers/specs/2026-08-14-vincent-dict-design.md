# Vincent Dict 设计文档

## 1. 背景与目标

`vincent-tools` 是可持续扩展的通用工具仓库。首个工具 `vincent-dict` 为 Java/Spring Boot 业务系统提供可嵌入的字典管理能力。

业务系统导入 `vincent-tools-bom`、引入 `vincent-dict-spring-boot2-starter`、创建组件表并完成少量适配配置后，即可获得：

- 统一的 Java 字典查询接口；
- 默认字典项与租户追加项；
- 内嵌后台管理页面和管理 API；
- 操作审计；
- 可选 Redis 缓存；
- 可选数据库自动迁移。

首版兼容现有业务系统的 Java 8 与 Spring Boot 2.2.6 技术基线，所有发布坐标、包名、配置、表名和业务代码均保持通用，不包含客户业务语义。

## 2. 范围

### 2.1 负责

- 字典定义和字典项管理；
- 默认项与租户追加规则；
- 字典查询应用服务；
- 管理 API 与内嵌管理页面；
- 维护操作审计；
- 可选 Redis 缓存；
- MySQL 建表和迁移脚本；
- Spring Boot 2 自动装配。

### 2.2 不负责

- 默认项覆盖或屏蔽；
- 租户私有字典定义；
- 字段映射和系统间值转换；
- 组织树、地区树和字典树；
- 用户、租户和权限数据维护；
- 国际化；
- 审批和发布流程；
- 导入导出；
- 独立部署的字典中心或远程查询服务；
- Spring Boot 3 支持。

## 3. 用户与权限

开发和运维人员可以使用管理页面维护字典、默认项和指定租户的追加项，并查看审计记录。客户用户不展示管理入口、不开放维护 API，只能通过业务功能间接读取字典数据。

管理页面和管理 API 默认关闭。宿主启用管理端时必须提供 `PermissionProvider`，否则应用启动失败。页面按权限隐藏操作，后端 API 对每个请求独立鉴权。

稳定权限集合包括：

- `DICT_VIEW`
- `DICT_CREATE`
- `DICT_UPDATE`
- `DICT_ENABLE_DISABLE`
- `DICT_DELETE`
- `ITEM_CREATE`
- `ITEM_UPDATE`
- `ITEM_ENABLE_DISABLE`
- `ITEM_DELETE`
- `AUDIT_VIEW`

## 4. 仓库和模块

```text
vincent-tools
├── pom.xml
├── vincent-tools-bom
└── vincent-dict
    ├── vincent-dict-domain
    ├── vincent-dict-application
    ├── vincent-dict-infra-mybatis
    ├── vincent-dict-infra-redis
    ├── vincent-dict-web
    ├── vincent-dict-admin-ui
    └── vincent-dict-spring-boot2-starter
```

根 POM 同时承担内部父工程与源码模块聚合职责。`vincent-tools-bom` 只管理已发布 Vincent 工具的兼容版本，不包含代码或构建插件。业务系统只需引入所需 Starter，不引入整个工具仓库。

首版 Maven 坐标统一使用 `com.vincent.tools` groupId；发布物 artifactId 与上述模块目录同名。开发版本从 `1.0.0-SNAPSHOT` 起步，正式发布使用语义化版本。

## 5. DDD 架构与依赖

### 5.1 领域层

`vincent-dict-domain` 是纯 Java 模块，包含聚合、值对象、领域规则和领域异常，不依赖 Spring、MyBatis、Redis、HTTP 或前端代码。

### 5.2 应用层

`vincent-dict-application` 包含查询和维护用例、事务语义、入站服务接口以及仓储、缓存、审计和宿主上下文端口。应用层不依赖具体基础设施。

### 5.3 外层适配器

- `vincent-dict-infra-mybatis`：PO、Mapper、仓储实现、数据库锁和 SQL；
- `vincent-dict-infra-redis`：可选 Redis 缓存适配器；
- `vincent-dict-web`：管理 API、异常转换和页面路由；
- `vincent-dict-admin-ui`：Vue 3 + TypeScript 管理端；
- `vincent-dict-spring-boot2-starter`：配置属性、条件 Bean、自动装配与静态资源聚合。

依赖只能由外向内。领域规则只能存在于领域层；Controller、Starter、Mapper 和缓存适配器不得复制领域规则。

## 6. 领域模型

### 6.1 Dict

`Dict` 是字典定义聚合根，包含：

```text
id, code, name, description, status, sortNo,
version, deleted, createdBy, createdAt, updatedBy, updatedAt
```

它负责创建、修改展示信息、排序、启停和逻辑删除。`code` 全局唯一，创建后不可修改，逻辑删除后不可复用。

### 6.2 DictItem

`DictItem` 是独立聚合根，避免读取一个字典时加载全部租户项。它包含：

```text
id, dictId, code, name, tenantId, description,
status, sortNo, version, deleted,
createdBy, createdAt, updatedBy, updatedAt
```

`tenantId = 0` 表示默认项，`tenantId > 0` 表示租户追加项。字典项只支持扁平结构。

### 6.3 不变量

- 租户只能在已有字典下追加字典项，不能创建字典定义；
- 租户不能覆盖、修改、停用、删除或屏蔽默认项；
- 默认项已使用某编码时，任何租户不能新增相同编码；
- 某编码已被任意租户使用时，不能再创建相同编码的默认项；
- 同一字典、同一租户下字典项编码唯一；
- 不同租户允许使用相同的租户项编码；
- 字典和字典项逻辑删除后，编码永久不可复用；
- 字典停用后，有效查询返回空集合；
- 查询结果只包含启用的默认项和当前租户启用项。

## 7. 租户与宿主扩展

宿主可以提供：

```java
public interface TenantProvider {
    OptionalLong currentTenantId();
}

public interface OperatorProvider {
    String currentOperatorId();
}

public interface PermissionProvider {
    boolean hasPermission(DictAdminPermission permission);
}

public interface TraceProvider {
    String currentTraceId();
}
```

未提供 `TenantProvider` 时，组件以单租户模式运行，只读取和维护默认项，不允许创建租户项。已注册 `TenantProvider` 但当前请求无法取得租户时，租户相关查询抛出 `TENANT_CONTEXT_MISSING`，不能静默回退。

启用管理端时 `OperatorProvider` 与 `PermissionProvider` 均为必需扩展。`TraceProvider` 可选；宿主未提供时组件优先读取日志 MDC 中的 Trace ID，仍不存在时生成本次请求的 UUID。

## 8. 查询接口和排序

业务代码只依赖应用层接口：

```java
public interface DictQueryService {
    List<DictItemView> listEffectiveItems(String dictCode);
    Optional<DictItemView> findEffectiveItem(String dictCode, String itemCode);
}
```

`DictItemView` 是不可变只读结果，不暴露领域实体、PO 或 Mapper。有效结果为：

```text
启用的默认项 + 当前租户启用的追加项
```

结果按 `sortNo`、`code`、`id` 依次升序，保证顺序稳定。

## 9. 写入、并发和审计

新增字典项时，应用服务在同一事务中：

1. 锁定所属字典记录；
2. 查询编码在默认和租户范围内的使用情况；
3. 执行领域规则校验；
4. 保存字典项；
5. 写入审计记录；
6. 提交事务；
7. 事务提交后失效缓存。

锁定字典记录可序列化同一字典下互相冲突的默认项和租户项创建操作。审计与业务写入处于同一数据库事务；审计失败时业务修改回滚。

每条审计记录包含操作类型、目标、目标租户、操作人、Trace ID、修改前后 JSON 文本和操作时间。审计表只追加，不修改、不删除。

## 10. 数据库

首版提供三张短名称表：

```text
vin_dict
vin_dict_item
vin_dict_audit
```

关键约束和索引：

```text
vin_dict:      UNIQUE (code)
vin_dict_item: UNIQUE (dict_id, tenant_id, code)
vin_dict_item: INDEX  (dict_id, tenant_id, status, deleted, sort_no)
vin_dict_item: INDEX  (tenant_id, deleted)
```

逻辑删除记录仍占用唯一键，以保证编码不可复用。审计的修改前后内容使用文本列保存 JSON，不强制依赖 MySQL JSON 类型。

组件同时发布：

- `db/manual/mysql/` 下供 DBA 审核执行的完整 SQL；
- `db/migration/mysql/` 下的可选迁移脚本。

自动迁移默认关闭。启用时使用独立脚本位置和独立 Flyway history 表，避免与宿主迁移冲突。

## 11. Redis 缓存

缓存默认关闭；未启用时每次从数据库读取。启用后复用宿主已有的 `StringRedisTemplate`，组件不维护独立 Redis 连接。

缓存键包含租户、字典编码和字典级版本：

```text
vin:dict:v1:{dictCode}:{version}:{tenantId}
vin:dict:version:{dictCode}
```

- 默认项或字典状态变化：递增字典级版本；
- 某租户项变化：删除该租户的精确缓存键；
- 旧版本数据依靠 TTL 清理；
- Redis 查询故障时降级查询数据库并限频告警；
- 数据库是最终事实来源；
- 缓存清理失败不回滚已提交的数据库事务。

## 12. 管理页面和 API

Vue 3 + TypeScript 前端作为独立源码模块构建，产物内嵌进 Boot 2 Starter。宿主无需单独部署前端。

页面包括：

- 字典列表：编码、名称、状态搜索及新增、编辑、启停、查看详情；
- 字典详情：基本信息、默认项、租户项、操作记录；
- 租户项按租户 ID 筛选和分页，不一次加载全部租户数据；
- 操作记录按操作人、操作类型和时间范围查询；
- 编码冲突即时提示；
- 字典项排序。

默认页面路径为 `/dict-admin`，管理 API 前缀为 `/vincent/dict/admin/api/v1`。页面只调用组件管理 API，不访问宿主 Mapper。业务查询能力默认只提供 Java API，不强制开放公共 HTTP 查询接口。

## 13. 配置

```yaml
vincent:
  dict:
    enabled: true
    admin:
      enabled: false
      base-path: /dict-admin
      api-path: /vincent/dict/admin/api/v1
    migration:
      enabled: false
    cache:
      enabled: false
      key-prefix: vin:dict
      ttl: 10m
```

配置校验规则：

- 启用管理端但缺少 `PermissionProvider` 或 `OperatorProvider` 时启动失败；
- 启用 Redis 缓存但缺少 `StringRedisTemplate` 时启动失败；
- 操作审计是维护能力的强制组成部分，不提供关闭开关；
- 未启用缓存时不加载 Redis 适配器 Bean；
- Starter 复用宿主的 `DataSource`、事务管理器、MyBatis 和 Redis 连接；
- Starter 不覆盖宿主 Bean 或修改宿主全局 MyBatis 配置。

## 14. 错误处理

稳定错误码至少包括：

```text
DICT_NOT_FOUND
DICT_DISABLED
DICT_CODE_CONFLICT
DICT_ITEM_CODE_CONFLICT
TENANT_CONTEXT_MISSING
DEFAULT_ITEM_PROTECTED
PERMISSION_DENIED
OPTIMISTIC_LOCK_CONFLICT
```

领域和应用层不依赖宿主响应类型。Java API 直接暴露组件异常；管理 API 使用组件响应结构并提供异常映射扩展点。数据库、审计或领域校验失败时写事务整体回滚。

## 15. 兼容基线

```text
Java                 8
Spring Boot          2.2.6.RELEASE
MyBatis-Plus         3.3.2
Flyway               6.5.7
MySQL                5.7+
Spring Data Redis    Spring Boot 2.2.6 管理版本
前端                 Vue 3 + TypeScript
```

首版只实现 Boot 2 Starter。领域层和应用层保持框架无关，为以后新增 Boot 3 Starter 保留边界，但不提前实现未使用的适配器。

## 16. 测试策略

- 领域测试：纯 JUnit 覆盖所有不变量、状态变化和排序；
- 应用测试：使用 Fake 端口覆盖租户、权限、审计、事务后缓存失效和异常传播；
- MySQL 集成测试：表结构、约束、逻辑删除、乐观锁、并发锁和事务回滚；
- Redis 集成测试：命中、未命中、TTL、版本失效和故障降级；
- Starter 测试：自动装配、条件 Bean、配置校验、页面挂载和宿主 Bean 共存；
- Web 测试：鉴权、输入校验、错误映射和分页；
- UI 测试：字典与字典项维护、权限展示、冲突提示和审计查询；
- 兼容测试：使用最小 Boot 2.2.6 示例宿主完成端到端验证。

## 17. 第一版验收标准

1. 业务系统可以导入 `vincent-tools-bom` 并引入 `vincent-dict-spring-boot2-starter`；
2. 手工执行版本化 SQL 后组件可正常启动；
3. 可选自动迁移不会污染宿主 Flyway 历史；
4. 宿主可适配租户、操作人、权限和 Trace 上下文；
5. 内部人员可使用内嵌页面完成字典、默认项和租户项维护；
6. 租户只能追加，不能影响默认项；
7. 业务代码通过 `DictQueryService` 获得正确、稳定排序的有效结果；
8. 所有维护操作有完整、事务一致的审计；
9. 无 Redis 时正常查询数据库，配置 Redis 后正确缓存和失效；
10. 全部领域、应用、基础设施、Starter、Web 和 UI 测试通过。
