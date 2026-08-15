package com.vincent.tools.audit.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vincent.tools.audit.application.AuditRecordView;
import com.vincent.tools.audit.application.AuditSearchQuery;
import com.vincent.tools.audit.application.AuditService;
import com.vincent.tools.common.core.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditAdminControllerTest {
    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");
    private static final String API = "/vincent/audit/admin/api/v1";

    private AuditService auditService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuditAdminController(auditService))
                .setControllerAdvice(new AuditWebExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void search_returns_page_result() throws Exception {
        AuditRecordView record = new AuditRecordView(
                1L, "tenant-a", "operator-1", "UPDATE", "user", "42",
                null, "{\"name\":\"new\"}", "127.0.0.1", "test-agent", "trace-1", NOW);
        PageResult<AuditRecordView> page = new PageResult<>(Collections.singletonList(record), 1, 1, 20);
        when(auditService.search(any(AuditSearchQuery.class))).thenReturn(page);

        mockMvc.perform(get(API + "/records")
                        .param("tenantId", "tenant-a")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.data.items[0].action").value("UPDATE"));

        verify(auditService).search(any(AuditSearchQuery.class));
    }

    @Test
    void search_accepts_optional_filters() throws Exception {
        when(auditService.search(any(AuditSearchQuery.class)))
                .thenReturn(new PageResult<>(Collections.<AuditRecordView>emptyList(), 0, 1, 20));

        mockMvc.perform(get(API + "/records")
                        .param("operatorId", "operator-1")
                        .param("action", "DELETE")
                        .param("resourceType", "user")
                        .param("resourceId", "99")
                        .param("createdFrom", "2026-08-01T00:00:00Z")
                        .param("createdTo", "2026-08-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(auditService).search(any(AuditSearchQuery.class));
    }
}
