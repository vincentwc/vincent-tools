# Vincent Dict Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and publish the DDD-based Vincent Dict core, MySQL persistence, manual SQL, Java query API, and Spring Boot 2 starter without Redis or management mutations.

**Architecture:** Pure Java domain and application modules define rules and ports. A MyBatis-Plus adapter implements persistence against host-owned MySQL tables, and a Boot 2 starter selects host infrastructure, validates the manual schema, and conditionally creates the query service. Web resources are present but remain disabled until the admin plan.

**Tech Stack:** Java 8, Maven 3.6+, Spring Boot 2.2.6.RELEASE, MyBatis-Plus 3.3.2, MySQL 5.7+, JUnit 5, AssertJ, Testcontainers 1.19.8.

## Global Constraints

- All Maven coordinates use groupId `com.vincent.tools`; development version is `1.0.0-SNAPSHOT`.
- Technical identifiers use `dict`, not `dictionary`; published artifact names start with `vincent-`.
- `vincent-dict-domain` must not depend on Spring, MyBatis, Redis, HTTP, Jackson, or Lombok.
- Java source and target compatibility are exactly 1.8; do not use records, sealed classes, `List.of`, or post-Java-8 APIs.
- Spring Boot compatibility baseline is exactly `2.2.6.RELEASE`; MyBatis-Plus baseline is exactly `3.3.2`.
- Database support is MySQL 5.7+ only; the component never creates or migrates tables at runtime.
- Tenant IDs are nonblank strings up to 64 characters; reserved value `"0"` represents default items.
- Dict and item codes must match `^[A-Z][A-Z0-9_]{0,63}$`; reject rather than normalize input.
- Dict/item database IDs are internal `BIGINT AUTO_INCREMENT` values and never part of the business query contract.
- Domain/application code does not depend on any host response wrapper or customer-specific package.
- Existing sibling business systems are read-only evidence; this plan modifies only `vincent-tools`.

---

### Task 1: Create the Maven reactor, parent POM, BOM, and module skeleton

**Files:**
- Modify: `.gitignore`
- Create: `pom.xml`
- Create: `vincent-tools-bom/pom.xml`
- Create: `vincent-dict/pom.xml`
- Create: `vincent-dict/vincent-dict-domain/pom.xml`
- Create: `vincent-dict/vincent-dict-application/pom.xml`
- Create: `vincent-dict/vincent-dict-infra-mybatis/pom.xml`
- Create: `vincent-dict/vincent-dict-admin-ui/pom.xml`
- Create: `vincent-dict/vincent-dict-web/pom.xml`
- Create: `vincent-dict/vincent-dict-boot2-starter/pom.xml`
- Create: `vincent-dict/vincent-dict-cache-redis-boot2-starter/pom.xml`

**Interfaces:**
- Consumes: none.
- Produces: one Maven reactor with all approved artifactIds and centralized versions.

- [ ] **Step 1: Extend repository ignores for generated build outputs**

```gitignore
.superpowers/
.idea/
**/target/
**/node_modules/
**/dist/
**/.frontend/
*.iml
```

- [ ] **Step 2: Create the root parent/aggregator POM**

Use packaging `pom`, modules `vincent-tools-bom` and `vincent-dict`, Java 8 compiler properties, UTF-8, and these locked properties:

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

Import `spring-boot-dependencies` in `dependencyManagement`; manage JUnit, AssertJ, Testcontainers, MySQL connector `5.1.49`, and all Vincent module versions. Configure `maven-compiler-plugin 3.11.0`, `maven-surefire-plugin 3.2.5`, `maven-failsafe-plugin 3.2.5`, and `maven-enforcer-plugin 3.4.1` with Maven `3.6.0+` and Java `[1.8,)`. Add `animal-sniffer-maven-plugin 1.23` with the `java18` signature so builds running on newer JDKs cannot call post-Java-8 APIs. Configure source jars for all published Java modules and Javadoc jars for public API modules. Do not commit `distributionManagement`, repository credentials, or internal repository URLs; deployment supplies `-DaltDeploymentRepository` or CI Maven settings externally.

- [ ] **Step 3: Create the public BOM**

