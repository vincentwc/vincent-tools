package com.vincent.tools.dict.infra.mybatis.mapper;

import com.vincent.tools.dict.infra.mybatis.po.DictPo;
import org.apache.ibatis.annotations.Param;

public interface DictMapper {
    DictPo selectPresentByCode(@Param("code") String code);
}
