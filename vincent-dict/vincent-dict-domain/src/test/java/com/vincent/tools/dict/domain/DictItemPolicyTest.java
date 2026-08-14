package com.vincent.tools.dict.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DictItemPolicyTest {
    private final DictItemPolicy policy = new DictItemPolicy();

    @Test
    void default_code_conflicts_with_any_historical_default_or_tenant_use() {
        assertConflict(TenantId.defaultItem(), ItemCodeUsage.defaultAndTenant(false, true));
        assertConflict(TenantId.defaultItem(), ItemCodeUsage.tenantOnly(true));
        assertConflict(TenantId.defaultItem(), ItemCodeUsage.tenantOnly(false));
    }

    @Test
    void tenant_code_cannot_shadow_default_code() {
        ItemCodeUsage usage = ItemCodeUsage.defaultAndTenant(false, true);

        assertConflict(TenantId.of("tenant-a"), usage);
    }

    @Test
    void tenant_code_cannot_reuse_same_tenant_history() {
        assertConflict(TenantId.of("tenant-a"), ItemCodeUsage.tenantOnly(true));
    }

    @Test
    void tenant_code_can_reuse_other_tenant_only_history() {
        assertThatCode(() -> policy.checkCreate(
                TenantId.of("tenant-a"), ItemCodeUsage.tenantOnly(false), 0, 1000))
                .doesNotThrowAnyException();
    }

    @Test
    void enforces_unDeleted_item_limit() {
        assertThatThrownBy(() -> policy.checkCreate(
                TenantId.of("tenant-a"), ItemCodeUsage.none(), 1000, 1000))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.DICT_ITEM_LIMIT_EXCEEDED);
    }

    @Test
    void permits_creation_below_the_unDeleted_item_limit() {
        assertThatCode(() -> policy.checkCreate(
                TenantId.of("tenant-a"), ItemCodeUsage.none(), 999, 1000))
                .doesNotThrowAnyException();
    }

    private void assertConflict(TenantId tenantId, ItemCodeUsage usage) {
        assertThatThrownBy(() -> policy.checkCreate(tenantId, usage, 0, 1000))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.DICT_ITEM_CODE_CONFLICT);
    }
}
