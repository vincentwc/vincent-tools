package com.vincent.tools.audit.example;

import com.vincent.tools.host.OperatorProvider;
import com.vincent.tools.host.PermissionProvider;
import com.vincent.tools.host.TenantProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class ExampleAdminAdapters {
    @Bean
    OperatorProvider operatorProvider() {
        return () -> "example-admin";
    }

    @Bean
    PermissionProvider permissionProvider() {
        return (permission, tenant) -> true;
    }

    @Bean
    TenantProvider tenantProvider() {
        return () -> Optional.of("tenant-a");
    }
}
