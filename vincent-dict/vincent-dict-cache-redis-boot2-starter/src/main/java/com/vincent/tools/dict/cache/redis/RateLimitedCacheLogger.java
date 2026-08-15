package com.vincent.tools.dict.cache.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

final class RateLimitedCacheLogger {
    static final long MIN_INTERVAL_MS = 30_000L;

    private final Logger log;
    private final ConcurrentHashMap<String, Long> lastLoggedAt = new ConcurrentHashMap<String, Long>();

    RateLimitedCacheLogger() {
        this.log = LoggerFactory.getLogger(RedisDictCache.class);
    }

    void warn(String failureClass, String message, Throwable error) {
        long now = System.currentTimeMillis();
        Long previous = lastLoggedAt.get(failureClass);
        if (previous != null && now - previous < MIN_INTERVAL_MS) {
            return;
        }
        lastLoggedAt.put(failureClass, Long.valueOf(now));
        log.warn(message, error);
    }
}
