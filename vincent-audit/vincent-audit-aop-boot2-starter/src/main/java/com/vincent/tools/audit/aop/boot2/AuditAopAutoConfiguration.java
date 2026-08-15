package com.vincent.tools.audit.aop.boot2;

import com.vincent.tools.audit.aop.AuditPayloadExtractor;
import com.vincent.tools.audit.application.AuditService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import org.springframework.beans.factory.ObjectProvider;

@Configuration
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
@ConditionalOnBean(AuditService.class)
@AutoConfigureAfter(name = "com.vincent.tools.audit.boot2.AuditCoreAutoConfiguration")
@EnableConfigurationProperties(AuditAopProperties.class)
@EnableAspectJAutoProxy
public class AuditAopAutoConfiguration {

    @Configuration
    @ConditionalOnProperty(prefix = "vincent.audit.aop", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class EnabledConfiguration {

        @Bean
        public AuditedSpelEvaluator auditedSpelEvaluator() {
            return new AuditedSpelEvaluator();
        }

        @Bean
        public AuditedRecordPublisher auditedRecordPublisher(AuditService auditService) {
            return new AuditedRecordPublisher(auditService);
        }

        @Bean
        public AuditedAspect auditedAspect(AuditedRecordPublisher recordPublisher,
                                           AuditedSpelEvaluator spelEvaluator,
                                           ObjectProvider<AuditPayloadExtractor> extractors) {
            java.util.List<AuditPayloadExtractor> resolved = new java.util.ArrayList<AuditPayloadExtractor>();
            for (AuditPayloadExtractor extractor : extractors) {
                resolved.add(extractor);
            }
            return new AuditedAspect(recordPublisher, spelEvaluator, resolved);
        }
    }
}
