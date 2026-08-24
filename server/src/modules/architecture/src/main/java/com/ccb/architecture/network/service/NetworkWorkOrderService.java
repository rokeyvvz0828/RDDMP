package com.ccb.architecture.network.service;

import com.ccb.architecture.network.model.NetworkWorkOrderModels.ActionType;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.CertPayload;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.ClbPayload;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.DnsPayload;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.HandlingResultCommand;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.HandlingResultStatus;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.HistoryEvent;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.Kind;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrder;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrderStatus;
import com.ccb.architecture.network.integration.NetworkAttachmentAccessPolicy;
import com.ccb.architecture.network.persistence.NetworkWorkOrderStore;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.attachment.integration.AttachmentBindingCommand;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.function.Consumer;

/**
 * 网络专项工单的状态机与业务规则（REQ-20260823-050）。
 *
 * <p>状态只能由业务动作与工作流生命周期事件改变；批准只把工单推进到 COMPLETED，
 * 表示外部实际配置已办理并登记，本服务不执行任何外部 CLB/DNS/证书动作。</p>
 */
@Service
public class NetworkWorkOrderService {
    /** 证书工单附件私钥类扩展名黑名单，命中即拒绝，避免私钥进入平台存储。 */
    public static final List<String> FORBIDDEN_CERT_ATTACHMENT_EXTENSIONS =
            List.of("key", "pem", "pfx", "p12", "jks", "keystore");

    public enum AccessScope {
        OWN,
        MANAGE
    }

    /** 提交准备结果；调用方（工作流协调器）在同一事务内继续启动流程。 */
    public record SubmissionPreparation(
            long workOrderId,
            int nextRound,
            String digest) {
    }

    /** 取消准备结果；审批中取消需要调用方终止工作流实例。 */
    public record CancellationPreparation(
            long workOrderId,
            long workflowInstanceId,
            int businessRound) {
    }

    public record WorkOrderDetail(
            WorkOrder workOrder,
            List<HistoryEvent> history) {
        public WorkOrderDetail {
            history = List.copyOf(history == null ? List.of() : history);
        }
    }

    public record CreateCommand(
            Kind kind,
            ActionType actionType,
            Map<String, Object> payload,
            String reason,
            List<Long> attachmentIds) {
        public CreateCommand {
            attachmentIds = List.copyOf(attachmentIds == null ? List.of() : attachmentIds);
        }
    }

    public record UpdateCommand(
            long rowVersion,
            String reason,
            Map<String, Object> payload,
            List<Long> attachmentIds) {
        public UpdateCommand {
            attachmentIds = List.copyOf(attachmentIds == null ? List.of() : attachmentIds);
        }
    }

    private final NetworkWorkOrderStore store;
    private final AttachmentGateway attachmentGateway;
    private final ObjectMapper objectMapper;
    private final LongSupplier idSupplier;
    private final Clock clock;

