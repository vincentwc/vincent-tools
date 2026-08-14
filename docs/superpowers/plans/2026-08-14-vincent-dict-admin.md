# Vincent Dict Admin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add transactional dictionary maintenance, scoped authorization, tenant lookup, REST management APIs, and an embedded Vue management page to the existing core Starter.

**Architecture:** Application command services enforce use-case ordering through repository, transaction, tenant-directory, operator, permission, and cache ports. MyBatis provides locked writes; Spring MVC adapters expose versioned admin APIs only in eligible Web hosts. A separately built Vue jar contributes static resources consumed by the same core Starter.

**Tech Stack:** Core plan output, Java 8, Spring MVC from the host, Spring Boot 2.2.6.RELEASE, Vue 3.5.x, TypeScript 5.7.x, Vite 6.x, Element Plus 2.9.x, Vitest 2.x, Node.js 22.12.x, frontend-maven-plugin 1.12.1.

## Global Constraints

- Complete `2026-08-14-vincent-dict-core.md` first; do not duplicate domain or query rules in Web/UI code.
- The management UI remains inside `vincent-dict-boot2-starter`; do not create an admin Starter.
- Spring MVC is optional/provided and must not make a non-Web host start a servlet container.
- Admin auto-configuration requires Servlet Web, `DispatcherServlet`, `vincent.dict.admin.enabled=true`, `OperatorProvider`, and `PermissionProvider`.
- Missing `TenantDirectory` disables and rejects tenant-item management but does not disable default-item management.
- Every tenant-item read/write authorization check includes the target tenant ID.
- Item/dict codes and ownership are immutable; deleted records can only be viewed or restored.
- A dict with undeleted items cannot be deleted; deletion never cascades.
- First version stores only created/updated metadata; do not add audit history or before/after JSON.
- `OperatorProvider` must return a nonblank, untrimmed-free identifier of at most 64 characters; application timestamps come from an injected Java `Clock` in UTC.
- Every write is transactional; cache invalidation occurs after a successful commit through `DictCache`.
- UI API calls use `/vincent/dict/admin/api/v1`; UI base path defaults to `/dict-admin`.

---

### Task 1: Define admin application contracts and transactional command services

**Files:**
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/admin/DictAdminPermission.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/admin/OperatorProvider.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/admin/PermissionProvider.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/admin/TenantDirectory.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/admin/PageResult.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/admin/TenantOption.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/admin/DictAdminService.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/admin/DefaultDictAdminService.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/admin/command/CreateDictCommand.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/admin/command/UpdateDictCommand.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/admin/command/CreateItemCommand.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/admin/command/UpdateItemCommand.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/admin/query/DictPageQuery.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/admin/query/ItemPageQuery.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/admin/view/DictSummary.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/admin/view/DictDetail.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/admin/view/DictItemDetail.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/port/DictAdminRepository.java`
- Create: `vincent-dict/vincent-dict-application/src/main/java/com/vincent/tools/dict/application/port/TxRunner.java`
- Test: `vincent-dict/vincent-dict-application/src/test/java/com/vincent/tools/dict/application/admin/DefaultDictAdminServiceTest.java`

**Interfaces:**
- Consumes: domain aggregates/policies, `DictCache`, configured limits.
- Produces: all admin commands/queries and ports consumed by MyBatis and Web tasks.

- [ ] **Step 1: Write failing scoped-permission and tenant-directory tests**

```java
@Test void tenant_item_create_checks_target_scope_and_directory() {
    service.createTenantItem(10L, "tenant-b",
        new CreateItemCommand("WAIT_CONFIRM", "Waiting", "", 20));

    assertThat(permissionChecks).containsExactly(
        new PermissionCheck(DictAdminPermission.ITEM_CREATE, Optional.of("tenant-b")));
    assertThat(tenantLookups).containsExactly("tenant-b");
}

