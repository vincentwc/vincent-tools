package com.vincent.tools.dict.infra.mybatis;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.vincent.tools.dict.application.EffectiveDictData;
import com.vincent.tools.dict.application.EffectiveItemData;
import com.vincent.tools.dict.domain.DictCode;
import com.vincent.tools.dict.domain.DictItemSource;
import com.vincent.tools.dict.domain.TenantId;
import com.vincent.tools.dict.infra.mybatis.mapper.DictItemMapper;
import com.vincent.tools.dict.infra.mybatis.mapper.DictMapper;
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

import javax.sql.DataSource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MybatisDictQueryRepositoryIT {
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
    void cleanBusinessTables() throws SQLException {
        Connection connection = openConnection();
        Statement statement = connection.createStatement();
        try {
            statement.execute("DELETE FROM vin_dict_item");
            statement.execute("DELETE FROM vin_dict");
        } finally {
            statement.close();
            connection.close();
        }
    }

    @Test
    void finds_enabled_non_deleted_items_in_default_and_current_tenant_scope() throws Exception {
        Connection connection = openConnection();
        try {
            long dictId = insertDict(connection, "ORDER_STATUS", 1, 0);
            insertItem(connection, dictId, TenantId.DEFAULT_VALUE, "DEFAULT_A", "Default A", "Default A item", 1, 1, 0);
            insertItem(connection, dictId, TenantId.DEFAULT_VALUE, "DEFAULT_B", "Default B", "Default B item", 2, 1, 0);
            insertItem(connection, dictId, "tenant-a", "TENANT_A", "Tenant A", "Tenant A item", 3, 1, 0);
            insertItem(connection, dictId, "tenant-b", "TENANT_B", "Tenant B", "Tenant B item", 3, 1, 0);
            insertItem(connection, dictId, TenantId.DEFAULT_VALUE, "DEFAULT_OFF", "Off", "Disabled default", 0, 0, 0);
            insertItem(connection, dictId, TenantId.DEFAULT_VALUE, "DEFAULT_GONE", "Gone", "Deleted default", 0, 1, 1);
        } finally {
            connection.close();
        }

        SqlSession session = sqlSessionFactory.openSession();
        try {
            Optional<EffectiveDictData> result = repository(session)
                    .findEffectiveData(DictCode.of("ORDER_STATUS"), "tenant-a");

            assertThat(result).isPresent();
            assertThat(result.get().isEnabled()).isTrue();
            assertThat(result.get().getItems())
                    .extracting(EffectiveItemData::getCode)
                    .containsExactly("DEFAULT_A", "DEFAULT_B", "TENANT_A")
                    .doesNotContain("TENANT_B");
            assertThat(result.get().getItems().get(0).getSource()).isEqualTo(DictItemSource.DEFAULT);
            assertThat(result.get().getItems().get(2).getSource()).isEqualTo(DictItemSource.TENANT);
            assertThat(result.get().getItems().get(2).getName()).isEqualTo("Tenant A");
            assertThat(result.get().getItems().get(2).getDescription()).isEqualTo("Tenant A item");
            assertThat(result.get().getItems().get(2).getSortNo()).isEqualTo(3);
        } finally {
            session.close();
        }
    }

    @Test
    void returns_disabled_dict_as_present_but_not_enabled() throws Exception {
        Connection connection = openConnection();
        try {
            long dictId = insertDict(connection, "DISABLED_STATUS", 0, 0);
            insertItem(connection, dictId, TenantId.DEFAULT_VALUE, "DEFAULT_A", "Default A", "Default A item", 1, 1, 0);
        } finally {
            connection.close();
        }

        SqlSession session = sqlSessionFactory.openSession();
        try {
            Optional<EffectiveDictData> result = repository(session)
                    .findEffectiveData(DictCode.of("DISABLED_STATUS"), "tenant-a");

            assertThat(result).isPresent();
            assertThat(result.get().isEnabled()).isFalse();
            assertThat(result.get().getItems())
                    .extracting(EffectiveItemData::getCode)
                    .containsExactly("DEFAULT_A");
        } finally {
            session.close();
        }
    }

    @Test
    void returns_empty_when_dict_is_missing() {
        SqlSession session = sqlSessionFactory.openSession();
        try {
            assertThat(repository(session).findEffectiveData(DictCode.of("MISSING_DICT"), "tenant-a")).isEmpty();
        } finally {
            session.close();
        }
    }

    @Test
    void returns_empty_when_dict_is_deleted() throws Exception {
        Connection connection = openConnection();
        try {
            insertDict(connection, "DELETED_STATUS", 1, 1);
        } finally {
            connection.close();
        }

        SqlSession session = sqlSessionFactory.openSession();
        try {
            assertThat(repository(session).findEffectiveData(DictCode.of("DELETED_STATUS"), "tenant-a")).isEmpty();
        } finally {
            session.close();
        }
    }

    @Test
    void duplicate_dict_code_fails() throws Exception {
        Connection connection = openConnection();
        try {
            insertDict(connection, "DUP_DICT", 1, 0);
            assertThatThrownBy(() -> insertDict(connection, "DUP_DICT", 1, 0))
                    .isInstanceOf(SQLException.class);
        } finally {
            connection.close();
        }
    }

    @Test
    void duplicate_item_code_fails_even_when_first_row_is_deleted() throws Exception {
        Connection connection = openConnection();
        try {
            final long dictId = insertDict(connection, "ITEM_DUP", 1, 0);
            insertItem(connection, dictId, "tenant-a", "SAME_CODE", "First", "Deleted", 1, 1, 1);
            assertThatThrownBy(() -> insertItem(connection, dictId, "tenant-a", "SAME_CODE", "Second", "Present", 1, 1, 0))
                    .isInstanceOf(SQLException.class);
        } finally {
            connection.close();
        }
    }

    @Test
    void same_item_code_in_two_tenants_succeeds() throws Exception {
        Connection connection = openConnection();
        try {
            long dictId = insertDict(connection, "SHARED_CODE", 1, 0);
            insertItem(connection, dictId, "tenant-a", "SHARED", "A", "A item", 1, 1, 0);
            insertItem(connection, dictId, "tenant-b", "SHARED", "B", "B item", 1, 1, 0);
        } finally {
            connection.close();
        }
    }

    @Test
    void meta_schema_version_equals_1() throws Exception {
        Connection connection = openConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT schema_version FROM vin_dict_meta WHERE id = 1");
        try {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("schema_version")).isEqualTo("1");
            assertThat(resultSet.next()).isFalse();
        } finally {
            resultSet.close();
            statement.close();
            connection.close();
        }
    }

    private static MybatisDictQueryRepository repository(SqlSession session) {
        return new MybatisDictQueryRepository(
                session.getMapper(DictMapper.class),
                session.getMapper(DictItemMapper.class));
    }

    private static SqlSessionFactory createSqlSessionFactory() {
        DataSource dataSource = new UnpooledDataSource(
                "com.mysql.jdbc.Driver",
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword());
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(DictMapper.class);
        configuration.addMapper(DictItemMapper.class);
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

    private static Connection openConnection() throws SQLException {
        return MYSQL.createConnection("");
    }

    private static long insertDict(Connection connection, String code, int status, int deleted) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO vin_dict (code, name, description, status, sort_no, version, deleted, "
                        + "created_by, created_at, updated_by, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 0, ?, ?, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3))",
                Statement.RETURN_GENERATED_KEYS);
        try {
            statement.setString(1, code);
            statement.setString(2, code + " name");
            statement.setString(3, code + " description");
            statement.setInt(4, status);
            statement.setInt(5, 1);
            statement.setInt(6, deleted);
            statement.setString(7, "tester");
            statement.setString(8, "tester");
            statement.executeUpdate();
            ResultSet keys = statement.getGeneratedKeys();
            try {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            } finally {
                keys.close();
            }
        } finally {
            statement.close();
        }
    }

    private static void insertItem(Connection connection, long dictId, String tenantId, String code, String name,
                                   String description, int sortNo, int status, int deleted) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO vin_dict_item (dict_id, tenant_id, code, name, description, status, sort_no, version, "
                        + "deleted, created_by, created_at, updated_by, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3))");
        try {
            statement.setLong(1, dictId);
            statement.setString(2, tenantId);
            statement.setString(3, code);
            statement.setString(4, name);
            statement.setString(5, description);
            statement.setInt(6, status);
            statement.setInt(7, sortNo);
            statement.setInt(8, deleted);
            statement.setString(9, "tester");
            statement.setString(10, "tester");
            statement.executeUpdate();
        } finally {
            statement.close();
        }
    }
}
