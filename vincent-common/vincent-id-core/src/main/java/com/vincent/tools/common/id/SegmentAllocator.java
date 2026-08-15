package com.vincent.tools.common.id;

public interface SegmentAllocator {
    long nextSegment(String bizKey);
}
