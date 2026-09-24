package com.ccb.datamigration.lifecycle.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.common.api.PageResult;
import com.ccb.datamigration.lifecycle.audit.AuditService;
import com.ccb.datamigration.lifecycle.audit.model.AuditView;
import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 任务审核管理接口（基线第 15 章，T7）：审核台账、单条/批量审核、批量打回三约束、10 分钟撤销窗口、审核记录溯源。 */
@RestController
@RequestMapping("/api/data-migration-lifecycle/audit")
@PreAuthorize("hasAnyAuthority('data-migration-lifecycle:access','data-migration:access','system:admin')")
public class AuditController {
    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<PageResult<AuditView>> pageAudit(
            @RequestParam(required = false) String orderCode,
            @RequestParam(required = false) String auditResult,
            @RequestParam(required = false) String auditStatus,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @AuthenticationPrincipal AuthUser user) {
        return ok(auditService.listAudit(user, orderCode, auditResult, auditStatus, page, size));
    }

    @GetMapping("/{orderId}/records")
    public ApiResponse<List<AuditView>> auditRecords(@PathVariable long orderId,
                                                     @RequestParam(defaultValue = "0") int processSeq,
                                                     @AuthenticationPrincipal AuthUser user) {
        return ok(auditService.auditRecords(user, orderId, processSeq));
    }

    @GetMapping("/{orderId}/package")
    public ApiResponse<Map<String, Object>> auditPackage(@PathVariable long orderId,
                                                         @AuthenticationPrincipal AuthUser user) {
        return ok(auditService.auditPackage(user, orderId));
    }

    @PostMapping("/pass")
    public ApiResponse<Map<String, Object>> pass(@RequestBody Map<String, Object> body,
                                                 @AuthenticationPrincipal AuthUser user) {
        return ok(auditService.auditPass(user, needLong(body, "orderId"), needInt(body, "processSeq"),
                text(body, "auditOpinion"), text(body, "specialRemark"), longList(body.get("auditAttachmentIds"))));
    }

    @PostMapping("/reject")
    public ApiResponse<Map<String, Object>> reject(@RequestBody Map<String, Object> body,
                                                   @AuthenticationPrincipal AuthUser user) {
        return ok(auditService.auditReject(user, needLong(body, "orderId"), needInt(body, "processSeq"),
                text(body, "auditOpinion"), text(body, "rectifyRequirement"), text(body, "specialRemark"),
                longList(body.get("auditAttachmentIds")), null));
    }

    @PostMapping("/batch-pass")
    public ApiResponse<Map<String, Object>> batchPass(@RequestBody Map<String, Object> body,
                                                      @AuthenticationPrincipal AuthUser user) {
        return ok(auditService.batchPass(user, longList(body.get("orderIds")),
                text(body, "auditOpinion"), text(body, "specialRemark")));
    }

    @PostMapping("/batch-reject")
    public ApiResponse<Map<String, Object>> batchReject(@RequestBody Map<String, Object> body,
                                                        @AuthenticationPrincipal AuthUser user) {
        return ok(auditService.batchReject(user, longList(body.get("orderIds")),
                text(body, "unifiedReason"), text(body, "rectifyRequirement"), bool(body.get("confirmed"))));
    }

    @PostMapping("/batch-revoke")
    public ApiResponse<Map<String, Object>> batchRevoke(@RequestBody Map<String, Object> body,
                                                        @AuthenticationPrincipal AuthUser user) {
        return ok(auditService.revokeBatch(user, needLong(body, "batchId")));
    }

    private static long needLong(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            throw new IllegalArgumentException("缺少参数：" + key);
        }
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static int needInt(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            throw new IllegalArgumentException("缺少参数：" + key);
        }
        return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean bool(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static List<Long> longList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(v -> v instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(v))).toList();
    }

    /** 统一成功响应助手（补齐 TraceId）。 */
    private static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.success(data, TraceId.getOrCreate());
    }
}
