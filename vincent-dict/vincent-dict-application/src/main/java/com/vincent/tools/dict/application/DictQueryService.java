package com.vincent.tools.dict.application;

import java.util.List;
import java.util.Optional;

public interface DictQueryService {
    List<DictItemView> listEffectiveItems(String dictCode);

    List<DictItemView> listEffectiveItems(String dictCode, String tenantId);

    Optional<DictItemView> findEffectiveItem(String dictCode, String itemCode);

    Optional<DictItemView> findEffectiveItem(String dictCode, String itemCode, String tenantId);
}
