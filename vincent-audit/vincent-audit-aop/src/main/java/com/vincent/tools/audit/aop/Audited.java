package com.vincent.tools.audit.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {
    String action();

    String resourceType();

    /** SpEL expression, e.g. {@code #orderId} or {@code #result}. */
    String resourceId();

    /** Optional SpEL expression; empty means resolve via TenantProvider. */
    String targetTenantId() default "";

    /** When true, record after the surrounding transaction commits. */
    boolean afterCommit() default false;
}
