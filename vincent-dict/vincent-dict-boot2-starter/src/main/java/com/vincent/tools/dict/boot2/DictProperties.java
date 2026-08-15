package com.vincent.tools.dict.boot2;

import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vincent.dict")
public class DictProperties {
    private boolean enabled = true;
    private String dataSourceBeanName;
    private String sqlSessionFactoryBeanName;
    private String transactionManagerBeanName;
    private final Admin admin = new Admin();
    private final Limits limits = new Limits();

    public void validate() {
        if (limits.defaultItemsPerDict <= 0
                || limits.tenantItemsPerDict <= 0
                || limits.maxEffectiveItems <= 0
                || limits.defaultPageSize <= 0
                || limits.maxPageSize <= 0) {
            throw new DictException(DictErrorCode.CONFIGURATION_INVALID, "all limits must be positive integers");
        }
        if ((long) limits.maxEffectiveItems
                < (long) limits.defaultItemsPerDict + (long) limits.tenantItemsPerDict) {
            throw new DictException(DictErrorCode.CONFIGURATION_INVALID,
                    "max-effective-items must be >= default-items-per-dict + tenant-items-per-dict");
        }
        if (limits.maxPageSize < limits.defaultPageSize) {
            throw new DictException(DictErrorCode.CONFIGURATION_INVALID,
                    "max-page-size must be >= default-page-size");
        }
        if (hasAnyInfrastructureBeanName() && !hasAllInfrastructureBeanNames()) {
            throw new DictException(DictErrorCode.CONFIGURATION_INVALID,
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
        private String basePath = "/dict-admin";
        private String apiPath = "/vincent/dict/admin/api/v1";

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
        private int defaultItemsPerDict = 1000;
        private int tenantItemsPerDict = 1000;
        private int maxEffectiveItems = 2000;
        private int defaultPageSize = 20;
        private int maxPageSize = 100;

        public int getDefaultItemsPerDict() {
            return defaultItemsPerDict;
        }

        public void setDefaultItemsPerDict(int defaultItemsPerDict) {
            this.defaultItemsPerDict = defaultItemsPerDict;
        }

        public int getTenantItemsPerDict() {
            return tenantItemsPerDict;
        }

        public void setTenantItemsPerDict(int tenantItemsPerDict) {
            this.tenantItemsPerDict = tenantItemsPerDict;
        }

        public int getMaxEffectiveItems() {
            return maxEffectiveItems;
        }

        public void setMaxEffectiveItems(int maxEffectiveItems) {
            this.maxEffectiveItems = maxEffectiveItems;
        }

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
