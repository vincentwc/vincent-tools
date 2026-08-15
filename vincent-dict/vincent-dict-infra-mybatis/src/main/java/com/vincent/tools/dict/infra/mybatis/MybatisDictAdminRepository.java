package com.vincent.tools.dict.infra.mybatis;

import com.vincent.tools.dict.application.admin.PageResult;
import com.vincent.tools.dict.application.admin.query.DictPageQuery;
import com.vincent.tools.dict.application.admin.query.ItemPageQuery;
import com.vincent.tools.dict.application.admin.view.DictItemDetail;
import com.vincent.tools.dict.application.admin.view.DictSummary;
import com.vincent.tools.dict.application.port.DictAdminRepository;
import com.vincent.tools.dict.domain.Dict;
import com.vincent.tools.dict.domain.DictCode;
import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;
import com.vincent.tools.dict.domain.DictFactory;
import com.vincent.tools.dict.domain.DictItem;
import com.vincent.tools.dict.domain.DictItemFactory;
import com.vincent.tools.dict.domain.DictItemSource;
import com.vincent.tools.dict.domain.DictStatus;
import com.vincent.tools.dict.domain.ItemCode;
import com.vincent.tools.dict.domain.ItemCodeUsage;
import com.vincent.tools.dict.domain.ItemStatus;
import com.vincent.tools.dict.domain.TenantId;
import com.vincent.tools.dict.infra.mybatis.mapper.DictItemMapper;
import com.vincent.tools.dict.infra.mybatis.mapper.DictMapper;
import com.vincent.tools.dict.infra.mybatis.po.DictItemPo;
import com.vincent.tools.dict.infra.mybatis.po.DictPo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class MybatisDictAdminRepository implements DictAdminRepository {
    private static final int STATUS_ENABLED = 1;
    private static final int DELETED_YES = 1;

    private final DictMapper dictMapper;
    private final DictItemMapper dictItemMapper;

    public MybatisDictAdminRepository(DictMapper dictMapper, DictItemMapper dictItemMapper) {
        this.dictMapper = dictMapper;
        this.dictItemMapper = dictItemMapper;
    }

    @Override
    public PageResult<DictSummary> pageDicts(DictPageQuery query) {
        Integer status = toStatusFilter(query.getEnabled());
        int offset = (query.getPage() - 1) * query.getSize();
        long total = dictMapper.countPage(query.getCode(), query.getName(), status, query.isIncludeDeleted());
        List<DictPo> rows = dictMapper.selectPage(
                query.getCode(), query.getName(), status, query.isIncludeDeleted(), offset, query.getSize());
        List<DictSummary> items = new ArrayList<DictSummary>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            DictPo row = rows.get(index);
            items.add(new DictSummary(
                    row.getId().longValue(),
                    row.getCode(),
                    row.getName(),
                    row.getDescription(),
                    isEnabled(row.getStatus()),
                    row.getSortNo().intValue(),
                    isDeleted(row.getDeleted())));
        }
        return new PageResult<DictSummary>(items, total, query.getPage(), query.getSize());
    }

    @Override
    public Optional<Dict> findDict(long dictId) {
        return Optional.ofNullable(toDict(dictMapper.selectById(dictId)));
    }

    @Override
    public Optional<Dict> lockDict(long dictId) {
        return Optional.ofNullable(toDict(dictMapper.selectByIdForUpdate(dictId)));
    }

    @Override
    public boolean existsDictCode(DictCode code) {
        return dictMapper.countByCode(code.value()) > 0;
    }

    @Override
    public long insertDict(Dict dict) {
        DictPo po = toDictPo(dict);
        dictMapper.insert(po);
        return po.getId().longValue();
    }

    @Override
    public void updateDict(Dict dict) {
        int updated = dictMapper.update(toDictPo(dict), dict.version());
        if (updated == 0) {
            throw new DictException(DictErrorCode.OPTIMISTIC_LOCK_CONFLICT, "dictionary version conflict");
        }
    }

    @Override
    public PageResult<DictItemDetail> pageItems(long dictId, ItemPageQuery query) {
        String tenantId = resolveTenantId(query.getTenantId());
        Integer status = toStatusFilter(query.getEnabled());
        int offset = (query.getPage() - 1) * query.getSize();
        long total = dictItemMapper.countPage(
                dictId, tenantId, query.getCode(), query.getName(), status, query.isIncludeDeleted());
        List<DictItemPo> rows = dictItemMapper.selectPage(
                dictId, tenantId, query.getCode(), query.getName(), status, query.isIncludeDeleted(),
                offset, query.getSize());
        List<DictItemDetail> items = new ArrayList<DictItemDetail>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            items.add(toItemDetail(rows.get(index)));
        }
        return new PageResult<DictItemDetail>(items, total, query.getPage(), query.getSize());
    }

    @Override
    public Optional<DictItem> findItem(long itemId) {
        return Optional.ofNullable(toItem(dictItemMapper.selectById(itemId)));
    }

    @Override
    public ItemCodeUsage findItemCodeUsage(long dictId, ItemCode code, TenantId tenantId) {
        List<DictItemPo> rows = dictItemMapper.selectCodeUsage(dictId, code.value());
        boolean defaultUsed = false;
        boolean sameTenantUsed = false;
        boolean otherTenantUsed = false;
        for (int index = 0; index < rows.size(); index++) {
            String rowTenantId = rows.get(index).getTenantId();
            if (TenantId.DEFAULT_VALUE.equals(rowTenantId)) {
                defaultUsed = true;
            }
            if (tenantId.value().equals(rowTenantId)) {
                sameTenantUsed = true;
            } else if (!TenantId.DEFAULT_VALUE.equals(rowTenantId)) {
                otherTenantUsed = true;
            }
        }
        return toUsage(defaultUsed, sameTenantUsed, otherTenantUsed);
    }

    @Override
    public int countUndeletedItems(long dictId) {
        return dictItemMapper.countUndeleted(dictId);
    }

    @Override
    public int countUndeletedItems(long dictId, TenantId tenantId) {
        return dictItemMapper.countUndeletedByTenant(dictId, tenantId.value());
    }

    @Override
    public long insertItem(DictItem item) {
        DictItemPo po = toItemPo(item);
        dictItemMapper.insert(po);
        return po.getId().longValue();
    }

    @Override
    public void updateItem(DictItem item) {
        int updated = dictItemMapper.update(toItemPo(item), item.version());
        if (updated == 0) {
            throw new DictException(DictErrorCode.OPTIMISTIC_LOCK_CONFLICT, "dictionary item version conflict");
        }
    }

    private static Dict toDict(DictPo po) {
        if (po == null) {
            return null;
        }
        return DictFactory.rebuild(
                po.getId().longValue(),
                DictCode.of(po.getCode()),
                po.getName(),
                po.getDescription(),
                isEnabled(po.getStatus()) ? DictStatus.ENABLED : DictStatus.DISABLED,
                po.getSortNo().intValue(),
                po.getVersion().intValue(),
                isDeleted(po.getDeleted()),
                po.getCreatedBy(),
                toInstant(po.getCreatedAt()),
                po.getUpdatedBy(),
                toInstant(po.getUpdatedAt()));
    }

    private static DictItem toItem(DictItemPo po) {
        if (po == null) {
            return null;
        }
        return DictItemFactory.rebuild(
                po.getId().longValue(),
                po.getDictId().longValue(),
                ItemCode.of(po.getCode()),
                po.getName(),
                toTenantId(po.getTenantId()),
                po.getDescription(),
                isEnabled(po.getStatus()) ? ItemStatus.ENABLED : ItemStatus.DISABLED,
                po.getSortNo().intValue(),
                po.getVersion().intValue(),
                isDeleted(po.getDeleted()),
                po.getCreatedBy(),
                toInstant(po.getCreatedAt()),
                po.getUpdatedBy(),
                toInstant(po.getUpdatedAt()));
    }

    private static DictItemDetail toItemDetail(DictItemPo po) {
        return new DictItemDetail(
                po.getId().longValue(),
                po.getDictId().longValue(),
                po.getCode(),
                po.getName(),
                po.getTenantId(),
                po.getDescription(),
                isEnabled(po.getStatus()),
                po.getSortNo().intValue(),
                isDeleted(po.getDeleted()),
                TenantId.DEFAULT_VALUE.equals(po.getTenantId()) ? DictItemSource.DEFAULT : DictItemSource.TENANT,
                po.getVersion().intValue(),
                po.getCreatedBy(),
                toInstant(po.getCreatedAt()),
                po.getUpdatedBy(),
                toInstant(po.getUpdatedAt()));
    }

    private static DictPo toDictPo(Dict dict) {
        DictPo po = new DictPo();
        po.setId(dict.id());
        po.setCode(dict.code().value());
        po.setName(dict.name());
        po.setDescription(dict.description());
        po.setStatus(Integer.valueOf(dict.status() == DictStatus.ENABLED ? STATUS_ENABLED : 0));
        po.setSortNo(Integer.valueOf(dict.sortNo()));
        po.setVersion(Integer.valueOf(dict.version()));
        po.setDeleted(Integer.valueOf(dict.isDeleted() ? DELETED_YES : 0));
        po.setCreatedBy(dict.createdBy());
        po.setCreatedAt(toDate(dict.createdAt()));
        po.setUpdatedBy(dict.updatedBy());
        po.setUpdatedAt(toDate(dict.updatedAt()));
        return po;
    }

    private static DictItemPo toItemPo(DictItem item) {
        DictItemPo po = new DictItemPo();
        po.setId(item.id());
        po.setDictId(Long.valueOf(item.dictId()));
        po.setTenantId(item.tenantId().value());
        po.setCode(item.code().value());
        po.setName(item.name());
        po.setDescription(item.description());
        po.setStatus(Integer.valueOf(item.status() == ItemStatus.ENABLED ? STATUS_ENABLED : 0));
        po.setSortNo(Integer.valueOf(item.sortNo()));
        po.setVersion(Integer.valueOf(item.version()));
        po.setDeleted(Integer.valueOf(item.isDeleted() ? DELETED_YES : 0));
        po.setCreatedBy(item.createdBy());
        po.setCreatedAt(toDate(item.createdAt()));
        po.setUpdatedBy(item.updatedBy());
        po.setUpdatedAt(toDate(item.updatedAt()));
        return po;
    }

    private static ItemCodeUsage toUsage(boolean defaultUsed, boolean sameTenantUsed, boolean otherTenantUsed) {
        if (!defaultUsed && !sameTenantUsed && !otherTenantUsed) {
            return ItemCodeUsage.none();
        }
        if (defaultUsed) {
            return ItemCodeUsage.defaultAndTenant(sameTenantUsed, true);
        }
        if (sameTenantUsed) {
            return ItemCodeUsage.tenantOnly(true);
        }
        return ItemCodeUsage.tenantOnly(false);
    }

    private static TenantId toTenantId(String tenantId) {
        if (TenantId.DEFAULT_VALUE.equals(tenantId)) {
            return TenantId.defaultItem();
        }
        return TenantId.of(tenantId);
    }

    private static String resolveTenantId(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            return TenantId.DEFAULT_VALUE;
        }
        return tenantId;
    }

    private static Integer toStatusFilter(Boolean enabled) {
        if (enabled == null) {
            return null;
        }
        return Integer.valueOf(enabled.booleanValue() ? STATUS_ENABLED : 0);
    }

    private static boolean isEnabled(Integer status) {
        return status != null && status.intValue() == STATUS_ENABLED;
    }

    private static boolean isDeleted(Integer deleted) {
        return deleted != null && deleted.intValue() == DELETED_YES;
    }

    private static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    private static Date toDate(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }
}
