package com.vincent.tools.dict.cache.redis;

import com.vincent.tools.dict.application.port.DictCache;
import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnClass(StringRedisTemplate.class)
@AutoConfigureAfter(name = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration")
@AutoConfigureBefore(name = "com.vincent.tools.dict.boot2.DictCoreAutoConfiguration")
@EnableConfigurationProperties(DictRedisProperties.class)
public class DictRedisAutoConfiguration {

    @Configuration
    @ConditionalOnProperty(prefix = "vincent.dict.cache", name = "enabled", havingValue = "true")
    static class EnabledConfiguration {
        @Bean
        @ConditionalOnMissingBean(DictCache.class)
        public DictCache redisDictCache(ObjectProvider<StringRedisTemplate> redisTemplates,
                                        DictRedisProperties properties) {
            properties.validate();
            StringRedisTemplate redisTemplate = redisTemplates.getIfAvailable();
            if (redisTemplate == null) {
                throw new DictException(DictErrorCode.CONFIGURATION_INVALID,
                        "StringRedisTemplate is required when vincent.dict.cache.enabled=true");
            }
            return new RedisDictCache(redisTemplate, properties);
        }
    }
}
