# Vincent Dict 核心实施计划

> **面向智能体执行者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans，按任务逐项实施本计划。步骤使用复选框（`- [ ]`）语法跟踪。

**目标：** 构建并发布基于 DDD 的 Vincent Dict 核心、MySQL 持久化、手工 SQL、Java 查询 API 与 Spring Boot 2 Starter，不包含 Redis 或管理端写入变更。

**架构：** 纯 Java 领域层和应用层模块定义规则与端口。MyBatis-Plus 适配器针对宿主拥有的 MySQL 表实现持久化；Boot 2 Starter 选择宿主基础设施、校验手工维护的 Schema，并按条件创建查询服务。Web 资源已存在，但在管理端计划完成前保持禁用。

**技术栈：** Java 8、Maven 3.6+、Spring Boot 2.2.6.RELEASE、MyBatis-Plus 3.3.2、MySQL 5.7+、JUnit 5、AssertJ、Testcontainers 1.19.8。

## 全局约束

- 所有 Maven 坐标均使用 groupId `com.vincent.tools`；开发版本为 `1.0.0-SNAPSHOT`。
- 技术标识使用 `dict`，而非 `dictionary`；发布的制品名称以 `vincent-` 开头。
- `vincent-dict-domain` 不得依赖 Spring、MyBatis、Redis、HTTP、Jackson 或 Lombok。
- Java 源码和目标兼容级别必须严格为 1.8；不得使用 records、sealed classes、`List.of` 或 Java 8 之后的 API。
- Spring Boot 兼容性基线必须严格为 `2.2.6.RELEASE`；MyBatis-Plus 基线必须严格为 `3.3.2`。
- 仅支持 MySQL 5.7+；组件绝不在运行时创建或迁移表。
- 租户 ID 为最长 64 个字符的非空字符串；保留值 `"0"` 表示默认项。
- 字典和字典项编码必须匹配 `^[A-Z][A-Z0-9_]{0,63}$`；拒绝输入，不进行规范化。
- 字典/字典项数据库 ID 是内部 `BIGINT AUTO_INCREMENT` 值，绝不属于业务查询契约。
- 领域/应用代码不得依赖任何宿主响应包装类或客户专用包。
- 既有同级业务系统仅作为只读证据；本计划只修改 `vincent-tools`。

---

### Task 1: 创建 Maven Reactor、父 POM、BOM 与模块骨架

**文件：**
- 修改：`.gitignore`
- 创建：`pom.xml`
- 创建：`vincent-tools-bom/pom.xml`
- 创建：`vincent-dict/pom.xml`
- 创建：`vincent-dict/vincent-dict-domain/pom.xml`
- 创建：`vincent-dict/vincent-dict-application/pom.xml`
- 创建：`vincent-dict/vincent-dict-infra-mybatis/pom.xml`
- 创建：`vincent-dict/vincent-dict-admin-ui/pom.xml`
- 创建：`vincent-dict/vincent-dict-web/pom.xml`
- 创建：`vincent-dict/vincent-dict-boot2-starter/pom.xml`
- 创建：`vincent-dict/vincent-dict-cache-redis-boot2-starter/pom.xml`

**接口：**
- 使用：无。
- 产出：包含所有获准 artifactId 及集中化版本管理的一个 Maven Reactor。

- [ ] **步骤 1：扩展仓库忽略规则以覆盖构建生成物**

```gitignore
.superpowers/
.idea/
**/target/
**/node_modules/
**/dist/
**/.frontend/
*.iml
```

- [ ] **步骤 2：创建根父/聚合 POM**

使用 `pom` 打包方式、模块 `vincent-tools-bom` 和 `vincent-dict`、Java 8 编译器属性、UTF-8 及以下锁定属性：

```xml
<groupId>com.vincent.tools</groupId>
<artifactId>vincent-tools</artifactId>
<version>1.0.0-SNAPSHOT</version>
<packaging>pom</packaging>

<properties>
    <java.version>1.8</java.version>
    <spring-boot.version>2.2.6.RELEASE</spring-boot.version>
    <mybatis-plus.version>3.3.2</mybatis-plus.version>
    <junit-jupiter.version>5.5.2</junit-jupiter.version>
    <assertj.version>3.14.0</assertj.version>
    <testcontainers.version>1.19.8</testcontainers.version>
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>
</properties>
```

