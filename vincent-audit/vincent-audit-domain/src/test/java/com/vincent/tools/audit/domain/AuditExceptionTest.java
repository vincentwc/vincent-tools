package com.vincent.tools.audit.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditExceptionTest {

    @Test
    void exposesErrorCode() {
        AuditException exception = new AuditException(AuditErrorCode.SCHEMA_MISSING, "schema missing");

        assertThat(exception.getCode()).isEqualTo(AuditErrorCode.SCHEMA_MISSING);
        assertThat(exception).hasMessage("schema missing");
    }

    @Test
    void requireNonBlankRejectsInvalidValues() {
        assertThatThrownBy(() -> AuditFieldLimits.requireNonBlank(" ", "action", AuditFieldLimits.MAX_ACTION_LENGTH))
                .isInstanceOf(AuditException.class)
                .extracting("code")
                .isEqualTo(AuditErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void optionalBoundedAllowsNull() {
        assertThat(AuditFieldLimits.optionalBounded(null, "clientIp", AuditFieldLimits.MAX_CLIENT_IP_LENGTH))
                .isNull();
    }
}
