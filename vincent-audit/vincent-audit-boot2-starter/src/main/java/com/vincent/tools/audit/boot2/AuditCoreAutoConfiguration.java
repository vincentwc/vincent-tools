package com.vincent.tools.audit.boot2;

import com.vincent.tools.audit.application.AuditContextProvider;
import com.vincent.tools.audit.application.AuditLimits;
import com.vincent.tools.audit.application.AuditService;
import com.vincent.tools.audit.application.DefaultAuditService;
import com.vincent.tools.audit.application.port.AuditRepository;
import com.vincent.tools.audit.infra.mybatis.MybatisAuditRepository;
import com.vincent.tools.audit.infra.mybatis.mapper.AuditLogMapper;
import com.vincent.tools.host.OperatorProvider;
import com.vincent.tools.host.PermissionProvider;
import com.vincent.tools.host.TenantProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AuditProperties.class)
@AutoConfigureAfter(name = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
})
public class AuditCoreAutoConfiguration {

    @Bean
    public static AuditInfrastructureResolver auditInfrastructureResolver() {
        return new AuditInfrastructureResolver();
    }

    @Configuration
    @ConditionalOnProperty(prefix = "vincent.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class EnabledConfiguration {

        @Bean
        @ConditionalOnMissingBean(AuditLimits.class)
        public AuditLimits auditLimits(AuditProperties properties) {
            return new AuditLimits(
                    properties.getLimits().getDefaultPageSize(),
                    properties.getLimits().getMaxPageSize());
        }

        @Bean
        @ConditionalOnMissingBean(AuditRepository.class)
        public AuditRepository auditRepository(ObjectProvider<AuditLogMapper> auditLogMappers) {
            return new MybatisAuditRepository(auditLogMappers.getObject());
        }

        @Bean
        public AuditSchemaValidator auditSchemaValidator(AuditInfrastructureResolver resolver,
                                                       ApplicationContext context) {
            resolver.validateMatching(context);
            AuditSchemaValidator validator = new AuditSchemaValidator();
            validator.validate(resolver.getDataSource(context));
            return validator;
        }

        @Bean
        @ConditionalOnMissingBean(Clock.class)
        public Clock auditClock() {
            return Clock.systemUTC();
        }

        @Bean
        @ConditionalOnMissingBean(AuditService.class)
        public AuditService auditService(AuditRepository repository,
                                         OperatorProvider operatorProvider,
                                         PermissionProvider permissionProvider,
                                         ObjectProvider<TenantProvider> tenantProviders,
                                         ObjectProvider<AuditContextProvider> contextProviders,
                                         AuditLimits limits,
                                         AuditProperties properties,
                                         Clock clock,
                                         AuditSchemaValidator schemaValidator) {
            return new DefaultAuditService(
                    repository,
                    operatorProvider,
                    permissionProvider,
                    tenantProviders.getIfAvailable(),
                    contextProviders.getIfAvailable(),
                    limits,
                    clock,
                    properties.isFailFast());
        }
    }
}
