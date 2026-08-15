package com.vincent.tools.dict.cache.redis;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public final class DictCacheKeyFactory {
    private final String prefix;

    public DictCacheKeyFactory(String prefix) {
        this.prefix = Objects.requireNonNull(prefix, "prefix");
    }

    public String globalVersion(String dictCode) {
        return prefix + ":gv:" + dictCode;
    }

    public String tenantVersion(String dictCode, String tenantId) {
        return prefix + ":tv:" + dictCode + ":" + encodeTenantId(tenantId);
    }

    public String payload(String dictCode, long globalVersion, long tenantVersion, String tenantId) {
        return prefix + ":v1:" + dictCode + ":" + globalVersion + ":" + tenantVersion + ":"
                + encodeTenantId(tenantId);
    }

    private static String encodeTenantId(String tenantId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tenantId.getBytes(StandardCharsets.UTF_8));
    }
}
