package com.vincent.tools.audit.boot2;

import com.vincent.tools.audit.domain.AuditErrorCode;
import com.vincent.tools.audit.domain.AuditException;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vincent.audit")
public class AuditProperties {
    private boolean enabled = true;
    private boolean failFast = true;
    private String dataSourceBeanName;
    private String sqlSessionFactoryBeanName;
    private String transactionManagerBeanName;
    private final Admin admin = new Admin();
    private final Limits limits = new Limits();

    public void validate() {
        if (limits.defaultPageSize <= 0 || limits.maxPageSize <= 0) {
            throw new AuditException(AuditErrorCode.CONFIGURATION_INVALID, "all limits must be positive integers");
        }
        if (limits.maxPageSize < limits.defaultPageSize) {
            throw new AuditException(AuditErrorCode.CONFIGURATION_INVALID,
                    "max-page-size must be >= default-page-size");
        }
        if (hasAnyInfrastructureBeanName() && !hasAllInfrastructureBeanNames()) {
            throw new AuditException(AuditErrorCode.CONFIGURATION_INVALID,
                    "data-source-bean-name, sql-session-factory-bean-name and transaction-manager-bean-name must be set together");
        }
    }

    public boolean hasAllInfrastructureBeanNames() {
        return hasText(dataSourceBeanName) && hasText(sqlSessionFactoryBeanName) && hasText(transactionManagerBeanName);
    }

    public boolean hasAnyInfrastructureBeanName() {
        return hasText(dataSourceBeanName) || hasText(sqlSessionFactoryBeanName) || hasText(transactionManagerBeanName);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public String getDataSourceBeanName() {
        return dataSourceBeanName;
    }

    public void setDataSourceBeanName(String dataSourceBeanName) {
        this.dataSourceBeanName = dataSourceBeanName;
    }

    public String getSqlSessionFactoryBeanName() {
        return sqlSessionFactoryBeanName;
    }

    public void setSqlSessionFactoryBeanName(String sqlSessionFactoryBeanName) {
        this.sqlSessionFactoryBeanName = sqlSessionFactoryBeanName;
    }

    public String getTransactionManagerBeanName() {
        return transactionManagerBeanName;
    }

    public void setTransactionManagerBeanName(String transactionManagerBeanName) {
        this.transactionManagerBeanName = transactionManagerBeanName;
    }

    public Admin getAdmin() {
        return admin;
    }

    public Limits getLimits() {
        return limits;
    }

    private static boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }

    public static class Admin {
        private boolean enabled = false;
        private String basePath = "/audit-admin";
        private String apiPath = "/vincent/audit/admin/api/v1";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }

        public String getApiPath() {
            return apiPath;
        }

        public void setApiPath(String apiPath) {
            this.apiPath = apiPath;
        }
    }

    public static class Limits {
        private int defaultPageSize = 20;
        private int maxPageSize = 100;

        public int getDefaultPageSize() {
            return defaultPageSize;
        }

        public void setDefaultPageSize(int defaultPageSize) {
            this.defaultPageSize = defaultPageSize;
        }

        public int getMaxPageSize() {
            return maxPageSize;
        }

        public void setMaxPageSize(int maxPageSize) {
            this.maxPageSize = maxPageSize;
        }
    }
}