    @Autowired
    public NetworkWorkOrderService(NetworkWorkOrderStore store,
                                   AttachmentGateway attachmentGateway,
                                   ObjectMapper objectMapper) {
        this(store, attachmentGateway, objectMapper,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000),
                Clock.systemUTC());
    }

    NetworkWorkOrderService(NetworkWorkOrderStore store, AttachmentGateway attachmentGateway,
                            ObjectMapper objectMapper, LongSupplier idSupplier, Clock clock) {
        this.store = Objects.requireNonNull(store, "工单存储不能为空");
        this.attachmentGateway = Objects.requireNonNull(attachmentGateway, "附件网关不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public List<WorkOrder> list(AuthUser actor, AccessScope scope, Kind kind,
                                WorkOrderStatus status, int limit, int offset) {
        requireActor(actor);
        Long applicantId = scope == AccessScope.MANAGE ? null : actor.id();
        return store.listWorkOrders(actor.tenantId(), applicantId, kind, status, limit, offset);
    }

    public WorkOrderDetail detail(AuthUser actor, AccessScope scope, long workOrderId) {
        requireActor(actor);
        WorkOrder workOrder = requireVisible(actor, scope, workOrderId);
        return new WorkOrderDetail(workOrder, store.listHistory(actor.tenantId(), workOrderId));
    }

    /** 创建草稿；载荷按 kind 强类型校验并规范化，subject 由服务端投影。 */
    @Transactional
    public WorkOrderDetail create(AuthUser actor, CreateCommand command) {
        requireActor(actor);
        Objects.requireNonNull(command, "创建命令不能为空");
        Objects.requireNonNull(command.kind(), "工单类型不能为空");
        Objects.requireNonNull(command.actionType(), "工单动作不能为空");
        if (!ActionType.allowedFor(command.kind(), command.actionType())) {
            throw badRequest("动作 " + command.actionType() + " 不属于工单类型 " + command.kind());
        }
        if (command.attachmentIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw badRequest("附件编号必须为正整数");
        }
        validateAttachments(actor, command.kind(), command.attachmentIds());

        NormalizedPayload normalized = normalizePayload(command.kind(), command.actionType(), command.payload());
        long id = nextId();
        LocalDateTime now = LocalDateTime.now(clock);
        WorkOrder workOrder = new WorkOrder(
                id, actor.tenantId(), command.kind(), command.actionType(), normalized.subject(),
                actor.id(), trimToNull(command.reason()), WorkOrderStatus.DRAFT, normalized.payloadJson(),
                serializeIds(command.attachmentIds()), null, null, null, null, null,
                0, null, null, null, null, false, 0, actor.id(), actor.id(), now, now);
        store.insertWorkOrder(workOrder);
        bindAttachments(actor, id, command.attachmentIds());
        store.insertHistory(new HistoryEvent(nextId(), actor.tenantId(), id, "CREATED", null,
                WorkOrderStatus.DRAFT, 0, "创建网络专项工单", snapshot(workOrder), null, actor.id(), now));
        return new WorkOrderDetail(workOrder, store.listHistory(actor.tenantId(), id));
    }

    /** 仅本人且 DRAFT/RETURNED 可更新；行版本防并发覆盖。 */
    @Transactional
    public WorkOrderDetail update(AuthUser actor, long workOrderId, UpdateCommand command) {
        requireActor(actor);
        Objects.requireNonNull(command, "更新命令不能为空");
        WorkOrder current = requireVisible(actor, AccessScope.OWN, workOrderId);
        requireOwner(current, actor);
        if (current.status() != WorkOrderStatus.DRAFT && current.status() != WorkOrderStatus.RETURNED) {
            throw conflict("当前状态不允许编辑草稿");
        }
        NormalizedPayload normalized = normalizePayload(current.kind(), current.actionType(), command.payload());
        validateAttachments(actor, current.kind(), command.attachmentIds());
        String attachmentIds = serializeIds(command.attachmentIds());
        if (!store.updateDraft(actor.tenantId(), workOrderId, current.status(), command.rowVersion(),
                trimToNull(command.reason()), normalized.payloadJson(), attachmentIds, actor.id())) {
            throw conflict("工单已被其他人修改，请刷新后重试");
        }
        WorkOrder updated = store.lockWorkOrder(actor.tenantId(), workOrderId)
                .orElseThrow(() -> notFound("网络专项工单不存在"));
        bindNewAttachments(actor, workOrderId, parseIds(current.attachmentIds()), command.attachmentIds());
        store.insertHistory(new HistoryEvent(nextId(), actor.tenantId(), workOrderId, "UPDATED",
                current.status(), updated.status(), current.currentBusinessRound(), "更新网络专项工单草稿",
                snapshot(updated), diff(current, updated), actor.id(), LocalDateTime.now(clock)));
        return new WorkOrderDetail(updated, store.listHistory(actor.tenantId(), workOrderId));
    }

    /** 移除已绑定的申请材料附件：仅 DRAFT/RETURNED，删除授权由附件策略复审。 */
    @Transactional
    public WorkOrderDetail removeAttachment(AuthUser actor, long workOrderId, long expectedRowVersion,
                                            long attachmentId) {
        requireActor(actor);
        WorkOrder current = store.lockWorkOrder(actor.tenantId(), workOrderId)
                .orElseThrow(() -> notFound("网络专项工单不存在"));
        if (current.status() != WorkOrderStatus.DRAFT && current.status() != WorkOrderStatus.RETURNED) {
            throw conflict("当前状态不允许移除附件");
        }
        if (current.rowVersion() != expectedRowVersion) {
            throw conflict("工单已被其他人修改，请刷新后重试");
        }
        List<Long> attachmentIds = new java.util.ArrayList<>(parseIds(current.attachmentIds()));
        if (!attachmentIds.contains(attachmentId)) {
            throw badRequest("该附件不属于当前工单");
        }
        attachmentGateway.deleteBound(attachmentId, NetworkAttachmentAccessPolicy.BUSINESS_TYPE,
                String.valueOf(workOrderId), actor);
        attachmentIds.remove(attachmentId);
        String nextIds = serializeIds(attachmentIds);
        if (!store.updateDraft(actor.tenantId(), workOrderId, current.status(), current.rowVersion(),
                current.reason(), current.payload(), nextIds, actor.id())) {
            throw conflict("工单已被其他人修改，请刷新后重试");
        }
        WorkOrder updated = store.lockWorkOrder(actor.tenantId(), workOrderId)
                .orElseThrow(() -> notFound("网络专项工单不存在"));
        store.insertHistory(new HistoryEvent(nextId(), actor.tenantId(), workOrderId, "ATTACHMENT_REMOVED",
                current.status(), updated.status(), current.currentBusinessRound(),
                "移除申请材料附件 " + attachmentId, snapshot(updated), null, actor.id(),
                LocalDateTime.now(clock)));
        return new WorkOrderDetail(updated, store.listHistory(actor.tenantId(), workOrderId));
    }

    /**
     * 提交准备：校验归属与状态、固化摘要、状态置 IN_REVIEW，然后调用工作流启动器。
     * 任一平台结果校验或持久化失败都会让状态与摘要一起回滚。
     */
    @Transactional
    public void coordinateSubmission(AuthUser actor, long workOrderId, long expectedRowVersion,
                                     Consumer<SubmissionPreparation> workflowStarter) {
        requireActor(actor);
        Objects.requireNonNull(workflowStarter, "工作流启动器不能为空");
        WorkOrder current = store.lockWorkOrder(actor.tenantId(), workOrderId)
                .orElseThrow(() -> notFound("网络专项工单不存在"));
        requireOwner(current, actor);
        if (current.status() != WorkOrderStatus.DRAFT && current.status() != WorkOrderStatus.RETURNED) {
            throw conflict("当前状态不允许提交审批");
        }
        if (current.rowVersion() != expectedRowVersion) {
            throw conflict("工单已被其他人修改，请刷新后重试");
        }
        String digest = digest(current);
        if (!store.compareAndSetStatus(actor.tenantId(), workOrderId, current.status(),
                current.rowVersion(), WorkOrderStatus.IN_REVIEW, actor.id())) {
            throw conflict("工单状态已被其他人修改，请刷新后重试");
        }
        store.insertHistory(new HistoryEvent(nextId(), actor.tenantId(), workOrderId, "SUBMITTED",
                current.status(), WorkOrderStatus.IN_REVIEW, current.currentBusinessRound(),
                "提交网络专项工单审批", snapshotAfter(workOrderId, current, WorkOrderStatus.IN_REVIEW, digest),
                null, actor.id(), LocalDateTime.now(clock)));
        workflowStarter.accept(new SubmissionPreparation(workOrderId, current.currentBusinessRound() + 1, digest));
    }

    /** 草稿/退回同步取消；审批中取消走 {@link #coordinateCancellation}。 */
    @Transactional
    public WorkOrderDetail cancel(AuthUser actor, AccessScope scope, long workOrderId, long expectedRowVersion) {
        requireActor(actor);
        WorkOrder current = requireVisible(actor, scope, workOrderId);
        requireOwner(current, actor);
        if (current.status() == WorkOrderStatus.IN_REVIEW) {
            throw conflict("审批中的工单必须通过终止流程取消");
        }
        if (current.status() != WorkOrderStatus.DRAFT && current.status() != WorkOrderStatus.RETURNED) {
            throw conflict("当前状态不允许取消");
        }
        if (current.rowVersion() != expectedRowVersion) {
            throw conflict("工单已被其他人修改，请刷新后重试");
        }
        store.compareAndSetStatus(actor.tenantId(), workOrderId, current.status(), current.rowVersion(),
                WorkOrderStatus.CANCELLED, actor.id());
        WorkOrder cancelled = store.lockWorkOrder(actor.tenantId(), workOrderId)
                .orElseThrow(() -> notFound("网络专项工单不存在"));
        store.insertHistory(new HistoryEvent(nextId(), actor.tenantId(), workOrderId, "CANCELLED",
                current.status(), WorkOrderStatus.CANCELLED, current.currentBusinessRound(),
                "取消网络专项工单", snapshot(cancelled), null, actor.id(), LocalDateTime.now(clock)));
        return new WorkOrderDetail(cancelled, store.listHistory(actor.tenantId(), workOrderId));
    }

    /** 审批中取消：登记取消请求并调用工作流终止器，等待 TERMINATED 事件终态化。 */
    @Transactional
    public void coordinateCancellation(AuthUser actor, long workOrderId, long expectedRowVersion,
                                       Consumer<CancellationPreparation> workflowTerminator) {
        requireActor(actor);
        Objects.requireNonNull(workflowTerminator, "工作流终止器不能为空");
        WorkOrder current = store.lockWorkOrder(actor.tenantId(), workOrderId)
                .orElseThrow(() -> notFound("网络专项工单不存在"));
        requireOwner(current, actor);
        if (current.status() != WorkOrderStatus.IN_REVIEW) {
            throw conflict("当前状态不允许发起取消");
        }
        if (current.rowVersion() != expectedRowVersion) {
            throw conflict("工单已被其他人修改，请刷新后重试");
        }
        if (current.currentWorkflowInstanceId() == null) {
            throw conflict("审批流程尚未启动，不能取消");
        }
        if (!store.compareAndSetCancellationRequested(actor.tenantId(), workOrderId,
                current.rowVersion(), true, actor.id())) {
            throw conflict("工单已被其他人修改，请刷新后重试");
        }
        store.insertHistory(new HistoryEvent(nextId(), actor.tenantId(), workOrderId, "CANCEL_REQUESTED",
                WorkOrderStatus.IN_REVIEW, WorkOrderStatus.IN_REVIEW, current.currentBusinessRound(),
                "登记取消请求并终止审批流程", null, null, actor.id(), LocalDateTime.now(clock)));
        workflowTerminator.accept(new CancellationPreparation(workOrderId,
                current.currentWorkflowInstanceId(), current.currentBusinessRound()));
    }

    /** 办理结果登记：manage 权限，IN_REVIEW 或 COMPLETED；不改变工单状态。 */
    @Transactional
    public WorkOrderDetail registerHandlingResult(AuthUser actor, long workOrderId, long expectedRowVersion,
                                                  HandlingResultCommand command) {
        requireActor(actor);
        Objects.requireNonNull(command, "办理结果不能为空");
        HandlingResultStatus resultStatus = parseResultStatus(command.resultStatus());
        String description = trimToNull(command.resultDescription());
        if (command.resultAttachmentIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw badRequest("凭证附件编号必须为正整数");
        }
        WorkOrder current = store.lockWorkOrder(actor.tenantId(), workOrderId)
                .orElseThrow(() -> notFound("网络专项工单不存在"));
        validateAttachments(actor, current.kind(), command.resultAttachmentIds());
        if (current.status() != WorkOrderStatus.IN_REVIEW && current.status() != WorkOrderStatus.COMPLETED) {
            throw conflict("当前状态不允许登记办理结果");
        }
        if (current.rowVersion() != expectedRowVersion) {
            throw conflict("工单已被其他人修改，请刷新后重试");
        }
        String resultAttachmentIds = serializeIds(command.resultAttachmentIds());
        if (!store.updateHandlingResult(actor.tenantId(), workOrderId, expectedRowVersion,
                resultStatus.name(), description, resultAttachmentIds, actor.id())) {
            throw conflict("工单已被其他人修改，请刷新后重试");
        }
        bindNewAttachments(actor, workOrderId, parseIds(current.resultAttachmentIds()),
                command.resultAttachmentIds());
        WorkOrder updated = store.lockWorkOrder(actor.tenantId(), workOrderId)
                .orElseThrow(() -> notFound("网络专项工单不存在"));
        store.insertHistory(new HistoryEvent(nextId(), actor.tenantId(), workOrderId, "RESULT_REGISTERED",
                current.status(), updated.status(), current.currentBusinessRound(),
                "登记办理结果 " + resultStatus.name(), resultSnapshot(updated), null, actor.id(),
                LocalDateTime.now(clock)));
        return new WorkOrderDetail(updated, store.listHistory(actor.tenantId(), workOrderId));
    }

    /** 工作流事件：退回/拒绝在同一事务落地。 */
    @Transactional
    public void applyReviewOutcomeInCurrentTransaction(long tenantId, long workOrderId,
                                                       long expectedRowVersion, long operatorId,
                                                       WorkOrderStatus outcome) {
        if (outcome != WorkOrderStatus.RETURNED && outcome != WorkOrderStatus.REJECTED) {
            throw new IllegalArgumentException("退回/拒绝之外的终态不允许通过评审路径落地");
        }
        WorkOrder current = store.lockWorkOrder(tenantId, workOrderId)
                .orElseThrow(() -> conflict("工作流事件关联的网络专项工单不存在"));
        if (current.status() != WorkOrderStatus.IN_REVIEW || current.cancellationRequested()) {
            throw conflict("工作流事件对应的工单已变化或正在取消");
        }
        if (current.rowVersion() != expectedRowVersion) {
            throw conflict("工单行版本已变化，无法应用工作流结论");
        }
        if (!store.compareAndSetStatus(tenantId, workOrderId, WorkOrderStatus.IN_REVIEW,
                current.rowVersion(), outcome, operatorId)) {
            throw conflict("工单状态已被其他人修改");
        }
        WorkOrder updated = store.lockWorkOrder(tenantId, workOrderId)
                .orElseThrow(() -> conflict("网络专项工单不存在"));
        store.insertHistory(new HistoryEvent(nextId(), tenantId, workOrderId, outcome.name(),
                WorkOrderStatus.IN_REVIEW, outcome, current.currentBusinessRound(),
                outcome == WorkOrderStatus.RETURNED ? "审批退回，等待修改后重提" : "审批拒绝",
                snapshot(updated), null, operatorId, LocalDateTime.now(clock)));
    }

    /** 工作流事件：批准 = 外部配置已办理并登记，工单进入 COMPLETED。 */
    @Transactional
    public void applyCompletionInCurrentTransaction(long tenantId, long workOrderId,
                                                    long expectedRowVersion, long operatorId) {
        WorkOrder current = store.lockWorkOrder(tenantId, workOrderId)
                .orElseThrow(() -> conflict("工作流事件关联的网络专项工单不存在"));
        if (current.status() != WorkOrderStatus.IN_REVIEW || current.cancellationRequested()) {
            throw conflict("工作流事件对应的工单已变化或正在取消");
        }
        if (current.rowVersion() != expectedRowVersion) {
            throw conflict("工单行版本已变化，无法应用工作流结论");
        }
        if (!store.compareAndSetStatus(tenantId, workOrderId, WorkOrderStatus.IN_REVIEW,
                current.rowVersion(), WorkOrderStatus.COMPLETED, operatorId)) {
            throw conflict("工单状态已被其他人修改");
        }
        WorkOrder updated = store.lockWorkOrder(tenantId, workOrderId)
                .orElseThrow(() -> conflict("网络专项工单不存在"));
        store.insertHistory(new HistoryEvent(nextId(), tenantId, workOrderId, "COMPLETED",
                WorkOrderStatus.IN_REVIEW, WorkOrderStatus.COMPLETED, current.currentBusinessRound(),
                "审批通过，外部配置已办理并登记", snapshot(updated), null, operatorId,
                LocalDateTime.now(clock)));
    }

    /** 工作流事件：TERMINATED 仅在已登记取消请求时确认 CANCELLED。 */
    @Transactional
    public void applyCancellationConfirmationInCurrentTransaction(long tenantId, long workOrderId,
                                                                  long expectedRowVersion, long operatorId) {
        WorkOrder current = store.lockWorkOrder(tenantId, workOrderId)
                .orElseThrow(() -> conflict("工作流事件关联的网络专项工单不存在"));
        if (current.status() != WorkOrderStatus.IN_REVIEW || !current.cancellationRequested()) {
            throw conflict("工作流事件没有匹配的取消请求");
        }
        if (current.rowVersion() != expectedRowVersion) {
            throw conflict("工单行版本已变化，无法应用工作流结论");
        }
        if (!store.compareAndSetStatus(tenantId, workOrderId, WorkOrderStatus.IN_REVIEW,
                current.rowVersion(), WorkOrderStatus.CANCELLED, operatorId)) {
            throw conflict("工单状态已被其他人修改");
        }
        WorkOrder updated = store.lockWorkOrder(tenantId, workOrderId)
                .orElseThrow(() -> conflict("网络专项工单不存在"));
        store.insertHistory(new HistoryEvent(nextId(), tenantId, workOrderId, "CANCELLED",
                WorkOrderStatus.IN_REVIEW, WorkOrderStatus.CANCELLED, current.currentBusinessRound(),
                "审批流程已终止并取消工单", snapshot(updated), null, operatorId,
                LocalDateTime.now(clock)));
    }

    public String digest(WorkOrder workOrder) {
        try {
            Map<String, Object> canonical = new LinkedHashMap<>();
            canonical.put("kind", workOrder.kind().name());
            canonical.put("actionType", workOrder.actionType().name());
            canonical.put("subject", workOrder.subject());
            canonical.put("payload", workOrder.payload());
            canonical.put("attachmentIds", parseIds(workOrder.attachmentIds()));
            String json = objectMapper.writeValueAsString(canonical);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("工单摘要计算失败", exception);
        }
    }

    private WorkOrder requireVisible(AuthUser actor, AccessScope scope, long workOrderId) {
        WorkOrder workOrder = store.findWorkOrder(actor.tenantId(), workOrderId)
                .orElseThrow(() -> notFound("网络专项工单不存在"));
        if (scope == AccessScope.OWN && workOrder.applicantId() != actor.id()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能查看本人发起的网络专项工单");
        }
        return workOrder;
    }

    private static void requireOwner(WorkOrder workOrder, AuthUser actor) {
        if (workOrder.applicantId() != actor.id()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能操作本人发起的网络专项工单");
        }
    }

    /** 附件必须可被当前用户访问；证书工单拒绝私钥类扩展名，避免私钥进入平台存储。 */
    private void validateAttachments(AuthUser actor, Kind kind, List<Long> attachmentIds) {
        if (attachmentIds.isEmpty()) {
            return;
        }
        for (long attachmentId : attachmentIds) {
            AttachmentItem item = attachmentGateway.get(attachmentId, actor);
            if (kind == Kind.CERT && isForbiddenCertExtension(item.fileName())) {
                throw badRequest("证书工单不允许上传私钥类文件（" + item.fileName() + "）");
            }
        }
    }

    private boolean isForbiddenCertExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        String extension = fileName.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
        return FORBIDDEN_CERT_ATTACHMENT_EXTENSIONS.contains(extension);
    }

    /** 新工单的全部附件一次性绑定到业务键。 */
    private void bindAttachments(AuthUser actor, long workOrderId, List<Long> attachmentIds) {
        for (long attachmentId : attachmentIds) {
            attachmentGateway.bind(new AttachmentBindingCommand(attachmentId,
                    NetworkAttachmentAccessPolicy.BUSINESS_TYPE, String.valueOf(workOrderId), null), actor);
        }
    }

    /** 更新时只绑定新增附件；已绑定附件保持原绑定，移除的附件不再展示。 */
    private void bindNewAttachments(AuthUser actor, long workOrderId, List<Long> currentIds,
                                    List<Long> nextIds) {
        for (long attachmentId : nextIds) {
            if (currentIds.contains(attachmentId)) {
                continue;
            }
            attachmentGateway.bind(new AttachmentBindingCommand(attachmentId,
                    NetworkAttachmentAccessPolicy.BUSINESS_TYPE, String.valueOf(workOrderId), null), actor);
        }
    }

    private NormalizedPayload normalizePayload(Kind kind, ActionType actionType, Map<String, Object> payload) {
        if (payload == null) {
            throw badRequest("工单业务载荷不能为空");
        }
        try {
            return switch (kind) {
                case CLB -> normalizeClb(actionType, payload);
                case DNS -> normalizeDns(actionType, payload);
                case CERT -> normalizeCert(actionType, payload);
            };
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception.getMessage());
        }
    }

    private NormalizedPayload normalizeClb(ActionType actionType, Map<String, Object> payload) {
        ClbPayload value = objectMapper.convertValue(payload, ClbPayload.class);
        requireNotBlank(value.clbName(), "CLB 名称不能为空");
        requireNotBlank(value.purpose(), "用途不能为空");
        String clbName = value.clbName().trim();
        String payloadJson = writeJson(new ClbPayload(clbName, value.purpose().trim(), value.description()));
        return new NormalizedPayload(clbName, payloadJson);
    }

    private NormalizedPayload normalizeDns(ActionType actionType, Map<String, Object> payload) {
        DnsPayload value = objectMapper.convertValue(payload, DnsPayload.class);
        requireNotBlank(value.domainName(), "域名不能为空");
        requireNotBlank(value.purpose(), "用途不能为空");
        String domainName = value.domainName().trim().toLowerCase(Locale.ROOT);
        String payloadJson = writeJson(new DnsPayload(domainName, value.purpose().trim(), value.description()));
        return new NormalizedPayload(domainName, payloadJson);
    }

    private NormalizedPayload normalizeCert(ActionType actionType, Map<String, Object> payload) {
        CertPayload value = objectMapper.convertValue(payload, CertPayload.class);
        requireNotBlank(value.certType(), "证书类型不能为空");
        if (!"SSL".equals(value.certType()) && !"EXTERNAL".equals(value.certType())) {
            throw badRequest("证书类型必须为 SSL 或 EXTERNAL");
        }
        requireNotBlank(value.subjectName(), "证书主题不能为空");
        requireNotBlank(value.purpose(), "用途不能为空");
        String subjectName = value.subjectName().trim();
        String payloadJson = writeJson(new CertPayload(value.certType(), subjectName,
                value.purpose().trim(), value.description()));
        return new NormalizedPayload(subjectName, payloadJson);
    }

    private record NormalizedPayload(String subject, String payloadJson) {
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("工单载荷序列化失败", exception);
        }
    }

    private String snapshot(WorkOrder workOrder) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("id", workOrder.id());
            snapshot.put("kind", workOrder.kind().name());
            snapshot.put("actionType", workOrder.actionType().name());
            snapshot.put("subject", workOrder.subject());
            snapshot.put("status", workOrder.status().name());
            snapshot.put("payload", workOrder.payload());
            snapshot.put("attachmentIds", parseIds(workOrder.attachmentIds()));
            snapshot.put("resultStatus", workOrder.resultStatus() == null ? null : workOrder.resultStatus().name());
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("工单快照序列化失败", exception);
        }
    }

    private String snapshotAfter(long workOrderId, WorkOrder current, WorkOrderStatus nextStatus, String digest) {
        WorkOrder projected = new WorkOrder(
                current.id(), current.tenantId(), current.kind(), current.actionType(), current.subject(),
                current.applicantId(), current.reason(), nextStatus, current.payload(), current.attachmentIds(),
                current.resultStatus(), current.resultDescription(), current.resultAttachmentIds(),
                current.resultRegisteredBy(), current.resultRegisteredAt(), current.currentBusinessRound(),
                current.currentWorkflowDefinitionId(), current.currentWorkflowVersionId(),
                current.currentWorkflowInstanceId(), digest, current.cancellationRequested(),
                current.rowVersion() + 1, current.createdBy(), current.updatedBy(), current.createdAt(),
                current.updatedAt());
        return snapshot(projected);
    }

    private String resultSnapshot(WorkOrder workOrder) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("resultStatus", workOrder.resultStatus() == null ? null : workOrder.resultStatus().name());
            snapshot.put("resultDescription", workOrder.resultDescription());
            snapshot.put("resultAttachmentIds", parseIds(workOrder.resultAttachmentIds()));
            snapshot.put("resultRegisteredBy", workOrder.resultRegisteredBy());
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("办理结果快照序列化失败", exception);
        }
    }

    private String diff(WorkOrder before, WorkOrder after) {
        try {
            Map<String, Object> diff = new LinkedHashMap<>();
            if (!Objects.equals(before.reason(), after.reason())) {
                diff.put("reason", List.of(before.reason(), after.reason()));
            }
            if (!Objects.equals(before.payload(), after.payload())) {
                diff.put("payloadChanged", true);
            }
            if (!Objects.equals(before.attachmentIds(), after.attachmentIds())) {
                diff.put("attachmentIds", List.of(before.attachmentIds(), after.attachmentIds()));
            }
            return objectMapper.writeValueAsString(diff);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("工单差异序列化失败", exception);
        }
    }

    private static HandlingResultStatus parseResultStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "办理结果状态不能为空");
        }
        try {
            return HandlingResultStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "办理结果状态必须为 SUCCESS 或 FAILED");
        }
    }

    private static void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<Long> parseIds(String json) {
        if (json == null || json.isBlank() || "null".equals(json) || "[]".equals(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, Long.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("工单附件编号解析失败", exception);
        }
    }

    private static String serializeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < ids.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(ids.get(index));
        }
        return builder.append(']').toString();
    }

    private static void requireActor(AuthUser actor) {
        if (actor == null || actor.id() <= 0 || actor.tenantId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要有效的认证用户和租户");
        }
    }

    private long nextId() {
        long value = idSupplier.getAsLong();
        if (value <= 0) {
            throw new IllegalStateException("工单标识生成器返回无效值");
        }
        return value;
    }

    private static BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    private static BusinessException notFound(String message) {
        throw new ArchitectureNotFoundException(message);
    }
}
