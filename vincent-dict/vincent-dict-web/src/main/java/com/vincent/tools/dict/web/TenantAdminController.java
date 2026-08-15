package com.vincent.tools.dict.web;

import com.vincent.tools.dict.application.admin.DictAdminPermission;
import com.vincent.tools.dict.application.admin.PageResult;
import com.vincent.tools.dict.application.admin.PermissionProvider;
import com.vincent.tools.dict.application.admin.TenantDirectory;
import com.vincent.tools.dict.application.admin.TenantOption;
import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("${vincent.dict.admin.api-path:/vincent/dict/admin/api/v1}")
public class TenantAdminController {
    private static final int MAX_PAGE_SIZE = 100;

    private final TenantDirectory tenantDirectory;
    private final PermissionProvider permissionProvider;

    public TenantAdminController(TenantDirectory tenantDirectory, PermissionProvider permissionProvider) {
        this.tenantDirectory = tenantDirectory;
        this.permissionProvider = permissionProvider;
    }

    @GetMapping("/tenants")
    public ApiResponse<PageResult<TenantOption>> pageTenants(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!permissionProvider.hasPermission(DictAdminPermission.DICT_VIEW, Optional.<String>empty())) {
            throw new DictException(DictErrorCode.PERMISSION_DENIED, "permission denied");
        }
        if (tenantDirectory == null) {
            throw new DictException(DictErrorCode.CONFIGURATION_INVALID, "tenant directory is not configured");
        }
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new DictException(DictErrorCode.INVALID_ARGUMENT, "invalid page");
        }
        String search = keyword;
        if ((search == null || search.length() == 0) && tenantId != null && tenantId.length() > 0) {
            search = tenantId;
        }
        return ApiResponse.ok(tenantDirectory.search(search, page, size));
    }
}
