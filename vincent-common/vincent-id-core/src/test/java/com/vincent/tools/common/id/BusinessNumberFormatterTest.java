package com.vincent.tools.common.id;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessNumberFormatterTest {
    @Test
    void formats_date_and_sequence_placeholders() {
        String number = BusinessNumberFormatter.format("ORD-{date}-{seq}", 42L, LocalDate.of(2026, 8, 15));
        assertThat(number).isEqualTo("ORD-20260815-42");
    }
}
