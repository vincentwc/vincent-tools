package com.vincent.tools.audit.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "javax.servlet.Filter")
@ConditionalOnProperty(prefix = "vincent.audit.admin", name = "enabled", havingValue = "false", matchIfMissing = true)
@EnableConfigurationProperties(AuditAdminWebProperties.class)
public class AuditAdminDisabledWebAutoConfiguration {

    @Bean
    public AuditAdminDisabledResourceFilter auditAdminDisabledResourceFilter(AuditAdminWebProperties properties) {
        return new AuditAdminDisabledResourceFilter(properties.getBasePath());
    }
}
