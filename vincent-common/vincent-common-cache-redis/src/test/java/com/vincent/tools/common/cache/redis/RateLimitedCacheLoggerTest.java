package com.vincent.tools.common.cache.redis;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitedCacheLoggerTest {
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(RateLimitedCacheLoggerTest.class);
        appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void same_failure_class_logs_once_within_interval() {
        RateLimitedCacheLogger cacheLogger = new RateLimitedCacheLogger(RateLimitedCacheLoggerTest.class);
        RuntimeException error = new RuntimeException("boom");

        cacheLogger.warn("redis-timeout", "first message", error);
        cacheLogger.warn("redis-timeout", "suppressed message", error);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("first message");
    }

    @Test
    void different_failure_classes_log_independently() {
        RateLimitedCacheLogger cacheLogger = new RateLimitedCacheLogger(RateLimitedCacheLoggerTest.class);

        cacheLogger.warn("redis-timeout", "timeout", new RuntimeException("t"));
        cacheLogger.warn("redis-connection", "connection", new RuntimeException("c"));

        assertThat(appender.list).hasSize(2);
    }
}
