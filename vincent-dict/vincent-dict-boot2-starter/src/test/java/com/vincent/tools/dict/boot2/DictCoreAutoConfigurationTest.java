package com.vincent.tools.dict.boot2;

import com.vincent.tools.dict.application.DictLimits;
import com.vincent.tools.dict.application.DictQueryService;
import com.vincent.tools.dict.application.SingleTenantProvider;
import com.vincent.tools.dict.application.admin.DefaultDictAdminService;
import com.vincent.tools.dict.application.admin.DictAdminPermission;
import com.vincent.tools.dict.application.admin.DictAdminService;
import com.vincent.tools.host.OperatorProvider;
import com.vincent.tools.host.PermissionProvider;
import com.vincent.tools.host.TenantProvider;
import com.vincent.tools.host.VincentPermission;
import com.vincent.tools.dict.application.port.DictAdminRepository;
import com.vincent.tools.dict.application.port.DictCache;
import com.vincent.tools.dict.application.port.NoopDictCache;
import com.vincent.tools.dict.application.port.TxRunner;
import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DictCoreAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DictCoreAutoConfiguration.class));

    @Test
    void disabled_does_not_create_query_service() {
        contextRunner.withPropertyValues("vincent.dict.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DictQueryService.class));
    }

    @Test
    void disabled_does_not_access_database() {
        contextRunner.withPropertyValues("vincent.dict.enabled=false")
                .withUserConfiguration(SingleInfrastructureConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DictQueryService.class);
                    assertThat(context.getBean(SchemaQueryDataSource.class).getConnectionCount()).isZero();
                });
    }

    @Test
    void uses_host_tenant_provider() {
        contextRunner.withUserConfiguration(SingleInfrastructureConfiguration.class)
                .withBean(TenantProvider.class, () -> new TenantProvider() {
                    @Override
                    public Optional<String> currentTenantId() {
                        return Optional.of("tenant-a");
                    }
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(DictQueryService.class);
                    assertThat(context.getBean(TenantProvider.class).currentTenantId()).contains("tenant-a");
                    assertThat(context.getBean(TenantProvider.class)).isNotInstanceOf(SingleTenantProvider.class);
                });
    }

    @Test
    void uses_single_tenant_provider_when_host_does_not_provide_one() {
        contextRunner.withUserConfiguration(SingleInfrastructureConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(DictQueryService.class);
                    assertThat(context).hasSingleBean(TenantProvider.class);
                    assertThat(context.getBean(TenantProvider.class)).isInstanceOf(SingleTenantProvider.class);
                    assertThat(context.getBean(TenantProvider.class).currentTenantId()).contains("0");
                });
    }

    @Test
    void uses_noop_cache_when_host_does_not_provide_one() {
        contextRunner.withUserConfiguration(SingleInfrastructureConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(DictCache.class);
                    assertThat(context.getBean(DictCache.class)).isInstanceOf(NoopDictCache.class);
                });
    }

    @Test
    void uses_host_dict_cache_when_present() {
        DictCache hostCache = new NoopDictCache();
        contextRunner.withUserConfiguration(SingleInfrastructureConfiguration.class)
                .withBean(DictCache.class, () -> hostCache)
                .run(context -> assertThat(context.getBean(DictCache.class)).isSameAs(hostCache));
    }

    @Test
    void maps_max_effective_items_into_dict_limits() {
        contextRunner.withUserConfiguration(SingleInfrastructureConfiguration.class)
                .withPropertyValues(
                        "vincent.dict.limits.default-items-per-dict=10",
                        "vincent.dict.limits.tenant-items-per-dict=10",
                        "vincent.dict.limits.max-effective-items=50")
                .run(context -> {
                    DictLimits limits = context.getBean(DictLimits.class);
                    assertThat(limits.getMaxEffectiveItems()).isEqualTo(50);
                    assertThat(limits.getDefaultItemsPerDict()).isEqualTo(10);
                    assertThat(limits.getTenantItemsPerDict()).isEqualTo(10);
                });
    }

    @Test
    void admin_disabled_does_not_wire_admin_service() {
        contextRunner.withUserConfiguration(SingleInfrastructureConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DictAdminService.class);
                    assertThat(context).doesNotHaveBean(DictAdminRepository.class);
                    assertThat(context).doesNotHaveBean(TxRunner.class);
                });
    }

    @Test
    void admin_enabled_without_operator_provider_fails() {
        contextRunner.withUserConfiguration(SingleInfrastructureConfiguration.class)
                .withPropertyValues("vincent.dict.admin.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void admin_enabled_wires_admin_service_repository_tx_and_utc_clock() {
        contextRunner.withUserConfiguration(SingleInfrastructureConfiguration.class, AdminAdaptersConfiguration.class)
                .withPropertyValues("vincent.dict.admin.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DictAdminService.class);
                    assertThat(context.getBean(DictAdminService.class)).isInstanceOf(DefaultDictAdminService.class);
                    assertThat(context).hasSingleBean(DictAdminRepository.class);
                    assertThat(context).hasSingleBean(TxRunner.class);
                    assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
                });
    }

    @Test
    void rejects_non_positive_max_effective_items() {
        contextRunner.withPropertyValues("vincent.dict.limits.max-effective-items=0")
                .run(context -> assertConfigurationInvalid(context));
    }

    @Test
    void rejects_non_positive_default_items_per_dict() {
        contextRunner.withPropertyValues("vincent.dict.limits.default-items-per-dict=0")
                .run(context -> assertConfigurationInvalid(context));
    }

    @Test
    void rejects_non_positive_tenant_items_per_dict() {
        contextRunner.withPropertyValues("vincent.dict.limits.tenant-items-per-dict=-1")
                .run(context -> assertConfigurationInvalid(context));
    }

    @Test
    void rejects_max_effective_items_below_item_budget() {
        contextRunner.withPropertyValues("vincent.dict.limits.max-effective-items=1500")
                .run(context -> assertConfigurationInvalid(context));
    }

    @Test
    void rejects_max_page_size_below_default_page_size() {
        contextRunner.withPropertyValues("vincent.dict.limits.max-page-size=10")
                .run(context -> assertConfigurationInvalid(context));
    }

    @Test
    void rejects_multiple_data_sources_without_primary_or_explicit_names() {
        contextRunner.withUserConfiguration(TwoDataSourcesNoPrimaryConfiguration.class)
                .run(context -> assertConfigurationInvalid(context));
    }

    @Test
    void rejects_explicit_name_for_missing_bean() {
        contextRunner.withUserConfiguration(SingleInfrastructureConfiguration.class)
                .withPropertyValues(
                        "vincent.dict.data-source-bean-name=missingDataSource",
                        "vincent.dict.sql-session-factory-bean-name=sqlSessionFactory",
                        "vincent.dict.transaction-manager-bean-name=transactionManager")
                .run(context -> assertConfigurationInvalid(context));
    }

    @Test
    void rejects_partial_infrastructure_bean_names() {
        contextRunner.withUserConfiguration(SingleInfrastructureConfiguration.class)
                .withPropertyValues("vincent.dict.data-source-bean-name=dataSource")
                .run(context -> assertConfigurationInvalid(context));
    }

    @Test
    void rejects_named_bean_with_wrong_type() {
        contextRunner.withUserConfiguration(SingleInfrastructureConfiguration.class)
                .withBean("notADataSource", String.class, () -> "not-a-data-source")
                .withPropertyValues(
                        "vincent.dict.data-source-bean-name=notADataSource",
                        "vincent.dict.sql-session-factory-bean-name=sqlSessionFactory",
                        "vincent.dict.transaction-manager-bean-name=transactionManager")
                .run(context -> assertConfigurationInvalid(context));
    }

    @Test
    void uses_unique_primary_data_source_among_many() {
        contextRunner.withUserConfiguration(PrimaryDataSourceConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(DictQueryService.class));
    }

    @Test
    void rejects_sql_session_factory_bound_to_another_data_source() {
        contextRunner.withUserConfiguration(MismatchedFactoryConfiguration.class)
                .withPropertyValues(
                        "vincent.dict.data-source-bean-name=dataSource",
                        "vincent.dict.sql-session-factory-bean-name=sqlSessionFactory",
                        "vincent.dict.transaction-manager-bean-name=transactionManager")
                .run(context -> assertConfigurationInvalid(context));
    }

    @Test
    void rejects_transaction_manager_bound_to_another_data_source() {
        contextRunner.withUserConfiguration(MismatchedTransactionManagerConfiguration.class)
                .withPropertyValues(
                        "vincent.dict.data-source-bean-name=dataSource",
                        "vincent.dict.sql-session-factory-bean-name=sqlSessionFactory",
                        "vincent.dict.transaction-manager-bean-name=transactionManager")
                .run(context -> assertConfigurationInvalid(context));
    }

    private static void assertConfigurationInvalid(
            org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        assertThat(context).hasFailed();
        assertThat(dictException(context).getCode()).isEqualTo(DictErrorCode.CONFIGURATION_INVALID);
    }

    private static DictException dictException(
            org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        Throwable current = context.getStartupFailure();
        while (current != null) {
            if (current instanceof DictException) {
                return (DictException) current;
            }
            current = current.getCause();
        }
        throw new AssertionError("DictException was not thrown", context.getStartupFailure());
    }

    @Configuration
    static class AdminAdaptersConfiguration {
        @Bean
        OperatorProvider operatorProvider() {
            return new OperatorProvider() {
                @Override
                public String currentOperatorId() {
                    return "operator";
                }
            };
        }

        @Bean
        PermissionProvider permissionProvider() {
            return new PermissionProvider() {
                @Override
                public boolean hasPermission(VincentPermission permission, Optional<String> targetTenantId) {
                    return true;
                }
            };
        }
    }

    @Configuration
    static class SingleInfrastructureConfiguration {
        @Bean
        SchemaQueryDataSource dataSource() {
            return new SchemaQueryDataSource();
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) {
            return sqlSessionFactoryFor(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @Configuration
    static class TwoDataSourcesNoPrimaryConfiguration {
        @Bean
        DataSource firstDataSource() {
            return new SchemaQueryDataSource();
        }

        @Bean
        DataSource secondDataSource() {
            return new SchemaQueryDataSource();
        }
    }

    @Configuration
    static class PrimaryDataSourceConfiguration {
        @Bean
        @Primary
        DataSource primaryDataSource() {
            return new SchemaQueryDataSource();
        }

        @Bean
        DataSource secondaryDataSource() {
            return new SchemaQueryDataSource();
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource primaryDataSource) {
            return sqlSessionFactoryFor(primaryDataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource primaryDataSource) {
            return new DataSourceTransactionManager(primaryDataSource);
        }
    }

    @Configuration
    static class MismatchedFactoryConfiguration {
        @Bean
        DataSource dataSource() {
            return new SchemaQueryDataSource();
        }

        @Bean
        DataSource otherDataSource() {
            return new SchemaQueryDataSource();
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource otherDataSource) {
            return sqlSessionFactoryFor(otherDataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @Configuration
    static class MismatchedTransactionManagerConfiguration {
        @Bean
        DataSource dataSource() {
            return new SchemaQueryDataSource();
        }

        @Bean
        DataSource otherDataSource() {
            return new SchemaQueryDataSource();
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) {
            return sqlSessionFactoryFor(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource otherDataSource) {
            return new DataSourceTransactionManager(otherDataSource);
        }
    }

    private static SqlSessionFactory sqlSessionFactoryFor(DataSource dataSource) {
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setEnvironment(new Environment("dict-test", new JdbcTransactionFactory(), dataSource));
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    static final class SchemaQueryDataSource implements DataSource {
        private final AtomicInteger connectionCount = new AtomicInteger();
        private final List<String> tables;
        private final String schemaVersion;

        SchemaQueryDataSource() {
            this(Arrays.asList("vin_dict_meta", "vin_dict", "vin_dict_item"), "1");
        }

        SchemaQueryDataSource(List<String> tables, String schemaVersion) {
            this.tables = Collections.unmodifiableList(new ArrayList<String>(tables));
            this.schemaVersion = schemaVersion;
        }

        int getConnectionCount() {
            return connectionCount.get();
        }

        @Override
        public Connection getConnection() {
            connectionCount.incrementAndGet();
            return proxy(Connection.class, new ConnectionHandler(this));
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }
    }

    private static final class ConnectionHandler implements InvocationHandler {
        private final SchemaQueryDataSource dataSource;

        ConnectionHandler(SchemaQueryDataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("createStatement".equals(name)) {
                return proxy(Statement.class, new StatementHandler(dataSource));
            }
            if ("prepareStatement".equals(name)) {
                return proxy(Statement.class, new StatementHandler(dataSource, (String) args[0]));
            }
            if ("close".equals(name) || "commit".equals(name) || "rollback".equals(name)
                    || "setAutoCommit".equals(name) || "setReadOnly".equals(name)
                    || "setTransactionIsolation".equals(name) || "clearWarnings".equals(name)) {
                return null;
            }
            if ("isClosed".equals(name) || "isReadOnly".equals(name) || "getAutoCommit".equals(name)) {
                return Boolean.FALSE;
            }
            if ("isValid".equals(name)) {
                return Boolean.TRUE;
            }
            if ("getWarnings".equals(name) || "getMetaData".equals(name)) {
                return null;
            }
            if ("nativeSQL".equals(name)) {
                return args[0];
            }
            if ("unwrap".equals(name)) {
                return proxy;
            }
            if ("isWrapperFor".equals(name)) {
                return Boolean.FALSE;
            }
            if ("toString".equals(name)) {
                return "SchemaQueryConnection";
            }
            if ("hashCode".equals(name)) {
                return Integer.valueOf(System.identityHashCode(proxy));
            }
            if ("equals".equals(name)) {
                return Boolean.valueOf(proxy == args[0]);
            }
            throw new UnsupportedOperationException(name);
        }
    }

    private static final class StatementHandler implements InvocationHandler {
        private final SchemaQueryDataSource dataSource;
        private final String preparedSql;

        StatementHandler(SchemaQueryDataSource dataSource) {
            this(dataSource, null);
        }

        StatementHandler(SchemaQueryDataSource dataSource, String preparedSql) {
            this.dataSource = dataSource;
            this.preparedSql = preparedSql;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("executeQuery".equals(name)) {
                return resultSetFor(args != null && args.length > 0 ? (String) args[0] : preparedSql);
            }
            if ("execute".equals(name)) {
                return Boolean.FALSE;
            }
            if ("close".equals(name) || "setFetchSize".equals(name) || "setMaxRows".equals(name)
                    || "setQueryTimeout".equals(name) || "clearWarnings".equals(name)
                    || "setString".equals(name) || "setInt".equals(name) || "setLong".equals(name)
                    || "setObject".equals(name)) {
                return null;
            }
            if ("getConnection".equals(name)) {
                return dataSource.getConnection();
            }
            if ("getWarnings".equals(name) || "getResultSet".equals(name)) {
                return null;
            }
            if ("getUpdateCount".equals(name)) {
                return Integer.valueOf(-1);
            }
            if ("getMoreResults".equals(name)) {
                return Boolean.FALSE;
            }
            if ("unwrap".equals(name)) {
                return proxy;
            }
            if ("isWrapperFor".equals(name) || "isClosed".equals(name)) {
                return Boolean.FALSE;
            }
            if ("toString".equals(name)) {
                return "SchemaQueryStatement";
            }
            if ("hashCode".equals(name)) {
                return Integer.valueOf(System.identityHashCode(proxy));
            }
            if ("equals".equals(name)) {
                return Boolean.valueOf(proxy == args[0]);
            }
            throw new UnsupportedOperationException(name);
        }

        private ResultSet resultSetFor(String sql) {
            String normalized = sql == null ? "" : sql.toLowerCase();
            if (normalized.indexOf("information_schema") >= 0) {
                return proxy(ResultSet.class, new ResultSetHandler(dataSource.tables, "TABLE_NAME"));
            }
            if (normalized.indexOf("vin_dict_meta") >= 0) {
                List<String> versions = dataSource.schemaVersion == null
                        ? Collections.<String>emptyList()
                        : Collections.singletonList(dataSource.schemaVersion);
                return proxy(ResultSet.class, new ResultSetHandler(versions, "schema_version"));
            }
            return proxy(ResultSet.class, new ResultSetHandler(Collections.<String>emptyList(), "value"));
        }
    }

    private static final class ResultSetHandler implements InvocationHandler {
        private final List<String> values;
        private final String column;
        private int index = -1;

        ResultSetHandler(List<String> values, String column) {
            this.values = values;
            this.column = column;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("next".equals(name)) {
                index++;
                return Boolean.valueOf(index < values.size());
            }
            if ("getString".equals(name) || "getObject".equals(name)) {
                return currentValue(args[0]);
            }
            if ("close".equals(name) || "beforeFirst".equals(name)) {
                return null;
            }
            if ("wasNull".equals(name) || "isClosed".equals(name)) {
                return Boolean.FALSE;
            }
            if ("findColumn".equals(name)) {
                return Integer.valueOf(1);
            }
            if ("unwrap".equals(name)) {
                return proxy;
            }
            if ("isWrapperFor".equals(name)) {
                return Boolean.FALSE;
            }
            if ("toString".equals(name)) {
                return "SchemaQueryResultSet";
            }
            if ("hashCode".equals(name)) {
                return Integer.valueOf(System.identityHashCode(proxy));
            }
            if ("equals".equals(name)) {
                return Boolean.valueOf(proxy == args[0]);
            }
            throw new UnsupportedOperationException(name);
        }

        private String currentValue(Object columnIdent) {
            if (columnIdent instanceof Integer) {
                return values.get(index);
            }
            String requested = String.valueOf(columnIdent);
            if (column.equalsIgnoreCase(requested)) {
                return values.get(index);
            }
            throw new UnsupportedOperationException("column " + requested);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
