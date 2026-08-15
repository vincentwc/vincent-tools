package com.vincent.tools.audit.application;

import com.vincent.tools.audit.application.port.AuditRepository;
import com.vincent.tools.audit.domain.AuditErrorCode;
import com.vincent.tools.audit.domain.AuditException;
import com.vincent.tools.common.core.PageResult;
import com.vincent.tools.host.OperatorProvider;
import com.vincent.tools.host.PermissionProvider;
import com.vincent.tools.host.TenantProvider;
import com.vincent.tools.host.VincentPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultAuditServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String OPERATOR = "operator-a";

    private InMemoryAuditRepository repository;
    private RecordingPermissionProvider permissionProvider;
    private AuditService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAuditRepository();
        permissionProvider = new RecordingPermissionProvider();
        service = new DefaultAuditService(
                repository,
                new FixedOperatorProvider(OPERATOR),
                permissionProvider,
                new FixedTenantProvider("tenant-a"),
                null,
                new AuditLimits(20, 100),
                CLOCK,
                true);
    }

    @Test
    void recordUsesTenantProviderAndOperator() {
        service.record(new AuditRecordCommand("UPDATE", "ORDER", "1001",
                Optional.<String>empty(), "{\"status\":\"NEW\"}", null));

        AuditRecord stored = repository.lastInserted();
        assertThat(stored.getTenantId()).isEqualTo("tenant-a");
        assertThat(stored.getOperatorId()).isEqualTo(OPERATOR);
        assertThat(stored.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void recordUsesExplicitTenantWithoutProviderLookup() {
        service = new DefaultAuditService(
                repository,
                new FixedOperatorProvider(OPERATOR),
                permissionProvider,
                null,
                null,
                new AuditLimits(20, 100),
                CLOCK,
                true);

        service.record(new AuditRecordCommand("UPDATE", "ORDER", "1001",
                Optional.of("tenant-b"), null, null));

        assertThat(repository.lastInserted().getTenantId()).isEqualTo("tenant-b");
    }

    @Test
    void missingTenantContextIsRejected() {
        service = new DefaultAuditService(
                repository,
                new FixedOperatorProvider(OPERATOR),
                permissionProvider,
                new FixedTenantProvider(null),
                null,
                new AuditLimits(20, 100),
                CLOCK,
                true);

        assertThatThrownBy(() -> service.record(new AuditRecordCommand("UPDATE", "ORDER", "1001",
                Optional.<String>empty(), null, null)))
                .isInstanceOf(AuditException.class)
                .extracting("code").isEqualTo(AuditErrorCode.TENANT_CONTEXT_MISSING);
    }

    @Test
    void invalidOperatorIsRejected() {
        service = new DefaultAuditService(
                repository,
                new FixedOperatorProvider(" "),
                permissionProvider,
                new FixedTenantProvider("tenant-a"),
                null,
                new AuditLimits(20, 100),
                CLOCK,
                true);

        assertThatThrownBy(() -> service.record(new AuditRecordCommand("UPDATE", "ORDER", "1001",
                Optional.<String>empty(), null, null)))
                .isInstanceOf(AuditException.class)
                .extracting("code").isEqualTo(AuditErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void failFastFalseSwallowsRepositoryFailures() {
        repository.failNextInsert(new AuditException(AuditErrorCode.INVALID_ARGUMENT, "payload too large"));
        service = new DefaultAuditService(
                repository,
                new FixedOperatorProvider(OPERATOR),
                permissionProvider,
                new FixedTenantProvider("tenant-a"),
                null,
                new AuditLimits(20, 100),
                CLOCK,
                false);

        service.record(new AuditRecordCommand("UPDATE", "ORDER", "1001",
                Optional.<String>empty(), null, null));

        assertThat(repository.insertCount()).isEqualTo(1);
    }

    @Test
    void searchChecksCrossTenantPermissionWhenTenantMissing() {
        service.search(new AuditSearchQuery(Optional.<String>empty(), Optional.<String>empty(),
                Optional.<String>empty(), Optional.<String>empty(), Optional.<String>empty(),
                Optional.<Instant>empty(), Optional.<Instant>empty(), 1, 20));

        assertThat(permissionProvider.checks()).containsExactly(
                new PermissionCheck(AuditPermission.AUDIT_VIEW, Optional.<String>empty()));
    }

    @Test
    void searchChecksTenantScopedPermission() {
        service.search(new AuditSearchQuery(Optional.of("tenant-b"), Optional.<String>empty(),
                Optional.<String>empty(), Optional.<String>empty(), Optional.<String>empty(),
                Optional.<Instant>empty(), Optional.<Instant>empty(), 1, 20));

        assertThat(permissionProvider.checks()).containsExactly(
                new PermissionCheck(AuditPermission.AUDIT_VIEW, Optional.of("tenant-b")));
    }

    @Test
    void searchRejectsOversizedPage() {
        assertThatThrownBy(() -> service.search(new AuditSearchQuery(Optional.<String>empty(),
                Optional.<String>empty(), Optional.<String>empty(), Optional.<String>empty(),
                Optional.<String>empty(), Optional.<Instant>empty(), Optional.<Instant>empty(), 1, 101)))
                .isInstanceOf(AuditException.class)
                .extracting("code").isEqualTo(AuditErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void searchRejectsMissingPermission() {
        permissionProvider.deny(AuditPermission.AUDIT_VIEW, Optional.<String>empty());

        assertThatThrownBy(() -> service.search(new AuditSearchQuery(Optional.<String>empty(),
                Optional.<String>empty(), Optional.<String>empty(), Optional.<String>empty(),
                Optional.<String>empty(), Optional.<Instant>empty(), Optional.<Instant>empty(), 1, 20)))
                .isInstanceOf(AuditException.class)
                .extracting("code").isEqualTo(AuditErrorCode.PERMISSION_DENIED);
    }

    private static final class FixedOperatorProvider implements OperatorProvider {
        private final String operatorId;

        private FixedOperatorProvider(String operatorId) {
            this.operatorId = operatorId;
        }

        @Override
        public String currentOperatorId() {
            return operatorId;
        }
    }

    private static final class FixedTenantProvider implements TenantProvider {
        private final String tenantId;

        private FixedTenantProvider(String tenantId) {
            this.tenantId = tenantId;
        }

        @Override
        public Optional<String> currentTenantId() {
            return Optional.ofNullable(tenantId);
        }
    }

    private static final class RecordingPermissionProvider implements PermissionProvider {
        private final List<PermissionCheck> checks = new ArrayList<PermissionCheck>();
        private PermissionCheck denied;

        @Override
        public boolean hasPermission(VincentPermission permission, Optional<String> targetTenantId) {
            PermissionCheck check = new PermissionCheck((AuditPermission) permission, targetTenantId);
            checks.add(check);
            return denied == null || !denied.equals(check);
        }

        private void deny(AuditPermission permission, Optional<String> targetTenantId) {
            denied = new PermissionCheck(permission, targetTenantId);
        }

        private List<PermissionCheck> checks() {
            return checks;
        }
    }

    private static final class PermissionCheck {
        private final AuditPermission permission;
        private final Optional<String> targetTenantId;

        private PermissionCheck(AuditPermission permission, Optional<String> targetTenantId) {
            this.permission = permission;
            this.targetTenantId = targetTenantId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PermissionCheck)) {
                return false;
            }
            PermissionCheck that = (PermissionCheck) other;
            return permission == that.permission && Objects.equals(targetTenantId, that.targetTenantId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(permission, targetTenantId);
        }
    }

    private static final class InMemoryAuditRepository implements AuditRepository {
        private AuditRecord lastInserted;
        private int insertCount;
        private RuntimeException nextFailure;

        @Override
        public void insert(AuditRecord record) {
            insertCount++;
            if (nextFailure != null) {
                RuntimeException failure = nextFailure;
                nextFailure = null;
                throw failure;
            }
            lastInserted = record;
        }

        @Override
        public PageResult<AuditRecordView> search(AuditSearchQuery query) {
            return new PageResult<AuditRecordView>(Collections.<AuditRecordView>emptyList(), 0L,
                    query.getPage(), query.getSize());
        }

        private AuditRecord lastInserted() {
            return lastInserted;
        }

        private int insertCount() {
            return insertCount;
        }

        private void failNextInsert(RuntimeException failure) {
            nextFailure = failure;
        }
    }
}
