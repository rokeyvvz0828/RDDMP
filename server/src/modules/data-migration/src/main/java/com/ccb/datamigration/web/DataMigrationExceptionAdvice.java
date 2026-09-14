package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.trace.TraceId;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 数据迁移模块级异常适配（T32 决策 D1/D3）。
 *
 * <p>项目隔离把 {@code projectId} 提升为模块所有查询与写入选参的必要条件。参数以查询串进入，
 * 缺失或非数字时在进入 service 之前就会被 Spring 拒绝，而平台全局兜底 {@code Exception} 处理器会把它
 * 渲染成 500。本适配仅覆盖 {@code com.ccb.datamigration.web} 包，把这类绑定失败按参数校验语义还原为
 * 400，使「省略或伪造 projectId」得到可预期的 40000，而不是服务端错误。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = IssueController.class)
public class DataMigrationExceptionAdvice {

    /** 必填查询参数缺失：属于请求不合法，不得退化为 500。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException exception) {
        return badRequest("缺少必填参数 " + exception.getParameterName() + "，数据迁移接口必须指定所属项目");
    }

    /** 参数类型不匹配（如 projectId=abc）：属于请求不合法，不得退化为 500。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return badRequest("参数 " + exception.getName() + " 非法，必须为数字");
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(ErrorCode.BAD_REQUEST, message, TraceId.getOrCreate()));
    }
}
