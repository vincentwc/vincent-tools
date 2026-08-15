package com.vincent.tools.dict.infra.mybatis.mapper;

import com.vincent.tools.dict.infra.mybatis.po.DictPo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DictMapper {
    DictPo selectPresentByCode(@Param("code") String code);

    DictPo selectById(@Param("id") long id);

    DictPo selectByIdForUpdate(@Param("id") long id);

    int countByCode(@Param("code") String code);

    int insert(DictPo po);

    int update(@Param("po") DictPo po, @Param("expectedVersion") int expectedVersion);

    long countPage(@Param("code") String code, @Param("name") String name, @Param("status") Integer status,
                   @Param("includeDeleted") boolean includeDeleted);

    List<DictPo> selectPage(@Param("code") String code, @Param("name") String name, @Param("status") Integer status,
                            @Param("includeDeleted") boolean includeDeleted, @Param("offset") int offset,
                            @Param("limit") int limit);
}
