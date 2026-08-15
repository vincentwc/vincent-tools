package com.vincent.tools.region.web;

import com.vincent.tools.region.application.RegionQueryService;
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
@ConditionalOnProperty(prefix = "vincent.region.admin", name = "enabled", havingValue = "true")
@AutoConfigureAfter(name = "com.vincent.tools.region.boot2.RegionCoreAutoConfiguration")
@EnableConfigurationProperties(RegionAdminWebProperties.class)
public class RegionAdminWebAutoConfiguration {
    private final PermissionProvider permissionProvider;

    public RegionAdminWebAutoConfiguration(PermissionProvider permissionProvider) {
        this.permissionProvider = Objects.requireNonNull(permissionProvider, "permissionProvider");
    }

    @Bean
    public RegionWebExceptionHandler regionWebExceptionHandler() {
        return new RegionWebExceptionHandler();
    }

    @Bean
    public RegionAdminController regionAdminController(RegionQueryService queryService) {
        return new RegionAdminController(queryService, permissionProvider);
    }
}
