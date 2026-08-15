package com.vincent.tools.common.core.infrastructure;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VincentInfrastructureResolverTest {
    private final VincentInfrastructureResolver resolver = new VincentInfrastructureResolver();

    @Test
    void rejects_when_no_data_source() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.refresh();

        assertThatThrownBy(() -> resolve(context))
                .isInstanceOf(InfrastructureConfigurationException.class)
                .satisfies(ex -> {
                    InfrastructureConfigurationException exception = (InfrastructureConfigurationException) ex;
                    assertThat(exception.errorCode())
                            .isEqualTo(InfrastructureConfigurationException.CONFIGURATION_INVALID);
                    assertThat(exception.getMessage()).contains("no DataSource bean");
                });
    }

    @Test
    void rejects_multiple_data_sources_without_primary() {
        StaticApplicationContext context = new StaticApplicationContext();
        registerSingleton(context, "firstDataSource", newStubDataSource());
        registerSingleton(context, "secondDataSource", newStubDataSource());
        context.refresh();

        assertThatThrownBy(() -> resolve(context))
                .isInstanceOf(InfrastructureConfigurationException.class)
                .satisfies(ex -> {
                    InfrastructureConfigurationException exception = (InfrastructureConfigurationException) ex;
                    assertThat(exception.errorCode())
                            .isEqualTo(InfrastructureConfigurationException.CONFIGURATION_INVALID);
                    assertThat(exception.getMessage())
                            .contains("multiple DataSource beans without unique @Primary");
                });
    }

    @Test
    void resolves_unique_infrastructure_beans() {
        StaticApplicationContext context = new StaticApplicationContext();
        DataSource dataSource = newStubDataSource();
        registerSingleton(context, "dataSource", dataSource);
        registerSingleton(context, "sqlSessionFactory", sqlSessionFactoryFor(dataSource));
        registerSingleton(context, "transactionManager", new DataSourceTransactionManager(dataSource));
        context.refresh();

        InfrastructureBeanNames names = resolve(context);

        assertThat(names.dataSourceBeanName).isEqualTo("dataSource");
        assertThat(names.sqlSessionFactoryBeanName).isEqualTo("sqlSessionFactory");
        assertThat(names.transactionManagerBeanName).isEqualTo("transactionManager");
        assertThat(resolver.getDataSource(context, names)).isSameAs(dataSource);
        resolver.validateMatching(context, names);
    }

    @Test
    void resolves_explicit_bean_names() {
        StaticApplicationContext context = new StaticApplicationContext();
        DataSource dataSource = newStubDataSource();
        registerSingleton(context, "customDataSource", dataSource);
        registerSingleton(context, "customSqlSessionFactory", sqlSessionFactoryFor(dataSource));
        registerSingleton(context, "customTransactionManager", new DataSourceTransactionManager(dataSource));
        context.refresh();

        InfrastructureBeanNames names = resolver.resolve(beanFactory(context),
                Optional.of("customDataSource"),
                Optional.of("customSqlSessionFactory"),
                Optional.of("customTransactionManager"));

        assertThat(names.dataSourceBeanName).isEqualTo("customDataSource");
        assertThat(names.sqlSessionFactoryBeanName).isEqualTo("customSqlSessionFactory");
        assertThat(names.transactionManagerBeanName).isEqualTo("customTransactionManager");
        resolver.validateMatching(context, names);
    }

    private InfrastructureBeanNames resolve(StaticApplicationContext context) {
        return resolver.resolve(beanFactory(context), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static ConfigurableListableBeanFactory beanFactory(StaticApplicationContext context) {
        return context.getBeanFactory();
    }

    private static void registerSingleton(StaticApplicationContext context, String name, Object instance) {
        beanFactory(context).registerSingleton(name, instance);
    }

    private static DataSource newStubDataSource() {
        return new SingleConnectionDataSource("jdbc:h2:mem:vincent-infra-resolver;DB_CLOSE_DELAY=-1", true);
    }

    private static SqlSessionFactory sqlSessionFactoryFor(DataSource dataSource) {
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setEnvironment(new Environment("vincent-test", new JdbcTransactionFactory(), dataSource));
        return new SqlSessionFactoryBuilder().build(configuration);
    }
}
