package com.vincent.tools.dict.cache.redis;

import java.util.ArrayList;
import java.util.List;

public final class DictCachePayload {
    public static final int CURRENT_FORMAT_VERSION = 1;

    private int formatVersion;
    private List<Item> items = new ArrayList<Item>();

    public int getFormatVersion() {
        return formatVersion;
    }

    public void setFormatVersion(int formatVersion) {
        this.formatVersion = formatVersion;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public static final class Item {
        private String code;
        private String name;
        private String description;
        private int sortNo;
        private String source;

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

        public int getSortNo() {
            return sortNo;
        }

        public void setSortNo(int sortNo) {
            this.sortNo = sortNo;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }
    }
}