在 `dependencyManagement` 中导入 `spring-boot-dependencies`；统一管理 JUnit、AssertJ、Testcontainers、MySQL 连接器 `5.1.49` 和所有 Vincent 模块版本。配置 `maven-compiler-plugin 3.11.0`、`maven-surefire-plugin 3.2.5`、`maven-failsafe-plugin 3.2.5`、`maven-enforcer-plugin 3.4.1`，并要求 Maven `3.6.0+` 与 Java `[1.8,)`。添加使用 `java18` 签名的 `animal-sniffer-maven-plugin 1.23`，以保证在较新 JDK 上运行的构建不能调用 Java 8 之后的 API。为所有发布的 Java 模块配置源码 JAR，为公共 API 模块配置 Javadoc JAR。不得提交 `distributionManagement`、仓库凭据或内部仓库 URL；部署时由外部提供 `-DaltDeploymentRepository` 或 CI Maven 设置。

- [ ] **步骤 3：创建公开 BOM**

`vincent-tools-bom/pom.xml` 必须继承根 POM，且只包含以下 `dependencyManagement` 条目：

```text
vincent-dict-domain
vincent-dict-application
vincent-dict-infra-mybatis
vincent-dict-web
vincent-dict-admin-ui
vincent-dict-boot2-starter
vincent-dict-cache-redis-boot2-starter
```

不得在公开 BOM 中放入构建插件或第三方库版本。

- [ ] **步骤 4：创建 dict 聚合模块与叶子 POM**

必须严格编码以下依赖方向：

```text
domain <- application <- infra-mybatis <- boot2-starter
                    ^                    ^
                    └── web ─────────────┤
admin-ui <───────────────────────────────┘
application <- cache-redis-boot2-starter
```

将 Redis 模块保留在 Reactor 中，但在 Redis 计划前保持为空。在 `vincent-dict-web` 中将 Spring MVC 依赖标记为 optional，以避免核心 Starter 将非 Web 宿主变成 Web 应用。

- [ ] **步骤 5：验证空 Reactor**

执行：

```bash
mvn -q -DskipTests install
mvn -q help:effective-pom -pl vincent-tools-bom
```

预期结果：Reactor `BUILD SUCCESS`；有效 BOM 列出 Vincent 制品且不含 Spring 构建插件。

- [ ] **步骤 6：提交 Reactor 骨架**

```bash
git add .gitignore pom.xml vincent-tools-bom vincent-dict
git commit -m "build: create vincent tools reactor"
```

---

### Task 2: 实现领域基础类型与校验

**文件：**
- 创建：`vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/DictCode.java`
- 创建：`vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/ItemCode.java`
- 创建：`vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/TenantId.java`
- 创建：`vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/DictStatus.java`
- 创建：`vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/ItemStatus.java`
- 创建：`vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/DictItemSource.java`
- 创建：`vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/DictErrorCode.java`
- 创建：`vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/DictException.java`
- 测试：`vincent-dict/vincent-dict-domain/src/test/java/com/vincent/tools/dict/domain/DictCodeTest.java`
- 测试：`vincent-dict/vincent-dict-domain/src/test/java/com/vincent/tools/dict/domain/TenantIdTest.java`

**接口：**
- 使用：仅 Java 8。
- 产出：`DictCode.of(String)`、`ItemCode.of(String)`、`TenantId.of(String)`、`TenantId.defaultItem()`、状态/来源枚举和稳定异常。

- [ ] **步骤 1：编写预期失败的编码与租户校验测试**

