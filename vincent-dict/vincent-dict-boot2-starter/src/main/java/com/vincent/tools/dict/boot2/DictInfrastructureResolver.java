package com.vincent.tools.dict.boot2;

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
import org.springframework.transaction.support.ResourceTransactionManager;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

public class DictInfrastructureResolver implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {
    static final String MAPPER_PACKAGE = "com.vincent.tools.dict.infra.mybatis.mapper";
    static final String MAPPER_SCANNER_BEAN_NAME = "vincentDictMapperScannerConfigurer";

    private Environment environment;
    private String dataSourceBeanName;
    private String sqlSessionFactoryBeanName;
    private String transactionManagerBeanName;

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
        DataSource dataSource = getDataSource(context);
        SqlSessionFactory sqlSessionFactory = getSqlSessionFactory(context);
        PlatformTransactionManager transactionManager = getTransactionManager(context);
        org.apache.ibatis.mapping.Environment sessionEnvironment = sqlSessionFactory.getConfiguration().getEnvironment();
        if (sessionEnvironment == null || sessionEnvironment.getDataSource() != dataSource) {
            throw new DictException(DictErrorCode.CONFIGURATION_INVALID,
                    "SqlSessionFactory is not bound to the selected DataSource");
        }
        if (!(transactionManager instanceof ResourceTransactionManager)
                || ((ResourceTransactionManager) transactionManager).getResourceFactory() != dataSource) {
            throw new DictException(DictErrorCode.CONFIGURATION_INVALID,
                    "transaction manager is not bound to the selected DataSource");
        }
    }

    public DataSource getDataSource(ApplicationContext context) {
        return context.getBean(dataSourceBeanName, DataSource.class);
    }

    public SqlSessionFactory getSqlSessionFactory(ApplicationContext context) {
        return context.getBean(sqlSessionFactoryBeanName, SqlSessionFactory.class);
    }

    public PlatformTransactionManager getTransactionManager(ApplicationContext context) {
        return context.getBean(transactionManagerBeanName, PlatformTransactionManager.class);
    }

    private void resolveNames(ConfigurableListableBeanFactory beanFactory, DictProperties properties) {
        if (properties.hasAllInfrastructureBeanNames()) {
            this.dataSourceBeanName = requireType(beanFactory, properties.getDataSourceBeanName(), DataSource.class,
                    "DataSource");
            this.sqlSessionFactoryBeanName = requireType(beanFactory, properties.getSqlSessionFactoryBeanName(),
                    SqlSessionFactory.class, "SqlSessionFactory");
            this.transactionManagerBeanName = requireType(beanFactory, properties.getTransactionManagerBeanName(),
                    PlatformTransactionManager.class, "PlatformTransactionManager");
            return;
        }
        this.dataSourceBeanName = uniqueOrPrimary(beanFactory, DataSource.class, "DataSource");
        this.sqlSessionFactoryBeanName = uniqueOrPrimary(beanFactory, SqlSessionFactory.class, "SqlSessionFactory");
        this.transactionManagerBeanName = uniqueOrPrimary(beanFactory, PlatformTransactionManager.class,
                "PlatformTransactionManager");
    }

    private void registerMapperScanner(BeanDefinitionRegistry registry) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(MapperScannerConfigurer.class);
        builder.addPropertyValue("basePackage", MAPPER_PACKAGE);
        builder.addPropertyValue("sqlSessionFactoryBeanName", sqlSessionFactoryBeanName);
        registry.registerBeanDefinition(MAPPER_SCANNER_BEAN_NAME, builder.getBeanDefinition());
    }

    private String uniqueOrPrimary(ConfigurableListableBeanFactory beanFactory, Class<?> type, String label) {
        List<String> candidates = candidateNames(beanFactory, type);
        if (candidates.isEmpty()) {
            throw new DictException(DictErrorCode.CONFIGURATION_INVALID, "no " + label + " bean");
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        List<String> primaries = new ArrayList<String>();
        for (int index = 0; index < candidates.size(); index++) {
            String name = candidates.get(index);
            if (isPrimary(beanFactory, name)) {
                primaries.add(name);
            }
        }
        if (primaries.size() == 1) {
            return primaries.get(0);
        }
        throw new DictException(DictErrorCode.CONFIGURATION_INVALID,
                "multiple " + label + " beans without unique @Primary");
    }

    private String requireType(ConfigurableListableBeanFactory beanFactory, String name, Class<?> type, String label) {
        if (!beanFactory.containsBean(name)) {
            throw new DictException(DictErrorCode.CONFIGURATION_INVALID, "missing " + label + " bean '" + name + "'");
        }
        Class<?> actual = beanFactory.getType(name);
        if (actual == null || !type.isAssignableFrom(actual)) {
            throw new DictException(DictErrorCode.CONFIGURATION_INVALID, "bean '" + name + "' is not a " + label);
        }
        return name;
    }

    private List<String> candidateNames(ConfigurableListableBeanFactory beanFactory, Class<?> type) {
        String[] names = beanFactory.getBeanNamesForType(type, false, false);
        List<String> candidates = new ArrayList<String>();
        for (int index = 0; index < names.length; index++) {
            String name = names[index];
            if (name.startsWith("&") || name.startsWith("scopedTarget.")) {
                continue;
            }
            candidates.add(name);
        }
        return candidates;
    }

    private boolean isPrimary(ConfigurableListableBeanFactory beanFactory, String name) {
        return beanFactory.containsBeanDefinition(name) && beanFactory.getMergedBeanDefinition(name).isPrimary();
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
