package com.ccb.architecture.network.web;

import com.ccb.architecture.network.model.NetworkWorkOrderModels.ActionType;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.HandlingResultCommand;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.HandlingResultStatus;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.HistoryEvent;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.Kind;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrder;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrderStatus;
import com.ccb.architecture.network.service.NetworkWorkOrderService;
import com.ccb.architecture.network.service.NetworkWorkOrderService.AccessScope;
import com.ccb.architecture.network.service.NetworkWorkOrderService.CreateCommand;
import com.ccb.architecture.network.service.NetworkWorkOrderService.UpdateCommand;
import com.ccb.architecture.network.service.NetworkWorkOrderService.WorkOrderDetail;
import com.ccb.architecture.network.service.NetworkWorkOrderSubmissionService;
import com.ccb.architecture.plan.model.PlanModels.WorkOrderType;
import com.ccb.common.api.ApiResponse;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemOperationAuditCommand;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * CLB/DNS/证书网络专项工单的 HTTP 边界（REQ-20260823-051）。
 *
 * <p>提交和审批中取消通过真实工作流协调器执行；批准、退回、拒绝只由平台工作流任务与
 * 生命周期事件驱动，不能通过这里绕过工作流直接终态化工单。</p>
 */
@RestController
@RequestMapping("/api/architecture/network-work-orders")
public class NetworkWorkOrderController {
    private static final Logger log = LoggerFactory.getLogger(NetworkWorkOrderController.class);
    private static final String MANAGE_AUTHORITY = "architecture:network-work-order:manage";

    private final NetworkWorkOrderService service;
    private final NetworkWorkOrderSubmissionService workflowService;
    private final SystemOperationAudit operationAudit;
    private final ObjectMapper objectMapper;
    private final com.ccb.architecture.plan.service.PlanWorkOrderService planWorkOrderService;

    public NetworkWorkOrderController(NetworkWorkOrderService service,
                                      NetworkWorkOrderSubmissionService workflowService,
                                      SystemOperationAudit operationAudit,
                                      ObjectMapper objectMapper,
                                      com.ccb.architecture.plan.service.PlanWorkOrderService planWorkOrderService) {
        this.service = service;
        this.workflowService = workflowService;
        this.operationAudit = operationAudit;
        this.objectMapper = objectMapper;
        this.planWorkOrderService = planWorkOrderService;
    }

    /** 关键写操作统一审计：成功记录成功，业务失败记录失败；审计失败不阻断业务结果。 */
    private <T> T audited(AuthUser actor, String operationCode, String method, String path,
                          Supplier<T> action) {
        try {
            T result = action.get();
            recordAudit(actor, operationCode, method, path, null, TraceId.getOrCreate());
            return result;
        } catch (BusinessException failure) {
            recordAudit(actor, operationCode, method, path, businessAuditMessage(failure), TraceId.getOrCreate());
            throw failure;
        } catch (RuntimeException failure) {
            recordAudit(actor, operationCode, method, path, "工单操作失败", TraceId.getOrCreate());
            throw failure;
        }
    }

    private void recordAudit(AuthUser actor, String operationCode, String method, String path,
                             String errorMessage, String traceId) {
        try {
            SystemOperationAuditCommand command = new SystemOperationAuditCommand(
                    actor, operationCode, method, path, errorMessage, traceId);
            if (errorMessage == null) {
                operationAudit.recordSuccess(command);
            } else {
                operationAudit.recordFailure(command);
            }
        } catch (RuntimeException auditFailure) {
            log.warn("网络专项工单审计写入失败 operationCode={}", operationCode, auditFailure);
        }
    }

