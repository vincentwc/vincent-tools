package com.vincent.tools.dict.cache.redis;

import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "vincent.dict.cache")
public class DictRedisProperties {
    private boolean enabled = false;
    private String keyPrefix = "vin:dict";
    private Duration ttl = Duration.ofSeconds(60);

    public void validate() {
        if (keyPrefix == null || keyPrefix.trim().length() == 0) {
            throw new DictException(DictErrorCode.CONFIGURATION_INVALID,
                    "vincent.dict.cache.key-prefix must not be empty");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new DictException(DictErrorCode.CONFIGURATION_INVALID,
                    "vincent.dict.cache.ttl must be positive");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }
}
