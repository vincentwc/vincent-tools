# Vincent Dict 管理端实施计划

> **面向智能代理执行者：** 必须使用子技能：通过 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 按任务逐项实施本计划。步骤使用复选框（`- [ ]`）语法进行跟踪。

**目标：** 在现有核心 Starter 中增加事务性字典维护、范围授权、租户查询、REST 管理 API，以及内嵌的 Vue 管理页面。

**架构：** 应用命令服务通过仓储、事务、租户目录、操作人、权限和缓存端口，强制执行用例顺序。MyBatis 提供加锁写入；Spring MVC 适配器仅在满足条件的 Web 宿主中暴露带版本的管理 API。独立构建的 Vue jar 提供静态资源，并由同一核心 Starter 使用。

**技术栈：** 核心计划产物、Java 8、由宿主提供的 Spring MVC、Spring Boot 2.2.6.RELEASE、Vue 3.5.x、TypeScript 5.7.x、Vite 6.x、Element Plus 2.9.x、Vitest 2.x、Node.js 22.12.x、frontend-maven-plugin 1.12.1。

## 全局约束

- 先完成 `2026-08-14-vincent-dict-core.md`；不得在 Web/UI 代码中重复实现领域或查询规则。
- 管理 UI 保留在 `vincent-dict-boot2-starter` 内；不得创建管理端 Starter。
- Spring MVC 为可选/由宿主提供的依赖，绝不能导致非 Web 宿主启动 servlet 容器。
- 管理端自动配置需要 Servlet Web、`DispatcherServlet`、`vincent.dict.admin.enabled=true`、`OperatorProvider` 和 `PermissionProvider`。
- 缺少 `TenantDirectory` 时，禁用并拒绝租户条目管理，但不得禁用默认条目管理。
- 每次租户条目读写的授权检查都必须包含目标租户 ID。
- 条目/字典编码和归属不可变；已删除记录仅可查看或恢复。
- 包含未删除条目的字典不可删除；删除绝不级联。
- 第一版仅存储创建/更新元数据；不增加审计历史或前后 JSON。
- `OperatorProvider` 必须返回非空白、无需去除首尾空白的标识符，长度不超过 64 个字符；应用时间戳来自注入的 UTC Java `Clock`。
- 每次写入均须在事务中执行；成功提交后通过 `DictCache` 进行缓存失效。
- UI API 调用使用 `/vincent/dict/admin/api/v1`；UI 基路径默认为 `/dict-admin`。

---

### Task 1：定义管理端应用契约和事务性命令服务

**文件：**
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

**接口：**
- 消费：领域聚合/策略、`DictCache`、已配置的限制。
- 产出：所有管理端命令/查询，以及供 MyBatis 和 Web 任务使用的端口。

- [ ] **步骤 1：编写失败的范围权限和租户目录测试**

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

- [ ] **步骤 2：编写失败的事务、锁、删除和缓存测试**

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

- [ ] **步骤 3：运行测试并验证失败**

```bash
mvn -q -pl vincent-dict/vincent-dict-application -am test
```

预期结果：因缺少管理端类型而编译失败。

- [ ] **步骤 4：定义精确的权限和管理服务签名**

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

在 `application/admin` 中创建不可变的命令/查询/结果类；不得使用 Web 注解。

- [ ] **步骤 5：使用 TxRunner 实现命令编排**

每次写入都要校验 `OperatorProvider.currentOperatorId()`，派生 `Instant.now(clock)`，并在 `TxRunner.required` 内执行仓储锁定/检查及聚合状态转换。注入 `java.time.Clock`；当宿主未提供时，Starter 使用 `Clock.systemUTC()`。仅在 `required` 返回后，字典/默认条目变更调用 `DictCache.evictAll(dictCode)`，租户条目变更调用 `evictTenant(dictCode, tenantId)`。`DictCache` 的实现必须自行吸收基础设施故障；缓存失败不得使应用写入回滚。

- [ ] **步骤 6：运行应用层测试**

```bash
mvn -q -pl vincent-dict/vincent-dict-application -am test
```