`vincent-tools-bom/pom.xml` must inherit the root and contain only `dependencyManagement` entries for:

```text
vincent-dict-domain
vincent-dict-application
vincent-dict-infra-mybatis
vincent-dict-web
vincent-dict-admin-ui
vincent-dict-boot2-starter
vincent-dict-cache-redis-boot2-starter
```

Do not put build plugins or third-party library versions in the public BOM.

- [ ] **Step 4: Create the dict aggregator and leaf POMs**

The dependency direction must be encoded exactly:

```text
domain <- application <- infra-mybatis <- boot2-starter
                    ^                    ^
                    └── web ─────────────┤
admin-ui <───────────────────────────────┘
application <- cache-redis-boot2-starter
```

Keep the Redis module in the reactor but empty until the Redis plan. Mark Spring MVC dependencies in `vincent-dict-web` as optional so the core Starter cannot turn a non-Web host into a Web application.

- [ ] **Step 5: Verify the empty reactor**

Run:

```bash
mvn -q -DskipTests install
mvn -q help:effective-pom -pl vincent-tools-bom
```

Expected: reactor `BUILD SUCCESS`; effective BOM lists Vincent artifacts without Spring build plugins.

- [ ] **Step 6: Commit the reactor skeleton**

```bash
git add .gitignore pom.xml vincent-tools-bom vincent-dict
git commit -m "build: create vincent tools reactor"
```

---

### Task 2: Implement domain primitives and validation

**Files:**
- Create: `vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/DictCode.java`
- Create: `vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/ItemCode.java`
- Create: `vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/TenantId.java`
- Create: `vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/DictStatus.java`
- Create: `vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/ItemStatus.java`
- Create: `vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/DictItemSource.java`
- Create: `vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/DictErrorCode.java`
- Create: `vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/DictException.java`
- Test: `vincent-dict/vincent-dict-domain/src/test/java/com/vincent/tools/dict/domain/DictCodeTest.java`
- Test: `vincent-dict/vincent-dict-domain/src/test/java/com/vincent/tools/dict/domain/TenantIdTest.java`

**Interfaces:**
- Consumes: Java 8 only.
- Produces: `DictCode.of(String)`, `ItemCode.of(String)`, `TenantId.of(String)`, `TenantId.defaultItem()`, status/source enums, and stable exceptions.

- [ ] **Step 1: Write failing code and tenant validation tests**

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

- [ ] **Step 2: Run the domain tests and verify failure**

Run:

```bash
mvn -q -pl vincent-dict/vincent-dict-domain test
```

Expected: compilation fails because value objects do not exist.

- [ ] **Step 3: Implement immutable value objects and stable errors**

Implement `DictCode` and `ItemCode` with the exact regex and maximum length. Implement `TenantId` with a private constructor and two factories:

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

Implement value equality/hashCode and redact neither code nor tenant in `toString`; these identifiers are not secrets.

Define the complete first-version `DictErrorCode` enum now so later modules do not invent strings:

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

- [ ] **Step 4: Run tests and dependency checks**

```bash
mvn -q -pl vincent-dict/vincent-dict-domain test
mvn -q -pl vincent-dict/vincent-dict-domain dependency:tree
```

Expected: tests pass; compile dependency tree contains only the JDK.

- [ ] **Step 5: Commit domain primitives**

```bash
git add vincent-dict/vincent-dict-domain
git commit -m "feat(dict): add domain value objects"
```

---

### Task 3: Implement Dict and DictItem aggregate rules

**Files:**
- Create: `vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/Dict.java`
- Create: `vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/DictItem.java`
- Create: `vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/DictItemPolicy.java`
- Create: `vincent-dict/vincent-dict-domain/src/main/java/com/vincent/tools/dict/domain/ItemCodeUsage.java`
- Create: `vincent-dict/vincent-dict-domain/src/test/java/com/vincent/tools/dict/domain/TestFixtures.java`
- Test: `vincent-dict/vincent-dict-domain/src/test/java/com/vincent/tools/dict/domain/DictTest.java`
- Test: `vincent-dict/vincent-dict-domain/src/test/java/com/vincent/tools/dict/domain/DictItemPolicyTest.java`

