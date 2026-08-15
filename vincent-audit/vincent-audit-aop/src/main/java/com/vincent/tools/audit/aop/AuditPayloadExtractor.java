package com.vincent.tools.audit.aop;

import java.lang.reflect.Method;

public interface AuditPayloadExtractor {
    boolean supports(String resourceType);

    AuditPayload extract(Object[] args, Object result, Method method);
}
