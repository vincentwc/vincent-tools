package com.vincent.tools.region.boot2;

import com.vincent.tools.region.domain.RegionErrorCode;
import com.vincent.tools.region.domain.RegionException;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vincent.region")
public class RegionProperties {
    private boolean enabled = true;
    private String dataSourceBeanName;
    private String sqlSessionFactoryBeanName;
    private String transactionManagerBeanName;
    private final Admin admin = new Admin();

    public void validate() {
        if (hasAnyInfrastructureBeanName() && !hasAllInfrastructureBeanNames()) {
            throw new RegionException(RegionErrorCode.CONFIGURATION_INVALID,
                    "data-source-bean-name, sql-session-factory-bean-name and transaction-manager-bean-name must be set together");
        }
    }

    public boolean hasAllInfrastructureBeanNames() {
        return hasText(dataSourceBeanName) && hasText(sqlSessionFactoryBeanName)
                && hasText(transactionManagerBeanName);
    }

    public boolean hasAnyInfrastructureBeanName() {
        return hasText(dataSourceBeanName) || hasText(sqlSessionFactoryBeanName)
                || hasText(transactionManagerBeanName);
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

    private static boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }

    public static class Admin {
        private boolean enabled = false;
        private String apiPath = "/vincent/region/admin/api/v1";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiPath() {
            return apiPath;
        }

        public void setApiPath(String apiPath) {
            this.apiPath = apiPath;
        }
    }
}
