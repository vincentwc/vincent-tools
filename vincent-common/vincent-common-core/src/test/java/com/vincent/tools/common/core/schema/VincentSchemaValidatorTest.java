package com.vincent.tools.common.core.schema;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VincentSchemaValidatorTest {
    private static final String INIT_SQL_PATH = "sql/mysql/1.0.0/001-init.sql";
    private static final SchemaExpectation DICT_SCHEMA_EXPECTATION = new SchemaExpectation(
            new String[] { "vin_dict_meta", "vin_dict", "vin_dict_item" },
            "vin_dict_meta",
            "id",
            1L,
            "schema_version",
            "1",
            INIT_SQL_PATH);

    private static MysqlTestDatabase database;
    private final VincentSchemaValidator validator = new VincentSchemaValidator();

    @BeforeAll
    static void startDatabase() {
        database = MysqlTestDatabase.start();
    }

    @AfterAll
    static void stopDatabase() {
        if (database != null) {
            database.stop();
        }
    }

    @BeforeEach
    void dropDictionaryTables() throws SQLException {
        database.execute(
                "DROP TABLE IF EXISTS vin_dict_item",
                "DROP TABLE IF EXISTS vin_dict",
                "DROP TABLE IF EXISTS vin_dict_meta");
    }

    @Test
    void missing_tables_fail_with_schema_missing() {
        assertThatThrownBy(() -> validator.validate(dataSource(), DICT_SCHEMA_EXPECTATION))
                .isInstanceOf(SchemaValidationException.class)
                .satisfies(ex -> {
                    SchemaValidationException exception = (SchemaValidationException) ex;
                    assertThat(exception.errorCode()).isEqualTo(SchemaValidationException.SCHEMA_MISSING);
                    assertThat(exception.getMessage()).contains(INIT_SQL_PATH);
                });
    }

    @Test
    void valid_schema_passes() throws Exception {
        applyInitSql();
        validator.validate(dataSource(), DICT_SCHEMA_EXPECTATION);
    }

    @Test
    void version_mismatch_fails_with_schema_version_mismatch() throws Exception {
        applyInitSql();
        database.execute("UPDATE vin_dict_meta SET schema_version = '2' WHERE id = 1");
        assertThatThrownBy(() -> validator.validate(dataSource(), DICT_SCHEMA_EXPECTATION))
                .isInstanceOf(SchemaValidationException.class)
                .satisfies(ex -> {
                    SchemaValidationException exception = (SchemaValidationException) ex;
                    assertThat(exception.errorCode())
                            .isEqualTo(SchemaValidationException.SCHEMA_VERSION_MISMATCH);
                    assertThat(exception.getMessage()).contains(INIT_SQL_PATH);
                });
    }

    @Test
    void missing_one_table_fails_with_schema_missing() throws Exception {
        applyInitSql();
        database.execute("DROP TABLE vin_dict_item");
        assertThatThrownBy(() -> validator.validate(dataSource(), DICT_SCHEMA_EXPECTATION))
                .isInstanceOf(SchemaValidationException.class)
                .satisfies(ex -> {
                    SchemaValidationException exception = (SchemaValidationException) ex;
                    assertThat(exception.errorCode()).isEqualTo(SchemaValidationException.SCHEMA_MISSING);
                    assertThat(exception.getMessage()).contains("vin_dict_item");
                    assertThat(exception.getMessage()).contains(INIT_SQL_PATH);
                });
    }

    @Test
    void missing_meta_row_fails_with_schema_version_mismatch() throws Exception {
        applyInitSql();
        database.execute("DELETE FROM vin_dict_meta WHERE id = 1");
        assertThatThrownBy(() -> validator.validate(dataSource(), DICT_SCHEMA_EXPECTATION))
                .isInstanceOf(SchemaValidationException.class)
                .satisfies(ex -> {
                    SchemaValidationException exception = (SchemaValidationException) ex;
                    assertThat(exception.errorCode())
                            .isEqualTo(SchemaValidationException.SCHEMA_VERSION_MISMATCH);
                    assertThat(exception.getMessage()).contains(INIT_SQL_PATH);
                });
    }

    private DataSource dataSource() {
        return database.dataSource();
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
        database.execute(statements.toArray(new String[statements.size()]));
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
                new File("../vincent-dict/sql/mysql/1.0.0/001-init.sql"),
                new File("../../vincent-dict/sql/mysql/1.0.0/001-init.sql"),
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