```java
class DictCodeTest {
    @Test void accepts_uppercase_business_code() {
        assertThat(DictCode.of("ORDER_STATUS").value()).isEqualTo("ORDER_STATUS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " order", "order", "1_ORDER", "ORDER-STATUS"})
    void rejects_non_canonical_codes(String value) {
        assertThatThrownBy(() -> DictCode.of(value))
            .isInstanceOf(DictException.class)
            .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
    }
}

class TenantIdTest {
    @Test void reserves_zero_for_default_items() {
        assertThat(TenantId.defaultItem().isDefault()).isTrue();
        assertThatThrownBy(() -> TenantId.of("0")).isInstanceOf(DictException.class);
    }
}
```

- [ ] **步骤 2：运行领域测试并确认失败**

执行：

```bash
mvn -q -pl vincent-dict/vincent-dict-domain test
```

预期结果：因值对象尚不存在而编译失败。

- [ ] **步骤 3：实现不可变值对象与稳定错误**

使用精确的正则表达式和最大长度实现 `DictCode` 与 `ItemCode`。使用私有构造器和两个工厂方法实现 `TenantId`：

```java
public final class TenantId {
    public static final String DEFAULT_VALUE = "0";

    public static TenantId of(String value) {
        if (value == null || value.trim().isEmpty() || value.length() > 64
                || DEFAULT_VALUE.equals(value) || !value.equals(value.trim())) {
            throw new DictException(DictErrorCode.INVALID_ARGUMENT, "invalid tenantId");
        }
        return new TenantId(value, false);
    }

    public static TenantId defaultItem() {
        return new TenantId(DEFAULT_VALUE, true);
    }
}
```

实现值相等性/hashCode；在 `toString` 中不得脱敏编码或租户，这些标识不是秘密信息。

现在定义完整的第一版 `DictErrorCode` 枚举，避免后续模块自行创建字符串：

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

- [ ] **步骤 4：运行测试与依赖检查**

```bash
mvn -q -pl vincent-dict/vincent-dict-domain test
mvn -q -pl vincent-dict/vincent-dict-domain dependency:tree
```

预期结果：测试通过；编译依赖树仅包含 JDK。

- [ ] **步骤 5：提交领域基础类型**

```bash
git add vincent-dict/vincent-dict-domain
git commit -m "feat(dict): add domain value objects"
```

---

### Task 3: 实现 Dict 与 DictItem 聚合规则

**文件：**
- 创建：`vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/Dict.java`
- 创建：`vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/DictItem.java`
- 创建：`vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/DictItemPolicy.java`
- 创建：`vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/ItemCodeUsage.java`
- 创建：`vincent-dict/vincent-dict-domain/src/test/java/com/vincent/tools/dict/domain/TestFixtures.java`
- 测试：`vincent-dict/vincent-dict-domain/src/test/java/com/vincent/tools/dict/domain/DictTest.java`
- 测试：`vincent-dict/vincent-dict-domain/src/test/java/com/vincent/tools/dict/domain/DictItemPolicyTest.java`

**接口：**
- 使用：Task 2 的值对象和 `DictException`。
- 产出：聚合工厂/重建方法及 `DictItemPolicy.checkCreate(TenantId, ItemCodeUsage, int, int)`。

- [ ] **步骤 1：编写预期失败的聚合状态转换测试**

```java
class DictTest {
    @Test void disabled_dict_remains_editable_but_not_effective() {
        Dict dict = Dict.create(DictCode.of("ORDER_STATUS"), "Order status", "", 10,
            "operator", Instant.parse("2026-08-14T00:00:00Z"));
        dict.disable("operator", Instant.parse("2026-08-14T00:01:00Z"));
        dict.rename("Order lifecycle", "operator", Instant.parse("2026-08-14T00:02:00Z"));
        assertThat(dict.isEffective()).isFalse();
        assertThat(dict.name()).isEqualTo("Order lifecycle");
    }

    @Test void non_empty_dict_cannot_be_deleted() {
        Dict dict = TestFixtures.activeDict();
        assertThatThrownBy(() -> dict.delete(1, "operator", TestFixtures.NOW))
            .isInstanceOf(DictException.class)
            .extracting("code").isEqualTo(DictErrorCode.DICT_NOT_EMPTY);
    }
}
```

