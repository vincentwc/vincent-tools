package com.vincent.tools.dict.cache.redis;

import com.vincent.tools.dict.application.port.DictCache;
import com.vincent.tools.dict.application.port.NoopDictCache;
import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DictRedisAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DictRedisAutoConfiguration.class));

    @Test
    void property_absent_does_not_create_redis_dict_cache() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DictCache.class);
            assertThat(context).doesNotHaveBean(RedisDictCache.class);
        });
    }

    @Test
    void property_false_does_not_create_redis_dict_cache() {
        contextRunner.withPropertyValues("vincent.dict.cache.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DictCache.class);
                    assertThat(context).doesNotHaveBean(RedisDictCache.class);
                });
    }

    @Test
    void enabled_with_string_redis_template_creates_one_redis_dict_cache() {
        contextRunner.withPropertyValues("vincent.dict.cache.enabled=true")
                .withBean(StringRedisTemplate.class, DictRedisAutoConfigurationTest::hostStringRedisTemplate)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DictCache.class);
                    assertThat(context).hasSingleBean(RedisDictCache.class);
                    assertThat(context.getBean(DictCache.class)).isInstanceOf(RedisDictCache.class);
                    assertThat(context.getBean(DictCache.class)).isNotInstanceOf(NoopDictCache.class);
                    DictRedisProperties properties = context.getBean(DictRedisProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getKeyPrefix()).isEqualTo("vin:dict");
                    assertThat(properties.getTtl()).isEqualTo(Duration.ofSeconds(60));
                });
    }

    @Test
    void enabled_without_string_redis_template_fails_as_configuration_invalid() {
        contextRunner.withPropertyValues("vincent.dict.cache.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(dictException(context).getCode()).isEqualTo(DictErrorCode.CONFIGURATION_INVALID);
                });
    }

    @Test
    void core_starter_without_redis_starter_does_not_register_redis_cache() throws IOException {
        assertThat(coreStarterSpringFactories())
                .doesNotContain("DictRedisAutoConfiguration")
                .doesNotContain("com.vincent.tools.dict.cache.redis");

        new ApplicationContextRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DictCache.class);
            assertThat(context).doesNotHaveBean(RedisDictCache.class);
        });
    }

    @Test
    void missing_string_redis_template_class_does_not_create_redis_cache() {
        contextRunner.withClassLoader(new FilteredClassLoader(StringRedisTemplate.class))
                .withPropertyValues("vincent.dict.cache.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DictCache.class);
                    assertThat(context).doesNotHaveBean(RedisDictCache.class);
                });
    }

    @Test
    void enabled_rejects_blank_key_prefix() {
        contextRunner.withPropertyValues(
                        "vincent.dict.cache.enabled=true",
                        "vincent.dict.cache.key-prefix=   ")
                .withBean(StringRedisTemplate.class, DictRedisAutoConfigurationTest::hostStringRedisTemplate)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(dictException(context).getCode()).isEqualTo(DictErrorCode.CONFIGURATION_INVALID);
                });
    }

    @Test
    void enabled_rejects_non_positive_ttl() {
        for (String ttl : new String[] {"0s", "-1s"}) {
            contextRunner.withPropertyValues(
                            "vincent.dict.cache.enabled=true",
                            "vincent.dict.cache.ttl=" + ttl)
                    .withBean(StringRedisTemplate.class, DictRedisAutoConfigurationTest::hostStringRedisTemplate)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(dictException(context).getCode()).isEqualTo(DictErrorCode.CONFIGURATION_INVALID);
                    });
        }
    }

    private static StringRedisTemplate hostStringRedisTemplate() {
        return new StringRedisTemplate(new UnusedRedisConnectionFactory());
    }

    private static DictException dictException(
            org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        Throwable current = context.getStartupFailure();
        while (current != null) {
            if (current instanceof DictException) {
                return (DictException) current;
            }
            current = current.getCause();
        }
        throw new AssertionError("DictException was not thrown", context.getStartupFailure());
    }

    private static String coreStarterSpringFactories() throws IOException {
        Path[] candidates = new Path[] {
                Paths.get("..", "vincent-dict-boot2-starter", "src", "main", "resources",
                        "META-INF", "spring.factories"),
                Paths.get("vincent-dict", "vincent-dict-boot2-starter", "src", "main", "resources",
                        "META-INF", "spring.factories")
        };
        for (int index = 0; index < candidates.length; index++) {
            Path path = candidates[index];
            if (Files.isRegularFile(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("core starter spring.factories was not found");
    }

    private static final class UnusedRedisConnectionFactory implements RedisConnectionFactory {
        @Override
        public RedisConnection getConnection() {
            throw new UnsupportedOperationException("redis is unused in auto-configuration tests");
        }

        @Override
        public RedisClusterConnection getClusterConnection() {
            throw new UnsupportedOperationException("redis is unused in auto-configuration tests");
        }

        @Override
        public boolean getConvertPipelineAndTxResults() {
            return false;
        }

        @Override
        public RedisSentinelConnection getSentinelConnection() {
            throw new UnsupportedOperationException("redis is unused in auto-configuration tests");
        }

        @Override
        public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
            return null;
        }
    }
}
