package com.vincent.tools.audit.web;

import com.vincent.tools.audit.application.AuditService;
import com.vincent.tools.host.OperatorProvider;
import com.vincent.tools.host.PermissionProvider;
import com.vincent.tools.host.VincentPermission;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuditAdminWebAutoConfigurationTest {
    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AuditAdminWebAutoConfiguration.class,
                    AuditAdminDisabledWebAutoConfiguration.class));

    private final ApplicationContextRunner noneRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AuditAdminWebAutoConfiguration.class,
                    AuditAdminDisabledWebAutoConfiguration.class));

    @Test
    void web_application_type_none_does_not_load_auto_configuration() {
        noneRunner.withPropertyValues("vincent.audit.admin.enabled=true")
                .withUserConfiguration(RequiredAdaptersConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AuditAdminWebAutoConfiguration.class);
                    assertThat(context).doesNotHaveBean(AuditAdminDisabledWebAutoConfiguration.class);
                    assertThat(context).doesNotHaveBean(AuditAdminDisabledResourceFilter.class);
                    assertThat(context).doesNotHaveBean(AuditAdminController.class);
                    assertThat(context).doesNotHaveBean(AuditAdminPageController.class);
                    assertThat(context).doesNotHaveBean(AuditAdminResourceHandler.class);
                });
    }

    @Test
    void disabled_admin_does_not_load_auto_configuration() {
        webRunner.withPropertyValues("vincent.audit.admin.enabled=false")
                .withUserConfiguration(RequiredAdaptersConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AuditAdminWebAutoConfiguration.class);
                    assertThat(context).doesNotHaveBean(AuditAdminController.class);
                    assertThat(context).hasSingleBean(AuditAdminDisabledWebAutoConfiguration.class);
                    assertThat(context).hasSingleBean(AuditAdminDisabledResourceFilter.class);
                });
    }

    @Test
    void missing_operator_provider_fails_context() {
        webRunner.withPropertyValues("vincent.audit.admin.enabled=true")
                .withUserConfiguration(PermissionAndServiceConfiguration.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missing_permission_provider_fails_context() {
        webRunner.withPropertyValues("vincent.audit.admin.enabled=true")
                .withUserConfiguration(OperatorAndServiceConfiguration.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void servlet_web_with_providers_loads_controllers() {
        webRunner.withPropertyValues("vincent.audit.admin.enabled=true")
                .withUserConfiguration(RequiredAdaptersConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AuditAdminWebAutoConfiguration.class);
                    assertThat(context).hasSingleBean(AuditAdminController.class);
                    assertThat(context).hasSingleBean(AuditAdminPageController.class);
                    assertThat(context).hasSingleBean(AuditAdminResourceHandler.class);
                    assertThat(context).hasSingleBean(AuditAdminPageAuthFilter.class);
                    assertThat(context).hasSingleBean(AuditWebExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(AuditAdminDisabledResourceFilter.class);
                });
    }

    @Test
    void resource_handler_maps_configured_base_path_to_classpath() {
        AuditAdminResourceHandler handler = new AuditAdminResourceHandler("/custom-admin", "/custom-api");
        assertThat(handler.resourcePattern()).isEqualTo("/custom-admin/**");
        assertThat(handler.resourceLocation()).isEqualTo("classpath:/META-INF/resources/audit-admin/");
    }

    @Configuration
    static class RequiredAdaptersConfiguration {
        @Bean
        OperatorProvider operatorProvider() {
            return () -> "operator";
        }

        @Bean
        PermissionProvider permissionProvider() {
            return new PermissionProvider() {
                @Override
                public boolean hasPermission(VincentPermission permission, Optional<String> targetTenantId) {
                    return true;
                }
            };
        }

        @Bean
        AuditService auditService() {
            return mock(AuditService.class);
        }
    }

    @Configuration
    static class PermissionAndServiceConfiguration {
        @Bean
        PermissionProvider permissionProvider() {
            return new PermissionProvider() {
                @Override
                public boolean hasPermission(VincentPermission permission, Optional<String> targetTenantId) {
                    return true;
                }
            };
        }

        @Bean
        AuditService auditService() {
            return mock(AuditService.class);
        }
    }

    @Configuration
    static class OperatorAndServiceConfiguration {
        @Bean
        OperatorProvider operatorProvider() {
            return () -> "operator";
        }

        @Bean
        AuditService auditService() {
            return mock(AuditService.class);
        }
    }
}
