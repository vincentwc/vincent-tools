package com.vincent.tools.dict.example;

import com.vincent.tools.dict.application.admin.OperatorProvider;
import com.vincent.tools.dict.application.admin.PageResult;
import com.vincent.tools.dict.application.admin.PermissionProvider;
import com.vincent.tools.dict.application.admin.TenantDirectory;
import com.vincent.tools.dict.application.admin.TenantOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    TenantDirectory tenantDirectory() {
        return new ExampleTenantDirectory();
    }

    static final class ExampleTenantDirectory implements TenantDirectory {
        private final List<TenantOption> tenants = Arrays.asList(
                new TenantOption("tenant-a", "Tenant A"),
                new TenantOption("tenant-b", "Tenant B"));

        @Override
        public PageResult<TenantOption> search(String keyword, int page, int size) {
            int safePage = page < 1 ? 1 : page;
            int safeSize = size < 1 ? 20 : size;
            List<TenantOption> matched = new ArrayList<TenantOption>();
            for (int index = 0; index < tenants.size(); index++) {
                TenantOption option = tenants.get(index);
                if (matches(keyword, option)) {
                    matched.add(option);
                }
            }
            int from = Math.min((safePage - 1) * safeSize, matched.size());
            int to = Math.min(from + safeSize, matched.size());
            return new PageResult<TenantOption>(new ArrayList<TenantOption>(matched.subList(from, to)),
                    matched.size(), safePage, safeSize);
        }

        @Override
        public boolean exists(String tenantId) {
            for (int index = 0; index < tenants.size(); index++) {
                if (tenants.get(index).getTenantId().equals(tenantId)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean matches(String keyword, TenantOption option) {
            if (keyword == null || keyword.trim().isEmpty()) {
                return true;
            }
            String needle = keyword.toLowerCase();
            return option.getTenantId().toLowerCase().contains(needle)
                    || (option.getName() != null && option.getName().toLowerCase().contains(needle));
        }
    }
}
