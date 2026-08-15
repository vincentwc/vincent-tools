package com.vincent.tools.region.infra.mybatis;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.vincent.tools.region.application.RegionView;
import com.vincent.tools.region.infra.mybatis.mapper.RegionMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisRegionRepositoryIT {
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:5.7.44"))
                    .withUrlParam("useSSL", "false")
                    .withUrlParam("useUnicode", "true")
                    .withUrlParam("characterEncoding", "UTF-8")
                    .withStartupTimeout(Duration.ofMinutes(3));

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void startDatabase() throws Exception {
        MYSQL.start();
        applySql("001-init.sql");
        applySql("001-data.sql");
        sqlSessionFactory = createSqlSessionFactory();
    }

    @AfterAll
    static void stopDatabase() {
        MYSQL.stop();
    }

    @Test
    void finds_region_and_lists_children() {
        SqlSession session = sqlSessionFactory.openSession();
        try {
            MybatisRegionRepository repository = new MybatisRegionRepository(session.getMapper(RegionMapper.class));

            Optional<RegionView> guangzhou = repository.findByCode("440100");
            assertThat(guangzhou).isPresent();
            assertThat(guangzhou.get().getName()).isEqualTo("广州市");

            List<RegionView> provinces = repository.listChildren("0");
            assertThat(provinces).extracting(RegionView::getCode).contains("110000", "440000");

            List<RegionView> guangzhouDistricts = repository.listChildren("440100");
            assertThat(guangzhouDistricts).extracting(RegionView::getName).contains("荔湾区", "越秀区");
        } finally {
            session.close();
        }
    }

    private static SqlSessionFactory createSqlSessionFactory() {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(RegionMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private static void applySql(String fileName) throws Exception {
        byte[] bytes = Files.readAllBytes(sqlFile(fileName).toPath());
        String script = new String(bytes, StandardCharsets.UTF_8);
        Connection connection = MYSQL.createConnection("");
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

    private static File sqlFile(String fileName) {
        File[] candidates = new File[] {
                new File("vincent-region/sql/mysql/1.0.0/" + fileName),
                new File("../sql/mysql/1.0.0/" + fileName),
                new File("sql/mysql/1.0.0/" + fileName)
        };
        for (int index = 0; index < candidates.length; index++) {
            if (candidates[index].isFile()) {
                return candidates[index];
            }
        }
        throw new IllegalStateException("missing sql script " + fileName);
    }
}