@Test void missing_tenant_directory_rejects_tenant_write() {
    service = fixture.withoutTenantDirectory().build();
    assertThatThrownBy(() -> service.createTenantItem(10L, "tenant-b", command))
        .isInstanceOf(DictException.class)
        .extracting("code").isEqualTo(DictErrorCode.CONFIGURATION_INVALID);
}
```

- [ ] **Step 2: Write failing transaction, lock, deletion, and cache tests**

```java
@Test void create_item_locks_dict_and_invalidates_after_commit() {
    service.createTenantItem(10L, "tenant-a", command);
    assertThat(events).containsExactly(
        "tx.begin", "dict.lock:10", "item.insert", "tx.commit",
        "cache.evict:ORDER_STATUS:tenant-a");
}

@Test void delete_non_empty_dict_is_rejected_without_cache_change() {
    repository.setUndeletedItemCount(10L, 1);
    assertThatThrownBy(() -> service.deleteDict(10L))
        .extracting("code").isEqualTo(DictErrorCode.DICT_NOT_EMPTY);
    assertThat(cacheEvents).isEmpty();
}
```

- [ ] **Step 3: Run tests and verify failure**

```bash
mvn -q -pl vincent-dict/vincent-dict-application -am test
```

Expected: compilation fails for missing admin types.

- [ ] **Step 4: Define exact permission and admin service signatures**

```java
public interface PermissionProvider {
    boolean hasPermission(DictAdminPermission permission, Optional<String> targetTenantId);
}

public interface TenantDirectory {
    PageResult<TenantOption> search(String keyword, int page, int size);
    boolean exists(String tenantId);
}