预期结果：所有管理端编排测试以精确的事件顺序通过。

- [ ] **步骤 7：提交管理端应用服务**

```bash
git add vincent-dict/vincent-dict-application
git commit -m "feat(dict): add admin application services"
```

---

### Task 2：实现带锁的 MyBatis 写入、恢复和分页

**文件：**
- Modify: `vincent-dict/vincent-dict-infra-mybatis/src/main/java/com/vincent/tools/dict/infra/mybatis/mapper/DictMapper.java`
- Modify: `vincent-dict/vincent-dict-infra-mybatis/src/main/java/com/vincent/tools/dict/infra/mybatis/mapper/DictItemMapper.java`
- Modify: `vincent-dict/vincent-dict-infra-mybatis/src/main/resources/com/vincent/tools/dict/infra/mybatis/mapper/DictMapper.xml`
- Modify: `vincent-dict/vincent-dict-infra-mybatis/src/main/resources/com/vincent/tools/dict/infra/mybatis/mapper/DictItemMapper.xml`
- Create: `vincent-dict/vincent-dict-infra-mybatis/src/main/java/com/vincent/tools/dict/infra/mybatis/MybatisDictAdminRepository.java`
- Create: `vincent-dict/vincent-dict-infra-mybatis/src/main/java/com/vincent/tools/dict/infra/mybatis/SpringTxRunner.java`
- Test: `vincent-dict/vincent-dict-infra-mybatis/src/test/java/com/vincent/tools/dict/infra/mybatis/MybatisDictAdminRepositoryIT.java`
- Test: `vincent-dict/vincent-dict-infra-mybatis/src/test/java/com/vincent/tools/dict/infra/mybatis/ConcurrentItemCreateIT.java`

**接口：**
- 消费：`DictAdminRepository`、`TxRunner`、聚合重建方法。
- 产出：事务性 CRUD、`SELECT ... FOR UPDATE`、乐观锁更新、恢复和有界分页。

- [ ] **步骤 1：编写失败的 CRUD 和恢复集成测试**

覆盖字典和条目的创建/更新/状态变更/删除/恢复。断言不可变列绝不出现在更新 SQL 中。断言已删除条目仍保留在唯一键上，且仅当其字典存在时才能恢复。

```java
assertThat(repository.restoreItem(itemId, operator, now)).isEqualTo(1);
assertThat(repository.findItem(itemId).isDeleted()).isFalse();
```

- [ ] **步骤 2：编写失败的并发测试**

使用两个线程和闩锁，在同一字典下并发创建默认 `WAIT_CONFIRM` 和租户 `WAIT_CONFIRM`。恰有一个事务成功；另一个返回 `DICT_ITEM_CODE_CONFLICT`。验证没有死锁，且仅存在一行记录。

- [ ] **步骤 3：运行集成测试并验证失败**

```bash
mvn -q -pl vincent-dict/vincent-dict-infra-mybatis -am -Dtest=MybatisDictAdminRepositoryIT,ConcurrentItemCreateIT test
```

预期结果：因缺少管理端仓储方法而失败。

- [ ] **步骤 4：实现显式的加锁/检查/更新 SQL**

必需的语句包括：

```sql
SELECT * FROM vin_dict WHERE id = #{id} FOR UPDATE;
SELECT COUNT(*) FROM vin_dict_item WHERE dict_id = #{dictId} AND deleted = 0;
SELECT tenant_id, deleted FROM vin_dict_item
 WHERE dict_id = #{dictId} AND code = #{code};
```

更新/删除/恢复语句包含 `version = #{expectedVersion}` 并递增 `version`；影响行数为零时映射为 `OPTIMISTIC_LOCK_CONFLICT`。分页查询不应对页码或页大小进行截断——由应用层校验并拒绝无效输入。

- [ ] **步骤 5：针对选定的事务管理器实现 SpringTxRunner**

封装一个 `TransactionTemplate`：

```java
public <T> T required(Supplier<T> action) {
    return transactionTemplate.execute(status -> action.get());
}
```

不得为应用服务添加 Spring 注解。

- [ ] **步骤 6：运行 MySQL 集成和并发测试**

