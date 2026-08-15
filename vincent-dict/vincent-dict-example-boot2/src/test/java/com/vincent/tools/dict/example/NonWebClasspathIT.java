package com.vincent.tools.dict.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DictExampleApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NonWebClasspathIT {
    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        ExampleMysqlSupport.register(registry);
    }

    @Test
    void non_web_context_has_no_controllers_dispatcher_or_embedded_server() {
        assertThat(context.getEnvironment().getProperty("spring.main.web-application-type")).isEqualTo("none");
        assertNoBeans("org.springframework.web.servlet.DispatcherServlet");
        assertNoBeans("org.springframework.boot.web.servlet.server.ServletWebServerFactory");
        assertNoBeans("com.vincent.tools.dict.web.DictAdminController");
        assertNoBeans("com.vincent.tools.dict.web.DictItemAdminController");
        assertNoBeans("com.vincent.tools.dict.web.TenantAdminController");
        assertNoBeans("com.vincent.tools.dict.web.DictAdminPageController");
        assertNoBeans("com.vincent.tools.dict.web.DictAdminResourceHandler");
        assertNoBeans("com.vincent.tools.dict.web.DictAdminPageAuthFilter");
        assertThat(context.getClass().getName()).doesNotContain("WebApplicationContext");
    }

    private void assertNoBeans(String className) {
        try {
            Class<?> type = Class.forName(className);
            assertThat(context.getBeanNamesForType(type)).isEmpty();
        } catch (ClassNotFoundException ignored) {
            // optional MVC types may be absent from a query-only classpath
        }
    }
}
