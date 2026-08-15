package com.vincent.tools.dict.boot2;

import com.vincent.tools.dict.application.DictQueryService;
import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DictSchemaValidatorIT {
    private static final String INIT_SQL_PATH = "sql/mysql/1.0.0/001-init.sql";
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:5.7.44"))
                    .withUrlParam("useSSL", "false")
                    .withStartupTimeout(Duration.ofMinutes(3));

    @BeforeAll
    static void startDatabase() {
        MYSQL.start();
    }

    @AfterAll
    static void stopDatabase() {
        MYSQL.stop();
    }

    @BeforeEach
    void dropDictionaryTables() throws SQLException {
        execute(
                "DROP TABLE IF EXISTS vin_dict_item",
                "DROP TABLE IF EXISTS vin_dict",
                "DROP TABLE IF EXISTS vin_dict_meta");
    }

    @Test
    void missing_tables_fail_with_schema_missing() {
        runner().run(context -> {
            assertThat(context).hasFailed();
            DictException exception = dictException(context);
            assertThat(exception.getCode()).isEqualTo(DictErrorCode.SCHEMA_MISSING);
            assertThat(exception.getMessage()).contains(INIT_SQL_PATH);
        });
    }

    @Test
    void valid_schema_starts_and_exposes_query_service() throws Exception {
        applyInitSql();
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DictQueryService.class);
        });
    }

    @Test
    void version_mismatch_fails_with_schema_version_mismatch() throws Exception {
        applyInitSql();
        execute("UPDATE vin_dict_meta SET schema_version = '2' WHERE id = 1");
        runner().run(context -> {
            assertThat(context).hasFailed();
            DictException exception = dictException(context);
            assertThat(exception.getCode()).isEqualTo(DictErrorCode.SCHEMA_VERSION_MISMATCH);
            assertThat(exception.getMessage()).contains(INIT_SQL_PATH);
        });
    }

    @Test
    void missing_one_table_fails_with_schema_missing() throws Exception {
        applyInitSql();
        execute("DROP TABLE vin_dict_item");
        runner().run(context -> {
            assertThat(context).hasFailed();
            DictException exception = dictException(context);
            assertThat(exception.getCode()).isEqualTo(DictErrorCode.SCHEMA_MISSING);
            assertThat(exception.getMessage()).contains(INIT_SQL_PATH);
        });
    }

    @Test
    void missing_meta_row_fails_with_schema_version_mismatch() throws Exception {
        applyInitSql();
        execute("DELETE FROM vin_dict_meta WHERE id = 1");
        runner().run(context -> {
            assertThat(context).hasFailed();
            DictException exception = dictException(context);
            assertThat(exception.getCode()).isEqualTo(DictErrorCode.SCHEMA_VERSION_MISMATCH);
            assertThat(exception.getMessage()).contains(INIT_SQL_PATH);
        });
    }

    @Test
    void disabled_does_not_access_database() {
        AccessCountingDataSource.reset();
        runner("vincent.dict.enabled=false")
                .withUserConfiguration(AccessCountingInfrastructureConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DictQueryService.class);
                    assertThat(AccessCountingDataSource.connectionCount()).isZero();
                });
    }

    private static ApplicationContextRunner runner(String... properties) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DictCoreAutoConfiguration.class))
                .withPropertyValues(properties)
                .withUserConfiguration(MysqlInfrastructureConfiguration.class);
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

    private static void applyInitSql() throws Exception {
        byte[] bytes = Files.readAllBytes(initSqlFile().toPath());
        String script = new String(bytes, StandardCharsets.UTF_8);
        String[] parts = script.split(";");
        List<String> statements = new ArrayList<String>();
        for (int index = 0; index < parts.length; index++) {
            String sql = stripSqlComments(parts[index]);
            if (!sql.isEmpty()) {
                statements.add(sql);
            }
        }
        execute(statements.toArray(new String[statements.size()]));
    }

    private static String stripSqlComments(String fragment) {
        String[] lines = fragment.split("\n");
        StringBuilder sql = new StringBuilder();
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex].trim();
            if (!line.isEmpty() && !line.startsWith("--")) {
                if (sql.length() > 0) {
                    sql.append(' ');
                }
                sql.append(line);
            }
        }
        return sql.toString();
    }

    private static File initSqlFile() {
        File[] candidates = new File[] {
                new File("vincent-dict/sql/mysql/1.0.0/001-init.sql"),
                new File("../sql/mysql/1.0.0/001-init.sql"),
                new File("sql/mysql/1.0.0/001-init.sql")
        };
        for (int index = 0; index < candidates.length; index++) {
            if (candidates[index].isFile()) {
                return candidates[index];
            }
        }
        throw new IllegalStateException("missing schema script 001-init.sql");
    }

    private static void execute(String... statements) throws SQLException {
        Connection connection = MYSQL.createConnection("");
        Statement statement = connection.createStatement();
        try {
            for (int index = 0; index < statements.length; index++) {
                statement.execute(statements[index]);
            }
        } finally {
            statement.close();
            connection.close();
        }
    }

    @Configuration
    static class MysqlInfrastructureConfiguration {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("com.mysql.jdbc.Driver");
            dataSource.setUrl(MYSQL.getJdbcUrl());
            dataSource.setUsername(MYSQL.getUsername());
            dataSource.setPassword(MYSQL.getPassword());
            return dataSource;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) {
            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
            configuration.setEnvironment(new Environment("dict-it", new JdbcTransactionFactory(), dataSource));
            return new SqlSessionFactoryBuilder().build(configuration);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @Configuration
    static class AccessCountingInfrastructureConfiguration {
        @Bean
        DataSource dataSource() {
            return new AccessCountingDataSource(mysqlDataSource());
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) {
            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
            configuration.setEnvironment(new Environment("dict-it", new JdbcTransactionFactory(), dataSource));
            return new SqlSessionFactoryBuilder().build(configuration);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    private static DriverManagerDataSource mysqlDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.jdbc.Driver");
        dataSource.setUrl(MYSQL.getJdbcUrl());
        dataSource.setUsername(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        return dataSource;
    }

    static final class AccessCountingDataSource implements DataSource {
        private static final AtomicInteger CONNECTIONS = new AtomicInteger();
        private final DataSource delegate;

        AccessCountingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        static void reset() {
            CONNECTIONS.set(0);
        }

        static int connectionCount() {
            return CONNECTIONS.get();
        }

        @Override
        public Connection getConnection() throws SQLException {
            CONNECTIONS.incrementAndGet();
            return delegate.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            CONNECTIONS.incrementAndGet();
            return delegate.getConnection(username, password);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }

        @Override
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }
    }
}