```bash
mvn -q -pl vincent-dict/vincent-dict-infra-mybatis -am verify
```

预期结果：CRUD、恢复、唯一性、分页、乐观锁和并发冲突测试通过。

- [ ] **步骤 7：提交写入适配器**

```bash
git add vincent-dict/vincent-dict-infra-mybatis
git commit -m "feat(dict): add transactional admin persistence"
```

---

### Task 3：通过条件安全控制暴露带版本的管理端 REST API

**文件：**
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

**接口：**
- 消费：`DictAdminService`、`TenantDirectory`、`PermissionProvider` 和管理端属性。
- 产出：`/vincent/dict/admin/api/v1` 下的 REST 契约和受保护的 SPA 入口路由。

- [ ] **步骤 1：编写失败的 MockMvc 授权和校验测试**

```java
mockMvc.perform(post("/vincent/dict/admin/api/v1/dicts/10/items/tenant")
        .contentType(APPLICATION_JSON)
        .content("{\"tenantId\":\"tenant-b\",\"code\":\"WAIT_CONFIRM\",\"name\":\"Waiting\",\"sortNo\":20}"))
    .andExpect(status().isForbidden());

verify(permissionProvider).hasPermission(
    DictAdminPermission.ITEM_CREATE, Optional.of("tenant-b"));
```

增加对无效小写编码、大小大于 100、缺少操作人、缺少租户目录、编辑已删除记录和稳定错误码的断言。

- [ ] **步骤 2：运行 Web 测试并验证失败**

```bash
mvn -q -pl vincent-dict/vincent-dict-web -am test
```

预期结果：因控制器和自动配置不存在而编译失败。

- [ ] **步骤 3：实现精确的 REST 资源**

使用以下路由：

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

每个端点返回包含 `success`、`code`、`message` 和 `data` 的 `ApiResponse<T>`；将稳定的组件错误映射为 HTTP 400/403/404/409/500，不使用宿主响应类型。`/capabilities` 针对默认范围或可选目标租户评估稳定权限，并返回 `tenantDirectoryAvailable`，使 UI 能隐藏未获授权的操作和租户标签页，同时不将授权视为前端安全边界。租户 ID 应位于 JSON 请求体中而非 URL 路径，因为有效的宿主租户标识符是不透明字符串。

- [ ] **步骤 4：实现条件化 Web 自动配置**

使用 `@ConditionalOnWebApplication(type = SERVLET)`、`@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")` 和 `@ConditionalOnProperty(prefix="vincent.dict.admin", name="enabled", havingValue="true")`。若缺少操作人/权限提供者，则使上下文创建失败。启动时不要求 `TenantDirectory`；缺少时将租户端点暴露为 `CONFIGURATION_INVALID`，并隐藏租户 UI。

- [ ] **步骤 5：保护 SPA 入口路由**

`GET /dict-admin` 和 `/dict-admin/` 必须要求 `DICT_VIEW`；`DictAdminResourceHandler` 将配置的基路径映射到 `classpath:/META-INF/resources/dict-admin/`，并以相对资源 URL 提供 SPA 回退页面。带哈希的 CSS/JS 资源不含数据或机密，因此可以公开缓存。所有数据端点仍须独立授权。

- [ ] **步骤 6：运行 Web 和非 Web 上下文测试**

```bash
mvn -q -pl vincent-dict/vincent-dict-web -am test
```

预期结果：已授权的 REST 测试通过；在 `WebApplicationType.NONE` 中不存在 Web 自动配置。

- [ ] **步骤 7：提交管理端 API**

```bash
git add vincent-dict/vincent-dict-web
git commit -m "feat(dict): add conditional admin api"
```

---

### Task 4：构建 Vue 模块和类型化 API 客户端

**文件：**
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

**接口：**
- 消费：任务 3 的 REST 路由和 `ApiResponse<T>`。
- 产出：可复现的 Node 构建，以及覆盖每个管理端点的类型化客户端函数。

- [ ] **步骤 1：创建包脚本和锁定的依赖**

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