- [ ] **步骤 2：编写预期失败的默认项/租户项冲突与数量上限测试**

```java
class DictItemPolicyTest {
    @Test void tenant_code_cannot_shadow_default_code() {
        ItemCodeUsage usage = ItemCodeUsage.defaultAndTenant(false, true);
        assertThatThrownBy(() -> policy.checkCreate(
            TenantId.of("tenant-a"), usage, 0, 1000))
            .isInstanceOf(DictException.class)
            .extracting("code").isEqualTo(DictErrorCode.DICT_ITEM_CODE_CONFLICT);
    }

    @Test void enforces_unDeleted_item_limit() {
        assertThatThrownBy(() -> policy.checkCreate(
            TenantId.of("tenant-a"), ItemCodeUsage.none(), 1000, 1000))
            .isInstanceOf(DictException.class)
            .extracting("code").isEqualTo(DictErrorCode.DICT_ITEM_LIMIT_EXCEEDED);
    }
}
```

- [ ] **步骤 3：运行测试并确认其失败**

```bash
mvn -q -pl vincent-dict/vincent-dict-domain test
```

预期结果：因缺少聚合和策略类型而编译失败。

- [ ] **步骤 4：不使用框架注解实现聚合**

使用私有字段、显式工厂和包可见的重建方法。创建时设置 `version=0`、`deleted=false` 及维护元数据。重建方法接收持久化的 ID/版本/删除状态。`DictItem` 不得暴露变更编码、字典 ID 或租户 ID 的方法。恢复方法应拒绝未删除状态；恢复字典项需要应用层提供 `dictRestored=true` 这一事实。

策略必须严格实现以下矩阵：

```text
create default + any historical default/tenant use = conflict
create tenant  + historical default use            = conflict
create tenant  + same-tenant historical use         = conflict
create tenant  + other-tenant-only historical use   = allowed
```

- [ ] **步骤 5：运行领域测试**

```bash
mvn -q -pl vincent-dict/vincent-dict-domain test
```

预期结果：所有聚合和策略测试通过。

- [ ] **步骤 6：提交聚合**

```bash
git add vincent-dict/vincent-dict-domain
git commit -m "feat(dict): implement domain aggregates"
```

---

### Task 4: 定义并实现 Java 查询应用 API

**文件：**
- 创建：`vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/DictQueryService.java`
- 创建：`vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/DefaultDictQueryService.java`
- 创建：`vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/DictItemView.java`
- 创建：`vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/EffectiveDictData.java`
- 创建：`vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/EffectiveItemData.java`
- 创建：`vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/TenantProvider.java`
- 创建：`vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/SingleTenantProvider.java`
- 创建：`vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/DictLimits.java`
- 创建：`vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/port/DictQueryRepository.java`
- 创建：`vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/port/DictCache.java`
- 创建：`vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/port/NoopDictCache.java`
- 创建：`vincent-dict/vincent-dict-application/src/test/java/com/vincent/tools/dict/application/ApplicationFixtures.java`
- 测试：`vincent-dict/vincent-dict-application/src/test/java/com/vincent/tools/dict/application/DefaultDictQueryServiceTest.java`

**接口：**
- 使用：领域编码、租户 ID、状态/来源枚举。
- 产出：四个 `DictQueryService` 重载、内部有序仓储快照，以及后续两份计划共同使用的旁路缓存端口。

- [ ] **步骤 1：使用内存替身编写预期失败的查询语义测试**

