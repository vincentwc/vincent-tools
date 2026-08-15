package com.vincent.tools.audit.example;

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
                    .withStartupTimeout(Duration.ofMinutes(3));

    private static final AtomicBoolean SCHEMA_APPLIED = new AtomicBoolean();

    private ExampleMysqlSupport() {
    }

    static void ensureStarted() {
        MYSQL.start();
        if (SCHEMA_APPLIED.compareAndSet(false, true)) {
            try {
                applyScript(readFile(initSqlFile()));
            } catch (Exception ex) {
                throw new IllegalStateException("failed to apply audit SQL before Spring startup", ex);
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

    static File starterAdminIndexHtml() {
        return firstExisting(new File[] {
                new File("vincent-audit/vincent-audit-boot2-starter/target/classes/META-INF/resources/audit-admin/index.html"),
                new File("../vincent-audit-boot2-starter/target/classes/META-INF/resources/audit-admin/index.html"),
                new File("vincent-audit-boot2-starter/target/classes/META-INF/resources/audit-admin/index.html")
        }, new File("../vincent-audit-boot2-starter/target/classes/META-INF/resources/audit-admin/index.html"));
    }

    private static File firstExisting(File[] candidates, File fallback) {
        for (File candidate : candidates) {
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return fallback;
    }

    private static void applyScript(String script) throws SQLException {
        String[] parts = script.split(";");
        List<String> statements = new ArrayList<String>();
        for (String part : parts) {
            String sql = stripSqlComments(part);
            if (!sql.isEmpty()) {
                statements.add(sql);
            }
        }
        Connection connection = MYSQL.createConnection("");
        Statement statement = connection.createStatement();
        try {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } finally {
            statement.close();
            connection.close();
        }
    }

    private static String stripSqlComments(String fragment) {
        String[] lines = fragment.split("\n");
        StringBuilder sql = new StringBuilder();
        for (String line : lines) {
            line = line.trim();
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
        byte[] bytes = Files.readAllBytes(file.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static File initSqlFile() {
        File[] candidates = new File[] {
                new File("vincent-audit/sql/mysql/1.0.0/001-init.sql"),
                new File("../sql/mysql/1.0.0/001-init.sql"),
                new File("sql/mysql/1.0.0/001-init.sql")
        };
        for (File candidate : candidates) {
            if (candidate.isFile()) {
                return candidate;
            }
        }
        throw new IllegalStateException("missing schema script 001-init.sql");
    }
}
