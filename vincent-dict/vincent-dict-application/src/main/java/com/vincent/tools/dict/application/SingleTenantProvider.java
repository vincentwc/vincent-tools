package com.vincent.tools.dict.application;

import com.vincent.tools.dict.domain.TenantId;
import com.vincent.tools.host.TenantProvider;

import java.util.Optional;

public final class SingleTenantProvider implements TenantProvider {
    @Override
    public Optional<String> currentTenantId() {
        return Optional.of(TenantId.DEFAULT_VALUE);
    }
}