**Interfaces:**
- Consumes: Task 2 value objects and `DictException`.
- Produces: aggregate factories/reconstitution methods and `DictItemPolicy.checkCreate(TenantId, ItemCodeUsage, int, int)`.

- [ ] **Step 1: Write failing aggregate state-transition tests**

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

- [ ] **Step 2: Write failing default/tenant collision and limit tests**

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

- [ ] **Step 3: Run tests and verify they fail**

```bash
mvn -q -pl vincent-dict/vincent-dict-domain test
```

Expected: compilation fails for missing aggregate and policy types.

- [ ] **Step 4: Implement aggregates without framework annotations**

Use private fields, explicit factories, and package-visible reconstitution methods. Creation sets `version=0`, `deleted=false`, and maintenance metadata. Reconstitution accepts persisted ID/version/deletion state. `DictItem` exposes no method that changes code, dict ID, or tenant ID. Restore methods reject nondeleted state; item restore requires an application-provided `dictRestored=true` fact.

The policy must implement the exact matrix:

```text
create default + any historical default/tenant use = conflict
create tenant  + historical default use            = conflict
create tenant  + same-tenant historical use         = conflict
create tenant  + other-tenant-only historical use   = allowed
```

- [ ] **Step 5: Run domain tests**

```bash
mvn -q -pl vincent-dict/vincent-dict-domain test
```

Expected: all aggregate and policy tests pass.

- [ ] **Step 6: Commit aggregates**

```bash
git add vincent-dict/vincent-dict-domain
git commit -m "feat(dict): implement domain aggregates"
```

---

### Task 4: Define and implement the Java query application API

