package com.vincent.tools.dict.application.admin;

import com.vincent.tools.dict.application.DictItemView;
import com.vincent.tools.dict.application.DictLimits;
import com.vincent.tools.dict.application.admin.command.CreateDictCommand;
import com.vincent.tools.dict.application.admin.command.CreateItemCommand;
import com.vincent.tools.dict.application.admin.command.UpdateDictCommand;
import com.vincent.tools.dict.application.admin.command.UpdateItemCommand;
import com.vincent.tools.dict.application.admin.query.DictPageQuery;
import com.vincent.tools.dict.application.admin.query.ItemPageQuery;
import com.vincent.tools.dict.application.admin.view.DictDetail;
import com.vincent.tools.dict.application.admin.view.DictItemDetail;
import com.vincent.tools.dict.application.admin.view.DictSummary;
import com.vincent.tools.dict.application.port.DictAdminRepository;
import com.vincent.tools.dict.application.port.DictCache;
import com.vincent.tools.dict.application.port.TxRunner;
import com.vincent.tools.dict.domain.Dict;
import com.vincent.tools.dict.domain.DictCode;
import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;
import com.vincent.tools.dict.domain.DictFactory;
import com.vincent.tools.dict.domain.DictItem;
import com.vincent.tools.dict.domain.DictItemFactory;
import com.vincent.tools.dict.domain.DictStatus;
import com.vincent.tools.dict.domain.ItemCode;
import com.vincent.tools.dict.domain.ItemCodeUsage;
import com.vincent.tools.dict.domain.ItemStatus;
import com.vincent.tools.dict.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultDictAdminServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String OPERATOR = "operator";

    private final CreateItemCommand command = new CreateItemCommand("WAIT_CONFIRM", "Waiting", "", 20);

    private List<PermissionCheck> permissionChecks;
    private List<String> tenantLookups;
    private List<String> events;
    private List<String> cacheEvents;
    private InMemoryDictAdminRepository repository;
    private Fixture fixture;
    private DictAdminService service;

    @BeforeEach
    void setUp() {
        fixture = new Fixture();
        service = fixture.build();
    }

    @Test
    void tenant_item_create_checks_target_scope_and_directory() {
        service.createTenantItem(10L, "tenant-b",
                new CreateItemCommand("WAIT_CONFIRM", "Waiting", "", 20));

        assertThat(permissionChecks).containsExactly(
                new PermissionCheck(DictAdminPermission.ITEM_CREATE, Optional.of("tenant-b")));
        assertThat(tenantLookups).containsExactly("tenant-b");
    }

    @Test
    void missing_tenant_directory_rejects_tenant_write() {
        service = fixture.withoutTenantDirectory().build();
        assertThatThrownBy(() -> service.createTenantItem(10L, "tenant-b", command))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.CONFIGURATION_INVALID);
    }

    @Test
    void create_item_locks_dict_and_invalidates_after_commit() {
        service.createTenantItem(10L, "tenant-a", command);
        assertThat(events).containsExactly(
                "tx.begin", "dict.lock:10", "item.insert", "tx.commit",
                "cache.evict:ORDER_STATUS:tenant-a");
    }

    @Test
    void delete_non_empty_dict_is_rejected_without_cache_change() {
        repository.setUndeletedItemCount(10L, 1);
        assertThatThrownBy(() -> service.deleteDict(10L))
                .extracting("code").isEqualTo(DictErrorCode.DICT_NOT_EMPTY);
        assertThat(cacheEvents).isEmpty();
    }

    @Test
    void default_item_create_uses_empty_scope_and_evicts_all_after_commit() {
        service.createDefaultItem(10L, command);

        assertThat(permissionChecks).containsExactly(
                new PermissionCheck(DictAdminPermission.ITEM_CREATE, Optional.<String>empty()));
        assertThat(tenantLookups).isEmpty();
        assertThat(events).containsExactly(
                "tx.begin", "dict.lock:10", "item.insert", "tx.commit",
                "cache.evict:ORDER_STATUS");
    }

    @Test
    void create_dict_uses_empty_scope_operator_clock_and_evicts_after_commit() {
        long id = service.createDict(new CreateDictCommand("ORDER_TYPE", "Order type", "", 1));

        assertThat(permissionChecks).containsExactly(
                new PermissionCheck(DictAdminPermission.DICT_CREATE, Optional.<String>empty()));
        assertThat(id).isEqualTo(100L);
        assertThat(repository.lastInsertedDict().createdBy()).isEqualTo(OPERATOR);
        assertThat(repository.lastInsertedDict().createdAt()).isEqualTo(NOW);
        assertThat(repository.lastInsertedDict().version()).isEqualTo(0);
        assertThat(events).containsExactly(
                "tx.begin", "dict.insert", "tx.commit", "cache.evict:ORDER_TYPE");
    }

    @Test
    void update_dict_locks_and_evicts_all_after_commit() {
        service.updateDict(10L, new UpdateDictCommand("Order lifecycle", "Lifecycle", 30));

        assertThat(permissionChecks).containsExactly(
                new PermissionCheck(DictAdminPermission.DICT_UPDATE, Optional.<String>empty()));
        assertThat(events).containsExactly(
                "tx.begin", "dict.lock:10", "dict.update", "tx.commit", "cache.evict:ORDER_STATUS");
        assertThat(repository.storedDict(10L).name()).isEqualTo("Order lifecycle");
        assertThat(repository.storedDict(10L).updatedBy()).isEqualTo(OPERATOR);
        assertThat(repository.storedDict(10L).updatedAt()).isEqualTo(NOW);
        assertThat(repository.storedDict(10L).version()).isEqualTo(3);
    }

    @Test
    void change_dict_status_uses_enable_disable_permission() {
        service.changeDictStatus(10L, false);

        assertThat(permissionChecks).containsExactly(
                new PermissionCheck(DictAdminPermission.DICT_ENABLE_DISABLE, Optional.<String>empty()));
        assertThat(repository.storedDict(10L).status()).isEqualTo(DictStatus.DISABLED);
        assertThat(events).containsExactly(
                "tx.begin", "dict.lock:10", "dict.update", "tx.commit", "cache.evict:ORDER_STATUS");
    }

    @Test
    void delete_empty_dict_evicts_all_after_commit() {
        service.deleteDict(10L);

        assertThat(permissionChecks).containsExactly(
                new PermissionCheck(DictAdminPermission.DICT_DELETE, Optional.<String>empty()));
        assertThat(repository.storedDict(10L).isDeleted()).isTrue();
        assertThat(events).containsExactly(
                "tx.begin", "dict.lock:10", "dict.update", "tx.commit", "cache.evict:ORDER_STATUS");
    }

    @Test
    void restore_dict_evicts_all_after_commit() {
        repository.replaceDict(deletedDict(10L, "ORDER_STATUS"));

        service.restoreDict(10L);

        assertThat(permissionChecks).containsExactly(
                new PermissionCheck(DictAdminPermission.DICT_RESTORE, Optional.<String>empty()));
        assertThat(repository.storedDict(10L).isDeleted()).isFalse();
        assertThat(events).containsExactly(
                "tx.begin", "dict.lock:10", "dict.update", "tx.commit", "cache.evict:ORDER_STATUS");
    }

    @Test
    void update_tenant_item_checks_target_scope_and_evicts_tenant() {
        repository.putItem(existingItem(91L, TenantId.of("tenant-a"), false));

        service.updateItem(91L, new UpdateItemCommand("Waiting confirm", "Updated", 25));

        assertThat(permissionChecks).containsExactly(
                new PermissionCheck(DictAdminPermission.ITEM_UPDATE, Optional.of("tenant-a")));
        assertThat(tenantLookups).containsExactly("tenant-a");
        assertThat(events).containsExactly(
                "tx.begin", "dict.lock:10", "item.update", "tx.commit",
                "cache.evict:ORDER_STATUS:tenant-a");
        assertThat(repository.storedItem(91L).name()).isEqualTo("Waiting confirm");
        assertThat(repository.storedItem(91L).version()).isEqualTo(1);
    }

    @Test
    void update_default_item_uses_empty_scope_and_evicts_all() {
        repository.putItem(existingItem(90L, TenantId.defaultItem(), false));

        service.updateItem(90L, new UpdateItemCommand("Waiting confirm", "Updated", 25));

        assertThat(permissionChecks).containsExactly(
                new PermissionCheck(DictAdminPermission.ITEM_UPDATE, Optional.<String>empty()));
        assertThat(tenantLookups).isEmpty();
        assertThat(events).containsExactly(
                "tx.begin", "dict.lock:10", "item.update", "tx.commit", "cache.evict:ORDER_STATUS");
    }

    @Test
    void change_and_delete_item_follow_item_scope_and_cache_rules() {
        repository.putItem(existingItem(91L, TenantId.of("tenant-a"), false));

        service.changeItemStatus(91L, false);
        assertThat(permissionChecks).containsExactly(
                new PermissionCheck(DictAdminPermission.ITEM_ENABLE_DISABLE, Optional.of("tenant-a")));
        assertThat(repository.storedItem(91L).status()).isEqualTo(ItemStatus.DISABLED);

        permissionChecks.clear();
        events.clear();
        cacheEvents.clear();
        tenantLookups.clear();

        service.deleteItem(91L);
        assertThat(permissionChecks).containsExactly(
                new PermissionCheck(DictAdminPermission.ITEM_DELETE, Optional.of("tenant-a")));
        assertThat(repository.storedItem(91L).isDeleted()).isTrue();
        assertThat(events).containsExactly(
                "tx.begin", "dict.lock:10", "item.update", "tx.commit",
                "cache.evict:ORDER_STATUS:tenant-a");
    }

    @Test
    void restore_item_requires_a_restored_dictionary() {
        repository.replaceDict(deletedDict(10L, "ORDER_STATUS"));
        repository.putItem(existingItem(91L, TenantId.of("tenant-a"), true));

        assertThatThrownBy(() -> service.restoreItem(91L))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
        assertThat(cacheEvents).isEmpty();
        assertThat(repository.storedItem(91L).isDeleted()).isTrue();
    }

    @Test
    void restore_item_evicts_after_commit_when_dictionary_is_present() {
        repository.putItem(existingItem(91L, TenantId.of("tenant-a"), true));

        service.restoreItem(91L);

        assertThat(permissionChecks).containsExactly(
                new PermissionCheck(DictAdminPermission.ITEM_RESTORE, Optional.of("tenant-a")));
        assertThat(repository.storedItem(91L).isDeleted()).isFalse();
        assertThat(events).containsExactly(
                "tx.begin", "dict.lock:10", "item.update", "tx.commit",
                "cache.evict:ORDER_STATUS:tenant-a");
    }

    @Test
    void page_dicts_checks_view_permission_and_rejects_invalid_paging() {
        PageResult<DictSummary> result = service.pageDicts(new DictPageQuery(null, null, null, false, 1, 20));

        assertThat(permissionChecks).containsExactly(
                new PermissionCheck(DictAdminPermission.DICT_VIEW, Optional.<String>empty()));
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(events).isEmpty();

        assertInvalid(() -> service.pageDicts(new DictPageQuery(null, null, null, false, 0, 20)));
        assertInvalid(() -> service.pageDicts(new DictPageQuery(null, null, null, false, 1, 0)));
        assertInvalid(() -> service.pageDicts(new DictPageQuery(null, null, null, false, 1, 101)));
    }

    @Test
    void page_tenant_items_checks_target_scope_and_directory() {
        PageResult<DictItemDetail> result = service.pageItems(10L,
                new ItemPageQuery("tenant-b", null, null, null, false, 2, 20));

        assertThat(permissionChecks).containsExactly(
                new PermissionCheck(DictAdminPermission.DICT_VIEW, Optional.of("tenant-b")));
        assertThat(tenantLookups).containsExactly("tenant-b");
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(20);
    }

    @Test
    void page_default_items_uses_empty_scope_and_skips_directory() {
        service.pageItems(10L, new ItemPageQuery(null, null, null, null, false, 1, 20));

        assertThat(permissionChecks).containsExactly(
                new PermissionCheck(DictAdminPermission.DICT_VIEW, Optional.<String>empty()));
        assertThat(tenantLookups).isEmpty();
    }

    @Test
    void missing_tenant_directory_rejects_tenant_item_query() {
        service = fixture.withoutTenantDirectory().build();

        assertThatThrownBy(() -> service.pageItems(10L,
                new ItemPageQuery("tenant-b", null, null, null, false, 1, 20)))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.CONFIGURATION_INVALID);
    }

    @Test
    void get_dict_hides_deleted_rows_unless_requested() {
        DictDetail visible = service.getDict(10L, false);
        assertThat(visible.getCode()).isEqualTo("ORDER_STATUS");
        assertThat(visible.isEnabled()).isTrue();
        assertThat(visible.isDeleted()).isFalse();
        assertThat(permissionChecks).containsExactly(
                new PermissionCheck(DictAdminPermission.DICT_VIEW, Optional.<String>empty()));
        assertThat(events).isEmpty();

        repository.replaceDict(deletedDict(10L, "ORDER_STATUS"));
        assertThatThrownBy(() -> service.getDict(10L, false))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.DICT_NOT_FOUND);
        assertThat(service.getDict(10L, true).isDeleted()).isTrue();
    }

    @Test
    void missing_operator_rejects_write_without_transaction() {
        service = fixture.withOperator("").build();

        assertThatThrownBy(() -> service.createDict(new CreateDictCommand("ORDER_TYPE", "Type", "", 1)))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
        assertThat(events).isEmpty();
        assertThat(cacheEvents).isEmpty();
    }

    @Test
    void permission_denied_rejects_before_transaction() {
        fixture.deny(DictAdminPermission.ITEM_CREATE, Optional.of("tenant-b"));

        assertThatThrownBy(() -> service.createTenantItem(10L, "tenant-b", command))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.PERMISSION_DENIED);
        assertThat(events).isEmpty();
        assertThat(tenantLookups).isEmpty();
    }

    @Test
    void unknown_tenant_rejects_write_without_transaction() {
        fixture.missingTenant("tenant-b");

        assertThatThrownBy(() -> service.createTenantItem(10L, "tenant-b", command))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.TENANT_NOT_FOUND);
        assertThat(events).isEmpty();
        assertThat(cacheEvents).isEmpty();
    }

    @Test
    void reserved_zero_tenant_is_rejected_as_invalid_argument() {
        assertThatThrownBy(() -> service.createTenantItem(10L, "0", command))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
        assertThat(events).isEmpty();
    }

    @Test
    void null_tenant_is_rejected_as_invalid_argument() {
        assertThatThrownBy(() -> service.createTenantItem(10L, null, command))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
        assertThat(events).isEmpty();
    }

    @Test
    void item_code_conflict_is_enforced_by_policy_inside_transaction() {
        repository.setItemCodeUsage(ItemCodeUsage.defaultAndTenant(false, true));

        assertThatThrownBy(() -> service.createTenantItem(10L, "tenant-a", command))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.DICT_ITEM_CODE_CONFLICT);
        assertThat(events).containsExactly("tx.begin", "dict.lock:10", "tx.rollback");
        assertThat(cacheEvents).isEmpty();
    }

    @Test
    void item_limit_uses_configured_tenant_limit() {
        service = fixture.withLimits(new DictLimits(2000, 1000, 2)).build();
        repository.setUndeletedItemCount(10L, TenantId.of("tenant-a"), 2);

        assertThatThrownBy(() -> service.createTenantItem(10L, "tenant-a", command))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.DICT_ITEM_LIMIT_EXCEEDED);
        assertThat(cacheEvents).isEmpty();
    }

    @Test
    void default_item_limit_uses_configured_default_limit() {
        service = fixture.withLimits(new DictLimits(2000, 3, 1000)).build();
        repository.setUndeletedItemCount(10L, TenantId.defaultItem(), 3);

        assertThatThrownBy(() -> service.createDefaultItem(10L, command))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.DICT_ITEM_LIMIT_EXCEEDED);
    }

    @Test
    void dict_limits_defaults_expose_admin_item_limits() {
        DictLimits limits = DictLimits.defaults();

        assertThat(limits.getMaxEffectiveItems()).isEqualTo(2000);
        assertThat(limits.getDefaultItemsPerDict()).isEqualTo(1000);
        assertThat(limits.getTenantItemsPerDict()).isEqualTo(1000);
        assertThat(new DictLimits(1500).getDefaultItemsPerDict()).isEqualTo(1000);
        assertThat(new DictLimits(1500).getTenantItemsPerDict()).isEqualTo(1000);
    }

    @Test
    void create_item_on_missing_or_deleted_dict_is_not_found() {
        assertThatThrownBy(() -> service.createDefaultItem(99L, command))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.DICT_NOT_FOUND);

        repository.replaceDict(deletedDict(10L, "ORDER_STATUS"));
        events.clear();
        cacheEvents.clear();
        assertThatThrownBy(() -> service.createDefaultItem(10L, command))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.DICT_NOT_FOUND);
        assertThat(cacheEvents).isEmpty();
    }

    @Test
    void conflicting_dict_code_is_rejected_inside_transaction() {
        repository.addExistingCode(DictCode.of("ORDER_STATUS"));

        assertThatThrownBy(() -> service.createDict(new CreateDictCommand("ORDER_STATUS", "Dup", "", 1)))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.DICT_CODE_CONFLICT);
        assertThat(events).containsExactly("tx.begin", "tx.rollback");
        assertThat(cacheEvents).isEmpty();
    }

    private static void assertInvalid(ThrowingAction action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
    }

    private static Dict deletedDict(long id, String code) {
        return DictFactory.rebuild(id, DictCode.of(code), "Order status", "", DictStatus.ENABLED, 10,
                3, true, OPERATOR, NOW, OPERATOR, NOW);
    }

    private static DictItem existingItem(long id, TenantId tenantId, boolean deleted) {
        return DictItemFactory.rebuild(id, 10L, ItemCode.of("WAIT_CONFIRM"), "Waiting", tenantId, "",
                ItemStatus.ENABLED, 20, 1, deleted, OPERATOR, NOW, OPERATOR, NOW);
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run();
    }

    private final class Fixture {
        private String operator = OPERATOR;
        private TenantDirectory tenantDirectory = new RecordingTenantDirectory();
        private DictLimits limits = DictLimits.defaults();
        private final RecordingPermissionProvider permissionProvider = new RecordingPermissionProvider();

        private Fixture withoutTenantDirectory() {
            tenantDirectory = null;
            return this;
        }

        private Fixture withOperator(String operatorId) {
            operator = operatorId;
            return this;
        }

        private Fixture withLimits(DictLimits dictLimits) {
            limits = dictLimits;
            return this;
        }

        private void deny(DictAdminPermission permission, Optional<String> targetTenantId) {
            permissionProvider.deny(permission, targetTenantId);
        }

        private void missingTenant(String tenantId) {
            ((RecordingTenantDirectory) tenantDirectory).missing(tenantId);
        }

        private DictAdminService build() {
            permissionChecks = permissionProvider.checks;
            if (tenantDirectory instanceof RecordingTenantDirectory) {
                tenantLookups = ((RecordingTenantDirectory) tenantDirectory).lookups;
            } else {
                tenantLookups = new ArrayList<String>();
            }
            events = new ArrayList<String>();
            cacheEvents = new ArrayList<String>();
            repository = new InMemoryDictAdminRepository(events);
            return new DefaultDictAdminService(
                    repository,
                    new RecordingTxRunner(events),
                    new RecordingDictCache(events, cacheEvents),
                    new FixedOperatorProvider(operator),
                    permissionProvider,
                    tenantDirectory,
                    limits,
                    CLOCK);
        }
    }

    private static final class PermissionCheck {
        private final DictAdminPermission permission;
        private final Optional<String> targetTenantId;

        private PermissionCheck(DictAdminPermission permission, Optional<String> targetTenantId) {
            this.permission = permission;
            this.targetTenantId = targetTenantId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PermissionCheck)) {
                return false;
            }
            PermissionCheck that = (PermissionCheck) other;
            return permission == that.permission && Objects.equals(targetTenantId, that.targetTenantId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(permission, targetTenantId);
        }

        @Override
        public String toString() {
            return permission + ":" + targetTenantId;
        }
    }

    private static final class RecordingPermissionProvider implements PermissionProvider {
        private final List<PermissionCheck> checks = new ArrayList<PermissionCheck>();
        private final List<PermissionCheck> denied = new ArrayList<PermissionCheck>();

        private void deny(DictAdminPermission permission, Optional<String> targetTenantId) {
            denied.add(new PermissionCheck(permission, targetTenantId));
        }

        @Override
        public boolean hasPermission(DictAdminPermission permission, Optional<String> targetTenantId) {
            PermissionCheck check = new PermissionCheck(permission, targetTenantId);
            checks.add(check);
            return !denied.contains(check);
        }
    }

    private static final class RecordingTenantDirectory implements TenantDirectory {
        private final List<String> lookups = new ArrayList<String>();
        private final List<String> missing = new ArrayList<String>();

        private void missing(String tenantId) {
            missing.add(tenantId);
        }

        @Override
        public PageResult<TenantOption> search(String keyword, int page, int size) {
            return new PageResult<TenantOption>(Collections.<TenantOption>emptyList(), 0, page, size);
        }

        @Override
        public boolean exists(String tenantId) {
            lookups.add(tenantId);
            return !missing.contains(tenantId);
        }
    }

    private static final class RecordingTxRunner implements TxRunner {
        private final List<String> events;

        private RecordingTxRunner(List<String> events) {
            this.events = events;
        }

        @Override
        public <T> T required(Supplier<T> action) {
            events.add("tx.begin");
            try {
                T result = action.get();
                events.add("tx.commit");
                return result;
            } catch (RuntimeException failure) {
                events.add("tx.rollback");
                throw failure;
            }
        }
    }

    private static final class RecordingDictCache implements DictCache {
        private final List<String> events;
        private final List<String> cacheEvents;

        private RecordingDictCache(List<String> events, List<String> cacheEvents) {
            this.events = events;
            this.cacheEvents = cacheEvents;
        }

        @Override
        public List<DictItemView> load(String dictCode, String tenantId, Supplier<List<DictItemView>> databaseLoader) {
            return databaseLoader.get();
        }

        @Override
        public void evictAll(String dictCode) {
            String event = "cache.evict:" + dictCode;
            events.add(event);
            cacheEvents.add(event);
        }

        @Override
        public void evictTenant(String dictCode, String tenantId) {
            String event = "cache.evict:" + dictCode + ":" + tenantId;
            events.add(event);
            cacheEvents.add(event);
        }
    }

    private static final class FixedOperatorProvider implements OperatorProvider {
        private final String operatorId;

        private FixedOperatorProvider(String operatorId) {
            this.operatorId = operatorId;
        }

        @Override
        public String currentOperatorId() {
            return operatorId;
        }
    }

    private static final class InMemoryDictAdminRepository implements DictAdminRepository {
        private final List<String> events;
        private final Map<Long, Dict> dicts = new LinkedHashMap<Long, Dict>();
        private final Map<Long, DictItem> items = new LinkedHashMap<Long, DictItem>();
        private final Map<Long, Integer> undeletedCounts = new HashMap<Long, Integer>();
        private final Map<String, Integer> undeletedCountsByTenant = new HashMap<String, Integer>();
        private final List<DictCode> existingCodes = new ArrayList<DictCode>();
        private ItemCodeUsage itemCodeUsage = ItemCodeUsage.none();
        private Dict lastInsertedDict;
        private long nextDictId = 100L;
        private long nextItemId = 200L;

        private InMemoryDictAdminRepository(List<String> events) {
            this.events = events;
            replaceDict(DictFactory.rebuild(10L, DictCode.of("ORDER_STATUS"), "Order status", "",
                    DictStatus.ENABLED, 10, 3, false, OPERATOR, NOW, OPERATOR, NOW));
        }

        private void setUndeletedItemCount(long dictId, int count) {
            undeletedCounts.put(dictId, count);
        }

        private void setUndeletedItemCount(long dictId, TenantId tenantId, int count) {
            undeletedCountsByTenant.put(countKey(dictId, tenantId), count);
        }

        private void setItemCodeUsage(ItemCodeUsage usage) {
            itemCodeUsage = usage;
        }

        private void addExistingCode(DictCode code) {
            existingCodes.add(code);
        }

        private void replaceDict(Dict dict) {
            dicts.put(dict.id(), dict);
        }

        private void putItem(DictItem item) {
            items.put(item.id(), item);
        }

        private Dict storedDict(long dictId) {
            return dicts.get(dictId);
        }

        private DictItem storedItem(long itemId) {
            return items.get(itemId);
        }

        private Dict lastInsertedDict() {
            return lastInsertedDict;
        }

        @Override
        public PageResult<DictSummary> pageDicts(DictPageQuery query) {
            return new PageResult<DictSummary>(Collections.<DictSummary>emptyList(), 0, query.getPage(), query.getSize());
        }

        @Override
        public Optional<Dict> findDict(long dictId) {
            return Optional.ofNullable(copyDict(dicts.get(dictId)));
        }

        @Override
        public Optional<Dict> lockDict(long dictId) {
            events.add("dict.lock:" + dictId);
            return Optional.ofNullable(copyDict(dicts.get(dictId)));
        }

        @Override
        public boolean existsDictCode(DictCode code) {
            return existingCodes.contains(code);
        }

        @Override
        public long insertDict(Dict dict) {
            events.add("dict.insert");
            lastInsertedDict = dict;
            long id = nextDictId++;
            dicts.put(id, dict);
            return id;
        }

        @Override
        public void updateDict(Dict dict) {
            events.add("dict.update");
            dicts.put(dict.id(), dict);
        }

        @Override
        public PageResult<DictItemDetail> pageItems(long dictId, ItemPageQuery query) {
            return new PageResult<DictItemDetail>(
                    Collections.<DictItemDetail>emptyList(), 0, query.getPage(), query.getSize());
        }

        @Override
        public Optional<DictItem> findItem(long itemId) {
            return Optional.ofNullable(copyItem(items.get(itemId)));
        }

        @Override
        public ItemCodeUsage findItemCodeUsage(long dictId, ItemCode code, TenantId tenantId) {
            return itemCodeUsage;
        }

        @Override
        public int countUndeletedItems(long dictId) {
            Integer count = undeletedCounts.get(dictId);
            return count == null ? 0 : count;
        }

        @Override
        public int countUndeletedItems(long dictId, TenantId tenantId) {
            Integer count = undeletedCountsByTenant.get(countKey(dictId, tenantId));
            return count == null ? 0 : count;
        }

        @Override
        public long insertItem(DictItem item) {
            events.add("item.insert");
            long id = nextItemId++;
            items.put(id, item);
            return id;
        }

        @Override
        public void updateItem(DictItem item) {
            events.add("item.update");
            items.put(item.id(), item);
        }

        private static String countKey(long dictId, TenantId tenantId) {
            return dictId + ":" + tenantId.value();
        }

        private static Dict copyDict(Dict dict) {
            if (dict == null) {
                return null;
            }
            return DictFactory.rebuild(dict.id(), dict.code(), dict.name(), dict.description(), dict.status(),
                    dict.sortNo(), dict.version(), dict.isDeleted(), dict.createdBy(), dict.createdAt(),
                    dict.updatedBy(), dict.updatedAt());
        }

        private static DictItem copyItem(DictItem item) {
            if (item == null) {
                return null;
            }
            return DictItemFactory.rebuild(item.id(), item.dictId(), item.code(), item.name(), item.tenantId(),
                    item.description(), item.status(), item.sortNo(), item.version(), item.isDeleted(),
                    item.createdBy(), item.createdAt(), item.updatedBy(), item.updatedAt());
        }
    }
}
