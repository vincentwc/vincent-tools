package com.vincent.tools.dict.web;

import com.vincent.tools.dict.application.admin.DictAdminService;
import com.vincent.tools.dict.web.dto.ItemRequests;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${vincent.dict.admin.api-path:/vincent/dict/admin/api/v1}")
public class DictItemAdminController {
    private final DictAdminService adminService;

    public DictItemAdminController(DictAdminService adminService) {
        this.adminService = adminService;
    }

    @PutMapping("/items/{itemId}")
    public ApiResponse<Void> updateItem(@PathVariable("itemId") long itemId,
                                        @RequestBody ItemRequests.Update request) {
        adminService.updateItem(itemId, request.toCommand());
        return ApiResponse.ok(null);
    }

    @PatchMapping("/items/{itemId}/status")
    public ApiResponse<Void> changeItemStatus(@PathVariable("itemId") long itemId,
                                              @RequestBody ItemRequests.StatusChange request) {
        adminService.changeItemStatus(itemId, request.requiredEnabled());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<Void> deleteItem(@PathVariable("itemId") long itemId) {
        adminService.deleteItem(itemId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/items/{itemId}/restore")
    public ApiResponse<Void> restoreItem(@PathVariable("itemId") long itemId) {
        adminService.restoreItem(itemId);
        return ApiResponse.ok(null);
    }
}
