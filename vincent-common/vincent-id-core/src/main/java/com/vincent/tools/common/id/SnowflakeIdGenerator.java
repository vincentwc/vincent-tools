package com.vincent.tools.common.id;

import java.time.Instant;
import java.util.Objects;

public final class SnowflakeIdGenerator {
    private static final long EPOCH_MILLIS = 1_577_836_800_000L; // 2020-01-01T00:00:00Z
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private final long workerId;
    private final long datacenterId;
    private final TimeSource timeSource;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        this(workerId, datacenterId, System::currentTimeMillis);
    }

    SnowflakeIdGenerator(long workerId, long datacenterId, TimeSource timeSource) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IdGenerationException("workerId must be between 0 and " + MAX_WORKER_ID);
        }
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IdGenerationException("datacenterId must be between 0 and " + MAX_DATACENTER_ID);
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource");
    }

    public synchronized long nextId() {
        long timestamp = currentMillis();
        if (timestamp < lastTimestamp) {
            throw new IdGenerationException("clock moved backwards");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH_MILLIS) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    static Instant toInstant(long id) {
        long timestamp = (id >> TIMESTAMP_SHIFT) + EPOCH_MILLIS;
        return Instant.ofEpochMilli(timestamp);
    }

    private long waitNextMillis(long previousTimestamp) {
        long timestamp = currentMillis();
        while (timestamp <= previousTimestamp) {
            timestamp = currentMillis();
        }
        return timestamp;
    }

    private long currentMillis() {
        return timeSource.currentTimeMillis();
    }

    interface TimeSource {
        long currentTimeMillis();
    }
}
