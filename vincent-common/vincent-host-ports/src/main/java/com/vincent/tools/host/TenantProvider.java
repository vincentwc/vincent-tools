package com.vincent.tools.host;

import java.util.Optional;

public interface TenantProvider {
    Optional<String> currentTenantId();
}
