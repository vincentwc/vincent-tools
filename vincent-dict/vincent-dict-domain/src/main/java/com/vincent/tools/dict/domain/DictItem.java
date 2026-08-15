package com.vincent.tools.dict.domain;

import java.time.Instant;

public final class DictItem {
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_OPERATOR_LENGTH = 64;

    private final Long id;
    private final long dictId;
    private final ItemCode code;
    private String name;
    private final TenantId tenantId;
    private String description;
    private ItemStatus status;
    private int sortNo;
    private final int version;
    private boolean deleted;
    private final String createdBy;
    private final Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;

    private DictItem(Long id, long dictId, ItemCode code, String name, TenantId tenantId, String description,
                     ItemStatus status, int sortNo, int version, boolean deleted, String createdBy, Instant createdAt,
                     String updatedBy, Instant updatedAt) {
        this.id = id;
        this.dictId = dictId;
        this.code = requireCode(code);
        this.name = requireName(name);
        this.tenantId = requireTenantId(tenantId);
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

    public static DictItem create(long dictId, ItemCode code, String name, TenantId tenantId, String description,
                                  int sortNo, String operator, Instant now) {
        return new DictItem(null, dictId, code, name, tenantId, description, ItemStatus.ENABLED, sortNo, 0, false,
                operator, now, operator, now);
    }

    static DictItem rebuild(long id, long dictId, ItemCode code, String name, TenantId tenantId, String description,
                            ItemStatus status, int sortNo, int version, boolean deleted, String createdBy,
                            Instant createdAt, String updatedBy, Instant updatedAt) {
        return new DictItem(id, dictId, code, name, tenantId, description, status, sortNo, version, deleted,
                createdBy, createdAt, updatedBy, updatedAt);
    }

    public void rename(String name, String operator, Instant now) {
        update(name, description, sortNo, operator, now);
    }

    public void update(String name, String description, int sortNo, String operator, Instant now) {
        requireNotDeleted();
        String validatedName = requireName(name);
        String validatedDescription = requireDescription(description);
        String validatedOperator = requireOperator(operator);
        Instant validatedNow = requireTimestamp(now);

        this.name = validatedName;
        this.description = validatedDescription;
        this.sortNo = sortNo;
        updateMaintenance(validatedOperator, validatedNow);
    }

    public void enable(String operator, Instant now) {
        requireNotDeleted();
        String validatedOperator = requireOperator(operator);
        Instant validatedNow = requireTimestamp(now);
        status = ItemStatus.ENABLED;
        updateMaintenance(validatedOperator, validatedNow);
    }

    public void disable(String operator, Instant now) {
        requireNotDeleted();
        String validatedOperator = requireOperator(operator);
        Instant validatedNow = requireTimestamp(now);
        status = ItemStatus.DISABLED;
        updateMaintenance(validatedOperator, validatedNow);
    }

    public void delete(String operator, Instant now) {
        requireNotDeleted();
        String validatedOperator = requireOperator(operator);
        Instant validatedNow = requireTimestamp(now);
        deleted = true;
        updateMaintenance(validatedOperator, validatedNow);
    }

    public void restore(boolean dictRestored, String operator, Instant now) {
        requireDeleted();
        if (!dictRestored) throw new DictException(DictErrorCode.INVALID_ARGUMENT, "dictionary is not restored");
        String validatedOperator = requireOperator(operator);
        Instant validatedNow = requireTimestamp(now);
        deleted = false;
        updateMaintenance(validatedOperator, validatedNow);
    }

    public Long id() { return id; }
    public long dictId() { return dictId; }
    public ItemCode code() { return code; }
    public String name() { return name; }
    public TenantId tenantId() { return tenantId; }
    public DictItemSource source() { return tenantId.isDefault() ? DictItemSource.DEFAULT : DictItemSource.TENANT; }
    public String description() { return description; }
    public ItemStatus status() { return status; }
    public int sortNo() { return sortNo; }
    public int version() { return version; }
    public boolean isDeleted() { return deleted; }
    public String createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
    public String updatedBy() { return updatedBy; }
    public Instant updatedAt() { return updatedAt; }
    public boolean isEffective() { return !deleted && status == ItemStatus.ENABLED; }

    private void updateMaintenance(String operator, Instant now) {
        updatedBy = operator;
        updatedAt = now;
    }

    private void requireNotDeleted() {
        if (deleted) throw new DictException(DictErrorCode.INVALID_ARGUMENT, "dictionary item is deleted");
    }

    private void requireDeleted() {
        if (!deleted) throw new DictException(DictErrorCode.INVALID_ARGUMENT, "dictionary item is not deleted");
    }

    private static ItemCode requireCode(ItemCode code) {
        if (code == null) throw invalidArgument("itemCode is required");
        return code;
    }

    private static TenantId requireTenantId(TenantId tenantId) {
        if (tenantId == null) throw invalidArgument("tenantId is required");
        return tenantId;
    }

    private static ItemStatus requireStatus(ItemStatus status) {
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
