package com.vincent.tools.dict.web;

import com.vincent.tools.common.web.ApiResponse;
import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {
        DictAdminController.class,
        DictItemAdminController.class,
        TenantAdminController.class,
        DictAdminPageController.class
})
public class DictWebExceptionHandler {

    @ExceptionHandler(DictException.class)
    public ResponseEntity<ApiResponse<Void>> handleDictException(DictException exception) {
        return ResponseEntity.status(statusOf(exception.getCode()))
                .body(ApiResponse.<Void>error(exception.getCode().name(), exception.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>error(DictErrorCode.INVALID_ARGUMENT.name(), exception.getMessage()));
    }

    static HttpStatus statusOf(DictErrorCode code) {
        switch (code) {
            case INVALID_ARGUMENT:
            case TENANT_CONTEXT_MISSING:
            case DEFAULT_ITEM_PROTECTED:
                return HttpStatus.BAD_REQUEST;
            case PERMISSION_DENIED:
                return HttpStatus.FORBIDDEN;
            case DICT_NOT_FOUND:
            case DICT_ITEM_NOT_FOUND:
            case TENANT_NOT_FOUND:
                return HttpStatus.NOT_FOUND;
            case DICT_CODE_CONFLICT:
            case DICT_ITEM_CODE_CONFLICT:
            case DICT_NOT_EMPTY:
            case DICT_ITEM_LIMIT_EXCEEDED:
            case OPTIMISTIC_LOCK_CONFLICT:
                return HttpStatus.CONFLICT;
            case SCHEMA_MISSING:
            case SCHEMA_VERSION_MISMATCH:
            case CONFIGURATION_INVALID:
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