```java
class DefaultDictQueryServiceTest {
    @Test void merges_default_and_current_tenant_items_in_stable_order() {
        repository.save(ApplicationFixtures.activeDict(), Arrays.asList(
            ApplicationFixtures.item("B", 10, DictItemSource.DEFAULT),
            ApplicationFixtures.item("A", 10, DictItemSource.TENANT)));
        DictQueryService service = service(() -> Optional.of("tenant-a"));

        assertThat(service.listEffectiveItems("ORDER_STATUS"))
            .extracting(DictItemView::getCode)
            .containsExactly("A", "B");
    }

    @Test void disabled_dict_returns_empty_and_missing_dict_throws() {
        repository.save(ApplicationFixtures.disabledDict(), Collections.emptyList());
        assertThat(service().listEffectiveItems("ORDER_STATUS")).isEmpty();
        assertThatThrownBy(() -> service().listEffectiveItems("MISSING"))
            .isInstanceOf(DictException.class)
            .extracting("code").isEqualTo(DictErrorCode.DICT_NOT_FOUND);
    }

    @Test void explicit_tenant_rejects_reserved_zero() {
        assertThatThrownBy(() -> service().listEffectiveItems("ORDER_STATUS", "0"))
            .isInstanceOf(DictException.class);
    }
}
```

- [ ] **步骤 2：运行应用测试并确认失败**

```bash
mvn -q -pl vincent-dict/vincent-dict-application -am test
```

预期结果：因缺少查询类型而编译失败。

- [ ] **步骤 3：定义精确的公共查询契约**

```java
public interface DictQueryService {
    List<DictItemView> listEffectiveItems(String dictCode);
    List<DictItemView> listEffectiveItems(String dictCode, String tenantId);
    Optional<DictItemView> findEffectiveItem(String dictCode, String itemCode);
    Optional<DictItemView> findEffectiveItem(String dictCode, String itemCode, String tenantId);
}
```

`DictItemView` 必须是 final 且不可变，仅包含 `code`、`name`、`description`、`sortNo` 和 `DictItemSource source`；返回不可修改的列表。

仓储契约必须严格如下：

```java
public interface DictQueryRepository {
    Optional<EffectiveDictData> findEffectiveData(DictCode dictCode, String tenantId);
}
```

`EffectiveDictData` 仅承载 `boolean enabled` 与 `List<EffectiveItemData>`。内部字典项数据包含持久化的 `id`，以支持最终的 `sortNo/code/id` 排序；映射至 `DictItemView` 时移除 ID。

- [ ] **步骤 4：实现租户解析、缓存接缝与查询语义**

默认的无宿主 Provider 返回 `Optional.of("0")` 以选择仅默认项模式。宿主 Provider 返回 `Optional.empty()` 时触发 `TENANT_CONTEXT_MISSING`。从一开始将缓存接缝定义为：

```java
public interface DictCache {
    List<DictItemView> load(
        String dictCode,
        String tenantId,
        Supplier<List<DictItemView>> databaseLoader
    );
    void evictAll(String dictCode);
    void evictTenant(String dictCode, String tenantId);
}
```

`NoopDictCache.load` 恰好调用一次其加载器。`DefaultDictQueryService` 通过该加载器委派一次仓储查询，映射内部有序快照，检查配置的 2,000 项上限，返回不可修改列表，并对返回列表进行过滤以实现 `findEffectiveItem`。

- [ ] **步骤 5：运行应用测试与禁止依赖检查**

```bash
mvn -q -pl vincent-dict/vincent-dict-application -am test
mvn -q -pl vincent-dict/vincent-dict-application dependency:tree
```

预期结果：测试通过；应用编译依赖仅包含 `vincent-dict-domain` 和 JDK 类型。

- [ ] **步骤 6：提交查询 API**

```bash
git add vincent-dict/vincent-dict-application
git commit -m "feat(dict): add query application service"
```

---

### Task 5: 添加手工 MySQL Schema、PO 映射与查询仓储

