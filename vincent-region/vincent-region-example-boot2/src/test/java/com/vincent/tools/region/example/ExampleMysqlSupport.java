package com.vincent.tools.region.example;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class ExampleMysqlSupport {
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:5.7.44"))
                    .withUrlParam("useSSL", "false")
                    .withUrlParam("useUnicode", "true")
                    .withUrlParam("characterEncoding", "UTF-8")
                    .withStartupTimeout(Duration.ofMinutes(3));

    private static final AtomicBoolean SCHEMA_APPLIED = new AtomicBoolean();

    private ExampleMysqlSupport() {
    }

    static void ensureStarted() {
        MYSQL.start();
        if (SCHEMA_APPLIED.compareAndSet(false, true)) {
            try {
                applyScript(readFile(initSqlFile()));
                applyScript(readFile(dataSqlFile()));
            } catch (Exception ex) {
                throw new IllegalStateException("failed to apply region SQL before Spring startup", ex);
            }
        }
    }

    static void register(DynamicPropertyRegistry registry) {
        ensureStarted();
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.jdbc.Driver");
    }

    private static void applyScript(String script) throws SQLException {
        String[] parts = script.split(";");
        List<String> statements = new ArrayList<String>();
        for (int index = 0; index < parts.length; index++) {
            String sql = stripSqlComments(parts[index]);
            if (!sql.isEmpty()) {
                statements.add(sql);
            }
        }
        Connection connection = MYSQL.createConnection("");
        Statement statement = connection.createStatement();
        try {
            for (int index = 0; index < statements.size(); index++) {
                statement.execute(statements.get(index));
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

    private static String readFile(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static File initSqlFile() {
        return firstExisting(new File[] {
                new File("vincent-region/sql/mysql/1.0.0/001-init.sql"),
                new File("../sql/mysql/1.0.0/001-init.sql"),
                new File("sql/mysql/1.0.0/001-init.sql")
        });
    }

    private static File dataSqlFile() {
        return firstExisting(new File[] {
                new File("vincent-region/sql/mysql/1.0.0/001-data.sql"),
                new File("../sql/mysql/1.0.0/001-data.sql"),
                new File("sql/mysql/1.0.0/001-data.sql")
        });
    }

    private static File firstExisting(File[] candidates) {
        for (int index = 0; index < candidates.length; index++) {
            if (candidates[index].isFile()) {
                return candidates[index];
            }
        }
        throw new IllegalStateException("missing region SQL script");
    }
}
