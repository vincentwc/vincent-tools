package com.vincent.tools.dict.domain;

import java.time.Instant;

public final class DictFactory {
    private DictFactory() {
    }

    public static Dict rebuild(long id, DictCode code, String name, String description, DictStatus status, int sortNo,
                               int version, boolean deleted, String createdBy, Instant createdAt, String updatedBy,
                               Instant updatedAt) {
        return Dict.rebuild(id, code, name, description, status, sortNo, version, deleted,
                createdBy, createdAt, updatedBy, updatedAt);
    }
}
