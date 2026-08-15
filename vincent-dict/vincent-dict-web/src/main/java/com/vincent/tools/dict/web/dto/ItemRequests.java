package com.vincent.tools.dict.web.dto;

import com.vincent.tools.dict.application.admin.command.CreateItemCommand;
import com.vincent.tools.dict.application.admin.command.UpdateItemCommand;
import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;

public final class ItemRequests {
    private ItemRequests() {
    }

    public static class Create {
        private String tenantId;
        private String code;
        private String name;
        private String description;
        private Integer sortNo;

        public CreateItemCommand toCommand() {
            return new CreateItemCommand(code, name, DictRequests.emptyIfNull(description),
                    DictRequests.sortOrZero(sortNo));
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Integer getSortNo() {
            return sortNo;
        }

        public void setSortNo(Integer sortNo) {
            this.sortNo = sortNo;
        }
    }

    public static class Update {
        private String name;
        private String description;
        private Integer sortNo;

        public UpdateItemCommand toCommand() {
            return new UpdateItemCommand(name, DictRequests.emptyIfNull(description),
                    DictRequests.sortOrZero(sortNo));
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Integer getSortNo() {
            return sortNo;
        }

        public void setSortNo(Integer sortNo) {
            this.sortNo = sortNo;
        }
    }

    public static class StatusChange {
        private Boolean enabled;

        public boolean requiredEnabled() {
            if (enabled == null) {
                throw new DictException(DictErrorCode.INVALID_ARGUMENT, "enabled is required");
            }
            return enabled.booleanValue();
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }
}
