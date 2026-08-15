package com.vincent.tools.audit.aop.boot2;

import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;

final class AuditedSpelEvaluator {
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

    String evaluateString(String expression, Method method, Object[] args, Object target, Object result) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }
        EvaluationContext context = new MethodBasedEvaluationContext(target, method, args, parameterNames);
        context.setVariable("result", result);
        Expression parsed = parser.parseExpression(expression.trim());
        Object value = parsed.getValue(context);
        return value == null ? null : String.valueOf(value);
    }
}
