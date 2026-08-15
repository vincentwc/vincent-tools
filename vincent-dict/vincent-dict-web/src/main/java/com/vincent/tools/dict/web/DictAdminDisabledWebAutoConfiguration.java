package com.vincent.tools.dict.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "javax.servlet.Filter")
@ConditionalOnProperty(prefix = "vincent.dict.admin", name = "enabled", havingValue = "false", matchIfMissing = true)
@EnableConfigurationProperties(DictAdminWebProperties.class)
public class DictAdminDisabledWebAutoConfiguration {

    @Bean
    public DictAdminDisabledResourceFilter dictAdminDisabledResourceFilter(DictAdminWebProperties properties) {
        return new DictAdminDisabledResourceFilter(properties.getBasePath());
    }
}
