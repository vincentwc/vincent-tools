package com.vincent.tools.audit.infra.mybatis;

import com.vincent.tools.audit.application.AuditRecord;
import com.vincent.tools.audit.application.AuditRecordView;
import com.vincent.tools.audit.application.AuditSearchQuery;
import com.vincent.tools.audit.application.port.AuditRepository;
import com.vincent.tools.audit.domain.AuditErrorCode;
import com.vincent.tools.audit.domain.AuditException;
import com.vincent.tools.audit.infra.mybatis.mapper.AuditLogMapper;
import com.vincent.tools.audit.infra.mybatis.po.AuditLogPo;
import com.vincent.tools.common.core.PageResult;
import org.apache.ibatis.exceptions.PersistenceException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MybatisAuditRepository implements AuditRepository {
    private final AuditLogMapper auditLogMapper;

    public MybatisAuditRepository(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public void insert(AuditRecord record) {
        try {
            auditLogMapper.insert(toPo(record));
        } catch (PersistenceException ex) {
            throw new AuditException(AuditErrorCode.INVALID_ARGUMENT, "audit record insert failed", ex);
        }
    }

    @Override
    public PageResult<AuditRecordView> search(AuditSearchQuery query) {
        String tenantId = query.getTenantId().orElse(null);
        String operatorId = query.getOperatorId().orElse(null);
        String action = query.getAction().orElse(null);
        String resourceType = query.getResourceType().orElse(null);
        String resourceId = query.getResourceId().orElse(null);
        Date createdFrom = toDate(query.getCreatedFrom().orElse(null));
        Date createdTo = toDate(query.getCreatedTo().orElse(null));
        int offset = (query.getPage() - 1) * query.getSize();

        long total = auditLogMapper.countSearch(tenantId, operatorId, action, resourceType, resourceId,
                createdFrom, createdTo);
        List<AuditLogPo> rows = auditLogMapper.selectSearch(tenantId, operatorId, action, resourceType, resourceId,
                createdFrom, createdTo, offset, query.getSize());

        List<AuditRecordView> items = new ArrayList<AuditRecordView>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            items.add(toView(rows.get(index)));
        }
        return new PageResult<AuditRecordView>(items, total, query.getPage(), query.getSize());
    }

    private static AuditLogPo toPo(AuditRecord record) {
        AuditLogPo po = new AuditLogPo();
        po.setTenantId(record.getTenantId());
        po.setOperatorId(record.getOperatorId());
        po.setAction(record.getAction());
        po.setResourceType(record.getResourceType());
        po.setResourceId(record.getResourceId());
        po.setBeforeJson(record.getBeforeJson());
        po.setAfterJson(record.getAfterJson());
        po.setClientIp(record.getClientIp());
        po.setUserAgent(record.getUserAgent());
        po.setTraceId(record.getTraceId());
        po.setCreatedAt(toDate(record.getCreatedAt()));
        return po;
    }

    private static AuditRecordView toView(AuditLogPo po) {
        return new AuditRecordView(
                po.getId().longValue(),
                po.getTenantId(),
                po.getOperatorId(),
                po.getAction(),
                po.getResourceType(),
                po.getResourceId(),
                po.getBeforeJson(),
                po.getAfterJson(),
                po.getClientIp(),
                po.getUserAgent(),
                po.getTraceId(),
                toInstant(po.getCreatedAt()));
    }

    private static Date toDate(Instant instant) {
        if (instant == null) {
            return null;
        }
        return new Date(instant.toEpochMilli());
    }

    private static Instant toInstant(Date date) {
        if (date == null) {
            return null;
        }
        return Instant.ofEpochMilli(date.getTime());
    }
}
