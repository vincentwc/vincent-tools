package com.vincent.tools.region.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegionExceptionTest {
    @Test
    void field_limits_reject_blank_code() {
        assertThatThrownBy(() -> RegionFieldLimits.requireNonBlank("  ", "code", RegionFieldLimits.MAX_CODE_LENGTH))
                .isInstanceOf(RegionException.class)
                .extracting("code")
                .isEqualTo(RegionErrorCode.INVALID_ARGUMENT);
    }
}
