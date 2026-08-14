package com.vincent.tools.dict.domain;

import java.time.Instant;

public final class Dict {
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_OPERATOR_LENGTH = 64;

    private final Long id;
    private final DictCode code;
    private String name;
    private final String description;
    private DictStatus status;
    private final int sortNo;
    private final int version;
    private boolean deleted;
    private final String createdBy;
    private final Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;

    private Dict(Long id, DictCode code, String name, String description, DictStatus status, int sortNo, int version,
                 boolean deleted, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
        this.id = id;
        this.code = requireCode(code);
        this.name = requireName(name);
        this.description = requireDescription(description);
        this.status = requireStatus(status);
        this.sortNo = sortNo;
        this.version = version;
        this.deleted = deleted;
        this.createdBy = requireOperator(createdBy);
        this.createdAt = requireTimestamp(createdAt);
        this.updatedBy = requireOperator(updatedBy);
        this.updatedAt = requireTimestamp(updatedAt);
    }

    public static Dict create(DictCode code, String name, String description, int sortNo, String operator, Instant now) {
        return new Dict(null, code, name, description, DictStatus.ENABLED, sortNo, 0, false,
                operator, now, operator, now);
    }

    static Dict rebuild(long id, DictCode code, String name, String description, DictStatus status, int sortNo,
                        int version, boolean deleted, String createdBy, Instant createdAt, String updatedBy,
                        Instant updatedAt) {
        return new Dict(id, code, name, description, status, sortNo, version, deleted,
                createdBy, createdAt, updatedBy, updatedAt);
    }

    public void rename(String name, String operator, Instant now) {
        this.name = requireName(name);
        updateMaintenance(operator, now);
    }

    public void enable(String operator, Instant now) {
        status = DictStatus.ENABLED;
        updateMaintenance(operator, now);
    }

    public void disable(String operator, Instant now) {
        status = DictStatus.DISABLED;
        updateMaintenance(operator, now);
    }

    public void delete(int unDeletedItemCount, String operator, Instant now) {
        if (unDeletedItemCount > 0) {
            throw new DictException(DictErrorCode.DICT_NOT_EMPTY, "dictionary contains undeleted items");
        }
        deleted = true;
        updateMaintenance(operator, now);
    }

    public void restore(String operator, Instant now) {
        if (!deleted) {
            throw new DictException(DictErrorCode.INVALID_ARGUMENT, "dictionary is not deleted");
        }
        deleted = false;
        updateMaintenance(operator, now);
    }

    public Long id() { return id; }
    public DictCode code() { return code; }
    public String name() { return name; }
    public String description() { return description; }
    public DictStatus status() { return status; }
    public int sortNo() { return sortNo; }
    public int version() { return version; }
    public boolean isDeleted() { return deleted; }
    public String createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
    public String updatedBy() { return updatedBy; }
    public Instant updatedAt() { return updatedAt; }
    public boolean isEffective() { return !deleted && status == DictStatus.ENABLED; }

    private void updateMaintenance(String operator, Instant now) {
        updatedBy = requireOperator(operator);
        updatedAt = requireTimestamp(now);
    }

    private static DictCode requireCode(DictCode code) {
        if (code == null) throw invalidArgument("dictCode is required");
        return code;
    }

    private static DictStatus requireStatus(DictStatus status) {
        if (status == null) throw invalidArgument("status is required");
        return status;
    }

    private static String requireName(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_NAME_LENGTH) throw invalidArgument("invalid name");
        return name;
    }

    private static String requireDescription(String description) {
        if (description == null || description.length() > MAX_DESCRIPTION_LENGTH) {
            throw invalidArgument("invalid description");
        }
        return description;
    }

    private static String requireOperator(String operator) {
        if (operator == null || operator.isEmpty() || operator.length() > MAX_OPERATOR_LENGTH
                || !operator.equals(operator.trim())) throw invalidArgument("invalid operator");
        return operator;
    }

    private static Instant requireTimestamp(Instant timestamp) {
        if (timestamp == null) throw invalidArgument("timestamp is required");
        return timestamp;
    }

    private static DictException invalidArgument(String message) {
        return new DictException(DictErrorCode.INVALID_ARGUMENT, message);
    }
}
