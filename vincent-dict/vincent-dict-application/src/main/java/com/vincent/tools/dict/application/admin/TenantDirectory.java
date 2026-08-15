package com.vincent.tools.dict.application.admin;

import com.vincent.tools.common.core.PageResult;

public interface TenantDirectory {
    PageResult<TenantOption> search(String keyword, int page, int size);

    boolean exists(String tenantId);
}
