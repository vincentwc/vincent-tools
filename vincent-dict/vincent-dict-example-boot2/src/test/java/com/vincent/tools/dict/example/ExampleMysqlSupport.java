package com.vincent.tools.dict.example;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
                applyScript(readClasspathResource("/demo-data.sql"));
            } catch (Exception ex) {
                throw new IllegalStateException("failed to apply dictionary SQL before Spring startup", ex);
            }
        }
    }

    static void register(DynamicPropertyRegistry registry) {
        ensureStarted();
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", new java.util.function.Supplier<Object>() {
            @Override
            public Object get() {
                return "com.mysql.jdbc.Driver";
            }
        });
    }

    static File starterAdminIndexHtml() {
        return firstExisting(new File[] {
                new File("vincent-dict/vincent-dict-boot2-starter/target/classes/META-INF/resources/dict-admin/index.html"),
                new File("../vincent-dict-boot2-starter/target/classes/META-INF/resources/dict-admin/index.html"),
                new File("vincent-dict-boot2-starter/target/classes/META-INF/resources/dict-admin/index.html")
        }, new File("../vincent-dict-boot2-starter/target/classes/META-INF/resources/dict-admin/index.html"));
    }

    static File starterJar() {
        return firstExisting(new File[] {
                new File("vincent-dict/vincent-dict-boot2-starter/target/vincent-dict-boot2-starter-1.0.0-SNAPSHOT.jar"),
                new File("../vincent-dict-boot2-starter/target/vincent-dict-boot2-starter-1.0.0-SNAPSHOT.jar"),
                new File("vincent-dict-boot2-starter/target/vincent-dict-boot2-starter-1.0.0-SNAPSHOT.jar")
        }, new File("../vincent-dict-boot2-starter/target/vincent-dict-boot2-starter-1.0.0-SNAPSHOT.jar"));
    }

    private static File firstExisting(File[] candidates, File fallback) {
        for (int index = 0; index < candidates.length; index++) {
            if (candidates[index].isFile()) {
                return candidates[index];
            }
        }
        return fallback;
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

    private static String readFile(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String readClasspathResource(String resource) throws IOException {
        InputStream input = ExampleMysqlSupport.class.getResourceAsStream(resource);
        if (input == null) {
            throw new IllegalStateException("missing classpath resource " + resource);
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
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
}
