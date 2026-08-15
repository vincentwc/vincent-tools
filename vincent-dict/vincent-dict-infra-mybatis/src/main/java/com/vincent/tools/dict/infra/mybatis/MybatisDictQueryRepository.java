package com.vincent.tools.dict.infra.mybatis;

import com.vincent.tools.dict.application.EffectiveDictData;
import com.vincent.tools.dict.application.EffectiveItemData;
import com.vincent.tools.dict.application.port.DictQueryRepository;
import com.vincent.tools.dict.domain.DictCode;
import com.vincent.tools.dict.domain.DictItemSource;
import com.vincent.tools.dict.domain.TenantId;
import com.vincent.tools.dict.infra.mybatis.mapper.DictItemMapper;
import com.vincent.tools.dict.infra.mybatis.mapper.DictMapper;
import com.vincent.tools.dict.infra.mybatis.po.DictItemPo;
import com.vincent.tools.dict.infra.mybatis.po.DictPo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MybatisDictQueryRepository implements DictQueryRepository {
    private static final int STATUS_ENABLED = 1;

    private final DictMapper dictMapper;
    private final DictItemMapper dictItemMapper;

    public MybatisDictQueryRepository(DictMapper dictMapper, DictItemMapper dictItemMapper) {
        this.dictMapper = dictMapper;
        this.dictItemMapper = dictItemMapper;
    }

    @Override
    public Optional<EffectiveDictData> findEffectiveData(DictCode dictCode, String tenantId) {
        DictPo dict = dictMapper.selectPresentByCode(dictCode.value());
        if (dict == null) {
            return Optional.empty();
        }

        List<DictItemPo> rows = dictItemMapper.selectEffectiveItems(dict.getId(), tenantId);
        List<EffectiveItemData> items = new ArrayList<EffectiveItemData>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            DictItemPo row = rows.get(index);
            items.add(new EffectiveItemData(
                    row.getId().longValue(),
                    row.getCode(),
                    row.getName(),
                    row.getDescription(),
                    row.getSortNo().intValue(),
                    sourceOf(row.getTenantId())));
        }
        return Optional.of(new EffectiveDictData(isEnabled(dict.getStatus()), items));
    }

    private static boolean isEnabled(Integer status) {
        return status != null && status.intValue() == STATUS_ENABLED;
    }

    private static DictItemSource sourceOf(String tenantId) {
        if (TenantId.DEFAULT_VALUE.equals(tenantId)) {
            return DictItemSource.DEFAULT;
        }
        return DictItemSource.TENANT;
    }
}