**Files:**
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/DictQueryService.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/DefaultDictQueryService.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/DictItemView.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/EffectiveDictData.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/EffectiveItemData.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/TenantProvider.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/SingleTenantProvider.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/DictLimits.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/port/DictQueryRepository.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/port/DictCache.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/port/NoopDictCache.java`
- Create: `vincent-dict/vincent-dict-application/src/test/java/com/vincent/tools/dict/application/ApplicationFixtures.java`
- Test: `vincent-dict/vincent-dict-application/src/test/java/com/vincent/tools/dict/application/DefaultDictQueryServiceTest.java`

**Interfaces:**
- Consumes: domain codes, tenant IDs, status/source enums.
- Produces: the four `DictQueryService` overloads, an internal ordered repository snapshot, and the cache-aside port used by both later plans.

- [ ] **Step 1: Write failing query semantic tests with in-memory fakes**

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

- [ ] **Step 2: Run application tests and verify failure**

```bash
mvn -q -pl vincent-dict/vincent-dict-application -am test
```

Expected: compilation fails for missing query types.

- [ ] **Step 3: Define the exact public query contract**

```java
public interface DictQueryService {
    List<DictItemView> listEffectiveItems(String dictCode);
    List<DictItemView> listEffectiveItems(String dictCode, String tenantId);
    Optional<DictItemView> findEffectiveItem(String dictCode, String itemCode);
    Optional<DictItemView> findEffectiveItem(String dictCode, String itemCode, String tenantId);
}
```

`DictItemView` must be final and immutable with only `code`, `name`, `description`, `sortNo`, and `DictItemSource source`; return unmodifiable lists.

The repository contract is exact:

```java
public interface DictQueryRepository {
    Optional<EffectiveDictData> findEffectiveData(DictCode dictCode, String tenantId);
}
```

`EffectiveDictData` carries only `boolean enabled` plus `List<EffectiveItemData>`. Internal item data includes persisted `id` for the final `sortNo/code/id` order; mapping to `DictItemView` removes the ID.

- [ ] **Step 4: Implement tenant resolution, caching seam, and query semantics**

The default no-host provider returns `Optional.of("0")` to select default-only mode. A host provider returning `Optional.empty()` causes `TENANT_CONTEXT_MISSING`. Define the cache seam from the start as:

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

`NoopDictCache.load` calls its loader exactly once. `DefaultDictQueryService` delegates one repository query through that loader, maps the internal ordered snapshot, checks the 2,000-item configured maximum, returns an unmodifiable list, and filters the returned list for `findEffectiveItem`.

- [ ] **Step 5: Run application tests and forbidden-dependency check**

```bash
mvn -q -pl vincent-dict/vincent-dict-application -am test
mvn -q -pl vincent-dict/vincent-dict-application dependency:tree
```

Expected: tests pass; application compile dependencies include only `vincent-dict-domain` and JDK types.

- [ ] **Step 6: Commit the query API**

```bash
git add vincent-dict/vincent-dict-application
git commit -m "feat(dict): add query application service"
```

---

### Task 5: Add manual MySQL schema, PO mappings, and query repository

**Files:**
- Create: `vincent-dict/sql/mysql/1.0.0/001-init.sql`
- Create: `vincent-dict/sql/mysql/README.md`
- Create: `vincent-dict/vincent-dict-infra-mybatis/src/main/java/com/vincent/tools/dict/infra/mybatis/po/DictPo.java`
- Create: `vincent-dict/vincent-dict-infra-mybatis/src/main/java/com/vincent/tools/dict/infra/mybatis/po/DictItemPo.java`
- Create: `vincent-dict/vincent-dict-infra-mybatis/src/main/java/com/vincent/tools/dict/infra/mybatis/mapper/DictMapper.java`
- Create: `vincent-dict/vincent-dict-infra-mybatis/src/main/java/com/vincent/tools/dict/infra/mybatis/mapper/DictItemMapper.java`
- Create: `vincent-dict/vincent-dict-infra-mybatis/src/main/resources/com/vincent/tools/dict/infra/mybatis/mapper/DictMapper.xml`
- Create: `vincent-dict/vincent-dict-infra-mybatis/src/main/resources/com/vincent/tools/dict/infra/mybatis/mapper/DictItemMapper.xml`
- Create: `vincent-dict/vincent-dict-infra-mybatis/src/main/java/com/vincent/tools/dict/infra/mybatis/MybatisDictQueryRepository.java`
- Test: `vincent-dict/vincent-dict-infra-mybatis/src/test/java/com/vincent/tools/dict/infra/mybatis/MybatisDictQueryRepositoryIT.java`

**Interfaces:**
- Consumes: `DictQueryRepository` and `DictItemView` from Task 4.
- Produces: schema version `1`, three tables, and one-query effective item lookup.

- [ ] **Step 1: Write the failing MySQL integration test**

Use Testcontainers `MySQLContainer<?>` with image `mysql:5.7.44`. Execute `001-init.sql`, insert one active dict, two default items, one current-tenant item, and one other-tenant item, then assert:

```java
assertThat(repository.findEffective("ORDER_STATUS", "tenant-a"))
    .extracting(DictItemView::getCode)
    .containsExactly("DEFAULT_A", "TENANT_A")
    .doesNotContain("TENANT_B");
