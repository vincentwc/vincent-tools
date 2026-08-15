package com.vincent.tools.audit.infra.mybatis;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.vincent.tools.audit.application.AuditRecord;
import com.vincent.tools.audit.application.AuditSearchQuery;
import com.vincent.tools.audit.application.AuditRecordView;
import com.vincent.tools.audit.infra.mybatis.mapper.AuditLogMapper;
import com.vincent.tools.common.core.PageResult;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisAuditRepositoryIT {
    private static final Instant NOW = Instant.parse("2026-08-15T12:34:56.789Z");
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:5.7.44"))
                    .withUrlParam("useSSL", "false")
                    .withStartupTimeout(Duration.ofMinutes(3));

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void startDatabase() throws Exception {
        MYSQL.start();
        applyInitSql();
        sqlSessionFactory = createSqlSessionFactory();
    }

    @AfterAll
    static void stopDatabase() {
        MYSQL.stop();
    }

    @BeforeEach
    void cleanAuditLog() throws SQLException {
        Connection connection = openConnection();
        Statement statement = connection.createStatement();
        try {
            statement.execute("DELETE FROM vin_audit_log");
        } finally {
            statement.close();
            connection.close();
        }
    }

    @Test
    void insertsAndSearchesWithFilters() {
        SqlSession session = sqlSessionFactory.openSession();
        try {
            MybatisAuditRepository repository = repository(session);
            repository.insert(sampleRecord("tenant-a", "UPDATE", "ORDER", "1001"));
            repository.insert(sampleRecord("tenant-b", "DELETE", "ORDER", "1002"));
            session.commit();

            PageResult<AuditRecordView> tenantA = repository.search(new AuditSearchQuery(
                    Optional.of("tenant-a"), Optional.<String>empty(), Optional.<String>empty(),
                    Optional.<String>empty(), Optional.<String>empty(), Optional.<Instant>empty(),
                    Optional.<Instant>empty(), 1, 20));
            assertThat(tenantA.getTotal()).isEqualTo(1L);
            assertThat(tenantA.getItems().get(0).getResourceId()).isEqualTo("1001");

            PageResult<AuditRecordView> byAction = repository.search(new AuditSearchQuery(
                    Optional.<String>empty(), Optional.<String>empty(), Optional.of("DELETE"),
                    Optional.<String>empty(), Optional.<String>empty(), Optional.<Instant>empty(),
                    Optional.<Instant>empty(), 1, 20));
            assertThat(byAction.getTotal()).isEqualTo(1L);
            assertThat(byAction.getItems().get(0).getTenantId()).isEqualTo("tenant-b");
        } finally {
            session.close();
        }
    }

    @Test
    void rollbackRemovesUncommittedAuditRecord() throws SQLException {
        SqlSession session = sqlSessionFactory.openSession(false);
        try {
            repository(session).insert(sampleRecord("tenant-a", "UPDATE", "ORDER", "2001"));
            session.rollback();
        } finally {
            session.close();
        }

        Connection connection = openConnection();
        Statement statement = connection.createStatement();
        try {
            ResultSet resultSet = statement.executeQuery("SELECT COUNT(1) FROM vin_audit_log");
            resultSet.next();
            assertThat(resultSet.getLong(1)).isZero();
        } finally {
            statement.close();
            connection.close();
        }
    }

    private static MybatisAuditRepository repository(SqlSession session) {
        return new MybatisAuditRepository(session.getMapper(AuditLogMapper.class));
    }

    private static AuditRecord sampleRecord(String tenantId, String action, String resourceType, String resourceId) {
        return new AuditRecord(tenantId, "operator", action, resourceType, resourceId,
                "{\"before\":true}", "{\"after\":true}", "127.0.0.1", "JUnit", "trace-1", NOW);
    }

    private static SqlSessionFactory createSqlSessionFactory() {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(AuditLogMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private static void applyInitSql() throws Exception {
        byte[] bytes = Files.readAllBytes(initSqlFile().toPath());
        String script = new String(bytes, StandardCharsets.UTF_8);
        Connection connection = openConnection();
        Statement statement = connection.createStatement();
        try {
            String[] parts = script.split(";");
            for (int index = 0; index < parts.length; index++) {
                String sql = stripSqlComments(parts[index]);
                if (!sql.isEmpty()) {
                    statement.execute(sql);
                }
            }
        } finally {
            statement.close();
            connection.close();
        }
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
                new File("vincent-audit/sql/mysql/1.0.0/001-init.sql"),
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

    private static Connection openConnection() throws SQLException {
        return MYSQL.createConnection("");
    }
}
