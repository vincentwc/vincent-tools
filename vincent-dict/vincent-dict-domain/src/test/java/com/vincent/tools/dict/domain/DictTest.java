package com.vincent.tools.dict.domain;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DictTest {
    @Test
    void disabled_dict_remains_editable_but_not_effective() {
        Dict dict = Dict.create(DictCode.of("ORDER_STATUS"), "Order status", "", 10,
                "operator", Instant.parse("2026-08-14T00:00:00Z"));

        dict.disable("operator", Instant.parse("2026-08-14T00:01:00Z"));
        dict.rename("Order lifecycle", "operator", Instant.parse("2026-08-14T00:02:00Z"));

        assertThat(dict.isEffective()).isFalse();
        assertThat(dict.name()).isEqualTo("Order lifecycle");
    }

    @Test
    void enabled_dict_becomes_effective_again_after_enabling() {
        Dict dict = TestFixtures.activeDict();
        dict.disable(TestFixtures.OPERATOR, TestFixtures.NOW);

        dict.enable(TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(60));

        assertThat(dict.status()).isEqualTo(DictStatus.ENABLED);
        assertThat(dict.isEffective()).isTrue();
    }

    @Test
    void enabled_dict_is_effective_until_it_is_deleted() {
        Dict dict = TestFixtures.activeDict();

        assertThat(dict.isEffective()).isTrue();
        dict.delete(0, TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(60));

        assertThat(dict.isEffective()).isFalse();
        assertThat(dict.isDeleted()).isTrue();
    }

    @Test
    void non_empty_dict_cannot_be_deleted() {
        Dict dict = TestFixtures.activeDict();

        assertThatThrownBy(() -> dict.delete(1, "operator", TestFixtures.NOW))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.DICT_NOT_EMPTY);
    }

    @Test
    void restore_rejects_a_dict_that_has_not_been_deleted() {
        Dict dict = TestFixtures.activeDict();

        assertThatThrownBy(() -> dict.restore(TestFixtures.OPERATOR, TestFixtures.NOW))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void deleted_dict_can_be_restored() {
        Dict dict = TestFixtures.activeDict();
        dict.delete(0, TestFixtures.OPERATOR, TestFixtures.NOW);

        dict.restore(TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(60));

        assertThat(dict.isDeleted()).isFalse();
        assertThat(dict.isEffective()).isTrue();
    }

    @Test
    void rebuild_restores_persisted_dict_state_without_changing_its_code() {
        Dict dict = Dict.rebuild(81L, DictCode.of("ORDER_STATUS"), "Order status", "", DictStatus.DISABLED, 10,
                7, true, "creator", TestFixtures.NOW, "editor", TestFixtures.NOW.plusSeconds(60));

        assertThat(dict.id()).isEqualTo(81L);
        assertThat(dict.version()).isEqualTo(7);
        assertThat(dict.code()).isEqualTo(DictCode.of("ORDER_STATUS"));
        assertThat(dict.isDeleted()).isTrue();
        assertThat(dict.isEffective()).isFalse();
    }

    @Test
    void create_rejects_invalid_dict_presentation_and_maintenance_data() {
        assertThatThrownBy(() -> Dict.create(DictCode.of("ORDER_STATUS"), "", "", 1,
                TestFixtures.OPERATOR, TestFixtures.NOW))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
        assertThatThrownBy(() -> Dict.create(DictCode.of("ORDER_STATUS"), repeat('n', 129), "", 1,
                TestFixtures.OPERATOR, TestFixtures.NOW))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
        assertThatThrownBy(() -> Dict.create(DictCode.of("ORDER_STATUS"), "Order", repeat('d', 501), 1,
                TestFixtures.OPERATOR, TestFixtures.NOW))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
        assertThatThrownBy(() -> Dict.create(DictCode.of("ORDER_STATUS"), "Order", "", 1,
                " operator", TestFixtures.NOW))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void item_keeps_its_code_dictionary_and_tenant_assignment_when_edited() {
        DictItem item = TestFixtures.activeTenantItem();

        item.disable(TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(60));
        item.rename("Enabled", TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(120));

        assertThat(item.code()).isEqualTo(ItemCode.of("ACTIVE"));
        assertThat(item.dictId()).isEqualTo(42L);
        assertThat(item.tenantId()).isEqualTo(TenantId.of("tenant-a"));
        assertThat(item.name()).isEqualTo("Enabled");
        assertThat(item.isEffective()).isFalse();
    }

    @Test
    void default_item_source_is_derived_from_its_immutable_tenant() {
        DictItem item = DictItem.create(42L, ItemCode.of("ACTIVE"), "Active", TenantId.defaultItem(), "", 10,
                TestFixtures.OPERATOR, TestFixtures.NOW);

        assertThat(item.source()).isEqualTo(DictItemSource.DEFAULT);
        assertThat(item.tenantId()).isEqualTo(TenantId.defaultItem());
    }

    @Test
    void enabled_item_becomes_effective_again_after_enabling() {
        DictItem item = TestFixtures.activeTenantItem();
        item.disable(TestFixtures.OPERATOR, TestFixtures.NOW);

        item.enable(TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(60));

        assertThat(item.status()).isEqualTo(ItemStatus.ENABLED);
        assertThat(item.isEffective()).isTrue();
    }

    @Test
    void item_restore_requires_a_deleted_item_and_a_restored_dictionary() {
        DictItem item = TestFixtures.activeTenantItem();

        assertThatThrownBy(() -> item.restore(true, TestFixtures.OPERATOR, TestFixtures.NOW))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
        item.delete(TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(60));
        assertThatThrownBy(() -> item.restore(false, TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(120)))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);

        item.restore(true, TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(180));

        assertThat(item.isDeleted()).isFalse();
        assertThat(item.isEffective()).isTrue();
    }

    @Test
    void rebuild_restores_persisted_item_state_without_changing_its_ownership() {
        DictItem item = DictItem.rebuild(91L, 42L, ItemCode.of("ACTIVE"), "Active", TenantId.of("tenant-a"), "",
                ItemStatus.DISABLED, 10, 3, true, "creator", TestFixtures.NOW, "editor",
                TestFixtures.NOW.plusSeconds(60));

        assertThat(item.id()).isEqualTo(91L);
        assertThat(item.version()).isEqualTo(3);
        assertThat(item.dictId()).isEqualTo(42L);
        assertThat(item.code()).isEqualTo(ItemCode.of("ACTIVE"));
        assertThat(item.tenantId()).isEqualTo(TenantId.of("tenant-a"));
        assertThat(item.source()).isEqualTo(DictItemSource.TENANT);
        assertThat(item.isDeleted()).isTrue();
        assertThat(item.isEffective()).isFalse();
    }

    @Test
    void item_create_rejects_invalid_presentation_and_maintenance_data() {
        assertThatThrownBy(() -> DictItem.create(42L, ItemCode.of("ACTIVE"), "", TenantId.of("tenant-a"), "", 1,
                TestFixtures.OPERATOR, TestFixtures.NOW))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
        assertThatThrownBy(() -> DictItem.create(42L, ItemCode.of("ACTIVE"), "Active", TenantId.of("tenant-a"),
                repeat('d', 501), 1, TestFixtures.OPERATOR, TestFixtures.NOW))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
        assertThatThrownBy(() -> DictItem.create(42L, ItemCode.of("ACTIVE"), "Active", TenantId.of("tenant-a"), "", 1,
                "operator ", TestFixtures.NOW))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void deleted_dict_rejects_every_non_restore_mutation_without_changing_state() {
        Dict dict = TestFixtures.activeDict();
        dict.delete(0, TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(60));
        DictState before = DictState.capture(dict);

        assertInvalid(() -> dict.rename("Renamed", TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(120)));
        assertThatDictStateIsUnchanged(dict, before);
        assertInvalid(() -> dict.enable(TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(120)));
        assertThatDictStateIsUnchanged(dict, before);
        assertInvalid(() -> dict.disable(TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(120)));
        assertThatDictStateIsUnchanged(dict, before);
        assertInvalid(() -> dict.update("Renamed", "changed", 20, TestFixtures.OPERATOR,
                TestFixtures.NOW.plusSeconds(120)));
        assertThatDictStateIsUnchanged(dict, before);
        assertInvalid(() -> dict.delete(0, TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(120)));
        assertThatDictStateIsUnchanged(dict, before);
    }

    @Test
    void deleted_item_rejects_every_non_restore_mutation_without_changing_state() {
        DictItem item = TestFixtures.activeTenantItem();
        item.delete(TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(60));
        DictItemState before = DictItemState.capture(item);

        assertInvalid(() -> item.rename("Renamed", TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(120)));
        assertThatItemStateIsUnchanged(item, before);
        assertInvalid(() -> item.enable(TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(120)));
        assertThatItemStateIsUnchanged(item, before);
        assertInvalid(() -> item.disable(TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(120)));
        assertThatItemStateIsUnchanged(item, before);
        assertInvalid(() -> item.update("Renamed", "changed", 20, TestFixtures.OPERATOR,
                TestFixtures.NOW.plusSeconds(120)));
        assertThatItemStateIsUnchanged(item, before);
        assertInvalid(() -> item.delete(TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(120)));
        assertThatItemStateIsUnchanged(item, before);
    }

    @Test
    void dict_invalid_operator_does_not_change_business_or_maintenance_state() {
        Dict dict = TestFixtures.activeDict();
        dict.disable(TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(60));
        DictState before = DictState.capture(dict);

        assertInvalid(() -> dict.enable(" operator", TestFixtures.NOW.plusSeconds(120)));

        assertThatDictStateIsUnchanged(dict, before);
    }

    @Test
    void dict_null_timestamp_does_not_change_business_or_maintenance_state() {
        Dict dict = TestFixtures.activeDict();
        DictState before = DictState.capture(dict);

        assertInvalid(() -> dict.disable(TestFixtures.OPERATOR, null));

        assertThatDictStateIsUnchanged(dict, before);
    }

    @Test
    void item_invalid_operator_does_not_change_business_or_maintenance_state() {
        DictItem item = TestFixtures.activeTenantItem();
        item.disable(TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(60));
        DictItemState before = DictItemState.capture(item);

        assertInvalid(() -> item.enable(" operator", TestFixtures.NOW.plusSeconds(120)));

        assertThatItemStateIsUnchanged(item, before);
    }

    @Test
    void item_null_timestamp_does_not_change_business_or_maintenance_state() {
        DictItem item = TestFixtures.activeTenantItem();
        DictItemState before = DictItemState.capture(item);

        assertInvalid(() -> item.disable(TestFixtures.OPERATOR, null));

        assertThatItemStateIsUnchanged(item, before);
    }

    @Test
    void dict_rejects_negative_undeleted_item_count_without_changing_state() {
        Dict dict = TestFixtures.activeDict();
        DictState before = DictState.capture(dict);

        assertInvalid(() -> dict.delete(-1, TestFixtures.OPERATOR, TestFixtures.NOW.plusSeconds(60)));

        assertThatDictStateIsUnchanged(dict, before);
    }

    @Test
    void dict_update_changes_all_editable_presentation_fields_together() {
        Dict dict = TestFixtures.activeDict();

        dict.update("Order lifecycle", "Lifecycle labels", 20, "editor", TestFixtures.NOW.plusSeconds(60));

        assertThat(dict.name()).isEqualTo("Order lifecycle");
        assertThat(dict.description()).isEqualTo("Lifecycle labels");
        assertThat(dict.sortNo()).isEqualTo(20);
        assertThat(dict.updatedBy()).isEqualTo("editor");
        assertThat(dict.updatedAt()).isEqualTo(TestFixtures.NOW.plusSeconds(60));
    }

    @Test
    void dict_update_with_invalid_description_leaves_all_state_unchanged() {
        Dict dict = TestFixtures.activeDict();
        DictState before = DictState.capture(dict);

        assertInvalid(() -> dict.update("Order lifecycle", repeat('d', 501), 20, "editor",
                TestFixtures.NOW.plusSeconds(60)));

        assertThatDictStateIsUnchanged(dict, before);
    }

    @Test
    void item_update_changes_all_editable_presentation_fields_together() {
        DictItem item = TestFixtures.activeTenantItem();

        item.update("Enabled", "Enabled state", 20, "editor", TestFixtures.NOW.plusSeconds(60));

        assertThat(item.name()).isEqualTo("Enabled");
        assertThat(item.description()).isEqualTo("Enabled state");
        assertThat(item.sortNo()).isEqualTo(20);
        assertThat(item.updatedBy()).isEqualTo("editor");
        assertThat(item.updatedAt()).isEqualTo(TestFixtures.NOW.plusSeconds(60));
    }

    @Test
    void item_update_with_invalid_operator_leaves_all_state_unchanged() {
        DictItem item = TestFixtures.activeTenantItem();
        DictItemState before = DictItemState.capture(item);

        assertInvalid(() -> item.update("Enabled", "Enabled state", 20, " editor",
                TestFixtures.NOW.plusSeconds(60)));

        assertThatItemStateIsUnchanged(item, before);
    }

    private static void assertInvalid(ThrowingAction action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
    }

    private static void assertThatDictStateIsUnchanged(Dict dict, DictState before) {
        assertThat(dict.id()).isEqualTo(before.id);
        assertThat(dict.code()).isEqualTo(before.code);
        assertThat(dict.name()).isEqualTo(before.name);
        assertThat(dict.description()).isEqualTo(before.description);
        assertThat(dict.status()).isEqualTo(before.status);
        assertThat(dict.sortNo()).isEqualTo(before.sortNo);
        assertThat(dict.version()).isEqualTo(before.version);
        assertThat(dict.isDeleted()).isEqualTo(before.deleted);
        assertThat(dict.createdBy()).isEqualTo(before.createdBy);
        assertThat(dict.createdAt()).isEqualTo(before.createdAt);
        assertThat(dict.updatedBy()).isEqualTo(before.updatedBy);
        assertThat(dict.updatedAt()).isEqualTo(before.updatedAt);
    }

    private static void assertThatItemStateIsUnchanged(DictItem item, DictItemState before) {
        assertThat(item.id()).isEqualTo(before.id);
        assertThat(item.dictId()).isEqualTo(before.dictId);
        assertThat(item.code()).isEqualTo(before.code);
        assertThat(item.name()).isEqualTo(before.name);
        assertThat(item.tenantId()).isEqualTo(before.tenantId);
        assertThat(item.source()).isEqualTo(before.source);
        assertThat(item.description()).isEqualTo(before.description);
        assertThat(item.status()).isEqualTo(before.status);
        assertThat(item.sortNo()).isEqualTo(before.sortNo);
        assertThat(item.version()).isEqualTo(before.version);
        assertThat(item.isDeleted()).isEqualTo(before.deleted);
        assertThat(item.createdBy()).isEqualTo(before.createdBy);
        assertThat(item.createdAt()).isEqualTo(before.createdAt);
        assertThat(item.updatedBy()).isEqualTo(before.updatedBy);
        assertThat(item.updatedAt()).isEqualTo(before.updatedAt);
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run();
    }

    private static final class DictState {
        private final Long id;
        private final DictCode code;
        private final String name;
        private final String description;
        private final DictStatus status;
        private final int sortNo;
        private final int version;
        private final boolean deleted;
        private final String createdBy;
        private final Instant createdAt;
        private final String updatedBy;
        private final Instant updatedAt;

        private DictState(Dict dict) {
            id = dict.id();
            code = dict.code();
            name = dict.name();
            description = dict.description();
            status = dict.status();
            sortNo = dict.sortNo();
            version = dict.version();
            deleted = dict.isDeleted();
            createdBy = dict.createdBy();
            createdAt = dict.createdAt();
            updatedBy = dict.updatedBy();
            updatedAt = dict.updatedAt();
        }

        private static DictState capture(Dict dict) {
            return new DictState(dict);
        }
    }

    private static final class DictItemState {
        private final Long id;
        private final long dictId;
        private final ItemCode code;
        private final String name;
        private final TenantId tenantId;
        private final DictItemSource source;
        private final String description;
        private final ItemStatus status;
        private final int sortNo;
        private final int version;
        private final boolean deleted;
        private final String createdBy;
        private final Instant createdAt;
        private final String updatedBy;
        private final Instant updatedAt;

        private DictItemState(DictItem item) {
            id = item.id();
            dictId = item.dictId();
            code = item.code();
            name = item.name();
            tenantId = item.tenantId();
            source = item.source();
            description = item.description();
            status = item.status();
            sortNo = item.sortNo();
            version = item.version();
            deleted = item.isDeleted();
            createdBy = item.createdBy();
            createdAt = item.createdAt();
            updatedBy = item.updatedBy();
            updatedAt = item.updatedAt();
        }

        private static DictItemState capture(DictItem item) {
            return new DictItemState(item);
        }
    }

    private static String repeat(char character, int count) {
        StringBuilder value = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            value.append(character);
        }
        return value.toString();
    }
}