```

Also assert a disabled dict returns a known disabled result rather than silently looking missing.

- [ ] **Step 2: Run the integration test and verify failure**

```bash
mvn -q -pl vincent-dict/vincent-dict-infra-mybatis -am -Dtest=MybatisDictQueryRepositoryIT test
```

Expected: FAIL because SQL and mappings do not exist.

- [ ] **Step 3: Write strict initialization SQL**

Create tables in this order: `vin_dict_meta`, `vin_dict`, `vin_dict_item`. Use InnoDB, `utf8mb4`, `BIGINT AUTO_INCREMENT`, `DATETIME(3)`, status `TINYINT` (`0=disabled`, `1=enabled`), deletion `TINYINT` (`0=present`, `1=deleted`), and optimistic `version INT NOT NULL DEFAULT 0`. Use `ascii_bin` for code columns and `utf8mb4_bin` for tenant ID. Insert exactly one meta row:

```sql
INSERT INTO vin_dict_meta (id, schema_version, updated_at)
VALUES (1, '1', CURRENT_TIMESTAMP(3));
```

Do not use `IF NOT EXISTS`; do not add business seed rows.

- [ ] **Step 4: Implement explicit MyBatis mappings**

Do not rely on host global logical-delete configuration. The effective item SQL must filter dict/item `deleted=0`, dict/item `status=1`, and item tenant scope `IN ('0', #{tenantId})`, then order by `sort_no, code, id`. Select internal item ID only for deterministic ordering; do not expose it from the application DTO.

- [ ] **Step 5: Run integration tests including constraints**

Add assertions that:

```text
duplicate dict code fails
duplicate (dict_id, tenant_id, code) fails even when the first row is deleted
same tenant item code in two different tenants succeeds
meta schema version equals 1
```

Run:

```bash
mvn -q -pl vincent-dict/vincent-dict-infra-mybatis -am verify
```

Expected: all unit and MySQL integration tests pass.

- [ ] **Step 6: Commit schema and read adapter**

```bash
git add vincent-dict/sql vincent-dict/vincent-dict-infra-mybatis
git commit -m "feat(dict): add mysql query adapter"
```

---

### Task 6: Build Boot 2 auto-configuration and fail-fast schema validation

**Files:**
- Create: `vincent-dict/vincent-dict-boot2-starter/src/main/java/com/vincent/tools/dict/boot2/DictProperties.java`
- Create: `vincent-dict/vincent-dict-boot2-starter/src/main/java/com/vincent/tools/dict/boot2/DictCoreAutoConfiguration.java`
- Create: `vincent-dict/vincent-dict-boot2-starter/src/main/java/com/vincent/tools/dict/boot2/DictInfrastructureResolver.java`
- Create: `vincent-dict/vincent-dict-boot2-starter/src/main/java/com/vincent/tools/dict/boot2/DictSchemaValidator.java`
- Create: `vincent-dict/vincent-dict-boot2-starter/src/main/resources/META-INF/spring.factories`
- Create: `vincent-dict/vincent-dict-boot2-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
- Test: `vincent-dict/vincent-dict-boot2-starter/src/test/java/com/vincent/tools/dict/boot2/DictCoreAutoConfigurationTest.java`
- Test: `vincent-dict/vincent-dict-boot2-starter/src/test/java/com/vincent/tools/dict/boot2/DictSchemaValidatorIT.java`

**Interfaces:**
- Consumes: query service, MyBatis mapper XML, host `DataSource`, `SqlSessionFactory`, and `PlatformTransactionManager`.
- Produces: `vincent.dict.*` configuration and conditional `DictQueryService` bean.

- [ ] **Step 1: Write failing ApplicationContextRunner tests**

Cover exact cases:

```java
contextRunner.withPropertyValues("vincent.dict.enabled=false")
    .run(context -> assertThat(context).doesNotHaveBean(DictQueryService.class));

contextRunner.withBean(TenantProvider.class, () -> () -> Optional.of("tenant-a"))
    .run(context -> assertThat(context).hasSingleBean(DictQueryService.class));
```

Add failures for invalid limits, multiple DataSources without a primary/explicit name, and an explicit nonexistent bean name.

- [ ] **Step 2: Run starter tests and verify failure**

```bash
mvn -q -pl vincent-dict/vincent-dict-boot2-starter -am test
```

Expected: compilation fails for missing configuration classes.

- [ ] **Step 3: Implement validated configuration properties**

Use prefix `vincent.dict` with defaults:

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

Support optional bean names `data-source-bean-name`, `sql-session-factory-bean-name`, and `transaction-manager-bean-name`. If one is set in a multi-infrastructure host, require all three. Validate that the selected `SqlSessionFactory` environment uses the selected DataSource and that the transaction manager exposes the same resource factory.

- [ ] **Step 4: Implement Boot 2 infrastructure resolution and Mapper registration**

Use `ObjectProvider`, bean names, `@Primary` resolution, and a programmatic `MapperScannerConfigurer` with explicit `sqlSessionFactoryBeanName`; never mutate host MyBatis global configuration. Register auto-configuration via:

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.vincent.tools.dict.boot2.DictCoreAutoConfiguration
```

- [ ] **Step 5: Implement read-only Schema validation**

On enabled startup, query `information_schema.tables` for `vin_dict_meta`, `vin_dict`, and `vin_dict_item`, then query meta row `id=1`. Missing tables throw `SCHEMA_MISSING`; version other than `"1"` throws `SCHEMA_VERSION_MISMATCH` with the required SQL path `sql/mysql/1.0.0/001-init.sql`. Execute no DDL.

- [ ] **Step 6: Run context and MySQL validation tests**

```bash
mvn -q -pl vincent-dict/vincent-dict-boot2-starter -am verify
```

Expected: all contexts and MySQL schema cases pass; a disabled component does not touch the database.

- [ ] **Step 7: Commit the starter**

```bash
git add vincent-dict/vincent-dict-boot2-starter
git commit -m "feat(dict): add boot2 core starter"
```

---

### Task 7: Add the compatibility example, consumer documentation, and core acceptance checks

**Files:**
- Create: `vincent-dict/vincent-dict-example-boot2/pom.xml`
- Create: `vincent-dict/vincent-dict-example-boot2/src/main/java/com/vincent/tools/dict/example/DictExampleApplication.java`
- Create: `vincent-dict/vincent-dict-example-boot2/src/main/java/com/vincent/tools/dict/example/ExampleTenantProvider.java`
- Create: `vincent-dict/vincent-dict-example-boot2/src/main/resources/application.yml`
- Create: `vincent-dict/vincent-dict-example-boot2/src/test/java/com/vincent/tools/dict/example/DictExampleApplicationIT.java`
- Create: `README.md`
- Create: `vincent-dict/README.md`
- Modify: `vincent-dict/pom.xml`
- Modify: `vincent-tools-bom/pom.xml`

**Interfaces:**
- Consumes: published BOM, core Starter, manual SQL, and `TenantProvider`.
- Produces: an executable compatibility proof and copy-paste consumer instructions.

- [ ] **Step 1: Write the failing example-host integration test**

Start a MySQL 5.7 container, apply manual SQL before Spring startup, set host properties dynamically, and assert:

```java
assertThat(queryService.listEffectiveItems("ORDER_STATUS"))
    .extracting(DictItemView::getCode)
    .containsExactly("CREATED", "WAIT_CONFIRM");
```

The example host must use Spring Boot `2.2.6.RELEASE`, Java 8, and the public Starter dependency rather than reaching into internal modules.

- [ ] **Step 2: Run the example test and verify failure before wiring**

```bash
mvn -q -pl vincent-dict/vincent-dict-example-boot2 -am test
```

Expected: FAIL until the example app and provider are registered.

- [ ] **Step 3: Implement the minimal example host**

Register:

```java
@Bean
TenantProvider tenantProvider() {
    return () -> Optional.of("tenant-a");
}
```

Keep demo data in test resources only. Do not add an HTTP query endpoint.

- [ ] **Step 4: Write core consumer documentation**

Document BOM import, core Starter dependency, manual SQL, required MySQL privileges, `TenantProvider`, explicit-tenant batch API, no-provider single-tenant behavior, data source selection, code rules, item limits, exception codes, and schema upgrade policy. Include the statement: “Vincent Dict never runs DDL at application startup.”

- [ ] **Step 5: Run the complete core acceptance suite**

```bash
mvn -q clean verify -DskipFrontend
mvn -q -pl vincent-dict/vincent-dict-boot2-starter dependency:tree
```

Expected: reactor passes; core Starter dependency tree contains no Redis and does not require Spring MVC to start a non-Web context.

- [ ] **Step 6: Commit core documentation and compatibility proof**

```bash
git add README.md vincent-tools-bom vincent-dict
git commit -m "docs(dict): add core usage and boot2 example"
```

## Core Plan Exit Criteria

- The full reactor builds on Java 8 and Maven 3.6+.
- Domain and application modules contain no forbidden framework dependencies.
- Manual SQL creates exactly `vin_dict_meta`, `vin_dict`, and `vin_dict_item` with Schema version `1`.
- A non-Web Boot 2.2.6 host can query default and tenant-effective items.
- Missing/incompatible schema and ambiguous infrastructure fail at startup with stable errors.
- Core Starter dependency tree contains no Redis and does not force a Web application.
