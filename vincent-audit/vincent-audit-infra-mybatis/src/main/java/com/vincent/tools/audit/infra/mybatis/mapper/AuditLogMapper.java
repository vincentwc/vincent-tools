package com.vincent.tools.audit.infra.mybatis.mapper;

import com.vincent.tools.audit.infra.mybatis.po.AuditLogPo;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

public interface AuditLogMapper {
    int insert(AuditLogPo po);

    long countSearch(@Param("tenantId") String tenantId, @Param("operatorId") String operatorId,
                     @Param("action") String action, @Param("resourceType") String resourceType,
                     @Param("resourceId") String resourceId, @Param("createdFrom") Date createdFrom,
                     @Param("createdTo") Date createdTo);

    List<AuditLogPo> selectSearch(@Param("tenantId") String tenantId, @Param("operatorId") String operatorId,
                                  @Param("action") String action, @Param("resourceType") String resourceType,
                                  @Param("resourceId") String resourceId, @Param("createdFrom") Date createdFrom,
                                  @Param("createdTo") Date createdTo, @Param("offset") int offset,
                                  @Param("limit") int limit);
}
