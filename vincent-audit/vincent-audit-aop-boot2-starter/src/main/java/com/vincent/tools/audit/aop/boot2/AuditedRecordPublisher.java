package com.vincent.tools.audit.aop.boot2;

import com.vincent.tools.audit.application.AuditRecordCommand;
import com.vincent.tools.audit.application.AuditService;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class AuditedRecordPublisher {
    private final AuditService auditService;

    AuditedRecordPublisher(AuditService auditService) {
        this.auditService = auditService;
    }

    void publish(AuditRecordCommand command, boolean afterCommit) {
        if (afterCommit && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                @Override
                public void afterCommit() {
                    auditService.record(command);
                }
            });
            return;
        }
        auditService.record(command);
    }
}