public interface DictAdminService {
    PageResult<DictSummary> pageDicts(DictPageQuery query);
    DictDetail getDict(long dictId, boolean includeDeleted);
    long createDict(CreateDictCommand command);
    void updateDict(long dictId, UpdateDictCommand command);
    void changeDictStatus(long dictId, boolean enabled);
    void deleteDict(long dictId);
    void restoreDict(long dictId);
    PageResult<DictItemDetail> pageItems(long dictId, ItemPageQuery query);
    long createDefaultItem(long dictId, CreateItemCommand command);
    long createTenantItem(long dictId, String tenantId, CreateItemCommand command);
    void updateItem(long itemId, UpdateItemCommand command);
    void changeItemStatus(long itemId, boolean enabled);
    void deleteItem(long itemId);
    void restoreItem(long itemId);
}
```

Create immutable command/query/result classes in `application/admin`; no Web annotations.

- [ ] **Step 5: Implement command orchestration with TxRunner**

Every write validates `OperatorProvider.currentOperatorId()`, derives `Instant.now(clock)`, and executes repository locks/checks and aggregate transitions inside `TxRunner.required`. Inject `java.time.Clock`; the Starter uses `Clock.systemUTC()` when the host supplies none. Call `DictCache.evictAll(dictCode)` for dict/default-item changes and `evictTenant(dictCode, tenantId)` for tenant-item changes only after `required` returns. `DictCache` implementations must absorb their own infrastructure faults; application writes must not be rolled back by cache failures.

- [ ] **Step 6: Run application tests**

```bash
mvn -q -pl vincent-dict/vincent-dict-application -am test
```

Expected: all admin orchestration tests pass with exact event order.

- [ ] **Step 7: Commit admin application services**

```bash
git add vincent-dict/vincent-dict-application
git commit -m "feat(dict): add admin application services"
```

---

### Task 2: Implement locked MyBatis writes, restore, and pagination

**Files:**
- Modify: `vincent-dict/vincent-dict-infra-mybatis/src/main/java/com/vincent/tools/dict/infra/mybatis/mapper/DictMapper.java`
- Modify: `vincent-dict/vincent-dict-infra-mybatis/src/main/java/com/vincent/tools/dict/infra/mybatis/mapper/DictItemMapper.java`
- Modify: `vincent-dict/vincent-dict-infra-mybatis/src/main/resources/com/vincent/tools/dict/infra/mybatis/mapper/DictMapper.xml`
- Modify: `vincent-dict/vincent-dict-infra-mybatis/src/main/resources/com/vincent/tools/dict/infra/mybatis/mapper/DictItemMapper.xml`
- Create: `vincent-dict/vincent-dict-infra-mybatis/src/main/java/com/vincent/tools/dict/infra/mybatis/MybatisDictAdminRepository.java`
- Create: `vincent-dict/vincent-dict-infra-mybatis/src/main/java/com/vincent/tools/dict/infra/mybatis/SpringTxRunner.java`
- Test: `vincent-dict/vincent-dict-infra-mybatis/src/test/java/com/vincent/tools/dict/infra/mybatis/MybatisDictAdminRepositoryIT.java`
- Test: `vincent-dict/vincent-dict-infra-mybatis/src/test/java/com/vincent/tools/dict/infra/mybatis/ConcurrentItemCreateIT.java`

**Interfaces:**
- Consumes: `DictAdminRepository`, `TxRunner`, aggregate reconstitution methods.
- Produces: transactional CRUD, `SELECT ... FOR UPDATE`, optimistic updates, restore and bounded paging.

- [ ] **Step 1: Write failing CRUD and restore integration tests**

Cover create/update/status/delete/restore for dict and item. Assert immutable columns never appear in update SQL. Assert a deleted item remains on the unique key and can be restored only when its dict is present.

```java
assertThat(repository.restoreItem(itemId, operator, now)).isEqualTo(1);
assertThat(repository.findItem(itemId).isDeleted()).isFalse();
```

- [ ] **Step 2: Write the failing concurrency test**

Use two threads and latches to concurrently create default `WAIT_CONFIRM` and tenant `WAIT_CONFIRM` under the same dict. Exactly one transaction succeeds; the other returns `DICT_ITEM_CODE_CONFLICT`. Verify no deadlock and only one row exists.

- [ ] **Step 3: Run integration tests and verify failure**

```bash
mvn -q -pl vincent-dict/vincent-dict-infra-mybatis -am -Dtest=MybatisDictAdminRepositoryIT,ConcurrentItemCreateIT test
```

Expected: FAIL for missing admin repository methods.

- [ ] **Step 4: Implement explicit lock/check/update SQL**

Required statements include:

```sql
SELECT * FROM vin_dict WHERE id = #{id} FOR UPDATE;
SELECT COUNT(*) FROM vin_dict_item WHERE dict_id = #{dictId} AND deleted = 0;
SELECT tenant_id, deleted FROM vin_dict_item
 WHERE dict_id = #{dictId} AND code = #{code};
```

Update/delete/restore statements include `version = #{expectedVersion}` and increment `version`; zero affected rows maps to `OPTIMISTIC_LOCK_CONFLICT`. Page queries clamp neither page nor size—the application validates them and rejects invalid input.

- [ ] **Step 5: Implement SpringTxRunner against the selected transaction manager**

Wrap a `TransactionTemplate`:

```java
public <T> T required(Supplier<T> action) {
    return transactionTemplate.execute(status -> action.get());
}
```

Do not annotate application services with Spring annotations.

- [ ] **Step 6: Run MySQL integration and concurrency tests**

```bash
mvn -q -pl vincent-dict/vincent-dict-infra-mybatis -am verify
```

Expected: CRUD, restore, uniqueness, pagination, optimistic lock and concurrent collision tests pass.

- [ ] **Step 7: Commit the write adapter**

```bash
git add vincent-dict/vincent-dict-infra-mybatis
git commit -m "feat(dict): add transactional admin persistence"
```

---

### Task 3: Expose the versioned admin REST API with conditional security

