package com.ccb.development.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemOperationAuditCommand;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages="com.ccb.development.web")
public class DevelopmentExceptionAdvice {
    private final SystemOperationAudit audit;
    public DevelopmentExceptionAdvice(SystemOperationAudit audit){this.audit=audit;}
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException error,HttpServletRequest request){return failure(error.code(),error.getMessage(),request);}
    @ExceptionHandler({HttpMessageNotReadableException.class,IllegalArgumentException.class,ArithmeticException.class})
    public ResponseEntity<ApiResponse<Void>> invalid(Exception error,HttpServletRequest request){return failure(40000,"提交字段、枚举或日期格式无效，请检查后重试",request);}
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException error,HttpServletRequest request){return failure(40900,"数据约束冲突，请刷新后重试",request);}
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException error,HttpServletRequest request){return failure(40300,"无此操作权限",request);}
    private ResponseEntity<ApiResponse<Void>> failure(int code,String message,HttpServletRequest request){
        String trace=TraceId.getOrCreate();var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication!=null&&authentication.getPrincipal() instanceof AuthUser actor&&!"GET".equals(request.getMethod())){
            audit.recordFailure(new SystemOperationAuditCommand(actor,"development.REQUEST",request.getMethod(),request.getRequestURI(),"操作失败："+code,trace));
        }
        int status=code/100;if(status<400||status>599)status=400;
        return ResponseEntity.status(status).body(ApiResponse.failure(code,message,trace));
    }
}
