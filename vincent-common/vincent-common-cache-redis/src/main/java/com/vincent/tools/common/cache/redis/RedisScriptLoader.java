package com.vincent.tools.common.cache.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class RedisScriptLoader {
    private RedisScriptLoader() {
    }

    public static DefaultRedisScript<Long> loadLongScript(String classpathLocation) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource(classpathLocation));
        script.setResultType(Long.class);
        return script;
    }
}
