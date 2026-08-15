package com.vincent.tools.audit.boot2;

import com.vincent.tools.common.core.infrastructure.InfrastructureBeanNames;
import com.vincent.tools.common.core.infrastructure.InfrastructureConfigurationException;
import com.vincent.tools.common.core.infrastructure.VincentInfrastructureResolver;
import com.vincent.tools.audit.domain.AuditErrorCode;
import com.vincent.tools.audit.domain.AuditException;
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

public class AuditInfrastructureResolver implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {
    static final String MAPPER_PACKAGE = "com.vincent.tools.audit.infra.mybatis.mapper";
    static final String MAPPER_SCANNER_BEAN_NAME = "vincentAuditMapperScannerConfigurer";

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
        AuditProperties properties = bindProperties(env);
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
            throw toAuditException(ex);
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

    private void resolveNames(ConfigurableListableBeanFactory beanFactory, AuditProperties properties) {
        try {
            this.beanNames = delegate.resolve(beanFactory,
                    optionalBeanName(properties.getDataSourceBeanName()),
                    optionalBeanName(properties.getSqlSessionFactoryBeanName()),
                    optionalBeanName(properties.getTransactionManagerBeanName()));
        } catch (InfrastructureConfigurationException ex) {
            throw toAuditException(ex);
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

    private static AuditException toAuditException(InfrastructureConfigurationException ex) {
        return new AuditException(AuditErrorCode.CONFIGURATION_INVALID, ex.getMessage());
    }

    private AuditProperties bindProperties(Environment environment) {
        return Binder.get(environment).bind("vincent.audit", AuditProperties.class).orElseGet(AuditProperties::new);
    }

    private Environment resolveEnvironment(ConfigurableListableBeanFactory beanFactory) {
        if (environment != null) {
            return environment;
        }
        return beanFactory.getBean(Environment.class);
    }

    private boolean isEnabled(Environment environment) {
        Boolean enabled = environment.getProperty("vincent.audit.enabled", Boolean.class);
        return enabled == null || enabled.booleanValue();
    }
}
