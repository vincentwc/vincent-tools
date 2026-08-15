package com.vincent.tools.audit.aop.boot2;

import com.vincent.tools.audit.aop.AuditPayload;
import com.vincent.tools.audit.aop.AuditPayloadExtractor;
import com.vincent.tools.audit.aop.Audited;
import com.vincent.tools.audit.application.AuditRecordCommand;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Aspect
public class AuditedAspect {
    private final AuditedRecordPublisher recordPublisher;
    private final AuditedSpelEvaluator spelEvaluator;
    private final List<AuditPayloadExtractor> payloadExtractors;

    public AuditedAspect(AuditedRecordPublisher recordPublisher,
                         AuditedSpelEvaluator spelEvaluator,
                         List<AuditPayloadExtractor> payloadExtractors) {
        this.recordPublisher = recordPublisher;
        this.spelEvaluator = spelEvaluator;
        this.payloadExtractors = payloadExtractors == null
                ? Collections.<AuditPayloadExtractor>emptyList()
                : payloadExtractors;
    }

    @Around("@annotation(audited)")
    public Object aroundAudited(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result = joinPoint.proceed();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        Object target = joinPoint.getTarget();

        String resourceId = spelEvaluator.evaluateString(audited.resourceId(), method, args, target, result);
        Optional<String> targetTenantId = optionalTenant(
                spelEvaluator.evaluateString(audited.targetTenantId(), method, args, target, result));
        AuditPayload payload = resolvePayload(audited.resourceType(), args, result, method);

        AuditRecordCommand command = new AuditRecordCommand(
                audited.action(),
                audited.resourceType(),
                resourceId,
                targetTenantId,
                payload == null ? null : payload.getBeforeJson(),
                payload == null ? null : payload.getAfterJson());
        recordPublisher.publish(command, audited.afterCommit());
        return result;
    }

    private AuditPayload resolvePayload(String resourceType, Object[] args, Object result, Method method) {
        for (int index = 0; index < payloadExtractors.size(); index++) {
            AuditPayloadExtractor extractor = payloadExtractors.get(index);
            if (extractor.supports(resourceType)) {
                return extractor.extract(args, result, method);
            }
        }
        return null;
    }

    private static Optional<String> optionalTenant(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(tenantId.trim());
    }
}
