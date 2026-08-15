package com.vincent.tools.dict.boot2;

import com.vincent.tools.dict.application.DefaultDictQueryService;
import com.vincent.tools.dict.application.DictLimits;
import com.vincent.tools.dict.application.DictQueryService;
import com.vincent.tools.dict.application.SingleTenantProvider;
import com.vincent.tools.dict.application.admin.DefaultDictAdminService;
import com.vincent.tools.dict.application.admin.DictAdminService;
import com.vincent.tools.dict.application.admin.TenantDirectory;
import com.vincent.tools.host.OperatorProvider;
import com.vincent.tools.host.PermissionProvider;
import com.vincent.tools.host.TenantProvider;
import com.vincent.tools.dict.application.port.DictAdminRepository;
import com.vincent.tools.dict.application.port.DictCache;
import com.vincent.tools.dict.application.port.DictQueryRepository;
import com.vincent.tools.dict.application.port.NoopDictCache;
import com.vincent.tools.dict.application.port.TxRunner;
import com.vincent.tools.dict.infra.mybatis.MybatisDictAdminRepository;
import com.vincent.tools.dict.infra.mybatis.MybatisDictQueryRepository;
import com.vincent.tools.dict.infra.mybatis.SpringTxRunner;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

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
            return new DictLimits(
                    properties.getLimits().getMaxEffectiveItems(),
                    properties.getLimits().getDefaultItemsPerDict(),
                    properties.getLimits().getTenantItemsPerDict());
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

        @Configuration
        @ConditionalOnProperty(prefix = "vincent.dict.admin", name = "enabled", havingValue = "true")
        static class AdminConfiguration {
            @Bean
            @ConditionalOnMissingBean(DictAdminRepository.class)
            public DictAdminRepository dictAdminRepository(ObjectProvider<DictMapper> dictMappers,
                                                           ObjectProvider<DictItemMapper> dictItemMappers) {
                return new MybatisDictAdminRepository(dictMappers.getObject(), dictItemMappers.getObject());
            }

            @Bean
            @ConditionalOnMissingBean(TxRunner.class)
            public TxRunner dictTxRunner(DictInfrastructureResolver resolver, ApplicationContext context) {
                return new SpringTxRunner(new TransactionTemplate(resolver.getTransactionManager(context)));
            }

            @Bean
            @ConditionalOnMissingBean(Clock.class)
            public Clock dictClock() {
                return Clock.systemUTC();
            }

            @Bean
            @ConditionalOnMissingBean(DictAdminService.class)
            public DictAdminService dictAdminService(DictAdminRepository repository,
                                                     TxRunner txRunner,
                                                     ObjectProvider<DictCache> caches,
                                                     OperatorProvider operatorProvider,
                                                     PermissionProvider permissionProvider,
                                                     ObjectProvider<TenantDirectory> tenantDirectories,
                                                     DictLimits limits,
                                                     Clock clock) {
                return new DefaultDictAdminService(
                        repository,
                        txRunner,
                        caches.getIfAvailable(NoopDictCache::new),
                        operatorProvider,
                        permissionProvider,
                        tenantDirectories.getIfAvailable(),
                        limits,
                        clock);
            }
        }
    }
}
