package com.vincent.tools.dict.example;

import com.vincent.tools.dict.application.admin.DictAdminPermission;
import com.vincent.tools.dict.application.admin.PermissionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = DictExampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.web-application-type=servlet",
                "vincent.dict.admin.enabled=true"
        })
class DictAdminPageIT {
    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private PermissionProvider permissionProvider;

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        ExampleMysqlSupport.register(registry);
    }

    @BeforeEach
    void allowView() {
        when(permissionProvider.hasPermission(any(DictAdminPermission.class), anyOptional())).thenReturn(Boolean.TRUE);
    }

    @Test
    void starter_contains_unpacked_admin_index_html() throws Exception {
        File unpacked = ExampleMysqlSupport.starterAdminIndexHtml();
        assertThat(unpacked).isFile();
        String html = new String(Files.readAllBytes(unpacked.toPath()), StandardCharsets.UTF_8);
        assertThat(html).contains("Vincent Dict Admin");
        assertThat(html).contains("assets/");
    }

    @Test
    void authorized_request_returns_spa_with_runtime_config_before_bundle() {
        ResponseEntity<String> response = restTemplate.getForEntity("/dict-admin", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String html = response.getBody();
        assertThat(html).contains("Vincent Dict Admin");
        assertThat(html).contains("window.__VIN_DICT_CONFIG__");
        assertThat(html).contains("\"apiPath\":\"/vincent/dict/admin/api/v1\"");
        assertThat(html).contains("\"historyBase\":\"/dict-admin\"");
        int configAt = html.indexOf("window.__VIN_DICT_CONFIG__");
        int bundleAt = html.toLowerCase(Locale.ROOT).indexOf("type=\"module\"");
        assertThat(configAt).isGreaterThanOrEqualTo(0);
        if (bundleAt >= 0) {
            assertThat(configAt).isLessThan(bundleAt);
        }
    }

    @Test
    void unauthorized_request_returns_403() {
        when(permissionProvider.hasPermission(eq(DictAdminPermission.DICT_VIEW), eq(Optional.<String>empty())))
                .thenReturn(Boolean.FALSE);
        ResponseEntity<String> response = restTemplate.getForEntity("/dict-admin", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("PERMISSION_DENIED");
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> anyOptional() {
        return any(Optional.class);
    }
}
