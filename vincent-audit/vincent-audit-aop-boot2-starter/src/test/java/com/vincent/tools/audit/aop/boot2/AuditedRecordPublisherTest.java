package com.vincent.tools.audit.aop.boot2;

import com.vincent.tools.audit.application.AuditRecordCommand;
import com.vincent.tools.audit.application.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuditedRecordPublisherTest {
    private AuditService auditService;
    private AuditedRecordPublisher publisher;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        publisher = new AuditedRecordPublisher(auditService);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishes_immediately_when_after_commit_is_false() {
        AuditRecordCommand command = sampleCommand();
        publisher.publish(command, false);
        verify(auditService).record(command);
    }

    @Test
    void publishes_immediately_when_no_transaction_is_active() {
        AuditRecordCommand command = sampleCommand();
        publisher.publish(command, true);
        verify(auditService).record(command);
    }

    @Test
    void publishes_after_commit_when_transaction_is_active() {
        TransactionSynchronizationManager.initSynchronization();
        AuditRecordCommand command = sampleCommand();
        publisher.publish(command, true);
        verify(auditService, never()).record(command);
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(auditService).record(command);
    }

    private static AuditRecordCommand sampleCommand() {
        return new AuditRecordCommand("CREATE", "ITEM", "1", java.util.Optional.empty(), null, null);
    }
}
