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

    private static String repeat(char character, int count) {
        StringBuilder value = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            value.append(character);
        }
        return value.toString();
    }
}
