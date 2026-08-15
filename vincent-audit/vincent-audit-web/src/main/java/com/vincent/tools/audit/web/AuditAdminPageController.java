package com.vincent.tools.audit.web;

import com.vincent.tools.audit.application.AuditPermission;
import com.vincent.tools.audit.domain.AuditErrorCode;
import com.vincent.tools.audit.domain.AuditException;
import com.vincent.tools.host.PermissionProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@RestController
public class AuditAdminPageController {
    private final PermissionProvider permissionProvider;
    private final AuditAdminWebProperties properties;

    public AuditAdminPageController(PermissionProvider permissionProvider) {
        this(permissionProvider, new AuditAdminWebProperties());
    }

    public AuditAdminPageController(PermissionProvider permissionProvider, AuditAdminWebProperties properties) {
        this.permissionProvider = permissionProvider;
        this.properties = properties == null ? new AuditAdminWebProperties() : properties;
    }

    @GetMapping({
            "${vincent.audit.admin.base-path:/audit-admin}",
            "${vincent.audit.admin.base-path:/audit-admin}/"
    })
    public ResponseEntity<Resource> page() {
        if (!permissionProvider.hasPermission(AuditPermission.AUDIT_VIEW, Optional.<String>empty())) {
            throw new AuditException(AuditErrorCode.PERMISSION_DENIED, "permission denied");
        }
        try {
            byte[] html = AuditAdminSpaHtml.readAndInject(properties.getApiPath(), properties.getBasePath())
                    .getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                    .cacheControl(CacheControl.noStore())
                    .body(new ByteArrayResource(html));
        } catch (IOException ex) {
            throw new AuditException(AuditErrorCode.CONFIGURATION_INVALID, "admin index.html is missing");
        }
    }
}
