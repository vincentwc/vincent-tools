package com.vincent.tools.host;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VincentPermissionTest {

    enum SamplePermission implements VincentPermission {
        DICT_VIEW;

        @Override
        public String code() {
            return name();
        }
    }

    @Test
    void codeReturnsEnumName() {
        assertThat(SamplePermission.DICT_VIEW.code()).isEqualTo("DICT_VIEW");
    }
}
