package com.ccb.architecture.plan.service;

import com.ccb.architecture.plan.model.PlanModels.Plan;
import com.ccb.architecture.plan.model.PlanModels.Task;
import com.ccb.architecture.plan.model.PlanModels.TaskWorkOrder;
import com.ccb.architecture.plan.model.PlanModels.WorkOrderCommand;
import com.ccb.architecture.plan.model.PlanModels.WorkOrderSource;
import com.ccb.architecture.plan.model.PlanModels.WorkOrderType;
import com.ccb.architecture.plan.persistence.PlanStore;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/** 任务-工单关联与完成门禁（REQ-20260830-056）。存在未结束关联工单时任务不能完成。 */
@Service
public class PlanWorkOrderService {
    private final PlanStore store;
    private final PlanEngine engine;
    private final PlanNotificationService notificationService;
    private final LongSupplier idSupplier;

    @org.springframework.beans.factory.annotation.Autowired
    public PlanWorkOrderService(PlanStore store, PlanEngine engine,
                                PlanNotificationService notificationService) {
        this(store, engine, notificationService,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000));
    }

    PlanWorkOrderService(PlanStore store, PlanEngine engine,
                         PlanNotificationService notificationService, LongSupplier idSupplier) {
        this.store = Objects.requireNonNull(store, "计划存储不能为空");
        this.engine = Objects.requireNonNull(engine, "计算引擎不能为空");
        this.notificationService = Objects.requireNonNull(notificationService, "通知服务不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
    }

    /** 事后关联工单（任务责任人）；类型与工单必须存在且属于本环境（资源申请）。 */
    @Transactional
    public List<TaskWorkOrder> attach(AuthUser actor, long taskId, WorkOrderCommand cmd, String reason,
                                      boolean isAdmin) {
        Task task = engine.requireTask(actor, taskId);
        if (!isAdmin && actor.id() != task.ownerUserId()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅任务责任人可以关联工单");
        }
        Plan plan = engine.requirePlan(actor, task.planId());
        if (plan.cancelled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "计划已取消，不能关联工单");
        }
        WorkOrderType type = cmd == null || cmd.workOrderType() == null
                ? null : cmd.workOrderType();
        if (type == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工单类型不能为空");
        }
        if (type == WorkOrderType.CRYPTO_POOL) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "加密机入池工单尚未上线，暂不支持关联");
        }
        List<Long> workOrderIds = cmd.workOrderIds() == null ? List.of()
                : cmd.workOrderIds().stream().filter(Objects::nonNull).distinct().toList();
        if (workOrderIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择需要关联的工单");
        }
        for (Long workOrderId : workOrderIds) {
            validateWorkOrder(actor, task, plan, type, workOrderId);
            if (store.findWorkOrders(actor.tenantId(), taskId).stream()
                    .anyMatch(w -> w.workOrderType() == type && w.workOrderId() == workOrderId)) {
                throw new BusinessException(ErrorCode.CONFLICT, "该工单已关联本任务");
            }
            long id = nextId();
            store.insertWorkOrder(actor.tenantId(), new TaskWorkOrder(id, taskId, task.planId(), type,
                    workOrderId, WorkOrderSource.ATTACHED_LATER, false));
            store.insertActivity(actor.tenantId(), nextId(), "PLAN", task.planId(), "WORK_ORDER", id,
                    "WORK_ORDER_ATTACHED", actor.id(), trimToNull(reason), null,
                    toJsonValue(type.name() + ":" + workOrderId));
        }
        engine.recompute(actor.tenantId(), task.planId(), LocalDateTime.now());
        notifyGated(actor, task);
        return store.findWorkOrders(actor.tenantId(), taskId);
    }

    /** 工单创建后回写关联（source=CREATED_FROM_TASK），由资源申请/网络工单创建接口调用。 */
    @Transactional
    public void registerCreatedWorkOrder(long tenantId, long taskId, WorkOrderType type, long workOrderId) {
        Task task = store.findTask(tenantId, taskId)
                .orElseThrow(() -> new ArchitectureNotFoundException("任务不存在"));
        if (task.cancelled()) {
            return;
        }
        store.insertWorkOrder(tenantId, new TaskWorkOrder(nextId(), taskId, task.planId(), type,
                workOrderId, WorkOrderSource.CREATED_FROM_TASK, false));
        engine.recompute(tenantId, task.planId(), LocalDateTime.now());
    }

    @Transactional
    public List<TaskWorkOrder> remove(AuthUser actor, long workOrderRelationId, String reason,
                                      boolean isAdmin) {
        TaskWorkOrder relation = store.findWorkOrder(actor.tenantId(), workOrderRelationId)
                .orElseThrow(() -> new ArchitectureNotFoundException("工单关联不存在"));
        Task task = engine.requireTask(actor, relation.taskId());
        Plan plan = engine.requirePlan(actor, task.planId());
        boolean allowed = isAdmin || actor.id() == task.ownerUserId()
                || actor.id() == plan.planOwnerUserId();
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅任务责任人、计划责任人或管理员可以解除关联");
        }
        if (relation.removed()) {
            throw new BusinessException(ErrorCode.CONFLICT, "工单关联已解除");
        }
        String removeReason = trimToNull(reason);
        if (removeReason == null) {
            removeReason = "解除工单关联";
        }
        store.removeWorkOrder(actor.tenantId(), workOrderRelationId, removeReason, actor.id());
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", task.planId(), "WORK_ORDER",
                workOrderRelationId, "WORK_ORDER_DETACHED", actor.id(), removeReason,
                toJsonValue(relation.workOrderType().name() + ":" + relation.workOrderId()), null);
        engine.recompute(actor.tenantId(), task.planId(), LocalDateTime.now());
        return store.findWorkOrders(actor.tenantId(), relation.taskId());
    }

    public List<TaskWorkOrder> list(AuthUser actor, long taskId) {
        return store.findWorkOrders(actor.tenantId(), taskId);
    }

    public List<TaskWorkOrder> openWorkOrders(AuthUser actor, long taskId) {
        return engine.openWorkOrderRefs(actor.tenantId(), store.findWorkOrders(actor.tenantId(), taskId));
    }

    private void validateWorkOrder(AuthUser actor, Task task, Plan plan, WorkOrderType type,
                                   long workOrderId) {
        if (type == WorkOrderType.RESOURCE_REQUEST) {
            List<long[]> refs = store.resourceRequestRefs(actor.tenantId(), List.of(workOrderId));
            if (refs.isEmpty()) {
                throw new ArchitectureNotFoundException("资源申请工单不存在");
            }
            long[] ref = refs.get(0);
            if (ref[1] != plan.environmentId()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "资源申请工单所属环境与计划环境不一致，不能关联");
            }
        } else {
            if (!store.networkWorkOrderRefs(actor.tenantId(), List.of(workOrderId))) {
                throw new ArchitectureNotFoundException("网络专项工单不存在");
            }
        }
    }

    private void notifyGated(AuthUser actor, Task task) {
        List<TaskWorkOrder> open = engine.openWorkOrderRefs(actor.tenantId(),
                store.findWorkOrders(actor.tenantId(), task.id()));
        if (open.isEmpty()) {
            return;
        }
        Plan plan = engine.requirePlan(actor, task.planId());
        List<Long> recipients = new java.util.ArrayList<>(store.findParticipantUserIds(actor.tenantId(),
                task.id()));
        recipients.add(task.ownerUserId());
        recipients.add(plan.planOwnerUserId());
        notificationService.notifyWorkOrderGated(actor.tenantId(), plan.planNo(), task.name(),
                String.valueOf(open.get(0).workOrderId()), recipients);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        return text.length() > 1000 ? null : text;
    }

    private String toJsonValue(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    private long nextId() {
        long value = idSupplier.getAsLong();
        if (value <= 0) {
            throw new IllegalStateException("工单关联标识生成器返回无效值");
        }
        return value;
    }
}
