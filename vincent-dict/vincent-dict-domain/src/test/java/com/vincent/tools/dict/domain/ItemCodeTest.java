package com.vincent.tools.dict.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemCodeTest {
    @Test
    void accepts_uppercase_business_code() {
        assertThat(ItemCode.of("ACTIVE").value()).isEqualTo("ACTIVE");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " item", "item", "1_ITEM", "ITEM-CODE", "ITEM "})
    void rejects_each_non_canonical_item_code_with_stable_invalid_argument(String value) {
        assertThatThrownBy(() -> ItemCode.of(value))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void rejects_item_code_longer_than_sixty_four_characters_with_stable_invalid_argument() {
        String value = "A" + repeat('B', 64);

        assertThatThrownBy(() -> ItemCode.of(value))
                .isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
    }

    private static String repeat(char character, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(character);
        }
        return result.toString();
    }
}
