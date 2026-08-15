package com.vincent.tools.region.web;

import com.vincent.tools.region.application.RegionPermission;
import com.vincent.tools.region.application.RegionQueryService;
import com.vincent.tools.region.application.RegionView;
import com.vincent.tools.region.domain.RegionErrorCode;
import com.vincent.tools.region.domain.RegionException;
import com.vincent.tools.common.web.ApiResponse;
import com.vincent.tools.host.PermissionProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("${vincent.region.admin.api-path:/vincent/region/admin/api/v1}")
public class RegionAdminController {
    private final RegionQueryService queryService;
    private final PermissionProvider permissionProvider;

    public RegionAdminController(RegionQueryService queryService, PermissionProvider permissionProvider) {
        this.queryService = queryService;
        this.permissionProvider = permissionProvider;
    }

    @GetMapping("/{code}")
    public ApiResponse<RegionView> find(@PathVariable String code) {
        requireViewPermission();
        return ApiResponse.ok(queryService.findByCode(code)
                .orElseThrow(() -> new RegionException(RegionErrorCode.REGION_NOT_FOUND, "region not found")));
    }

    @GetMapping("/children")
    public ApiResponse<List<RegionView>> children(@RequestParam(required = false) String parentCode) {
        requireViewPermission();
        return ApiResponse.ok(queryService.listChildren(parentCode));
    }

    private void requireViewPermission() {
        if (!permissionProvider.hasPermission(RegionPermission.REGION_VIEW, Optional.<String>empty())) {
            throw new RegionException(RegionErrorCode.PERMISSION_DENIED, "permission denied");
        }
    }
}
