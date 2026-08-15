package com.vincent.tools.dict.web;

import com.vincent.tools.dict.application.admin.DictAdminService;
import com.vincent.tools.dict.application.admin.OperatorProvider;
import com.vincent.tools.dict.application.admin.PermissionProvider;
import com.vincent.tools.dict.application.admin.TenantDirectory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
@ConditionalOnProperty(prefix = "vincent.dict.admin", name = "enabled", havingValue = "true")
@AutoConfigureAfter(name = "com.vincent.tools.dict.boot2.DictCoreAutoConfiguration")
@EnableConfigurationProperties(DictAdminWebProperties.class)
public class DictAdminWebAutoConfiguration {
    private final PermissionProvider permissionProvider;

    public DictAdminWebAutoConfiguration(OperatorProvider operatorProvider,
                                         PermissionProvider permissionProvider) {
        Objects.requireNonNull(operatorProvider, "operatorProvider");
        this.permissionProvider = Objects.requireNonNull(permissionProvider, "permissionProvider");
    }

    @Bean
    public DictWebExceptionHandler dictWebExceptionHandler() {
        return new DictWebExceptionHandler();
    }

    @Bean
    public DictAdminController dictAdminController(DictAdminService adminService,
                                                   ObjectProvider<TenantDirectory> tenantDirectories) {
        return new DictAdminController(adminService, permissionProvider, tenantDirectories.getIfAvailable());
    }

    @Bean
    public DictItemAdminController dictItemAdminController(DictAdminService adminService) {
        return new DictItemAdminController(adminService);
    }

    @Bean
    public TenantAdminController tenantAdminController(ObjectProvider<TenantDirectory> tenantDirectories) {
        return new TenantAdminController(tenantDirectories.getIfAvailable(), permissionProvider);
    }

    @Bean
    public DictAdminPageAuthFilter dictAdminPageAuthFilter(DictAdminWebProperties properties) {
        return new DictAdminPageAuthFilter(permissionProvider, properties.getBasePath());
    }

    @Bean
    public DictAdminPageController dictAdminPageController(DictAdminWebProperties properties) {
        return new DictAdminPageController(permissionProvider, properties);
    }

    @Bean
    public DictAdminResourceHandler dictAdminResourceHandler(DictAdminWebProperties properties) {
        return new DictAdminResourceHandler(properties.getBasePath(), properties.getApiPath());
    }
}
