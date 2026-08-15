package com.vincent.tools.common.core.schema;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

public final class VincentSchemaValidator {
    public void validate(DataSource dataSource, SchemaExpectation expectation) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            assertRequiredTablesExist(connection, expectation);
            assertSchemaVersion(connection, expectation);
        } catch (SchemaValidationException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new SchemaValidationException(SchemaValidationException.SCHEMA_MISSING,
                    "failed to read schema, apply " + expectation.getInitSqlPath());
        } finally {
            closeQuietly(connection);
        }
    }

    private void assertRequiredTablesExist(Connection connection, SchemaExpectation expectation)
            throws SQLException {
        String[] requiredTables = expectation.getRequiredTables();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(
                "SELECT TABLE_NAME FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME IN (" + tableInClause(requiredTables) + ")");
        try {
            Set<String> found = new HashSet<String>();
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME");
                if (tableName != null) {
                    found.add(tableName);
                }
            }
            for (int index = 0; index < requiredTables.length; index++) {
                if (!found.contains(requiredTables[index])) {
                    throw new SchemaValidationException(SchemaValidationException.SCHEMA_MISSING,
                            "missing table " + requiredTables[index] + ", apply "
                                    + expectation.getInitSqlPath());
                }
            }
        } finally {
            resultSet.close();
            statement.close();
        }
    }

    private void assertSchemaVersion(Connection connection, SchemaExpectation expectation)
            throws SQLException {
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(
                "SELECT " + expectation.getVersionColumn()
                        + " FROM " + expectation.getMetaTable()
                        + " WHERE " + expectation.getMetaIdColumn()
                        + " = " + expectation.getMetaRowId());
        try {
            if (!resultSet.next()
                    || !expectation.getRequiredVersion().equals(resultSet.getString(1))) {
                throw new SchemaValidationException(SchemaValidationException.SCHEMA_VERSION_MISMATCH,
                        "schema version must be " + expectation.getRequiredVersion() + ", apply "
                                + expectation.getInitSqlPath());
            }
        } finally {
            resultSet.close();
            statement.close();
        }
    }

    private static String tableInClause(String[] requiredTables) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < requiredTables.length; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append('\'').append(requiredTables[index]).append('\'');
        }
        return builder.toString();
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