**文件：**
- 创建：`vincent-dict/sql/mysql/1.0.0/001-init.sql`
- 创建：`vincent-dict/sql/mysql/README.md`
- 创建：`vincent-dict/vincent-dict-infra-mybatis/src/main/java/com/vincent/tools/dict/infra/mybatis/po/DictPo.java`
- 创建：`vincent-dict/vincent-dict-infra-mybatis/src/main/java/com/vincent/tools/dict/infra/mybatis/po/DictItemPo.java`
- 创建：`vincent-dict/vincent-dict-infra-mybatis/src/main/java/com/vincent/tools/dict/infra/mybatis/mapper/DictMapper.java`
- 创建：`vincent-dict/vincent-dict-infra-mybatis/src/main/java/com/vincent/tools/dict/infra/mybatis/mapper/DictItemMapper.java`
- 创建：`vincent-dict/vincent-dict-infra-mybatis/src/main/resources/com/vincent/tools/dict/infra/mybatis/mapper/DictMapper.xml`
- 创建：`vincent-dict/vincent-dict-infra-mybatis/src/main/resources/com/vincent/tools/dict/infra/mybatis/mapper/DictItemMapper.xml`
- 创建：`vincent-dict/vincent-dict-infra-mybatis/src/main/java/com/vincent/tools/dict/infra/mybatis/MybatisDictQueryRepository.java`
- 测试：`vincent-dict/vincent-dict-infra-mybatis/src/test/java/com/vincent/tools/dict/infra/mybatis/MybatisDictQueryRepositoryIT.java`

**接口：**
- 使用：Task 4 中的 `DictQueryRepository` 与 `DictItemView`。
- 产出：Schema 版本 `1`、三张表和单次查询的有效字典项查找。

- [ ] **步骤 1：编写预期失败的 MySQL 集成测试**

使用镜像 `mysql:5.7.44` 的 Testcontainers `MySQLContainer<?>`。执行 `001-init.sql`，插入一个启用字典、两个默认项、一个当前租户项和一个其他租户项，然后断言：

```java
assertThat(repository.findEffective("ORDER_STATUS", "tenant-a"))
    .extracting(DictItemView::getCode)
    .containsExactly("DEFAULT_A", "TENANT_A")
    .doesNotContain("TENANT_B");
```

还应断言禁用字典返回可识别的禁用结果，而不是被静默地视为不存在。

- [ ] **步骤 2：运行集成测试并确认失败**

```bash
mvn -q -pl vincent-dict/vincent-dict-infra-mybatis -am -Dtest=MybatisDictQueryRepositoryIT test
```

预期结果：因 SQL 和映射尚不存在而失败。

- [ ] **步骤 3：编写严格的初始化 SQL**

按以下顺序创建表：`vin_dict_meta`、`vin_dict`、`vin_dict_item`。使用 InnoDB、`utf8mb4`、`BIGINT AUTO_INCREMENT`、`DATETIME(3)`、状态 `TINYINT`（`0=disabled`、`1=enabled`）、删除标记 `TINYINT`（`0=present`、`1=deleted`）以及乐观锁 `version INT NOT NULL DEFAULT 0`。编码列使用 `ascii_bin`，租户 ID 使用 `utf8mb4_bin`。严格插入一条元数据记录：

```sql
INSERT INTO vin_dict_meta (id, schema_version, updated_at)
VALUES (1, '1', CURRENT_TIMESTAMP(3));
```

不得使用 `IF NOT EXISTS`；不得添加业务种子数据。

- [ ] **步骤 4：实现显式 MyBatis 映射**

不得依赖宿主全局逻辑删除配置。有效字典项 SQL 必须过滤字典/字典项的 `deleted=0`、字典/字典项的 `status=1` 以及字典项租户范围 `IN ('0', #{tenantId})`，随后按 `sort_no, code, id` 排序。仅为确定性排序查询内部字典项 ID；不得通过应用 DTO 暴露它。

- [ ] **步骤 5：运行包含约束验证的集成测试**

添加以下断言：

```text
duplicate dict code fails
duplicate (dict_id, tenant_id, code) fails even when the first row is deleted
same tenant item code in two different tenants succeeds
meta schema version equals 1
```

执行：

```bash
mvn -q -pl vincent-dict/vincent-dict-infra-mybatis -am verify
```

预期结果：所有单元测试与 MySQL 集成测试通过。

- [ ] **步骤 6：提交 Schema 与读适配器**

```bash
git add vincent-dict/sql vincent-dict/vincent-dict-infra-mybatis
git commit -m "feat(dict): add mysql query adapter"
```

---

### Task 6: 构建 Boot 2 自动配置与快速失败的 Schema 校验

