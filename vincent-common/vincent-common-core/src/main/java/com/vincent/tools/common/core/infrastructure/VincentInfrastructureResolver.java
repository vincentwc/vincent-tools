package com.vincent.tools.common.core.infrastructure;

import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.ResourceTransactionManager;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class VincentInfrastructureResolver {
    public InfrastructureBeanNames resolve(ConfigurableListableBeanFactory beanFactory,
            Optional<String> dataSourceBeanName,
            Optional<String> sqlSessionFactoryBeanName,
            Optional<String> transactionManagerBeanName) {
        if (hasAny(dataSourceBeanName, sqlSessionFactoryBeanName, transactionManagerBeanName)) {
            if (!hasAll(dataSourceBeanName, sqlSessionFactoryBeanName, transactionManagerBeanName)) {
                throw new InfrastructureConfigurationException(
                        InfrastructureConfigurationException.CONFIGURATION_INVALID,
                        "data-source, sql-session-factory and transaction-manager bean names must be set together");
            }
            return new InfrastructureBeanNames(
                    requireType(beanFactory, dataSourceBeanName.get(), DataSource.class, "DataSource"),
                    requireType(beanFactory, sqlSessionFactoryBeanName.get(), SqlSessionFactory.class,
                            "SqlSessionFactory"),
                    requireType(beanFactory, transactionManagerBeanName.get(), PlatformTransactionManager.class,
                            "PlatformTransactionManager"));
        }
        return new InfrastructureBeanNames(
                uniqueOrPrimary(beanFactory, DataSource.class, "DataSource"),
                uniqueOrPrimary(beanFactory, SqlSessionFactory.class, "SqlSessionFactory"),
                uniqueOrPrimary(beanFactory, PlatformTransactionManager.class, "PlatformTransactionManager"));
    }

    public void validateMatching(ApplicationContext context, InfrastructureBeanNames names) {
        DataSource dataSource = getDataSource(context, names);
        SqlSessionFactory sqlSessionFactory = getSqlSessionFactory(context, names);
        PlatformTransactionManager transactionManager = getTransactionManager(context, names);
        org.apache.ibatis.mapping.Environment sessionEnvironment = sqlSessionFactory.getConfiguration().getEnvironment();
        if (sessionEnvironment == null || sessionEnvironment.getDataSource() != dataSource) {
            throw new InfrastructureConfigurationException(
                    InfrastructureConfigurationException.CONFIGURATION_INVALID,
                    "SqlSessionFactory is not bound to the selected DataSource");
        }
        if (!(transactionManager instanceof ResourceTransactionManager)
                || ((ResourceTransactionManager) transactionManager).getResourceFactory() != dataSource) {
            throw new InfrastructureConfigurationException(
                    InfrastructureConfigurationException.CONFIGURATION_INVALID,
                    "transaction manager is not bound to the selected DataSource");
        }
    }

    public DataSource getDataSource(ApplicationContext context, InfrastructureBeanNames names) {
        return context.getBean(names.dataSourceBeanName, DataSource.class);
    }

    public SqlSessionFactory getSqlSessionFactory(ApplicationContext context, InfrastructureBeanNames names) {
        return context.getBean(names.sqlSessionFactoryBeanName, SqlSessionFactory.class);
    }

    public PlatformTransactionManager getTransactionManager(ApplicationContext context, InfrastructureBeanNames names) {
        return context.getBean(names.transactionManagerBeanName, PlatformTransactionManager.class);
    }

    private static boolean hasAny(Optional<String> dataSourceBeanName, Optional<String> sqlSessionFactoryBeanName,
            Optional<String> transactionManagerBeanName) {
        return dataSourceBeanName.isPresent() || sqlSessionFactoryBeanName.isPresent()
                || transactionManagerBeanName.isPresent();
    }

    private static boolean hasAll(Optional<String> dataSourceBeanName, Optional<String> sqlSessionFactoryBeanName,
            Optional<String> transactionManagerBeanName) {
        return dataSourceBeanName.isPresent() && sqlSessionFactoryBeanName.isPresent()
                && transactionManagerBeanName.isPresent();
    }

    private String uniqueOrPrimary(ConfigurableListableBeanFactory beanFactory, Class<?> type, String label) {
        List<String> candidates = candidateNames(beanFactory, type);
        if (candidates.isEmpty()) {
            throw new InfrastructureConfigurationException(
                    InfrastructureConfigurationException.CONFIGURATION_INVALID, "no " + label + " bean");
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
        throw new InfrastructureConfigurationException(
                InfrastructureConfigurationException.CONFIGURATION_INVALID,
                "multiple " + label + " beans without unique @Primary");
    }

    private String requireType(ConfigurableListableBeanFactory beanFactory, String name, Class<?> type, String label) {
        if (!beanFactory.containsBean(name)) {
            throw new InfrastructureConfigurationException(
                    InfrastructureConfigurationException.CONFIGURATION_INVALID,
                    "missing " + label + " bean '" + name + "'");
        }
        Class<?> actual = beanFactory.getType(name);
        if (actual == null || !type.isAssignableFrom(actual)) {
            throw new InfrastructureConfigurationException(
                    InfrastructureConfigurationException.CONFIGURATION_INVALID,
                    "bean '" + name + "' is not a " + label);
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
}
