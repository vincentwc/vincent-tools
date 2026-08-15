package com.vincent.tools.region.web;

import com.vincent.tools.region.domain.RegionErrorCode;
import com.vincent.tools.region.domain.RegionException;
import com.vincent.tools.common.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RegionAdminController.class)
public class RegionWebExceptionHandler {

    @ExceptionHandler(RegionException.class)
    public ResponseEntity<ApiResponse<Void>> handleRegionException(RegionException exception) {
        return ResponseEntity.status(statusOf(exception.getCode()))
                .body(ApiResponse.<Void>error(exception.getCode().name(), exception.getMessage()));
    }

    static HttpStatus statusOf(RegionErrorCode code) {
        switch (code) {
            case INVALID_ARGUMENT:
            case REGION_NOT_FOUND:
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
