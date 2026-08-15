package com.vincent.tools.dict.application.port;

import com.vincent.tools.dict.application.EffectiveDictData;
import com.vincent.tools.dict.domain.DictCode;

import java.util.Optional;

public interface DictQueryRepository {
    Optional<EffectiveDictData> findEffectiveData(DictCode dictCode, String tenantId);
}
