package com.vincent.tools.dict.domain;

import java.time.Instant;

public final class DictItemFactory {
    private DictItemFactory() {
    }

    public static DictItem rebuild(long id, long dictId, ItemCode code, String name, TenantId tenantId,
                                   String description, ItemStatus status, int sortNo, int version, boolean deleted,
                                   String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
        return DictItem.rebuild(id, dictId, code, name, tenantId, description, status, sortNo, version, deleted,
                createdBy, createdAt, updatedBy, updatedAt);
    }
}
