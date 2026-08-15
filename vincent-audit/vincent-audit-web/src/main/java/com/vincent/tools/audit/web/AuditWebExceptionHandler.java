package com.vincent.tools.audit.web;

import com.vincent.tools.audit.application.AuditPermission;
import com.vincent.tools.audit.domain.AuditErrorCode;
import com.vincent.tools.audit.domain.AuditException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.vincent.tools.common.web.ApiResponse;

@RestControllerAdvice(assignableTypes = {
        AuditAdminController.class,
        AuditAdminPageController.class
})
public class AuditWebExceptionHandler {

    @ExceptionHandler(AuditException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuditException(AuditException exception) {
        return ResponseEntity.status(statusOf(exception.getCode()))
                .body(ApiResponse.<Void>error(exception.getCode().name(), exception.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>error(AuditErrorCode.INVALID_ARGUMENT.name(), exception.getMessage()));
    }

    static HttpStatus statusOf(AuditErrorCode code) {
        switch (code) {
            case INVALID_ARGUMENT:
            case TENANT_CONTEXT_MISSING:
                return HttpStatus.BAD_REQUEST;
            case PERMISSION_DENIED:
                return HttpStatus.FORBIDDEN;
            case SCHEMA_MISSING:
            case SCHEMA_VERSION_MISMATCH:
            case CONFIGURATION_INVALID:
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
