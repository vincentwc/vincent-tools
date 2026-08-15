package com.vincent.tools.dict.application;

import com.vincent.tools.dict.application.port.DictCache;
import com.vincent.tools.dict.application.port.DictQueryRepository;
import com.vincent.tools.host.TenantProvider;
import com.vincent.tools.dict.domain.DictCode;
import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;
import com.vincent.tools.dict.domain.ItemCode;
import com.vincent.tools.dict.domain.TenantId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class DefaultDictQueryService implements DictQueryService {
    private static final Comparator<EffectiveItemData> EFFECTIVE_ITEM_ORDER =
            new Comparator<EffectiveItemData>() {
                @Override
                public int compare(EffectiveItemData left, EffectiveItemData right) {
                    int sortNoComparison = Integer.compare(left.getSortNo(), right.getSortNo());
                    if (sortNoComparison != 0) {
                        return sortNoComparison;
                    }
                    int codeComparison = left.getCode().compareTo(right.getCode());
                    if (codeComparison != 0) {
                        return codeComparison;
                    }
                    return Long.compare(left.getId(), right.getId());
                }
            };

    private final DictQueryRepository repository;
    private final TenantProvider tenantProvider;
    private final DictCache cache;
    private final DictLimits limits;

    public DefaultDictQueryService(DictQueryRepository repository, TenantProvider tenantProvider, DictCache cache,
                                   DictLimits limits) {
        this.repository = repository;
        this.tenantProvider = tenantProvider;
        this.cache = cache;
        this.limits = limits;
    }

    @Override
    public List<DictItemView> listEffectiveItems(String dictCode) {
        return loadEffectiveItems(DictCode.of(dictCode), resolveCurrentTenant());
    }

    @Override
    public List<DictItemView> listEffectiveItems(String dictCode, String tenantId) {
        return loadEffectiveItems(DictCode.of(dictCode), TenantId.of(tenantId).value());
    }

    @Override
    public Optional<DictItemView> findEffectiveItem(String dictCode, String itemCode) {
        return findByCode(listEffectiveItems(dictCode), ItemCode.of(itemCode).value());
    }

    @Override
    public Optional<DictItemView> findEffectiveItem(String dictCode, String itemCode, String tenantId) {
        return findByCode(listEffectiveItems(dictCode, tenantId), ItemCode.of(itemCode).value());
    }

    private List<DictItemView> loadEffectiveItems(final DictCode dictCode, final String tenantId) {
        List<DictItemView> loaded = cache.load(dictCode.value(), tenantId,
                () -> loadFromRepository(dictCode, tenantId));
        return Collections.unmodifiableList(new ArrayList<DictItemView>(loaded));
    }

    private List<DictItemView> loadFromRepository(DictCode dictCode, String tenantId) {
        EffectiveDictData data = repository.findEffectiveData(dictCode, tenantId)
                .orElseThrow(() -> new DictException(DictErrorCode.DICT_NOT_FOUND, "dictionary was not found"));
        if (!data.isEnabled()) {
            return Collections.emptyList();
        }
        if (data.getItems().size() > limits.getMaxEffectiveItems()) {
            throw new DictException(DictErrorCode.CONFIGURATION_INVALID, "effective item limit exceeded");
        }

        List<EffectiveItemData> orderedItems = new ArrayList<EffectiveItemData>(data.getItems());
        Collections.sort(orderedItems, EFFECTIVE_ITEM_ORDER);
        List<DictItemView> views = new ArrayList<DictItemView>(orderedItems.size());
        for (EffectiveItemData item : orderedItems) {
            views.add(new DictItemView(item.getCode(), item.getName(), item.getDescription(), item.getSortNo(),
                    item.getSource()));
        }
        return Collections.unmodifiableList(views);
    }

    private String resolveCurrentTenant() {
        Optional<String> currentTenant = tenantProvider.currentTenantId();
        if (!currentTenant.isPresent()) {
            throw new DictException(DictErrorCode.TENANT_CONTEXT_MISSING, "tenant context is missing");
        }
        String tenantId = currentTenant.get();
        if (tenantProvider instanceof SingleTenantProvider && TenantId.DEFAULT_VALUE.equals(tenantId)) {
            return tenantId;
        }
        return TenantId.of(tenantId).value();
    }

    private static Optional<DictItemView> findByCode(List<DictItemView> items, String itemCode) {
        for (DictItemView item : items) {
            if (item.getCode().equals(itemCode)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }
}
