package com.vincent.tools.dict.web.dto;

import com.vincent.tools.dict.application.admin.command.CreateDictCommand;
import com.vincent.tools.dict.application.admin.command.UpdateDictCommand;
import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;

public final class DictRequests {
    private DictRequests() {
    }

    public static class Create {
        private String code;
        private String name;
        private String description;
        private Integer sortNo;

        public CreateDictCommand toCommand() {
            return new CreateDictCommand(code, name, emptyIfNull(description), sortOrZero(sortNo));
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

        public UpdateDictCommand toCommand() {
            return new UpdateDictCommand(name, emptyIfNull(description), sortOrZero(sortNo));
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

    static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    static int sortOrZero(Integer sortNo) {
        return sortNo == null ? 0 : sortNo.intValue();
    }
}