**文件：**
- 创建：`vincent-dict/vincent-dict-boot2-starter/src/main/java/com/vincent/tools/dict/boot2/DictProperties.java`
- 创建：`vincent-dict/vincent-dict-boot2-starter/src/main/java/com/vincent/tools/dict/boot2/DictCoreAutoConfiguration.java`
- 创建：`vincent-dict/vincent-dict-boot2-starter/src/main/java/com/vincent/tools/dict/boot2/DictInfrastructureResolver.java`
- 创建：`vincent-dict/vincent-dict-boot2-starter/src/main/java/com/vincent/tools/dict/boot2/DictSchemaValidator.java`
- 创建：`vincent-dict/vincent-dict-boot2-starter/src/main/resources/META-INF/spring.factories`
- 创建：`vincent-dict/vincent-dict-boot2-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
- 测试：`vincent-dict/vincent-dict-boot2-starter/src/test/java/com/vincent/tools/dict/boot2/DictCoreAutoConfigurationTest.java`
- 测试：`vincent-dict/vincent-dict-boot2-starter/src/test/java/com/vincent/tools/dict/boot2/DictSchemaValidatorIT.java`

**接口：**
- 使用：查询服务、MyBatis mapper XML、宿主 `DataSource`、`SqlSessionFactory` 与 `PlatformTransactionManager`。
- 产出：`vincent.dict.*` 配置及条件化的 `DictQueryService` Bean。

- [ ] **步骤 1：编写预期失败的 ApplicationContextRunner 测试**

覆盖以下精确场景：

```java
contextRunner.withPropertyValues("vincent.dict.enabled=false")
    .run(context -> assertThat(context).doesNotHaveBean(DictQueryService.class));

contextRunner.withBean(TenantProvider.class, () -> () -> Optional.of("tenant-a"))
    .run(context -> assertThat(context).hasSingleBean(DictQueryService.class));
```

增加对无效上限、没有 primary/显式名称时存在多个 DataSource，以及显式指定不存在的 Bean 名称等失败场景的覆盖。

- [ ] **步骤 2：运行 Starter 测试并确认失败**

```bash
mvn -q -pl vincent-dict/vincent-dict-boot2-starter -am test
```

预期结果：因缺少配置类而编译失败。

- [ ] **步骤 3：实现经过校验的配置属性**

使用前缀 `vincent.dict` 及以下默认值：

```text
enabled=true
admin.enabled=false
admin.base-path=/dict-admin
admin.api-path=/vincent/dict/admin/api/v1
limits.default-items-per-dict=1000
limits.tenant-items-per-dict=1000
limits.max-effective-items=2000
limits.default-page-size=20
limits.max-page-size=100
```

支持可选 Bean 名称 `data-source-bean-name`、`sql-session-factory-bean-name` 和 `transaction-manager-bean-name`。若在多基础设施宿主中设置其中一个，则必须同时设置三个。校验所选 `SqlSessionFactory` 环境使用所选 DataSource，且事务管理器暴露相同的资源工厂。

- [ ] **步骤 4：实现 Boot 2 基础设施解析与 Mapper 注册**

使用 `ObjectProvider`、Bean 名称、`@Primary` 解析，以及带显式 `sqlSessionFactoryBeanName` 的编程式 `MapperScannerConfigurer`；绝不修改宿主 MyBatis 全局配置。通过以下内容注册自动配置：

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.vincent.tools.dict.boot2.DictCoreAutoConfiguration
```

- [ ] **步骤 5：实现只读 Schema 校验**

启用状态下启动时，查询 `information_schema.tables` 以检查 `vin_dict_meta`、`vin_dict` 和 `vin_dict_item`，随后查询 `id=1` 的元数据记录。缺少表时抛出 `SCHEMA_MISSING`；版本不是 `"1"` 时抛出 `SCHEMA_VERSION_MISMATCH`，并给出必需 SQL 路径 `sql/mysql/1.0.0/001-init.sql`。不得执行任何 DDL。

- [ ] **步骤 6：运行上下文与 MySQL 校验测试**

