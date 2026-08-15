package com.vincent.tools.audit.application;

import com.vincent.tools.host.VincentPermission;

public enum AuditPermission implements VincentPermission {
    AUDIT_VIEW;

    @Override
    public String code() {
        return name();
    }
}
