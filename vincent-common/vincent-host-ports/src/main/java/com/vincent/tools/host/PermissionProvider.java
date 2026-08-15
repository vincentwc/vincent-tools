package com.vincent.tools.host;

import java.util.Optional;

public interface PermissionProvider {
    boolean hasPermission(VincentPermission permission, Optional<String> targetTenantId);
}
