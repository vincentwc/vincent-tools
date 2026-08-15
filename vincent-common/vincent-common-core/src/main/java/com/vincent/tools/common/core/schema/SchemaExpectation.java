package com.vincent.tools.common.core.schema;

public final class SchemaExpectation {
    private final String[] requiredTables;
    private final String metaTable;
    private final String metaIdColumn;
    private final long metaRowId;
    private final String versionColumn;
    private final String requiredVersion;
    private final String initSqlPath;

    public SchemaExpectation(String[] requiredTables, String metaTable,
            String metaIdColumn, long metaRowId, String versionColumn,
            String requiredVersion, String initSqlPath) {
        this.requiredTables = requiredTables.clone();
        this.metaTable = metaTable;
        this.metaIdColumn = metaIdColumn;
        this.metaRowId = metaRowId;
        this.versionColumn = versionColumn;
        this.requiredVersion = requiredVersion;
        this.initSqlPath = initSqlPath;
    }

    public String[] getRequiredTables() {
        return requiredTables.clone();
    }

    public String getMetaTable() {
        return metaTable;
    }

    public String getMetaIdColumn() {
        return metaIdColumn;
    }

    public long getMetaRowId() {
        return metaRowId;
    }

    public String getVersionColumn() {
        return versionColumn;
    }

    public String getRequiredVersion() {
        return requiredVersion;
    }

    public String getInitSqlPath() {
        return initSqlPath;
    }
}
