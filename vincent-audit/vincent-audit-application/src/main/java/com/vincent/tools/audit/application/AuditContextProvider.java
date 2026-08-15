package com.vincent.tools.audit.application;

import java.util.Optional;

public interface AuditContextProvider {
    String clientIp();

    String userAgent();

    String traceId();
}
