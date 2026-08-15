package com.vincent.tools.region.application;

import com.vincent.tools.host.VincentPermission;

public enum RegionPermission implements VincentPermission {
    REGION_VIEW;

    @Override
    public String code() {
        return name();
    }
}
