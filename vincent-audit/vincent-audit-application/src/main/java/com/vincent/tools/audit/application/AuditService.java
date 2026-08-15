package com.vincent.tools.audit.application;

import com.vincent.tools.common.core.PageResult;

import java.util.Optional;

public interface AuditService {
    void record(AuditRecordCommand command);

    PageResult<AuditRecordView> search(AuditSearchQuery query);
}
