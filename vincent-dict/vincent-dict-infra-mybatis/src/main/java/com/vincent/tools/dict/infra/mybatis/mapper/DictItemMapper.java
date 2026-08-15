package com.vincent.tools.dict.infra.mybatis.mapper;

import com.vincent.tools.dict.infra.mybatis.po.DictItemPo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DictItemMapper {
    List<DictItemPo> selectEffectiveItems(@Param("dictId") long dictId, @Param("tenantId") String tenantId);
}