通过 `npm install --package-lock-only` 生成并提交 `package-lock.json`；后续构建使用 `npm ci`。

- [ ] **步骤 2：编写失败的类型化客户端测试**

模拟 Axios 并断言 `createTenantItem(10, "tenant-b", payload)` 向 `/dicts/10/items/tenant` 提交 `{tenantId:"tenant-b", ...payload}`，而恢复操作调用精确的 `/restore` 路由。断言 API 错误保留组件 `code` 以供 UI 展示消息。

- [ ] **步骤 3：运行 UI 测试并验证失败**

```bash
cd vincent-dict/vincent-dict-admin-ui
npm ci
npm run test
```

预期结果：因客户端函数不存在而测试失败。

- [ ] **步骤 4：实现类型化客户端和路由外壳**

从注入的运行时值 `window.__VIN_DICT_CONFIG__.apiPath` 设置 Axios 基础 URL，回退值为 `/vincent/dict/admin/api/v1`。使用 `/`、`/dicts/:dictId` 和兜底重定向路由。不得增加全局状态库。

- [ ] **步骤 5：使用兼容 Java 8 的插件将 UI 构建集成到 Maven**

使用 `frontend-maven-plugin 1.12.1`、Node `v22.12.0`、`npm ci`、`npm test` 和 `npm run build`。将 Vite 输出配置到带相对资源 URL 的 `target/classes/META-INF/resources/dict-admin`。仅为专注后端的本地迭代提供 `-DskipFrontend`；发布验证不得跳过它。

- [ ] **步骤 6：运行 UI 和 Maven 模块检查**

```bash
cd vincent-dict/vincent-dict-admin-ui
npm run test
npm run build
cd ../..
mvn -q -pl vincent-dict/vincent-dict-admin-ui package
```

预期结果：测试/类型检查/构建通过；jar 包含 `META-INF/resources/dict-admin/index.html` 和带哈希的资源。

- [ ] **步骤 7：提交 UI 基础设施**

```bash
git add vincent-dict/vincent-dict-admin-ui
git commit -m "feat(dict): add admin ui foundation"
```

---

### Task 5：实现字典、默认条目、租户条目和恢复页面

**文件：**
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

**接口：**
- 消费：任务 4 的类型化客户端以及服务端权限/租户能力数据。
- 产出：完整的第一版管理工作流。

- [ ] **步骤 1：编写失败的列表和恢复组件测试**

使用模拟 API 挂载列表/详情视图。断言会发送状态/已删除筛选条件，小写编码显示本地校验，已删除行仅显示“查看”和“恢复”，并且 `DICT_NOT_EMPTY` 会显示服务端消息而不移除该行。

- [ ] **步骤 2：编写失败的租户选择器测试**

断言搜索采用 300 ms 防抖，页大小绝不超过 100，缺少租户能力时隐藏租户标签页，并且在创建时由服务端重新校验选定的租户 ID。

- [ ] **步骤 3：运行测试并验证失败**

```bash
cd vincent-dict/vincent-dict-admin-ui
npm run test
```

预期结果：因视图/组件缺失而组件测试失败。

- [ ] **步骤 4：实现列表和字典详情工作流**

使用 Element Plus 表格/表单/对话框/标签页。字典列表包含编码/名称/状态/已删除筛选，以及创建/编辑/状态变更/删除/恢复操作。详情展示基本信息、默认条目和租户条目。所有列表均使用服务端分页，默认 20、最大 100。

- [ ] **步骤 5：实现条目表单和租户选择**

创建时编码仅对新行可编辑；更新表单不包含编码、字典和租户。已删除条目除恢复外禁用所有控件。展示来源（`DEFAULT`/`TENANT`）和租户显示名称，且不得在管理路由之外暴露内部字典/条目 ID。

- [ ] **步骤 6：运行 UI 测试、类型检查和生产构建**

```bash
cd vincent-dict/vincent-dict-admin-ui
npm run test
npm run typecheck
npm run build
```

预期结果：全部通过，Vue 测试不产生控制台警告。

- [ ] **步骤 7：提交管理页面**

