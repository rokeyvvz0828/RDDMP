package com.ccb.architecture.web;

import com.ccb.architecture.change.service.SubsystemReferenceGuard;
import com.ccb.architecture.change.web.SubsystemChangeApplicationController;
import com.ccb.architecture.decision.web.ArchitectureDecisionController;
import com.ccb.architecture.environment.web.EnvironmentResourceController;
import com.ccb.architecture.standard.web.ArchitectureStandardController;
import com.ccb.architecture.network.web.NetworkWorkOrderController;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.trace.TraceId;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = {
        PhysicalSubsystemController.class,
        SubsystemChangeApplicationController.class,
        ArchitectureStandardController.class,
        ArchitectureDecisionController.class,
        DeploymentUnitController.class,
        DeploymentUnitImportController.class,
        EnvironmentResourceController.class,
        NetworkWorkOrderController.class
})
public class ArchitectureExceptionAdvice {
    public static final int NOT_FOUND_CODE = 40400;

    @ExceptionHandler(ArchitectureNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ArchitectureNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(NOT_FOUND_CODE, exception.getMessage(), TraceId.getOrCreate()));
    }

    /** 使兼容写入口在模块级 MockMvc 与正式 Boot 中保持相同的冲突语义。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        HttpStatus status = switch (exception.code()) {
            case ErrorCode.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case ErrorCode.FORBIDDEN -> HttpStatus.FORBIDDEN;
            case ErrorCode.CONFLICT -> HttpStatus.CONFLICT;
            case SubsystemReferenceGuard.SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(exception.code(), exception.getMessage(), TraceId.getOrCreate()));
    }
}
