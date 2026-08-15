package com.vincent.tools.audit.application;

import com.vincent.tools.audit.domain.AuditErrorCode;
import com.vincent.tools.audit.domain.AuditException;

public final class AuditLimits {
    private final int defaultPageSize;
    private final int maxPageSize;

    public AuditLimits(int defaultPageSize, int maxPageSize) {
        if (defaultPageSize <= 0 || maxPageSize <= 0 || maxPageSize < defaultPageSize) {
            throw new AuditException(AuditErrorCode.CONFIGURATION_INVALID, "invalid audit page limits");
        }
        this.defaultPageSize = defaultPageSize;
        this.maxPageSize = maxPageSize;
    }

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }
}