```bash
git add vincent-dict/vincent-dict-admin-ui
git commit -m "feat(dict): implement admin management screens"
```

---

### Task 6：将 UI 打包进 Starter，并验证 Web/非 Web 使用方

**文件：**
- Modify: `vincent-dict/vincent-dict-web/pom.xml`
- Modify: `vincent-dict/vincent-dict-boot2-starter/pom.xml`
- Modify: `vincent-dict/vincent-dict-boot2-starter/src/main/resources/META-INF/spring.factories`
- Create: `vincent-dict/vincent-dict-example-boot2/src/main/java/com/vincent/tools/dict/example/ExampleAdminAdapters.java`
- Create: `vincent-dict/vincent-dict-example-boot2/src/test/java/com/vincent/tools/dict/example/DictAdminPageIT.java`
- Create: `vincent-dict/vincent-dict-example-boot2/src/test/java/com/vincent/tools/dict/example/NonWebClasspathIT.java`
- Modify: `vincent-dict/README.md`

**接口：**
- 消费：UI 资源 jar、Web 自动配置、核心 Starter 和示例宿主。
- 产出：一个仅在满足条件时提供管理端服务、且在非 Web 宿主中仍保持安全的 Starter。

- [ ] **步骤 1：编写失败的打包资源和路由测试**

断言 Starter jar 包含 `/META-INF/resources/dict-admin/index.html`；已授权的 Web 请求访问 `/dict-admin` 时返回 SPA；未授权请求返回 403；非 Web 应用上下文不包含控制器、dispatcher servlet 或嵌入式服务器工厂。

- [ ] **步骤 2：运行打包测试并验证失败**

```bash
mvn -q -pl vincent-dict/vincent-dict-example-boot2 -am -Dtest=DictAdminPageIT,NonWebClasspathIT test
```

预期结果：在模块依赖和适配器接入前失败。

- [ ] **步骤 3：将 UI/Web 接入同一核心 Starter**

添加 `vincent-dict-web` 和 `vincent-dict-admin-ui` 依赖。保持 Spring MVC 依赖为可选。使用 `maven-dependency-plugin` 在 `process-resources` 阶段仅从 UI jar 解压 `META-INF/resources/dict-admin/**` 到 Starter 输出，使发布的 Starter jar 包含页面资源。将管理端自动配置添加到 `spring.factories`；必须在实例化任何仅 MVC 类型前评估条件。

- [ ] **步骤 4：实现示例适配器**

提供内存中的示例实现：

```java
OperatorProvider operatorProvider() { return () -> "example-admin"; }
PermissionProvider permissionProvider() { return (permission, tenant) -> true; }
TenantDirectory tenantDirectory() { return new ExampleTenantDirectory(); }
```

仅在示例模块中使用这些实现。

- [ ] **步骤 5：编写 Web 启用方式和安全责任归属文档**

记录管理端启用方式、SPA/API 路径、必需的操作人/权限适配器、目标租户授权、可选租户目录行为、宿主认证/CSRF 责任，以及非 Web 手动维护警告。

- [ ] **步骤 6：运行完整的管理端验收验证**

```bash
mvn -q clean verify
unzip -l vincent-dict/vincent-dict-boot2-starter/target/vincent-dict-boot2-starter-1.0.0-SNAPSHOT.jar META-INF/resources/dict-admin/index.html
```

预期结果：后端/UI/集成测试通过；索引文件存在；非 Web 上下文保持非 Web。

- [ ] **步骤 7：提交管理端打包和文档**

```bash
git add vincent-dict
git commit -m "feat(dict): embed conditional admin console"
```

## 管理端计划退出标准

- 所有写入均强制执行范围权限、操作人元数据、租户校验和 DDD 不变量。
- 默认/租户编码的并发创建不得违反冲突规则。
- 删除不级联；已删除记录可查看且可恢复。
- Web API 和 Vue UI 通过有界分页实现每项已批准的管理操作。
- 同一核心 Starter 在满足条件的 Web 宿主中提供 UI，在其他情况下保持非 Web。
- 不引入审计表、审计历史或宿主专属响应包装器。
