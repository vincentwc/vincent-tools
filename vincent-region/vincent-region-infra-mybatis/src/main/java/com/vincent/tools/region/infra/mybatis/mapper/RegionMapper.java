package com.vincent.tools.region.infra.mybatis.mapper;

import com.vincent.tools.region.infra.mybatis.po.RegionPo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RegionMapper {
    RegionPo selectByCode(@Param("code") String code);

    List<RegionPo> selectByParentCode(@Param("parentCode") String parentCode);
}
