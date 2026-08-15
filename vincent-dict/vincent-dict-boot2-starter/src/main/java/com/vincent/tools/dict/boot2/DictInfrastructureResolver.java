package com.vincent.tools.dict.boot2;

import com.vincent.tools.common.core.infrastructure.InfrastructureBeanNames;
import com.vincent.tools.common.core.infrastructure.InfrastructureConfigurationException;
import com.vincent.tools.common.core.infrastructure.VincentInfrastructureResolver;
import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Optional;

public class DictInfrastructureResolver implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {
    static final String MAPPER_PACKAGE = "com.vincent.tools.dict.infra.mybatis.mapper";
    static final String MAPPER_SCANNER_BEAN_NAME = "vincentDictMapperScannerConfigurer";

    private final VincentInfrastructureResolver delegate = new VincentInfrastructureResolver();

    private Environment environment;
    private InfrastructureBeanNames beanNames;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        ConfigurableListableBeanFactory beanFactory = (ConfigurableListableBeanFactory) registry;
        Environment env = resolveEnvironment(beanFactory);
        if (!isEnabled(env)) {
            return;
        }
        DictProperties properties = bindProperties(env);
        properties.validate();
        resolveNames(beanFactory, properties);
        registerMapperScanner(registry);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    public void validateMatching(ApplicationContext context) {
        try {
            delegate.validateMatching(context, beanNames);
        } catch (InfrastructureConfigurationException ex) {
            throw toDictException(ex);
        }
    }

    public DataSource getDataSource(ApplicationContext context) {
        return delegate.getDataSource(context, beanNames);
    }

    public SqlSessionFactory getSqlSessionFactory(ApplicationContext context) {
        return delegate.getSqlSessionFactory(context, beanNames);
    }

    public PlatformTransactionManager getTransactionManager(ApplicationContext context) {
        return delegate.getTransactionManager(context, beanNames);
    }

    private void resolveNames(ConfigurableListableBeanFactory beanFactory, DictProperties properties) {
        try {
            this.beanNames = delegate.resolve(beanFactory,
                    optionalBeanName(properties.getDataSourceBeanName()),
                    optionalBeanName(properties.getSqlSessionFactoryBeanName()),
                    optionalBeanName(properties.getTransactionManagerBeanName()));
        } catch (InfrastructureConfigurationException ex) {
            throw toDictException(ex);
        }
    }

    private void registerMapperScanner(BeanDefinitionRegistry registry) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(MapperScannerConfigurer.class);
        builder.addPropertyValue("basePackage", MAPPER_PACKAGE);
        builder.addPropertyValue("sqlSessionFactoryBeanName", beanNames.sqlSessionFactoryBeanName);
        registry.registerBeanDefinition(MAPPER_SCANNER_BEAN_NAME, builder.getBeanDefinition());
    }

    private static Optional<String> optionalBeanName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(name.trim());
    }

    private static DictException toDictException(InfrastructureConfigurationException ex) {
        return new DictException(DictErrorCode.CONFIGURATION_INVALID, ex.getMessage());
    }

    private DictProperties bindProperties(Environment environment) {
        return Binder.get(environment).bind("vincent.dict", DictProperties.class).orElseGet(DictProperties::new);
    }

    private Environment resolveEnvironment(ConfigurableListableBeanFactory beanFactory) {
        if (environment != null) {
            return environment;
        }
        return beanFactory.getBean(Environment.class);
    }

    private boolean isEnabled(Environment environment) {
        Boolean enabled = environment.getProperty("vincent.dict.enabled", Boolean.class);
        return enabled == null || enabled.booleanValue();
    }
}
