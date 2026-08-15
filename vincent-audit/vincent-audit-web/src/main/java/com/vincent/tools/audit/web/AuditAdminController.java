package com.vincent.tools.audit.web;

import com.vincent.tools.audit.application.AuditPermission;
import com.vincent.tools.audit.application.AuditRecordView;
import com.vincent.tools.audit.application.AuditSearchQuery;
import com.vincent.tools.audit.application.AuditService;
import com.vincent.tools.common.core.PageResult;
import com.vincent.tools.common.web.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;

@RestController
@RequestMapping("${vincent.audit.admin.api-path:/vincent/audit/admin/api/v1}")
public class AuditAdminController {
    private final AuditService auditService;

    public AuditAdminController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/records")
    public ApiResponse<PageResult<AuditRecordView>> search(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String operatorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(auditService.search(new AuditSearchQuery(
                optional(tenantId),
                optional(operatorId),
                optional(action),
                optional(resourceType),
                optional(resourceId),
                Optional.ofNullable(createdFrom),
                Optional.ofNullable(createdTo),
                page,
                size)));
    }

    private static Optional<String> optional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }
}
