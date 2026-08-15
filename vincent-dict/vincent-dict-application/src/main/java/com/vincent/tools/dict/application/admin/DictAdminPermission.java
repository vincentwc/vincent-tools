package com.vincent.tools.dict.application.admin;

import com.vincent.tools.host.VincentPermission;

public enum DictAdminPermission implements VincentPermission {
    DICT_VIEW,
    DICT_CREATE,
    DICT_UPDATE,
    DICT_ENABLE_DISABLE,
    DICT_DELETE,
    DICT_RESTORE,
    ITEM_CREATE,
    ITEM_UPDATE,
    ITEM_ENABLE_DISABLE,
    ITEM_DELETE,
    ITEM_RESTORE;

    @Override
    public String code() {
        return name();
    }
}
