package com.vincent.tools.common.core.schema;

import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

final class MysqlTestDatabase {
    private static final String DEFAULT_JDBC_URL =
            "jdbc:mysql://127.0.0.1:3306/vincent_common_core_test?useSSL=false";
    private static final String DEFAULT_USERNAME = "root";
    private static final String DEFAULT_PASSWORD = "123456";

    private final DataSource dataSource;
    private final MySQLContainer<?> container;

    private MysqlTestDatabase(DataSource dataSource, MySQLContainer<?> container) {
        this.dataSource = dataSource;
        this.container = container;
    }

    static MysqlTestDatabase start() {
        try {
            MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:5.7.44"))
                    .withUrlParam("useSSL", "false")
                    .withStartupTimeout(Duration.ofMinutes(3));
            mysql.start();
            return new MysqlTestDatabase(containerDataSource(mysql), mysql);
        } catch (IllegalStateException ex) {
            return new MysqlTestDatabase(externalDataSource(), null);
        }
    }

    DataSource dataSource() {
        return dataSource;
    }

    void execute(String... statements) throws SQLException {
        if (container != null) {
            Connection connection = container.createConnection("");
            Statement statement = connection.createStatement();
            try {
                for (int index = 0; index < statements.length; index++) {
                    statement.execute(statements[index]);
                }
            } finally {
                statement.close();
                connection.close();
            }
            return;
        }
        Connection connection = dataSource.getConnection();
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

    void stop() {
        if (container != null) {
            container.stop();
        }
    }

    private static DataSource containerDataSource(MySQLContainer<?> mysql) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.jdbc.Driver");
        dataSource.setUrl(mysql.getJdbcUrl());
        dataSource.setUsername(mysql.getUsername());
        dataSource.setPassword(mysql.getPassword());
        return dataSource;
    }

    private static DataSource externalDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.jdbc.Driver");
        dataSource.setUrl(System.getenv().getOrDefault("VINCENT_TEST_MYSQL_JDBC_URL", DEFAULT_JDBC_URL));
        dataSource.setUsername(System.getenv().getOrDefault("VINCENT_TEST_MYSQL_USERNAME", DEFAULT_USERNAME));
        dataSource.setPassword(System.getenv().getOrDefault("VINCENT_TEST_MYSQL_PASSWORD", DEFAULT_PASSWORD));
        return dataSource;
    }
}
