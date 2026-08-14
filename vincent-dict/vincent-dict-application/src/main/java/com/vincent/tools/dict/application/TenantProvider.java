package com.vincent.tools.dict.application;

import java.util.Optional;

public interface TenantProvider {
    Optional<String> currentTenantId();
}
