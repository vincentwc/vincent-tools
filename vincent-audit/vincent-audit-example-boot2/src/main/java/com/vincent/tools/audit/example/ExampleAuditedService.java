package com.vincent.tools.audit.example;

import com.vincent.tools.audit.aop.Audited;
import org.springframework.stereotype.Service;

@Service
public class ExampleAuditedService {
    @Audited(action = "CREATE", resourceType = "DEMO_ITEM", resourceId = "#result")
    public String createDemoItem(String code) {
        return "demo-" + code;
    }
}
