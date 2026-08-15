package com.vincent.tools.common.core.infrastructure;

public final class InfrastructureBeanNames {
    public final String dataSourceBeanName;
    public final String sqlSessionFactoryBeanName;
    public final String transactionManagerBeanName;

    public InfrastructureBeanNames(String dataSourceBeanName, String sqlSessionFactoryBeanName,
            String transactionManagerBeanName) {
        this.dataSourceBeanName = dataSourceBeanName;
        this.sqlSessionFactoryBeanName = sqlSessionFactoryBeanName;
        this.transactionManagerBeanName = transactionManagerBeanName;
    }
}
