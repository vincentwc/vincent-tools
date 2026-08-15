package com.vincent.tools.region.example;

import com.vincent.tools.host.PermissionProvider;
import com.vincent.tools.host.VincentPermission;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class ExamplePermissionAdapter {
    @Bean
    PermissionProvider permissionProvider() {
        return new PermissionProvider() {
            @Override
            public boolean hasPermission(VincentPermission permission, Optional<String> targetTenantId) {
                return true;
            }
        };
    }
}