**Files:**
- Create: `vincent-dict/vincent-dict-web/src/main/java/com/vincent/tools/dict/web/DictAdminController.java`
- Create: `vincent-dict/vincent-dict-web/src/main/java/com/vincent/tools/dict/web/DictItemAdminController.java`
- Create: `vincent-dict/vincent-dict-web/src/main/java/com/vincent/tools/dict/web/TenantAdminController.java`
- Create: `vincent-dict/vincent-dict-web/src/main/java/com/vincent/tools/dict/web/DictAdminPageController.java`
- Create: `vincent-dict/vincent-dict-web/src/main/java/com/vincent/tools/dict/web/DictAdminResourceHandler.java`
- Create: `vincent-dict/vincent-dict-web/src/main/java/com/vincent/tools/dict/web/ApiResponse.java`
- Create: `vincent-dict/vincent-dict-web/src/main/java/com/vincent/tools/dict/web/DictWebExceptionHandler.java`
- Create: `vincent-dict/vincent-dict-web/src/main/java/com/vincent/tools/dict/web/dto/DictRequests.java`
- Create: `vincent-dict/vincent-dict-web/src/main/java/com/vincent/tools/dict/web/dto/ItemRequests.java`
- Create: `vincent-dict/vincent-dict-web/src/main/java/com/vincent/tools/dict/web/DictAdminWebAutoConfiguration.java`
- Create: `vincent-dict/vincent-dict-web/src/main/resources/META-INF/spring.factories`
- Test: `vincent-dict/vincent-dict-web/src/test/java/com/vincent/tools/dict/web/DictAdminControllerTest.java`
- Test: `vincent-dict/vincent-dict-web/src/test/java/com/vincent/tools/dict/web/DictAdminWebAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `DictAdminService`, `TenantDirectory`, `PermissionProvider`, and admin properties.
- Produces: REST contract under `/vincent/dict/admin/api/v1` and protected SPA entry route.

- [ ] **Step 1: Write failing MockMvc authorization and validation tests**

```java
mockMvc.perform(post("/vincent/dict/admin/api/v1/dicts/10/items/tenant")
        .contentType(APPLICATION_JSON)
        .content("{\"tenantId\":\"tenant-b\",\"code\":\"WAIT_CONFIRM\",\"name\":\"Waiting\",\"sortNo\":20}"))
    .andExpect(status().isForbidden());

verify(permissionProvider).hasPermission(
    DictAdminPermission.ITEM_CREATE, Optional.of("tenant-b"));
```

Add invalid lowercase code, size > 100, missing operator, missing tenant directory, deleted-edit, and stable error-code assertions.

- [ ] **Step 2: Run Web tests and verify failure**

```bash
mvn -q -pl vincent-dict/vincent-dict-web -am test
```

Expected: compilation fails because controllers and auto-configuration are absent.

- [ ] **Step 3: Implement exact REST resources**

Use these routes:

```text
GET    /dicts
POST   /dicts
GET    /capabilities?tenantId={optionalTenantId}
GET    /dicts/{dictId}
PUT    /dicts/{dictId}
PATCH  /dicts/{dictId}/status
DELETE /dicts/{dictId}
POST   /dicts/{dictId}/restore
GET    /dicts/{dictId}/items
POST   /dicts/{dictId}/items/default
POST   /dicts/{dictId}/items/tenant
PUT    /items/{itemId}
PATCH  /items/{itemId}/status
DELETE /items/{itemId}
POST   /items/{itemId}/restore
GET    /tenants
```

Every endpoint returns `ApiResponse<T>` with `success`, `code`, `message`, and `data`; map stable component errors to HTTP 400/403/404/409/500 without host response types. `/capabilities` evaluates stable permissions for either default scope or the optional target tenant and returns `tenantDirectoryAvailable`, allowing the UI to hide unauthorized actions and the tenant tab without treating authorization as a front-end security boundary. Tenant ID belongs in the JSON request body rather than a URL path because valid host tenant identifiers are opaque strings.

- [ ] **Step 4: Implement conditional Web auto-configuration**

Use `@ConditionalOnWebApplication(type = SERVLET)`, `@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")`, and `@ConditionalOnProperty(prefix="vincent.dict.admin", name="enabled", havingValue="true")`. Fail context creation if operator/permission providers are absent. Do not require `TenantDirectory` for startup; expose tenant endpoint as `CONFIGURATION_INVALID` and hide tenant UI when absent.

