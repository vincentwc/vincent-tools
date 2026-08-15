package com.vincent.tools.dict.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = DictExampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.web-application-type=servlet",
                "vincent.dict.enabled=false",
                "vincent.dict.admin.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                        + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        })
class DictAdminDisabledPageIT {
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void disabled_admin_does_not_serve_public_spa() {
        assertNotPublicSpa("/dict-admin");
        assertNotPublicSpa("/dict-admin/index.html");
    }

    private void assertNotPublicSpa(String path) {
        ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);
        boolean spa = response.getStatusCode() == HttpStatus.OK
                && response.getBody() != null
                && response.getBody().contains("Vincent Dict Admin");
        assertThat(spa)
                .as("%s must not be a 200 SPA when admin is disabled", path)
                .isFalse();
    }
}