    private static String businessAuditMessage(BusinessException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "工单操作失败" : message;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('architecture:network-work-order:view',"
            + "'architecture:network-work-order:apply','architecture:network-work-order:manage')")
    public ApiResponse<List<WorkOrderSummaryResponse>> list(
            @RequestParam(required = false) Kind kind,
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @AuthenticationPrincipal AuthUser actor,
            Authentication authentication) {
        List<WorkOrderSummaryResponse> workOrders = service.list(actor, accessScope(authentication),
                        kind, status, limit, offset)
                .stream()
                .map(this::toSummary)
                .toList();
        return success(workOrders);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('architecture:network-work-order:view',"
            + "'architecture:network-work-order:apply','architecture:network-work-order:manage')")
    public ApiResponse<WorkOrderDetailResponse> detail(@PathVariable long id,
                                                       @AuthenticationPrincipal AuthUser actor,
                                                       Authentication authentication) {
        return success(toDetail(service.detail(actor, accessScope(authentication), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('architecture:network-work-order:apply',"
            + "'architecture:network-work-order:manage')")
    public ApiResponse<WorkOrderDetailResponse> create(@RequestBody CreateWorkOrderRequest request,
                                                       @AuthenticationPrincipal AuthUser actor) {
        Kind kind = requiredKind(request == null ? null : request.kind());
        ActionType actionType = requiredAction(request == null ? null : request.actionType());
        WorkOrderDetail detail = audited(actor, "architecture.network-work-order.create", "POST",
                "/api/architecture/network-work-orders", () -> service.create(actor,
                        new CreateCommand(kind, actionType, request.payload(), request.reason(),
                                request.attachmentIds())));
        registerCreatedFromTask(actor, request, detail.workOrder().id());
        return success(toDetail(detail));
    }

    private void registerCreatedFromTask(AuthUser actor, CreateWorkOrderRequest request, long workOrderId) {
        if (request == null || request.planTaskId() == null) {
            return;
        }
        Kind kind = requiredKind(request == null ? null : request.kind());
        WorkOrderType workOrderType = switch (kind) {
            case CLB -> WorkOrderType.NETWORK_CLB;
            case DNS -> WorkOrderType.NETWORK_DNS;
            case CERT -> WorkOrderType.NETWORK_CERT;
        };
        planWorkOrderService.registerCreatedWorkOrder(actor.tenantId(), request.planTaskId(),
                workOrderType, workOrderId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('architecture:network-work-order:apply',"
            + "'architecture:network-work-order:manage')")
    public ApiResponse<WorkOrderDetailResponse> update(@PathVariable long id,
                                                       @RequestBody UpdateWorkOrderRequest request,
                                                       @AuthenticationPrincipal AuthUser actor) {
        long rowVersion = requiredRowVersion(request == null ? null : request.rowVersion());
        WorkOrderDetail detail = audited(actor, "architecture.network-work-order.update", "PUT",
                "/api/architecture/network-work-orders/" + id, () -> service.update(actor, id,
                        new UpdateCommand(rowVersion, request == null ? null : request.reason(),
                                request == null ? null : request.payload(),
                                request == null ? List.of() : request.attachmentIds())));
        return success(toDetail(detail));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('architecture:network-work-order:apply',"
            + "'architecture:network-work-order:manage')")
    public ApiResponse<WorkOrderDetailResponse> submit(@PathVariable long id,
                                                       @RequestBody SubmitWorkOrderRequest request,
                                                       @AuthenticationPrincipal AuthUser actor) {
        long rowVersion = requiredRowVersion(request == null ? null : request.rowVersion());
        WorkOrderDetail detail = audited(actor, "architecture.network-work-order.submit", "POST",
                "/api/architecture/network-work-orders/" + id + "/submit", () ->
                workflowService.submit(actor, id, rowVersion));
        return success(toDetail(detail));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('architecture:network-work-order:apply',"
            + "'architecture:network-work-order:manage')")
    public ApiResponse<WorkOrderDetailResponse> cancel(@PathVariable long id,
                                                       @RequestBody CancelWorkOrderRequest request,
                                                       @AuthenticationPrincipal AuthUser actor) {
        long rowVersion = requiredRowVersion(request == null ? null : request.rowVersion());
        WorkOrderDetail detail = audited(actor, "architecture.network-work-order.cancel", "POST",
                "/api/architecture/network-work-orders/" + id + "/cancel", () ->
                workflowService.cancel(actor, id, rowVersion));
        return success(toDetail(detail));
    }

    /** 办理结果登记：管理权限；状态 IN_REVIEW 或 COMPLETED 由服务层校验。 */
    @PostMapping("/{id}/handling-result")
    @PreAuthorize("hasAuthority('architecture:network-work-order:manage')")
    public ApiResponse<WorkOrderDetailResponse> registerHandlingResult(
            @PathVariable long id,
            @RequestBody RegisterHandlingResultRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        long rowVersion = requiredRowVersion(request == null ? null : request.rowVersion());
        HandlingResultCommand command = new HandlingResultCommand(
                request == null ? null : request.resultStatus(),
                request == null ? null : request.resultDescription(),
                request == null ? List.of() : request.resultAttachmentIds());
        WorkOrderDetail detail = audited(actor, "architecture.network-work-order.result", "POST",
                "/api/architecture/network-work-orders/" + id + "/handling-result", () ->
                service.registerHandlingResult(actor, id, rowVersion, command));
        return success(toDetail(detail));
    }

    /** 移除已绑定申请材料附件；删除授权由服务层与附件策略共同执行。 */
    @PostMapping("/{id}/attachments/{attachmentId}/remove")
    @PreAuthorize("hasAnyAuthority('architecture:network-work-order:apply',"
            + "'architecture:network-work-order:manage')")
    public ApiResponse<WorkOrderDetailResponse> removeAttachment(
            @PathVariable long id,
            @PathVariable long attachmentId,
            @RequestBody CancelWorkOrderRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        long rowVersion = requiredRowVersion(request == null ? null : request.rowVersion());
        WorkOrderDetail detail = audited(actor, "architecture.network-work-order.attachment-remove", "POST",
                "/api/architecture/network-work-orders/" + id + "/attachments/" + attachmentId + "/remove",
                () -> service.removeAttachment(actor, id, rowVersion, attachmentId));
        return success(toDetail(detail));
    }

    private AccessScope accessScope(Authentication authentication) {
        if (authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> MANAGE_AUTHORITY.equals(authority.getAuthority()))) {
            return AccessScope.MANAGE;
        }
        return AccessScope.OWN;
    }

    private Kind requiredKind(String kind) {
        if (kind == null || kind.isBlank()) {
            throw badRequest("kind 不能为空，必须为 CLB、DNS 或 CERT");
        }
        try {
            return Kind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badRequest("kind 非法，必须为 CLB、DNS 或 CERT");
        }
    }

    private ActionType requiredAction(String actionType) {
        if (actionType == null || actionType.isBlank()) {
            throw badRequest("actionType 不能为空");
        }
        try {
            return ActionType.valueOf(actionType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badRequest("actionType 非法");
        }
    }

    private long requiredRowVersion(Long rowVersion) {
        if (rowVersion == null || rowVersion < 0) {
            throw badRequest("rowVersion 必须为非负整数");
        }
        return rowVersion;
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, TraceId.getOrCreate());
    }

    private WorkOrderSummaryResponse toSummary(WorkOrder workOrder) {
        return new WorkOrderSummaryResponse(workOrder.id(), workOrder.kind(), workOrder.actionType(),
                workOrder.subject(), workOrder.applicantId(), workOrder.reason(), workOrder.status(),
                workOrder.resultStatus(), workOrder.resultDescription(), workOrder.currentBusinessRound(),
                workOrder.cancellationRequested(), workOrder.rowVersion(), workOrder.createdBy(),
                workOrder.updatedBy(), workOrder.createdAt(), workOrder.updatedAt());
    }

    private WorkOrderDetailResponse toDetail(WorkOrderDetail detail) {
        WorkOrder workOrder = detail.workOrder();
        return new WorkOrderDetailResponse(toSummary(workOrder), parsePayload(workOrder),
                parseIds(workOrder.attachmentIds()), parseIds(workOrder.resultAttachmentIds()),
                detail.history().stream().map(this::toHistory).toList());
    }

    private Map<String, Object> parsePayload(WorkOrder workOrder) {
        try {
            return objectMapper.readValue(workOrder.payload(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("工单载荷解析失败");
        }
    }

    private List<Long> parseIds(String json) {
        if (json == null || json.isBlank() || "null".equals(json) || "[]".equals(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, Long.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("工单附件编号解析失败");
        }
    }

    private HistoryResponse toHistory(HistoryEvent event) {
        return new HistoryResponse(event.id(), event.eventType(), event.fromStatus(), event.toStatus(),
                event.businessRound(), event.summary(), event.snapshotJson(), event.diffJson(),
                event.operatorId(), event.occurredAt());
    }

    /** 输入 DTO 不含 tenantId、applicantId 或 accessScope；这些字段即使出现在 JSON 中也会被忽略。 */
    @JsonIgnoreProperties({"tenantId", "applicantId", "accessScope"})
    public record CreateWorkOrderRequest(String kind, String actionType, String reason,
                                         Long planTaskId,
                                         Map<String, Object> payload, List<Long> attachmentIds) {
        public CreateWorkOrderRequest {
            attachmentIds = List.copyOf(attachmentIds == null ? List.of() : attachmentIds);
        }
    }

    @JsonIgnoreProperties({"tenantId", "applicantId", "accessScope"})
    public record UpdateWorkOrderRequest(Long rowVersion, String reason,
                                         Map<String, Object> payload, List<Long> attachmentIds) {
        public UpdateWorkOrderRequest {
            attachmentIds = List.copyOf(attachmentIds == null ? List.of() : attachmentIds);
        }
    }

    @JsonIgnoreProperties({"tenantId", "applicantId", "accessScope"})
    public record SubmitWorkOrderRequest(Long rowVersion) {
    }

    @JsonIgnoreProperties({"tenantId", "applicantId", "accessScope"})
    public record CancelWorkOrderRequest(Long rowVersion) {
    }

    @JsonIgnoreProperties({"tenantId", "applicantId", "accessScope"})
    public record RegisterHandlingResultRequest(Long rowVersion, String resultStatus,
                                                String resultDescription, List<Long> resultAttachmentIds) {
        public RegisterHandlingResultRequest {
            resultAttachmentIds = List.copyOf(resultAttachmentIds == null ? List.of() : resultAttachmentIds);
        }
    }

    /** 列表返回的工单主数据投影，不暴露服务端租户标识。 */
    public record WorkOrderSummaryResponse(long id, Kind kind, ActionType actionType, String subject,
                                           long applicantId, String reason, WorkOrderStatus status,
                                           HandlingResultStatus resultStatus, String resultDescription,
                                           int currentBusinessRound, boolean cancellationRequested,
                                           long rowVersion, long createdBy, long updatedBy,
                                           LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /** 详情聚合解析后的载荷、附件 id 列表与不可变历史，同样不暴露 tenantId。 */
    public record WorkOrderDetailResponse(WorkOrderSummaryResponse workOrder, Map<String, Object> payload,
                                          List<Long> attachmentIds, List<Long> resultAttachmentIds,
                                          List<HistoryResponse> history) {
        public WorkOrderDetailResponse {
            attachmentIds = List.copyOf(attachmentIds == null ? List.of() : attachmentIds);
            resultAttachmentIds = List.copyOf(resultAttachmentIds == null ? List.of() : resultAttachmentIds);
            history = List.copyOf(history == null ? List.of() : history);
        }
    }

    public record HistoryResponse(long id, String eventType, WorkOrderStatus fromStatus,
                                  WorkOrderStatus toStatus, int businessRound, String summary,
                                  String snapshotJson, String diffJson, long operatorId,
                                  LocalDateTime occurredAt) {
    }
}
