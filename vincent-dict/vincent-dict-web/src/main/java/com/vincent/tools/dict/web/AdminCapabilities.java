package com.vincent.tools.dict.web;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AdminCapabilities {
    private final boolean tenantDirectoryAvailable;
    private final Map<String, Boolean> permissions;

    public AdminCapabilities(boolean tenantDirectoryAvailable, Map<String, Boolean> permissions) {
        this.tenantDirectoryAvailable = tenantDirectoryAvailable;
        this.permissions = Collections.unmodifiableMap(new LinkedHashMap<String, Boolean>(permissions));
    }

    public boolean isTenantDirectoryAvailable() {
        return tenantDirectoryAvailable;
    }

    public Map<String, Boolean> getPermissions() {
        return permissions;
    }
}
