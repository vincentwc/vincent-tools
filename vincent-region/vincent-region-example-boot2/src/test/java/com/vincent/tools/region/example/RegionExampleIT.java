package com.vincent.tools.region.example;

import com.vincent.tools.region.application.RegionPermission;
import com.vincent.tools.region.application.RegionQueryService;
import com.vincent.tools.host.PermissionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = RegionExampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=servlet")
class RegionExampleIT {
    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RegionQueryService regionQueryService;

    @MockBean
    private PermissionProvider permissionProvider;

    @org.springframework.test.context.DynamicPropertySource
    static void registerDataSource(org.springframework.test.context.DynamicPropertyRegistry registry) {
        ExampleMysqlSupport.register(registry);
    }

    @BeforeEach
    void allowView() {
        when(permissionProvider.hasPermission(any(RegionPermission.class), anyOptional())).thenReturn(Boolean.TRUE);
    }

    @Test
    void queryService_lists_provinces_and_finds_city() {
        assertThat(regionQueryService.listChildren("0")).hasSize(2);
        assertThat(regionQueryService.findByCode("440100")).isPresent();
        assertThat(regionQueryService.findByCode("440100").get().getName()).isEqualTo("广州市");
    }

    @Test
    void admin_api_lists_children_and_finds_region() {
        ResponseEntity<String> children = restTemplate.getForEntity(
                "/vincent/region/admin/api/v1/children?parentCode=440000", String.class);
        assertThat(children.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(children.getBody()).contains("440100");
        assertThat(children.getBody()).contains("广州市");

        ResponseEntity<String> found = restTemplate.getForEntity(
                "/vincent/region/admin/api/v1/440103", String.class);
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody()).contains("荔湾区");
    }

    @Test
    void unauthorized_admin_request_returns403() {
        when(permissionProvider.hasPermission(eq(RegionPermission.REGION_VIEW), eq(Optional.<String>empty())))
                .thenReturn(Boolean.FALSE);
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/vincent/region/admin/api/v1/children", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("PERMISSION_DENIED");
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> anyOptional() {
        return any(Optional.class);
    }
}
