package com.vincent.tools.dict.boot2;

import com.vincent.tools.dict.application.DefaultDictQueryService;
import com.vincent.tools.dict.application.DictLimits;
import com.vincent.tools.dict.application.DictQueryService;
import com.vincent.tools.dict.application.SingleTenantProvider;
import com.vincent.tools.dict.application.TenantProvider;
import com.vincent.tools.dict.application.port.DictCache;
import com.vincent.tools.dict.application.port.DictQueryRepository;
import com.vincent.tools.dict.application.port.NoopDictCache;
import com.vincent.tools.dict.infra.mybatis.MybatisDictQueryRepository;
import com.vincent.tools.dict.infra.mybatis.mapper.DictItemMapper;
import com.vincent.tools.dict.infra.mybatis.mapper.DictMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DictProperties.class)
@AutoConfigureAfter(name = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
})
public class DictCoreAutoConfiguration {

    @Bean
    public static DictInfrastructureResolver dictInfrastructureResolver() {
        return new DictInfrastructureResolver();
    }

    @Configuration
    @ConditionalOnProperty(prefix = "vincent.dict", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class EnabledConfiguration {

        @Bean
        @ConditionalOnMissingBean(TenantProvider.class)
        public TenantProvider dictTenantProvider() {
            return new SingleTenantProvider();
        }

        @Bean
        @ConditionalOnMissingBean(DictCache.class)
        public DictCache dictCache() {
            return new NoopDictCache();
        }

        @Bean
        @ConditionalOnMissingBean(DictLimits.class)
        public DictLimits dictLimits(DictProperties properties) {
            return new DictLimits(properties.getLimits().getMaxEffectiveItems());
        }

        @Bean
        @ConditionalOnMissingBean(DictQueryRepository.class)
        public DictQueryRepository dictQueryRepository(ObjectProvider<DictMapper> dictMappers,
                                                       ObjectProvider<DictItemMapper> dictItemMappers) {
            return new MybatisDictQueryRepository(dictMappers.getObject(), dictItemMappers.getObject());
        }

        @Bean
        public DictSchemaValidator dictSchemaValidator(DictInfrastructureResolver resolver,
                                                       ApplicationContext context) {
            resolver.validateMatching(context);
            DictSchemaValidator validator = new DictSchemaValidator();
            validator.validate(resolver.getDataSource(context));
            return validator;
        }

        @Bean
        @ConditionalOnMissingBean(DictQueryService.class)
        public DictQueryService dictQueryService(DictQueryRepository repository,
                                                 ObjectProvider<TenantProvider> tenantProviders,
                                                 ObjectProvider<DictCache> caches,
                                                 DictLimits limits,
                                                 DictSchemaValidator schemaValidator) {
            return new DefaultDictQueryService(
                    repository,
                    tenantProviders.getIfAvailable(SingleTenantProvider::new),
                    caches.getIfAvailable(NoopDictCache::new),
                    limits);
        }
    }
}
