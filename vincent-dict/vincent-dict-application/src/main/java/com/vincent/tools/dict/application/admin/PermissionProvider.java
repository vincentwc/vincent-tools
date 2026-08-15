package com.vincent.tools.dict.application.admin;

import java.util.Optional;

public interface PermissionProvider {
    boolean hasPermission(DictAdminPermission permission, Optional<String> targetTenantId);
}
