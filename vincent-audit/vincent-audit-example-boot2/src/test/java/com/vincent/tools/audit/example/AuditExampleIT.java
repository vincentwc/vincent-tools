package com.vincent.tools.audit.example;

import com.vincent.tools.audit.application.AuditPermission;
import com.vincent.tools.audit.application.AuditRecordCommand;
import com.vincent.tools.audit.application.AuditService;
import com.vincent.tools.host.PermissionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = AuditExampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.web-application-type=servlet",
                "vincent.audit.admin.enabled=true"
        })
class AuditExampleIT {
    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AuditService auditService;

    @MockBean
    private PermissionProvider permissionProvider;

    @org.springframework.test.context.DynamicPropertySource
    static void registerDataSource(org.springframework.test.context.DynamicPropertyRegistry registry) {
        ExampleMysqlSupport.register(registry);
    }

    @BeforeEach
    void allowView() {
        when(permissionProvider.hasPermission(any(AuditPermission.class), anyOptional())).thenReturn(Boolean.TRUE);
    }

    @Test
    void recordAndSearchThroughAdminApi() {
        auditService.record(new AuditRecordCommand("UPDATE", "ORDER", "9001",
                Optional.of("tenant-a"), "{\"status\":\"NEW\"}", "{\"status\":\"DONE\"}"));

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/vincent/audit/admin/api/v1/records?tenantId=tenant-a&action=UPDATE", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("9001");
        assertThat(response.getBody()).contains("UPDATE");
    }

    @Test
    void starterContainsUnpackedAdminIndexHtml() throws Exception {
        assertThat(ExampleMysqlSupport.starterAdminIndexHtml()).isFile();
        String html = new String(Files.readAllBytes(ExampleMysqlSupport.starterAdminIndexHtml().toPath()),
                StandardCharsets.UTF_8);
        assertThat(html).contains("Vincent Audit Admin");
    }

    @Test
    void authorizedRequestReturnsSpaWithRuntimeConfig() {
        ResponseEntity<String> response = restTemplate.getForEntity("/audit-admin", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String html = response.getBody();
        assertThat(html).contains("Vincent Audit Admin");
        assertThat(html).contains("assets/");
        assertThat(html).contains("<base href=\"/audit-admin/\">");
        assertThat(html).contains("window.__VIN_AUDIT_CONFIG__");
        assertThat(html).contains("\"apiPath\":\"/vincent/audit/admin/api/v1\"");
        int configAt = html.indexOf("window.__VIN_AUDIT_CONFIG__");
        assertThat(configAt).isGreaterThanOrEqualTo(0);
    }

    @Test
    void unauthorizedRequestReturns403() {
        when(permissionProvider.hasPermission(eq(AuditPermission.AUDIT_VIEW), eq(Optional.<String>empty())))
                .thenReturn(Boolean.FALSE);
        ResponseEntity<String> response = restTemplate.getForEntity("/audit-admin", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("PERMISSION_DENIED");
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> anyOptional() {
        return any(Optional.class);
    }
}
