package com.vincent.tools.dict.domain;

public final class DictItemPolicy {
    public void checkCreate(TenantId tenantId, ItemCodeUsage usage, int unDeletedItemCount, int itemLimit) {
        if (tenantId == null || usage == null || unDeletedItemCount < 0 || itemLimit < 0) {
            throw new DictException(DictErrorCode.INVALID_ARGUMENT, "invalid item creation policy input");
        }
        if ((tenantId.isDefault() && usage.hasAnyUse())
                || (!tenantId.isDefault() && (usage.hasDefaultUse() || usage.hasSameTenantUse()))) {
            throw new DictException(DictErrorCode.DICT_ITEM_CODE_CONFLICT, "dictionary item code conflicts with history");
        }
        if (unDeletedItemCount >= itemLimit) {
            throw new DictException(DictErrorCode.DICT_ITEM_LIMIT_EXCEEDED, "dictionary item limit exceeded");
        }
    }
}