- [ ] **Step 5: Protect the SPA entry route**

`GET /dict-admin` and `/dict-admin/` must require `DICT_VIEW`; `DictAdminResourceHandler` maps the configured base path to `classpath:/META-INF/resources/dict-admin/` and serves the SPA fallback with relative asset URLs. Hashed CSS/JS assets may be publicly cacheable because they contain no data or secrets. All data endpoints remain independently authorized.

- [ ] **Step 6: Run Web and non-Web context tests**

```bash
mvn -q -pl vincent-dict/vincent-dict-web -am test
```

Expected: authorized REST tests pass; Web auto-config is absent in `WebApplicationType.NONE`.

- [ ] **Step 7: Commit the admin API**

```bash
git add vincent-dict/vincent-dict-web
git commit -m "feat(dict): add conditional admin api"
```

---

### Task 4: Build the Vue module and typed API client

**Files:**
- Modify: `vincent-dict/vincent-dict-admin-ui/pom.xml`
- Create: `vincent-dict/vincent-dict-admin-ui/package.json`
- Create: `vincent-dict/vincent-dict-admin-ui/package-lock.json`
- Create: `vincent-dict/vincent-dict-admin-ui/tsconfig.json`
- Create: `vincent-dict/vincent-dict-admin-ui/vite.config.ts`
- Create: `vincent-dict/vincent-dict-admin-ui/src/main.ts`
- Create: `vincent-dict/vincent-dict-admin-ui/src/App.vue`
- Create: `vincent-dict/vincent-dict-admin-ui/src/router.ts`
- Create: `vincent-dict/vincent-dict-admin-ui/src/api/http.ts`
- Create: `vincent-dict/vincent-dict-admin-ui/src/api/dict.ts`
- Create: `vincent-dict/vincent-dict-admin-ui/src/api/types.ts`
- Test: `vincent-dict/vincent-dict-admin-ui/src/api/dict.spec.ts`

**Interfaces:**
- Consumes: Task 3 REST routes and `ApiResponse<T>`.
- Produces: reproducible Node build and typed client functions for every management endpoint.

- [ ] **Step 1: Create package scripts and locked dependencies**

```json
{
  "scripts": {
    "dev": "vite",
    "test": "vitest run",
    "typecheck": "vue-tsc --noEmit",
    "build": "npm run typecheck && vite build"
  },
  "dependencies": {
    "axios": "1.7.9",
    "element-plus": "2.9.3",
    "vue": "3.5.13",
    "vue-router": "4.5.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "5.2.1",
    "@vue/test-utils": "2.4.6",
    "typescript": "5.7.3",
    "vite": "6.0.11",
    "vitest": "2.1.8",
    "vue-tsc": "2.2.0"
  }
}
```

Generate and commit `package-lock.json` with `npm install --package-lock-only`; subsequent builds use `npm ci`.

- [ ] **Step 2: Write failing typed-client tests**

Mock Axios and assert `createTenantItem(10, "tenant-b", payload)` posts `{tenantId:"tenant-b", ...payload}` to `/dicts/10/items/tenant`, while restore calls the exact `/restore` route. Assert API errors retain component `code` for UI messages.

- [ ] **Step 3: Run UI tests and verify failure**

```bash
cd vincent-dict/vincent-dict-admin-ui
npm ci
npm run test
```

Expected: tests fail because client functions are absent.

- [ ] **Step 4: Implement the typed client and router shell**

