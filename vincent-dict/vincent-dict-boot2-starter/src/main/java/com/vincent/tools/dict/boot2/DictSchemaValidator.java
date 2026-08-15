package com.vincent.tools.dict.boot2;

import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

public class DictSchemaValidator {
    public static final String INIT_SQL_PATH = "sql/mysql/1.0.0/001-init.sql";
    public static final String REQUIRED_VERSION = "1";
    private static final String[] REQUIRED_TABLES = new String[] {
            "vin_dict_meta", "vin_dict", "vin_dict_item"
    };

    public void validate(DataSource dataSource) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            assertRequiredTablesExist(connection);
            assertSchemaVersion(connection);
        } catch (DictException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new DictException(DictErrorCode.SCHEMA_MISSING,
                    "failed to read dictionary schema, apply " + INIT_SQL_PATH);
        } finally {
            closeQuietly(connection);
        }
    }

    private void assertRequiredTablesExist(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(
                "SELECT TABLE_NAME FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME IN ('vin_dict_meta', 'vin_dict', 'vin_dict_item')");
        try {
            Set<String> found = new HashSet<String>();
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME");
                if (tableName != null) {
                    found.add(tableName);
                }
            }
            for (int index = 0; index < REQUIRED_TABLES.length; index++) {
                if (!found.contains(REQUIRED_TABLES[index])) {
                    throw new DictException(DictErrorCode.SCHEMA_MISSING,
                            "missing dictionary table " + REQUIRED_TABLES[index] + ", apply " + INIT_SQL_PATH);
                }
            }
        } finally {
            resultSet.close();
            statement.close();
        }
    }

    private void assertSchemaVersion(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT schema_version FROM vin_dict_meta WHERE id = 1");
        try {
            if (!resultSet.next() || !REQUIRED_VERSION.equals(resultSet.getString("schema_version"))) {
                throw new DictException(DictErrorCode.SCHEMA_VERSION_MISMATCH,
                        "dictionary schema version must be 1, apply " + INIT_SQL_PATH);
            }
        } finally {
            resultSet.close();
            statement.close();
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
