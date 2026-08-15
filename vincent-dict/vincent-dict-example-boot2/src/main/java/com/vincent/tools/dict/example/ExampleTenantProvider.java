package com.vincent.tools.dict.example;

import com.vincent.tools.dict.application.TenantProvider;

import java.util.Optional;

public class ExampleTenantProvider implements TenantProvider {
    @Override
    public Optional<String> currentTenantId() {
        return Optional.of("tenant-a");
    }
}
