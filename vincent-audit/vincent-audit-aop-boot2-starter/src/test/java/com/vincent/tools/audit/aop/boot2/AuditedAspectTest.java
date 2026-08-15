package com.vincent.tools.audit.aop.boot2;

import com.vincent.tools.audit.aop.AuditPayload;
import com.vincent.tools.audit.aop.AuditPayloadExtractor;
import com.vincent.tools.audit.aop.Audited;
import com.vincent.tools.audit.application.AuditRecordCommand;
import com.vincent.tools.audit.application.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuditedAspectTest {
    private AnnotationConfigApplicationContext context;
    private AuditService auditService;
    private SampleAuditedService sampleService;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        context = new AnnotationConfigApplicationContext();
        context.registerBean(AuditService.class, () -> auditService);
        context.register(SampleAspectConfiguration.class);
        context.refresh();
        sampleService = context.getBean(SampleAuditedService.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void records_successful_invocation_with_spel_resource_id() {
        AtomicReference<AuditRecordCommand> captured = new AtomicReference<AuditRecordCommand>();
        org.mockito.Mockito.doAnswer(invocation -> {
            captured.set((AuditRecordCommand) invocation.getArguments()[0]);
            return null;
        }).when(auditService).record(any(AuditRecordCommand.class));

        String created = sampleService.createItem("alpha");
        assertThat(created).isEqualTo("item-alpha");

        AuditRecordCommand command = captured.get();
        assertThat(command.getAction()).isEqualTo("CREATE");
        assertThat(command.getResourceType()).isEqualTo("ITEM");
        assertThat(command.getResourceId()).isEqualTo("item-alpha");
        assertThat(command.getAfterJson()).isEqualTo("{\"code\":\"alpha\"}");
    }

    @Test
    void does_not_record_when_method_throws() {
        try {
            sampleService.fail("boom");
        } catch (IllegalStateException ignored) {
        }
        verify(auditService, never()).record(any(AuditRecordCommand.class));
    }

    @Service
    static class SampleAuditedService {
        @Audited(action = "CREATE", resourceType = "ITEM", resourceId = "#result")
        String createItem(String code) {
            return "item-" + code;
        }

        @Audited(action = "DELETE", resourceType = "ITEM", resourceId = "#itemId")
        void fail(String itemId) {
            throw new IllegalStateException("failed");
        }
    }

    static final class ItemPayloadExtractor implements AuditPayloadExtractor {
        @Override
        public boolean supports(String resourceType) {
            return "ITEM".equals(resourceType);
        }

        @Override
        public AuditPayload extract(Object[] args, Object result, Method method) {
            if (args.length > 0 && args[0] != null) {
                return new AuditPayload(null, "{\"code\":\"" + args[0] + "\"}");
            }
            return new AuditPayload(null, null);
        }
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class SampleAspectConfiguration {
        @Bean
        SampleAuditedService sampleAuditedService() {
            return new SampleAuditedService();
        }

        @Bean
        AuditPayloadExtractor itemPayloadExtractor() {
            return new ItemPayloadExtractor();
        }

        @Bean
        AuditedSpelEvaluator auditedSpelEvaluator() {
            return new AuditedSpelEvaluator();
        }

        @Bean
        AuditedRecordPublisher auditedRecordPublisher(AuditService auditService) {
            return new AuditedRecordPublisher(auditService);
        }

        @Bean
        AuditedAspect auditedAspect(AuditedRecordPublisher publisher,
                                    AuditedSpelEvaluator evaluator,
                                    AuditPayloadExtractor itemPayloadExtractor) {
            return new AuditedAspect(publisher, evaluator, Arrays.asList(itemPayloadExtractor));
        }
    }
}
