package com.vincent.tools.audit.boot2;

import com.vincent.tools.audit.application.AuditLimits;
import com.vincent.tools.audit.application.AuditService;
import com.vincent.tools.audit.application.DefaultAuditService;
import com.vincent.tools.audit.domain.AuditErrorCode;
import com.vincent.tools.audit.domain.AuditException;
import com.vincent.tools.host.OperatorProvider;
import com.vincent.tools.host.PermissionProvider;
import com.vincent.tools.host.VincentPermission;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

class AuditCoreAutoConfigurationTest {
    private static final List<String> AUDIT_TABLES =
            Arrays.asList("vin_audit_meta", "vin_audit_log");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuditCoreAutoConfiguration.class));

    @Test
    void disabled_does_not_create_audit_service() {
        contextRunner.withPropertyValues("vincent.audit.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AuditService.class));
    }

    @Test
    void disabled_does_not_access_database() {
        contextRunner.withPropertyValues("vincent.audit.enabled=false")
                .withUserConfiguration(SingleInfrastructureConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AuditService.class);
                    assertThat(context.getBean(SchemaQueryDataSource.class).getConnectionCount()).isZero();
                });
    }

    @Test
    void enabled_without_host_adapters_fails() {
        contextRunner.withUserConfiguration(SingleInfrastructureConfiguration.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabled_wires_audit_service_repository_and_utc_clock() {
        contextRunner.withUserConfiguration(SingleInfrastructureConfiguration.class, HostAdaptersConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AuditService.class);
                    assertThat(context.getBean(AuditService.class)).isInstanceOf(DefaultAuditService.class);
                    assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
                });
    }

    @Test
    void maps_page_limits_from_properties() {
        contextRunner.withUserConfiguration(SingleInfrastructureConfiguration.class, HostAdaptersConfiguration.class)
                .withPropertyValues(
                        "vincent.audit.limits.default-page-size=30",
                        "vincent.audit.limits.max-page-size=80")
                .run(context -> {
                    AuditLimits limits = context.getBean(AuditLimits.class);
                    assertThat(limits.getDefaultPageSize()).isEqualTo(30);
                    assertThat(limits.getMaxPageSize()).isEqualTo(80);
                });
    }

    @Test
    void rejects_max_page_size_below_default_page_size() {
        contextRunner.withPropertyValues("vincent.audit.limits.max-page-size=10")
                .run(context -> assertConfigurationInvalid(context));
    }

    @Test
    void rejects_multiple_data_sources_without_primary_or_explicit_names() {
        contextRunner.withUserConfiguration(TwoDataSourcesNoPrimaryConfiguration.class)
                .run(context -> assertConfigurationInvalid(context));
    }

    @Test
    void rejects_partial_infrastructure_bean_names() {
        contextRunner.withUserConfiguration(SingleInfrastructureConfiguration.class)
                .withPropertyValues("vincent.audit.data-source-bean-name=dataSource")
                .run(context -> assertConfigurationInvalid(context));
    }

    private static void assertConfigurationInvalid(
            org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        assertThat(context).hasFailed();
        assertThat(auditException(context).getCode()).isEqualTo(AuditErrorCode.CONFIGURATION_INVALID);
    }

    private static AuditException auditException(
            org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        Throwable current = context.getStartupFailure();
        while (current != null) {
            if (current instanceof AuditException) {
                return (AuditException) current;
            }
            current = current.getCause();
        }
        throw new AssertionError("AuditException was not thrown", context.getStartupFailure());
    }

    @Configuration
    static class HostAdaptersConfiguration {
        @Bean
        OperatorProvider operatorProvider() {
            return () -> "operator";
        }

        @Bean
        PermissionProvider permissionProvider() {
            return (VincentPermission permission, Optional<String> targetTenantId) -> true;
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

    private static SqlSessionFactory sqlSessionFactoryFor(DataSource dataSource) {
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setEnvironment(new Environment("audit-test", new JdbcTransactionFactory(), dataSource));
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    static final class SchemaQueryDataSource implements DataSource {
        private final AtomicInteger connectionCount = new AtomicInteger();
        private final List<String> tables;
        private final String schemaVersion;

        SchemaQueryDataSource() {
            this(AUDIT_TABLES, "1");
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
            if (normalized.indexOf("vin_audit_meta") >= 0) {
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
