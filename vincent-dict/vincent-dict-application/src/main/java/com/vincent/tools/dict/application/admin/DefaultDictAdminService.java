package com.vincent.tools.dict.application.admin;

import com.vincent.tools.dict.application.DictLimits;
import com.vincent.tools.dict.application.admin.command.CreateDictCommand;
import com.vincent.tools.dict.application.admin.command.CreateItemCommand;
import com.vincent.tools.dict.application.admin.command.UpdateDictCommand;
import com.vincent.tools.dict.application.admin.command.UpdateItemCommand;
import com.vincent.tools.dict.application.admin.query.DictPageQuery;
import com.vincent.tools.dict.application.admin.query.ItemPageQuery;
import com.vincent.tools.dict.application.admin.view.DictDetail;
import com.vincent.tools.dict.application.admin.view.DictItemDetail;
import com.vincent.tools.dict.application.admin.view.DictSummary;
import com.vincent.tools.common.core.PageResult;
import com.vincent.tools.dict.application.port.DictAdminRepository;
import com.vincent.tools.dict.application.port.DictCache;
import com.vincent.tools.dict.application.port.TxRunner;
import com.vincent.tools.host.OperatorProvider;
import com.vincent.tools.host.PermissionProvider;
import com.vincent.tools.dict.domain.Dict;
import com.vincent.tools.dict.domain.DictCode;
import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;
import com.vincent.tools.dict.domain.DictItem;
import com.vincent.tools.dict.domain.DictItemPolicy;
import com.vincent.tools.dict.domain.DictStatus;
import com.vincent.tools.dict.domain.ItemCode;
import com.vincent.tools.dict.domain.ItemCodeUsage;
import com.vincent.tools.dict.domain.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class DefaultDictAdminService implements DictAdminService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_OPERATOR_LENGTH = 64;

    private final DictAdminRepository repository;
    private final TxRunner txRunner;
    private final DictCache cache;
    private final OperatorProvider operatorProvider;
    private final PermissionProvider permissionProvider;
    private final TenantDirectory tenantDirectory;
    private final DictLimits limits;
    private final Clock clock;
    private final DictItemPolicy itemPolicy = new DictItemPolicy();

    public DefaultDictAdminService(DictAdminRepository repository, TxRunner txRunner, DictCache cache,
                                   OperatorProvider operatorProvider, PermissionProvider permissionProvider,
                                   TenantDirectory tenantDirectory, DictLimits limits, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.txRunner = Objects.requireNonNull(txRunner, "txRunner");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.operatorProvider = Objects.requireNonNull(operatorProvider, "operatorProvider");
        this.permissionProvider = Objects.requireNonNull(permissionProvider, "permissionProvider");
        this.tenantDirectory = tenantDirectory;
        this.limits = Objects.requireNonNull(limits, "limits");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PageResult<DictSummary> pageDicts(DictPageQuery query) {
        requireQuery(query);
        requirePermission(DictAdminPermission.DICT_VIEW, Optional.<String>empty());
        validatePage(query.getPage(), query.getSize());
        return repository.pageDicts(query);
    }

    @Override
    public DictDetail getDict(long dictId, boolean includeDeleted) {
        requirePermission(DictAdminPermission.DICT_VIEW, Optional.<String>empty());
        Dict dict = repository.findDict(dictId)
                .orElseThrow(() -> notFound(DictErrorCode.DICT_NOT_FOUND, "dictionary was not found"));
        if (dict.isDeleted() && !includeDeleted) {
            throw notFound(DictErrorCode.DICT_NOT_FOUND, "dictionary was not found");
        }
        return toDetail(dict);
    }

    @Override
    public long createDict(CreateDictCommand command) {
        requireCommand(command);
        requirePermission(DictAdminPermission.DICT_CREATE, Optional.<String>empty());
        String operator = requireOperator();
        Instant now = Instant.now(clock);
        final DictCode code = DictCode.of(command.getCode());
        Long id = txRunner.required(() -> {
            if (repository.existsDictCode(code)) {
                throw new DictException(DictErrorCode.DICT_CODE_CONFLICT, "dictionary code already exists");
            }
            Dict dict = Dict.create(code, command.getName(), command.getDescription(), command.getSortNo(),
                    operator, now);
            return Long.valueOf(repository.insertDict(dict));
        });
        cache.evictAll(code.value());
        return id.longValue();
    }

    @Override
    public void updateDict(long dictId, UpdateDictCommand command) {
        requireCommand(command);
        requirePermission(DictAdminPermission.DICT_UPDATE, Optional.<String>empty());
        String operator = requireOperator();
        Instant now = Instant.now(clock);
        String dictCode = txRunner.required(() -> {
            Dict dict = lockDict(dictId);
            dict.update(command.getName(), command.getDescription(), command.getSortNo(), operator, now);
            repository.updateDict(dict);
            return dict.code().value();
        });
        cache.evictAll(dictCode);
    }

    @Override
    public void changeDictStatus(long dictId, boolean enabled) {
        requirePermission(DictAdminPermission.DICT_ENABLE_DISABLE, Optional.<String>empty());
        String operator = requireOperator();
        Instant now = Instant.now(clock);
        String dictCode = txRunner.required(() -> {
            Dict dict = lockDict(dictId);
            if (enabled) {
                dict.enable(operator, now);
            } else {
                dict.disable(operator, now);
            }
            repository.updateDict(dict);
            return dict.code().value();
        });
        cache.evictAll(dictCode);
    }

    @Override
    public void deleteDict(long dictId) {
        requirePermission(DictAdminPermission.DICT_DELETE, Optional.<String>empty());
        String operator = requireOperator();
        Instant now = Instant.now(clock);
        String dictCode = txRunner.required(() -> {
            Dict dict = lockDict(dictId);
            dict.delete(repository.countUndeletedItems(dictId), operator, now);
            repository.updateDict(dict);
            return dict.code().value();
        });
        cache.evictAll(dictCode);
    }

    @Override
    public void restoreDict(long dictId) {
        requirePermission(DictAdminPermission.DICT_RESTORE, Optional.<String>empty());
        String operator = requireOperator();
        Instant now = Instant.now(clock);
        String dictCode = txRunner.required(() -> {
            Dict dict = lockDict(dictId);
            dict.restore(operator, now);
            repository.updateDict(dict);
            return dict.code().value();
        });
        cache.evictAll(dictCode);
    }

    @Override
    public PageResult<DictItemDetail> pageItems(long dictId, ItemPageQuery query) {
        requireQuery(query);
        Optional<String> scope = requireTenantScope(query.getTenantId());
        requirePermission(DictAdminPermission.DICT_VIEW, scope);
        validatePage(query.getPage(), query.getSize());
        if (scope.isPresent()) {
            requireTenantExists(scope.get());
        }
        return repository.pageItems(dictId, query);
    }

    @Override
    public long createDefaultItem(long dictId, CreateItemCommand command) {
        requireCommand(command);
        requirePermission(DictAdminPermission.ITEM_CREATE, Optional.<String>empty());
        String operator = requireOperator();
        Instant now = Instant.now(clock);
        ItemCode code = ItemCode.of(command.getCode());
        TenantId tenantId = TenantId.defaultItem();
        ItemWrite write = txRunner.required(() -> createItem(dictId, tenantId, code, command, operator, now,
                limits.getDefaultItemsPerDict()));
        cache.evictAll(write.dictCode);
        return write.itemId;
    }

    @Override
    public long createTenantItem(long dictId, String tenantIdValue, CreateItemCommand command) {
        requireCommand(command);
        TenantId tenantId = requireExternalTenant(tenantIdValue);
        requirePermission(DictAdminPermission.ITEM_CREATE, Optional.of(tenantId.value()));
        String operator = requireOperator();
        requireTenantExists(tenantId.value());
        Instant now = Instant.now(clock);
        ItemCode code = ItemCode.of(command.getCode());
        ItemWrite write = txRunner.required(() -> createItem(dictId, tenantId, code, command, operator, now,
                limits.getTenantItemsPerDict()));
        cache.evictTenant(write.dictCode, tenantIdValue);
        return write.itemId;
    }

    @Override
    public void updateItem(long itemId, UpdateItemCommand command) {
        requireCommand(command);
        DictItem loaded = requireItem(itemId);
        requirePermission(DictAdminPermission.ITEM_UPDATE, itemScope(loaded));
        String operator = requireOperator();
        requireItemDirectory(loaded);
        Instant now = Instant.now(clock);
        ItemWrite write = txRunner.required(() -> {
            Dict dict = lockDict(loaded.dictId());
            DictItem item = requireItem(itemId);
            item.update(command.getName(), command.getDescription(), command.getSortNo(), operator, now);
            repository.updateItem(item);
            return new ItemWrite(item.id(), dict.code().value());
        });
        evictItem(write.dictCode, loaded);
    }

    @Override
    public void changeItemStatus(long itemId, boolean enabled) {
        DictItem loaded = requireItem(itemId);
        requirePermission(DictAdminPermission.ITEM_ENABLE_DISABLE, itemScope(loaded));
        String operator = requireOperator();
        requireItemDirectory(loaded);
        Instant now = Instant.now(clock);
        ItemWrite write = txRunner.required(() -> {
            Dict dict = lockDict(loaded.dictId());
            DictItem item = requireItem(itemId);
            if (enabled) {
                item.enable(operator, now);
            } else {
                item.disable(operator, now);
            }
            repository.updateItem(item);
            return new ItemWrite(item.id(), dict.code().value());
        });
        evictItem(write.dictCode, loaded);
    }

    @Override
    public void deleteItem(long itemId) {
        DictItem loaded = requireItem(itemId);
        requirePermission(DictAdminPermission.ITEM_DELETE, itemScope(loaded));
        String operator = requireOperator();
        requireItemDirectory(loaded);
        Instant now = Instant.now(clock);
        ItemWrite write = txRunner.required(() -> {
            Dict dict = lockDict(loaded.dictId());
            DictItem item = requireItem(itemId);
            item.delete(operator, now);
            repository.updateItem(item);
            return new ItemWrite(item.id(), dict.code().value());
        });
        evictItem(write.dictCode, loaded);
    }

    @Override
    public void restoreItem(long itemId) {
        DictItem loaded = requireItem(itemId);
        requirePermission(DictAdminPermission.ITEM_RESTORE, itemScope(loaded));
        String operator = requireOperator();
        requireItemDirectory(loaded);
        Instant now = Instant.now(clock);
        ItemWrite write = txRunner.required(() -> {
            Dict dict = lockDict(loaded.dictId());
            DictItem item = requireItem(itemId);
            item.restore(!dict.isDeleted(), operator, now);
            repository.updateItem(item);
            return new ItemWrite(item.id(), dict.code().value());
        });
        evictItem(write.dictCode, loaded);
    }

    private ItemWrite createItem(long dictId, TenantId tenantId, ItemCode code, CreateItemCommand command,
                                 String operator, Instant now, int itemLimit) {
        Dict dict = lockPresentDict(dictId);
        ItemCodeUsage usage = repository.findItemCodeUsage(dictId, code, tenantId);
        int undeletedCount = repository.countUndeletedItems(dictId, tenantId);
        itemPolicy.checkCreate(tenantId, usage, undeletedCount, itemLimit);
        DictItem item = DictItem.create(dictId, code, command.getName(), tenantId, command.getDescription(),
                command.getSortNo(), operator, now);
        return new ItemWrite(repository.insertItem(item), dict.code().value());
    }

    private Dict lockDict(long dictId) {
        return repository.lockDict(dictId)
                .orElseThrow(() -> notFound(DictErrorCode.DICT_NOT_FOUND, "dictionary was not found"));
    }

    private Dict lockPresentDict(long dictId) {
        Dict dict = lockDict(dictId);
        if (dict.isDeleted()) {
            throw notFound(DictErrorCode.DICT_NOT_FOUND, "dictionary was not found");
        }
        return dict;
    }

    private DictItem requireItem(long itemId) {
        return repository.findItem(itemId)
                .orElseThrow(() -> notFound(DictErrorCode.DICT_ITEM_NOT_FOUND, "dictionary item was not found"));
    }

    private void requirePermission(DictAdminPermission permission, Optional<String> targetTenantId) {
        if (!permissionProvider.hasPermission(permission, targetTenantId)) {
            throw new DictException(DictErrorCode.PERMISSION_DENIED, "permission denied");
        }
    }

    private String requireOperator() {
        String operator = operatorProvider.currentOperatorId();
        if (operator == null || operator.isEmpty() || operator.length() > MAX_OPERATOR_LENGTH
                || !operator.equals(operator.trim())) {
            throw new DictException(DictErrorCode.INVALID_ARGUMENT, "invalid operator");
        }
        return operator;
    }

    private void requireTenantExists(String tenantId) {
        if (tenantDirectory == null) {
            throw new DictException(DictErrorCode.CONFIGURATION_INVALID, "tenant directory is not configured");
        }
        if (!tenantDirectory.exists(tenantId)) {
            throw new DictException(DictErrorCode.TENANT_NOT_FOUND, "tenant was not found");
        }
    }

    private void requireItemDirectory(DictItem item) {
        if (!item.tenantId().isDefault()) {
            requireTenantExists(item.tenantId().value());
        }
    }

    private void evictItem(String dictCode, DictItem item) {
        if (item.tenantId().isDefault()) {
            cache.evictAll(dictCode);
        } else {
            cache.evictTenant(dictCode, item.tenantId().value());
        }
    }

    private static Optional<String> itemScope(DictItem item) {
        if (item.tenantId().isDefault()) {
            return Optional.empty();
        }
        return Optional.of(item.tenantId().value());
    }

    private static TenantId requireExternalTenant(String tenantIdValue) {
        if (tenantIdValue == null) {
            throw new DictException(DictErrorCode.INVALID_ARGUMENT, "tenantId is required");
        }
        return TenantId.of(tenantIdValue);
    }

    private static Optional<String> requireTenantScope(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(TenantId.of(tenantId).value());
    }

    private static void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new DictException(DictErrorCode.INVALID_ARGUMENT, "invalid page");
        }
    }

    private static void requireCommand(Object command) {
        if (command == null) {
            throw new DictException(DictErrorCode.INVALID_ARGUMENT, "command is required");
        }
    }

    private static void requireQuery(Object query) {
        if (query == null) {
            throw new DictException(DictErrorCode.INVALID_ARGUMENT, "query is required");
        }
    }

    private static DictDetail toDetail(Dict dict) {
        return new DictDetail(dict.id().longValue(), dict.code().value(), dict.name(), dict.description(),
                dict.status() == DictStatus.ENABLED, dict.sortNo(), dict.version(), dict.isDeleted(),
                dict.createdBy(), dict.createdAt(), dict.updatedBy(), dict.updatedAt());
    }

    private static DictException notFound(DictErrorCode code, String message) {
        return new DictException(code, message);
    }

    private static final class ItemWrite {
        private final long itemId;
        private final String dictCode;

        private ItemWrite(long itemId, String dictCode) {
            this.itemId = itemId;
            this.dictCode = dictCode;
        }
    }
}
