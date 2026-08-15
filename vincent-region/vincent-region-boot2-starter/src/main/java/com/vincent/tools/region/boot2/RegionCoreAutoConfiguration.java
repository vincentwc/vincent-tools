package com.vincent.tools.region.boot2;

import com.vincent.tools.region.application.DefaultRegionQueryService;
import com.vincent.tools.region.application.RegionQueryService;
import com.vincent.tools.region.application.port.RegionRepository;
import com.vincent.tools.region.infra.mybatis.MybatisRegionRepository;
import com.vincent.tools.region.infra.mybatis.mapper.RegionMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RegionProperties.class)
@AutoConfigureAfter(name = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
})
public class RegionCoreAutoConfiguration {

    @Bean
    public static RegionInfrastructureResolver regionInfrastructureResolver() {
        return new RegionInfrastructureResolver();
    }

    @Configuration
    @ConditionalOnProperty(prefix = "vincent.region", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class EnabledConfiguration {

        @Bean
        @ConditionalOnMissingBean(RegionRepository.class)
        public RegionRepository regionRepository(ObjectProvider<RegionMapper> regionMappers) {
            return new MybatisRegionRepository(regionMappers.getObject());
        }

        @Bean
        public RegionSchemaValidator regionSchemaValidator(RegionInfrastructureResolver resolver,
                                                           ApplicationContext context) {
            resolver.validateMatching(context);
            RegionSchemaValidator validator = new RegionSchemaValidator();
            validator.validate(resolver.getDataSource(context));
            return validator;
        }

        @Bean
        @ConditionalOnMissingBean(RegionQueryService.class)
        public RegionQueryService regionQueryService(RegionRepository repository,
                                                     RegionSchemaValidator schemaValidator) {
            return new DefaultRegionQueryService(repository);
        }
    }
}
