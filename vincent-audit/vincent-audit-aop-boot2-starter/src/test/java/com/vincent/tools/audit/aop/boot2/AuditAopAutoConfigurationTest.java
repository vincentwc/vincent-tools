package com.vincent.tools.audit.aop.boot2;

import com.vincent.tools.audit.application.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuditAopAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuditAopAutoConfiguration.class));

    @Test
    void disabled_does_not_register_aspect() {
        contextRunner.withPropertyValues("vincent.audit.aop.enabled=false")
                .withUserConfiguration(AuditServiceConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AuditedAspect.class);
                });
    }

    @Test
    void enabled_registers_aspect_when_audit_service_present() {
        contextRunner.withUserConfiguration(AuditServiceConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AuditedAspect.class);
                    assertThat(context).hasSingleBean(AuditedSpelEvaluator.class);
                    assertThat(context).hasSingleBean(AuditedRecordPublisher.class);
                });
    }

    @Test
    void missing_audit_service_does_not_load_configuration() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AuditAopAutoConfiguration.class);
            assertThat(context).doesNotHaveBean(AuditedAspect.class);
        });
    }

    @Configuration
    static class AuditServiceConfiguration {
        @Bean
        AuditService auditService() {
            return mock(AuditService.class);
        }
    }
}
