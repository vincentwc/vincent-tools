package com.vincent.tools.dict.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DictCodeTest {
    @Test
    void accepts_uppercase_business_code() {
        assertThat(DictCode.of("ORDER_STATUS").value()).isEqualTo("ORDER_STATUS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " order", "order", "1_ORDER", "ORDER-STATUS"})
    void rejects_non_canonical_codes(String value) {
        assertThatThrownBy(() -> DictCode.of(value))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void rejects_code_longer_than_sixty_four_characters() {
        String value = "A" + new String(new char[64]).replace('\0', 'B');

        assertThatThrownBy(() -> DictCode.of(value))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void preserves_value_equality_and_string_representation() {
        DictCode first = DictCode.of("ORDER_STATUS");
        DictCode second = DictCode.of("ORDER_STATUS");

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first.toString()).isEqualTo("ORDER_STATUS");
    }
}
