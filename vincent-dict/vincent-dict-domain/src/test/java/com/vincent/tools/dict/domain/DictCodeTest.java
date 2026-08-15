package com.vincent.tools.dict.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DictCodeTest {
    @Test
    void rejects_null_code_with_stable_invalid_argument() {
        assertThatThrownBy(() -> DictCode.of(null))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void accepts_uppercase_business_code() {
        assertThat(DictCode.of("ORDER_STATUS").value()).isEqualTo("ORDER_STATUS");
    }

    @Test
    void accepts_code_at_the_sixty_four_character_limit() {
        String value = "A" + repeat('B', 63);

        assertThat(DictCode.of(value).value()).isEqualTo(value);
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

    private static String repeat(char character, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(character);
        }
        return result.toString();
    }
}
