package com.vincent.tools.audit.web;

import com.vincent.tools.audit.application.AuditService;
import com.vincent.tools.host.OperatorProvider;
import com.vincent.tools.host.PermissionProvider;
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
@ConditionalOnProperty(prefix = "vincent.audit.admin", name = "enabled", havingValue = "true")
@AutoConfigureAfter(name = "com.vincent.tools.audit.boot2.AuditCoreAutoConfiguration")
@EnableConfigurationProperties(AuditAdminWebProperties.class)
public class AuditAdminWebAutoConfiguration {
    private final PermissionProvider permissionProvider;

    public AuditAdminWebAutoConfiguration(OperatorProvider operatorProvider,
                                          PermissionProvider permissionProvider) {
        Objects.requireNonNull(operatorProvider, "operatorProvider");
        this.permissionProvider = Objects.requireNonNull(permissionProvider, "permissionProvider");
    }

    @Bean
    public AuditWebExceptionHandler auditWebExceptionHandler() {
        return new AuditWebExceptionHandler();
    }

    @Bean
    public AuditAdminController auditAdminController(AuditService auditService) {
        return new AuditAdminController(auditService);
    }

    @Bean
    public AuditAdminPageAuthFilter auditAdminPageAuthFilter(AuditAdminWebProperties properties) {
        return new AuditAdminPageAuthFilter(permissionProvider, properties.getBasePath());
    }

    @Bean
    public AuditAdminPageController auditAdminPageController(AuditAdminWebProperties properties) {
        return new AuditAdminPageController(permissionProvider, properties);
    }

    @Bean
    public AuditAdminResourceHandler auditAdminResourceHandler(AuditAdminWebProperties properties) {
        return new AuditAdminResourceHandler(properties.getBasePath(), properties.getApiPath());
    }
}
