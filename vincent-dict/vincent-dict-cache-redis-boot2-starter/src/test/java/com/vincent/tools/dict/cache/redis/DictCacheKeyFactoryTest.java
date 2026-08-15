package com.vincent.tools.dict.cache.redis;

import com.vincent.tools.dict.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class DictCacheKeyFactoryTest {
    private final DictCacheKeyFactory keys = new DictCacheKeyFactory("vin:dict");

    @Test
    void global_version_key_uses_prefix_and_dict_code() {
        assertThat(keys.globalVersion("ORDER_STATUS"))
                .isEqualTo("vin:dict:gv:ORDER_STATUS");
    }

    @Test
    void tenant_version_key_encodes_tenant_id() {
        assertThat(keys.tenantVersion("ORDER_STATUS", "tenant:a"))
                .doesNotContain("tenant:a")
                .startsWith("vin:dict:tv:ORDER_STATUS:")
                .isEqualTo("vin:dict:tv:ORDER_STATUS:" + encoded("tenant:a"));
    }

    @Test
    void payload_key_includes_versions_and_encoded_tenant() {
        assertThat(keys.payload("ORDER_STATUS", 3L, 5L, "tenant:a"))
                .isEqualTo("vin:dict:v1:ORDER_STATUS:3:5:" + encoded("tenant:a"))
                .doesNotContain("tenant:a");
    }

    @Test
    void default_item_tenant_is_never_raw_in_keys() {
        String tenantId = TenantId.DEFAULT_VALUE;
        String encodedTenant = encoded(tenantId);

        assertThat(keys.tenantVersion("ORDER_STATUS", tenantId))
                .doesNotEndWith(":" + tenantId)
                .isEqualTo("vin:dict:tv:ORDER_STATUS:" + encodedTenant);
        assertThat(keys.payload("ORDER_STATUS", 0L, 0L, tenantId))
                .doesNotEndWith(":" + tenantId)
                .isEqualTo("vin:dict:v1:ORDER_STATUS:0:0:" + encodedTenant);
    }

    private static String encoded(String tenantId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tenantId.getBytes(StandardCharsets.UTF_8));
    }
}
