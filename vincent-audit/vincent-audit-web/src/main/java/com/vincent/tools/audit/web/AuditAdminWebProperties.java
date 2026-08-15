package com.vincent.tools.audit.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vincent.audit.admin")
public class AuditAdminWebProperties {
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
