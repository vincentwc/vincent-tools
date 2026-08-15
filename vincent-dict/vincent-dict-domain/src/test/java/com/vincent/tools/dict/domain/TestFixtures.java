package com.vincent.tools.dict.domain;

import java.time.Instant;

final class TestFixtures {
    static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");
    static final String OPERATOR = "operator";

    private TestFixtures() {
    }

    static Dict activeDict() {
        return Dict.create(DictCode.of("ORDER_STATUS"), "Order status", "", 10, OPERATOR, NOW);
    }

    static DictItem activeTenantItem() {
        return DictItem.create(42L, ItemCode.of("ACTIVE"), "Active", TenantId.of("tenant-a"), "", 10,
                OPERATOR, NOW);
    }
}
