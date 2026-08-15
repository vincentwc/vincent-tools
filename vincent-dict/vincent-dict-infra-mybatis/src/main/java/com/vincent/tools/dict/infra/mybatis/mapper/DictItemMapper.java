package com.vincent.tools.dict.infra.mybatis.mapper;

import com.vincent.tools.dict.infra.mybatis.po.DictItemPo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DictItemMapper {
    List<DictItemPo> selectEffectiveItems(@Param("dictId") long dictId, @Param("tenantId") String tenantId);

    DictItemPo selectById(@Param("id") long id);

    List<DictItemPo> selectCodeUsage(@Param("dictId") long dictId, @Param("code") String code);

    int countUndeleted(@Param("dictId") long dictId);

    int countUndeletedByTenant(@Param("dictId") long dictId, @Param("tenantId") String tenantId);

    int insert(DictItemPo po);

    int update(@Param("po") DictItemPo po, @Param("expectedVersion") int expectedVersion);

    long countPage(@Param("dictId") long dictId, @Param("tenantId") String tenantId, @Param("code") String code,
                   @Param("name") String name, @Param("status") Integer status,
                   @Param("includeDeleted") boolean includeDeleted);

    List<DictItemPo> selectPage(@Param("dictId") long dictId, @Param("tenantId") String tenantId,
                                @Param("code") String code, @Param("name") String name, @Param("status") Integer status,
                                @Param("includeDeleted") boolean includeDeleted, @Param("offset") int offset,
                                @Param("limit") int limit);
}
