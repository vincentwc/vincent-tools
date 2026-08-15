package com.vincent.tools.audit.web;

import com.vincent.tools.audit.domain.AuditErrorCode;
import com.vincent.tools.audit.domain.AuditException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.servlet.ServletException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class AuditWebExceptionHandlerTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(HostConfig.class);
        context.refresh();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void advice_does_not_handle_exceptions_from_non_admin_controller() throws Exception {
        ControllerAdviceBean advice = new ControllerAdviceBean(new AuditWebExceptionHandler());
        assertThat(advice.isApplicableToBeanType(HostController.class)).isFalse();
        assertThat(advice.isApplicableToBeanType(AuditAdminController.class)).isTrue();
        assertThat(advice.isApplicableToBeanType(AuditAdminPageController.class)).isTrue();

        RestControllerAdvice annotation = AuditWebExceptionHandler.class.getAnnotation(RestControllerAdvice.class);
        assertThat(annotation.assignableTypes()).containsExactlyInAnyOrder(
                AuditAdminController.class,
                AuditAdminPageController.class);

        try {
            MvcResult result = mockMvc.perform(get("/host/boom")).andReturn();
            throw new AssertionError("host exception was mapped to component ApiResponse: status="
                    + result.getResponse().getStatus()
                    + " body=" + result.getResponse().getContentAsString());
        } catch (ServletException expected) {
            assertThat(expected.getCause()).isInstanceOf(AuditException.class);
        }
    }

    @RestController
    static class HostController {
        @GetMapping("/host/boom")
        public String boom() {
            throw new AuditException(AuditErrorCode.PERMISSION_DENIED, "denied");
        }
    }

    @Configuration
    @EnableWebMvc
    static class HostConfig {
        @Bean
        HostController hostController() {
            return new HostController();
        }

        @Bean
        AuditWebExceptionHandler auditWebExceptionHandler() {
            return new AuditWebExceptionHandler();
        }
    }
}
