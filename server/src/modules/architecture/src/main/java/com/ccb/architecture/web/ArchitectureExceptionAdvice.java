package com.ccb.architecture.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = LogicalSubsystemController.class)
public class ArchitectureExceptionAdvice {
    public static final int NOT_FOUND_CODE = 40400;

    @ExceptionHandler(ArchitectureNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ArchitectureNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(NOT_FOUND_CODE, exception.getMessage(), TraceId.getOrCreate()));
    }
}
