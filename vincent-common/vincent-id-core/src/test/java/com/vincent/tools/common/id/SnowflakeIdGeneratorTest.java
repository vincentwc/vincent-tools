package com.vincent.tools.common.id;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdGeneratorTest {
    @Test
    void rejects_invalid_worker_or_datacenter_id() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(-1, 0))
                .isInstanceOf(IdGenerationException.class);
        assertThatThrownBy(() -> new SnowflakeIdGenerator(0, 32))
                .isInstanceOf(IdGenerationException.class);
    }

    @Test
    void generates_unique_monotonic_ids() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 2);
        Set<Long> ids = new HashSet<Long>();
        long previous = -1L;
        for (int index = 0; index < 1000; index++) {
            long id = generator.nextId();
            assertThat(ids.add(id)).isTrue();
            assertThat(id).isGreaterThan(previous);
            previous = id;
        }
    }

    @Test
    void rejects_clock_moving_backwards() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1, new SnowflakeIdGenerator.TimeSource() {
            private int calls;

            @Override
            public long currentTimeMillis() {
                calls++;
                return calls == 1 ? 2_000L : 1_000L;
            }
        });
        generator.nextId();
        assertThatThrownBy(generator::nextId).isInstanceOf(IdGenerationException.class);
    }

    @Test
    void embeds_timestamp_in_id() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(3, 4, () -> 1_700_000_000_000L);
        Instant instant = SnowflakeIdGenerator.toInstant(generator.nextId());
        assertThat(instant.toEpochMilli()).isEqualTo(1_700_000_000_000L);
    }
}
