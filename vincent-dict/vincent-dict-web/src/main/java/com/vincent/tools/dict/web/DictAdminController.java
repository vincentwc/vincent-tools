package com.vincent.tools.dict.web;

import com.vincent.tools.dict.application.admin.DictAdminPermission;
import com.vincent.tools.dict.application.admin.DictAdminService;
import com.vincent.tools.dict.application.admin.PageResult;
import com.vincent.tools.dict.application.admin.PermissionProvider;
import com.vincent.tools.dict.application.admin.TenantDirectory;
import com.vincent.tools.dict.application.admin.query.DictPageQuery;
import com.vincent.tools.dict.application.admin.query.ItemPageQuery;
import com.vincent.tools.dict.application.admin.view.DictDetail;
import com.vincent.tools.dict.application.admin.view.DictItemDetail;
import com.vincent.tools.dict.application.admin.view.DictSummary;
import com.vincent.tools.dict.domain.TenantId;
import com.vincent.tools.dict.web.dto.DictRequests;
import com.vincent.tools.dict.web.dto.ItemRequests;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("${vincent.dict.admin.api-path:/vincent/dict/admin/api/v1}")
public class DictAdminController {
    private final DictAdminService adminService;
    private final PermissionProvider permissionProvider;
    private final TenantDirectory tenantDirectory;

    public DictAdminController(DictAdminService adminService, PermissionProvider permissionProvider,
                               TenantDirectory tenantDirectory) {
        this.adminService = adminService;
        this.permissionProvider = permissionProvider;
        this.tenantDirectory = tenantDirectory;
    }

    @GetMapping("/dicts")
    public ApiResponse<PageResult<DictSummary>> pageDicts(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.pageDicts(
                new DictPageQuery(code, name, enabled, includeDeleted, page, size)));
    }

    @PostMapping("/dicts")
    public ApiResponse<IdPayload> createDict(@RequestBody DictRequests.Create request) {
        return ApiResponse.ok(new IdPayload(adminService.createDict(request.toCommand())));
    }

    @GetMapping("/capabilities")
    public ApiResponse<AdminCapabilities> capabilities(@RequestParam(required = false) String tenantId) {
        Optional<String> scope = Optional.empty();
        if (tenantId != null && tenantId.length() > 0) {
            scope = Optional.of(TenantId.of(tenantId).value());
        }
        Map<String, Boolean> permissions = new LinkedHashMap<String, Boolean>();
        for (DictAdminPermission permission : DictAdminPermission.values()) {
            permissions.put(permission.name(), Boolean.valueOf(permissionProvider.hasPermission(permission, scope)));
        }
        return ApiResponse.ok(new AdminCapabilities(tenantDirectory != null, permissions));
    }

    @GetMapping("/dicts/{dictId}")
    public ApiResponse<DictDetail> getDict(@PathVariable("dictId") long dictId,
                                           @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return ApiResponse.ok(adminService.getDict(dictId, includeDeleted));
    }

    @PutMapping("/dicts/{dictId}")
    public ApiResponse<Void> updateDict(@PathVariable("dictId") long dictId,
                                        @RequestBody DictRequests.Update request) {
        adminService.updateDict(dictId, request.toCommand());
        return ApiResponse.ok(null);
    }

    @PatchMapping("/dicts/{dictId}/status")
    public ApiResponse<Void> changeDictStatus(@PathVariable("dictId") long dictId,
                                              @RequestBody DictRequests.StatusChange request) {
        adminService.changeDictStatus(dictId, request.requiredEnabled());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/dicts/{dictId}")
    public ApiResponse<Void> deleteDict(@PathVariable("dictId") long dictId) {
        adminService.deleteDict(dictId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/dicts/{dictId}/restore")
    public ApiResponse<Void> restoreDict(@PathVariable("dictId") long dictId) {
        adminService.restoreDict(dictId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/dicts/{dictId}/items")
    public ApiResponse<PageResult<DictItemDetail>> pageItems(
            @PathVariable("dictId") long dictId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.pageItems(dictId,
                new ItemPageQuery(tenantId, code, name, enabled, includeDeleted, page, size)));
    }

    @PostMapping("/dicts/{dictId}/items/default")
    public ApiResponse<IdPayload> createDefaultItem(@PathVariable("dictId") long dictId,
                                                    @RequestBody ItemRequests.Create request) {
        return ApiResponse.ok(new IdPayload(adminService.createDefaultItem(dictId, request.toCommand())));
    }

    @PostMapping("/dicts/{dictId}/items/tenant")
    public ApiResponse<IdPayload> createTenantItem(@PathVariable("dictId") long dictId,
                                                   @RequestBody ItemRequests.Create request) {
        return ApiResponse.ok(new IdPayload(
                adminService.createTenantItem(dictId, request.getTenantId(), request.toCommand())));
    }
}
