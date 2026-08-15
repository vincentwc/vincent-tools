package com.vincent.tools.audit.application.port;

import com.vincent.tools.audit.application.AuditRecord;
import com.vincent.tools.audit.application.AuditSearchQuery;
import com.vincent.tools.audit.application.AuditRecordView;
import com.vincent.tools.common.core.PageResult;

public interface AuditRepository {
    void insert(AuditRecord record);

    PageResult<AuditRecordView> search(AuditSearchQuery query);
}
