package com.vincent.tools.dict.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantIdTest {
    @Test
    void reserves_zero_for_default_items() {
        assertThat(TenantId.defaultItem().isDefault()).isTrue();
        assertThatThrownBy(() -> TenantId.of("0")).isInstanceOf(DictException.class);
    }

    @Test
    void accepts_non_default_tenant_without_normalization() {
        assertThat(TenantId.of("tenant-a").value()).isEqualTo("tenant-a");
        assertThat(TenantId.of("tenant-a").isDefault()).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", " tenant-a", "tenant-a "})
    void rejects_missing_or_padded_tenant_ids(String value) {
        assertThatThrownBy(() -> TenantId.of(value))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void preserves_value_equality_and_string_representation() {
        TenantId first = TenantId.of("tenant-a");
        TenantId second = TenantId.of("tenant-a");

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first.toString()).isEqualTo("tenant-a");
        assertThat(TenantId.defaultItem().toString()).isEqualTo("0");
    }
}