Set Axios base URL from injected runtime value `window.__VIN_DICT_CONFIG__.apiPath`, falling back to `/vincent/dict/admin/api/v1`. Use routes `/`, `/dicts/:dictId`, and a catch-all redirect. Do not add a global state library.

- [ ] **Step 5: Integrate UI build into Maven with Java-8-compatible plugin**

Use `frontend-maven-plugin 1.12.1`, Node `v22.12.0`, `npm ci`, `npm test`, and `npm run build`. Configure Vite output into `target/classes/META-INF/resources/dict-admin` with relative asset URLs. Provide `-DskipFrontend` only for backend-focused local iterations; release verification must not skip it.

- [ ] **Step 6: Run UI and Maven module checks**

```bash
cd vincent-dict/vincent-dict-admin-ui
npm run test
npm run build
cd ../..
mvn -q -pl vincent-dict/vincent-dict-admin-ui package
```

Expected: tests/typecheck/build pass; jar contains `META-INF/resources/dict-admin/index.html` and hashed assets.

- [ ] **Step 7: Commit the UI foundation**

```bash
git add vincent-dict/vincent-dict-admin-ui
git commit -m "feat(dict): add admin ui foundation"
```

---

### Task 5: Implement dictionary, default-item, tenant-item, and restore screens

**Files:**
- Create: `vincent-dict/vincent-dict-admin-ui/src/views/DictListView.vue`
- Create: `vincent-dict/vincent-dict-admin-ui/src/views/DictDetailView.vue`
- Create: `vincent-dict/vincent-dict-admin-ui/src/components/DictForm.vue`
- Create: `vincent-dict/vincent-dict-admin-ui/src/components/ItemTable.vue`
- Create: `vincent-dict/vincent-dict-admin-ui/src/components/ItemForm.vue`
- Create: `vincent-dict/vincent-dict-admin-ui/src/components/TenantPicker.vue`
- Create: `vincent-dict/vincent-dict-admin-ui/src/components/ErrorAlert.vue`
- Create: `vincent-dict/vincent-dict-admin-ui/src/views/DictListView.spec.ts`
- Create: `vincent-dict/vincent-dict-admin-ui/src/views/DictDetailView.spec.ts`
- Create: `vincent-dict/vincent-dict-admin-ui/src/components/TenantPicker.spec.ts`

**Interfaces:**
- Consumes: typed client from Task 4 and server permission/tenant capability data.
- Produces: complete first-version management workflows.

- [ ] **Step 1: Write failing list and restore component tests**

Mount list/detail views with mocked API. Assert status/deleted filters are sent, lowercase codes show local validation, deleted rows show only “View” and “Restore”, and `DICT_NOT_EMPTY` displays the server message without removing the row.

- [ ] **Step 2: Write failing tenant-picker tests**

Assert search is debounced by 300 ms, page size never exceeds 100, a missing tenant capability hides the tenant tab, and a selected tenant ID is revalidated by the server during create.

- [ ] **Step 3: Run tests and verify failure**

```bash
cd vincent-dict/vincent-dict-admin-ui
npm run test
```

Expected: component tests fail because views/components are missing.

- [ ] **Step 4: Implement list and dict detail workflows**

Use Element Plus table/form/dialog/tabs. Dict list includes code/name/status/deleted filters and create/edit/status/delete/restore actions. Detail shows basic info, default items, and tenant items. All lists are server-paginated with default 20/max 100.

- [ ] **Step 5: Implement item forms and tenant selection**

Create code is editable only for new rows; update forms omit code, dict and tenant. Deleted items disable all controls except restore. Show source (`DEFAULT`/`TENANT`) and tenant display name without exposing internal dict/item IDs outside management routes.

- [ ] **Step 6: Run UI tests, typecheck, and production build**

```bash
cd vincent-dict/vincent-dict-admin-ui
npm run test
npm run typecheck
npm run build
```

Expected: all pass with no console warnings from Vue tests.

