package com.vincent.tools.dict.web;

import com.vincent.tools.dict.application.admin.DictAdminService;
import com.vincent.tools.dict.application.admin.OperatorProvider;
import com.vincent.tools.dict.application.admin.PermissionProvider;
import com.vincent.tools.dict.application.admin.TenantDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DictAdminWebAutoConfigurationTest {
    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DictAdminWebAutoConfiguration.class,
                    DictAdminDisabledWebAutoConfiguration.class));

    private final ApplicationContextRunner noneRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DictAdminWebAutoConfiguration.class,
                    DictAdminDisabledWebAutoConfiguration.class));

    @Test
    void web_application_type_none_does_not_load_auto_configuration() {
        noneRunner.withPropertyValues("vincent.dict.admin.enabled=true")
                .withUserConfiguration(RequiredAdaptersConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DictAdminWebAutoConfiguration.class);
                    assertThat(context).doesNotHaveBean(DictAdminDisabledWebAutoConfiguration.class);
                    assertThat(context).doesNotHaveBean(DictAdminDisabledResourceFilter.class);
                    assertThat(context).doesNotHaveBean(DictAdminController.class);
                    assertThat(context).doesNotHaveBean(DictAdminPageController.class);
                    assertThat(context).doesNotHaveBean(DictAdminResourceHandler.class);
                });
    }

    @Test
    void disabled_admin_does_not_load_auto_configuration() {
        webRunner.withPropertyValues("vincent.dict.admin.enabled=false")
                .withUserConfiguration(RequiredAdaptersConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DictAdminWebAutoConfiguration.class);
                    assertThat(context).doesNotHaveBean(DictAdminController.class);
                    assertThat(context).hasSingleBean(DictAdminDisabledWebAutoConfiguration.class);
                    assertThat(context).hasSingleBean(DictAdminDisabledResourceFilter.class);
                });
    }

    @Test
    void missing_operator_provider_fails_context() {
        webRunner.withPropertyValues("vincent.dict.admin.enabled=true")
                .withUserConfiguration(PermissionAndServiceConfiguration.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missing_permission_provider_fails_context() {
        webRunner.withPropertyValues("vincent.dict.admin.enabled=true")
                .withUserConfiguration(OperatorAndServiceConfiguration.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missing_tenant_directory_does_not_fail_startup() {
        webRunner.withPropertyValues("vincent.dict.admin.enabled=true")
                .withUserConfiguration(RequiredAdaptersConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DictAdminController.class);
                    assertThat(context).hasSingleBean(TenantAdminController.class);
                    assertThat(context).doesNotHaveBean(TenantDirectory.class);
                });
    }

    @Test
    void servlet_web_with_providers_loads_controllers() {
        webRunner.withPropertyValues("vincent.dict.admin.enabled=true")
                .withUserConfiguration(RequiredAdaptersWithDirectoryConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DictAdminWebAutoConfiguration.class);
                    assertThat(context).hasSingleBean(DictAdminController.class);
                    assertThat(context).hasSingleBean(DictItemAdminController.class);
                    assertThat(context).hasSingleBean(TenantAdminController.class);
                    assertThat(context).hasSingleBean(DictAdminPageController.class);
                    assertThat(context).hasSingleBean(DictAdminResourceHandler.class);
                    assertThat(context).hasSingleBean(DictAdminPageAuthFilter.class);
                    assertThat(context).hasSingleBean(DictWebExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(DictAdminDisabledResourceFilter.class);
                });
    }

    @Test
    void resource_handler_maps_configured_base_path_to_classpath() {
        DictAdminResourceHandler handler = new DictAdminResourceHandler("/custom-admin");
        assertThat(handler.resourcePattern()).isEqualTo("/custom-admin/**");
        assertThat(handler.resourceLocation()).isEqualTo("classpath:/META-INF/resources/dict-admin/");
    }

    @Configuration
    static class RequiredAdaptersConfiguration {
        @Bean
        OperatorProvider operatorProvider() {
            return new OperatorProvider() {
                @Override
                public String currentOperatorId() {
                    return "operator";
                }
            };
        }

        @Bean
        PermissionProvider permissionProvider() {
            return new PermissionProvider() {
                @Override
                public boolean hasPermission(com.vincent.tools.dict.application.admin.DictAdminPermission permission,
                                             Optional<String> targetTenantId) {
                    return true;
                }
            };
        }

        @Bean
        DictAdminService dictAdminService() {
            return mock(DictAdminService.class);
        }
    }

    @Configuration
    static class RequiredAdaptersWithDirectoryConfiguration extends RequiredAdaptersConfiguration {
        @Bean
        TenantDirectory tenantDirectory() {
            return mock(TenantDirectory.class);
        }
    }

    @Configuration
    static class PermissionAndServiceConfiguration {
        @Bean
        PermissionProvider permissionProvider() {
            return new PermissionProvider() {
                @Override
                public boolean hasPermission(com.vincent.tools.dict.application.admin.DictAdminPermission permission,
                                             Optional<String> targetTenantId) {
                    return true;
                }
            };
        }

        @Bean
        DictAdminService dictAdminService() {
            return mock(DictAdminService.class);
        }
    }

    @Configuration
    static class OperatorAndServiceConfiguration {
        @Bean
        OperatorProvider operatorProvider() {
            return new OperatorProvider() {
                @Override
                public String currentOperatorId() {
                    return "operator";
                }
            };
        }

        @Bean
        DictAdminService dictAdminService() {
            return mock(DictAdminService.class);
        }
    }
}