```bash
mvn -q -pl vincent-dict/vincent-dict-boot2-starter -am verify
```

预期结果：所有上下文和 MySQL Schema 场景均通过；禁用组件不会访问数据库。

- [ ] **步骤 7：提交 Starter**

```bash
git add vincent-dict/vincent-dict-boot2-starter
git commit -m "feat(dict): add boot2 core starter"
```

---

### Task 7: 添加兼容性示例、消费者文档与核心验收检查

**文件：**
- 创建：`vincent-dict/vincent-dict-example-boot2/pom.xml`
- 创建：`vincent-dict/vincent-dict-example-boot2/src/main/java/com/vincent/tools/dict/example/DictExampleApplication.java`
- 创建：`vincent-dict/vincent-dict-example-boot2/src/main/java/com/vincent/tools/dict/example/ExampleTenantProvider.java`
- 创建：`vincent-dict/vincent-dict-example-boot2/src/main/resources/application.yml`
- 创建：`vincent-dict/vincent-dict-example-boot2/src/test/java/com/vincent/tools/dict/example/DictExampleApplicationIT.java`
- 创建：`README.md`
- 创建：`vincent-dict/README.md`
- 修改：`vincent-dict/pom.xml`
- 修改：`vincent-tools-bom/pom.xml`

**接口：**
- 使用：已发布的 BOM、核心 Starter、手工 SQL 与 `TenantProvider`。
- 产出：可执行的兼容性证明和可直接复制粘贴的消费者说明。

- [ ] **步骤 1：编写预期失败的示例宿主集成测试**

启动 MySQL 5.7 容器，在 Spring 启动前执行手工 SQL，动态设置宿主属性，并断言：

```java
assertThat(queryService.listEffectiveItems("ORDER_STATUS"))
    .extracting(DictItemView::getCode)
    .containsExactly("CREATED", "WAIT_CONFIRM");
```

示例宿主必须使用 Spring Boot `2.2.6.RELEASE`、Java 8 以及公开的 Starter 依赖，而不是访问内部模块。

- [ ] **步骤 2：在接线前运行示例测试并确认失败**

```bash
mvn -q -pl vincent-dict/vincent-dict-example-boot2 -am test
```

预期结果：在示例应用和 Provider 注册前失败。

- [ ] **步骤 3：实现最小示例宿主**

注册：

```java
@Bean
TenantProvider tenantProvider() {
    return () -> Optional.of("tenant-a");
}
```

演示数据仅保留在测试资源中。不得添加 HTTP 查询端点。

- [ ] **步骤 4：编写核心消费者文档**

记录 BOM 导入、核心 Starter 依赖、手工 SQL、所需 MySQL 权限、`TenantProvider`、显式租户批量 API、无 Provider 时的单租户行为、数据源选择、编码规则、字典项上限、异常代码和 Schema 升级策略。包括以下声明：“Vincent Dict never runs DDL at application startup.”

- [ ] **步骤 5：运行完整核心验收套件**

```bash
mvn -q clean verify -DskipFrontend
mvn -q -pl vincent-dict/vincent-dict-boot2-starter dependency:tree
```

预期结果：Reactor 通过；核心 Starter 依赖树不包含 Redis，且启动非 Web 上下文不需要 Spring MVC。

- [ ] **步骤 6：提交核心文档与兼容性证明**

```bash
git add README.md vincent-tools-bom vincent-dict
git commit -m "docs(dict): add core usage and boot2 example"
```

## 核心计划退出标准

- 完整 Reactor 可在 Java 8 和 Maven 3.6+ 上构建。
- 领域和应用模块不包含任何被禁止的框架依赖。
- 手工 SQL 严格创建 `vin_dict_meta`、`vin_dict` 与 `vin_dict_item`，Schema 版本为 `1`。
- 非 Web 的 Boot 2.2.6 宿主可以查询默认项和租户生效项。
- 缺失/不兼容的 Schema 与存在歧义的基础设施会在启动时以稳定错误快速失败。
- 核心 Starter 依赖树不包含 Redis，且不会强制成为 Web 应用。
