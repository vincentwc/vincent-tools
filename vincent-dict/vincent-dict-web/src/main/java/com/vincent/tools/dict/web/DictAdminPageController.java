package com.vincent.tools.dict.web;

import com.vincent.tools.dict.application.admin.DictAdminPermission;
import com.vincent.tools.host.PermissionProvider;
import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;
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
public class DictAdminPageController {
    private final PermissionProvider permissionProvider;
    private final DictAdminWebProperties properties;

    public DictAdminPageController(PermissionProvider permissionProvider) {
        this(permissionProvider, new DictAdminWebProperties());
    }

    public DictAdminPageController(PermissionProvider permissionProvider, DictAdminWebProperties properties) {
        this.permissionProvider = permissionProvider;
        this.properties = properties == null ? new DictAdminWebProperties() : properties;
    }

    @GetMapping({
            "${vincent.dict.admin.base-path:/dict-admin}",
            "${vincent.dict.admin.base-path:/dict-admin}/"
    })
    public ResponseEntity<Resource> page() {
        if (!permissionProvider.hasPermission(DictAdminPermission.DICT_VIEW, Optional.<String>empty())) {
            throw new DictException(DictErrorCode.PERMISSION_DENIED, "permission denied");
        }
        try {
            byte[] html = DictAdminSpaHtml.readAndInject(properties.getApiPath(), properties.getBasePath())
                    .getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                    .cacheControl(CacheControl.noStore())
                    .body(new ByteArrayResource(html));
        } catch (IOException ex) {
            throw new DictException(DictErrorCode.CONFIGURATION_INVALID, "admin index.html is missing");
        }
    }
}