- [ ] **Step 7: Commit management screens**

```bash
git add vincent-dict/vincent-dict-admin-ui
git commit -m "feat(dict): implement admin management screens"
```

---

### Task 6: Package the UI in the Starter and verify Web/non-Web consumers

**Files:**
- Modify: `vincent-dict/vincent-dict-web/pom.xml`
- Modify: `vincent-dict/vincent-dict-boot2-starter/pom.xml`
- Modify: `vincent-dict/vincent-dict-boot2-starter/src/main/resources/META-INF/spring.factories`
- Create: `vincent-dict/vincent-dict-example-boot2/src/main/java/com/vincent/tools/dict/example/ExampleAdminAdapters.java`
- Create: `vincent-dict/vincent-dict-example-boot2/src/test/java/com/vincent/tools/dict/example/DictAdminPageIT.java`
- Create: `vincent-dict/vincent-dict-example-boot2/src/test/java/com/vincent/tools/dict/example/NonWebClasspathIT.java`
- Modify: `vincent-dict/README.md`

**Interfaces:**
- Consumes: UI resource jar, Web auto-config, core Starter and example host.
- Produces: one Starter that serves admin only when eligible and remains safe in non-Web hosts.

- [ ] **Step 1: Write failing packaged-resource and route tests**

Assert the Starter jar contains `/META-INF/resources/dict-admin/index.html`; an authorized Web request to `/dict-admin` returns the SPA; an unauthorized request returns 403; a non-Web application context has no controller, dispatcher servlet, or embedded server factory.

- [ ] **Step 2: Run packaging tests and verify failure**

```bash
mvn -q -pl vincent-dict/vincent-dict-example-boot2 -am -Dtest=DictAdminPageIT,NonWebClasspathIT test
```

Expected: FAIL until module dependencies and adapters are wired.

- [ ] **Step 3: Wire UI/Web into the same core Starter**

Add `vincent-dict-web` and `vincent-dict-admin-ui` dependencies. Keep Spring MVC dependencies optional. Use `maven-dependency-plugin` during `process-resources` to unpack only `META-INF/resources/dict-admin/**` from the UI jar into the Starter output, so the published Starter jar contains the page assets. Add admin auto-config to `spring.factories`; conditions must be evaluated before any MVC-only type is instantiated.

- [ ] **Step 4: Implement example adapters**

Provide in-memory example implementations:

```java
OperatorProvider operatorProvider() { return () -> "example-admin"; }
PermissionProvider permissionProvider() { return (permission, tenant) -> true; }
TenantDirectory tenantDirectory() { return new ExampleTenantDirectory(); }
```

Use these only in the example module.

- [ ] **Step 5: Document Web activation and security ownership**

Document admin enablement, SPA/API paths, mandatory operator/permission adapters, target tenant authorization, optional tenant directory behavior, host authentication/CSRF responsibility, and non-Web manual maintenance warning.

- [ ] **Step 6: Run complete admin acceptance verification**

```bash
mvn -q clean verify
unzip -l vincent-dict/vincent-dict-boot2-starter/target/vincent-dict-boot2-starter-1.0.0-SNAPSHOT.jar META-INF/resources/dict-admin/index.html
```

Expected: backend/UI/integration tests pass; index exists; non-Web context remains non-Web.

- [ ] **Step 7: Commit admin packaging and docs**

```bash
git add vincent-dict
git commit -m "feat(dict): embed conditional admin console"
```

## Admin Plan Exit Criteria

- All writes enforce scoped permissions, operator metadata, tenant validation and DDD invariants.
- Concurrent default/tenant code creation cannot violate collision rules.
- Deletion is non-cascading; deleted records are viewable and restorable.
- Web API and Vue UI implement every approved management action with bounded pagination.
- The same core Starter serves the UI in eligible Web hosts and remains non-Web otherwise.
- No audit table, audit history, or host-specific response wrapper is introduced.
