package com.vincent.tools.audit.application;

import com.vincent.tools.audit.application.port.AuditRepository;
import com.vincent.tools.audit.domain.AuditErrorCode;
import com.vincent.tools.audit.domain.AuditException;
import com.vincent.tools.audit.domain.AuditFieldLimits;
import com.vincent.tools.common.core.PageResult;
import com.vincent.tools.host.OperatorProvider;
import com.vincent.tools.host.PermissionProvider;
import com.vincent.tools.host.TenantProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class DefaultAuditService implements AuditService {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAuditService.class);

    private final AuditRepository repository;
    private final OperatorProvider operatorProvider;
    private final PermissionProvider permissionProvider;
    private final TenantProvider tenantProvider;
    private final AuditContextProvider contextProvider;
    private final AuditLimits limits;
    private final Clock clock;
    private final boolean failFast;

    public DefaultAuditService(AuditRepository repository, OperatorProvider operatorProvider,
                               PermissionProvider permissionProvider, TenantProvider tenantProvider,
                               AuditContextProvider contextProvider, AuditLimits limits, Clock clock,
                               boolean failFast) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.operatorProvider = Objects.requireNonNull(operatorProvider, "operatorProvider");
        this.permissionProvider = Objects.requireNonNull(permissionProvider, "permissionProvider");
        this.tenantProvider = tenantProvider;
        this.contextProvider = contextProvider;
        this.limits = Objects.requireNonNull(limits, "limits");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.failFast = failFast;
    }

    @Override
    public void record(AuditRecordCommand command) {
        requireCommand(command);
        AuditRecord record = buildRecord(command);
        try {
            repository.insert(record);
        } catch (RuntimeException ex) {
            if (failFast) {
                throw ex;
            }
            LOG.error("audit record failed: action={}, resourceType={}, resourceId={}",
                    record.getAction(), record.getResourceType(), record.getResourceId(), ex);
        }
    }

    @Override
    public PageResult<AuditRecordView> search(AuditSearchQuery query) {
        requireQuery(query);
        Optional<String> tenantScope = query.getTenantId()
                .map(this::validateTenantFilter);
        requirePermission(AuditPermission.AUDIT_VIEW, tenantScope);
        validatePage(query.getPage(), query.getSize());
        return repository.search(query);
    }

    private AuditRecord buildRecord(AuditRecordCommand command) {
        String tenantId = resolveTenantId(command.getTargetTenantId());
        String operatorId = requireOperator();
        String action = AuditFieldLimits.requireNonBlank(command.getAction(), "action",
                AuditFieldLimits.MAX_ACTION_LENGTH);
        String resourceType = AuditFieldLimits.requireNonBlank(command.getResourceType(), "resourceType",
                AuditFieldLimits.MAX_RESOURCE_TYPE_LENGTH);
        String resourceId = AuditFieldLimits.requireNonBlank(command.getResourceId(), "resourceId",
                AuditFieldLimits.MAX_RESOURCE_ID_LENGTH);
        AuditFieldLimits.requireNonBlank(tenantId, "tenantId", AuditFieldLimits.MAX_TENANT_ID_LENGTH);

        String clientIp = null;
        String userAgent = null;
        String traceId = null;
        if (contextProvider != null) {
            clientIp = AuditFieldLimits.optionalBounded(contextProvider.clientIp(), "clientIp",
                    AuditFieldLimits.MAX_CLIENT_IP_LENGTH);
            userAgent = AuditFieldLimits.optionalBounded(contextProvider.userAgent(), "userAgent",
                    AuditFieldLimits.MAX_USER_AGENT_LENGTH);
            traceId = AuditFieldLimits.optionalBounded(contextProvider.traceId(), "traceId",
                    AuditFieldLimits.MAX_TRACE_ID_LENGTH);
        }

        return new AuditRecord(tenantId, operatorId, action, resourceType, resourceId,
                command.getBeforeJson(), command.getAfterJson(), clientIp, userAgent, traceId,
                Instant.now(clock));
    }

    private String resolveTenantId(Optional<String> targetTenantId) {
        if (targetTenantId != null && targetTenantId.isPresent()) {
            return targetTenantId.get();
        }
        if (tenantProvider == null) {
            throw new AuditException(AuditErrorCode.TENANT_CONTEXT_MISSING, "tenant context is missing");
        }
        return tenantProvider.currentTenantId()
                .orElseThrow(() -> new AuditException(AuditErrorCode.TENANT_CONTEXT_MISSING,
                        "tenant context is missing"));
    }

    private String validateTenantFilter(String tenantId) {
        return AuditFieldLimits.requireNonBlank(tenantId, "tenantId", AuditFieldLimits.MAX_TENANT_ID_LENGTH);
    }

    private void requirePermission(AuditPermission permission, Optional<String> targetTenantId) {
        if (!permissionProvider.hasPermission(permission, targetTenantId)) {
            throw new AuditException(AuditErrorCode.PERMISSION_DENIED, "permission denied");
        }
    }

    private String requireOperator() {
        String operator = operatorProvider.currentOperatorId();
        if (operator == null || operator.isEmpty() || operator.length() > AuditFieldLimits.MAX_OPERATOR_ID_LENGTH
                || !operator.equals(operator.trim())) {
            throw new AuditException(AuditErrorCode.INVALID_ARGUMENT, "invalid operator");
        }
        return operator;
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > limits.getMaxPageSize()) {
            throw new AuditException(AuditErrorCode.INVALID_ARGUMENT, "invalid page");
        }
    }

    private static void requireCommand(AuditRecordCommand command) {
        if (command == null) {
            throw new AuditException(AuditErrorCode.INVALID_ARGUMENT, "command is required");
        }
    }

    private static void requireQuery(AuditSearchQuery query) {
        if (query == null) {
            throw new AuditException(AuditErrorCode.INVALID_ARGUMENT, "query is required");
        }
    }
}
