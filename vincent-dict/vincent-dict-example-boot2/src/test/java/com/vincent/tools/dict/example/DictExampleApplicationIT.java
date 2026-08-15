package com.vincent.tools.dict.example;

import com.vincent.tools.dict.application.DictItemView;
import com.vincent.tools.dict.application.DictQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DictExampleApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DictExampleApplicationIT {
    @Autowired
    private DictQueryService queryService;

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        ExampleMysqlSupport.register(registry);
    }

    @Test
    void lists_default_and_current_tenant_effective_items() {
        assertThat(queryService.listEffectiveItems("ORDER_STATUS"))
                .extracting(DictItemView::getCode)
                .containsExactly("CREATED", "WAIT_CONFIRM");
    }
}
