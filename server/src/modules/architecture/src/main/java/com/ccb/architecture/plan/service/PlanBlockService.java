package com.ccb.architecture.plan.service;

import com.ccb.architecture.plan.model.PlanModels.Block;
import com.ccb.architecture.plan.model.PlanModels.Plan;
import com.ccb.architecture.plan.model.PlanModels.BlockCommand;
import com.ccb.architecture.plan.model.PlanModels.Task;
import com.ccb.architecture.plan.model.PlanModels.TaskStatus;
import com.ccb.architecture.plan.persistence.PlanStore;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/** 任务阻塞记录（REQ-20260830-056）：登记、修改与解除；未解除阻塞决定任务阻塞状态并向上聚合。 */
@Service
public class PlanBlockService {
    private final PlanStore store;
    private final PlanEngine engine;
    private final PlanNotificationService notificationService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final LongSupplier idSupplier;

    @org.springframework.beans.factory.annotation.Autowired
    public PlanBlockService(PlanStore store, PlanEngine engine, PlanNotificationService notificationService,
                            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this(store, engine, notificationService, objectMapper,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000));
    }

    PlanBlockService(PlanStore store, PlanEngine engine, PlanNotificationService notificationService,
                     com.fasterxml.jackson.databind.ObjectMapper objectMapper, LongSupplier idSupplier) {
        this.store = Objects.requireNonNull(store, "计划存储不能为空");
        this.engine = Objects.requireNonNull(engine, "计算引擎不能为空");
        this.notificationService = Objects.requireNonNull(notificationService, "通知服务不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 能力不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
    }

    @Transactional
    public Block addBlock(AuthUser actor, long projectId, long taskId, BlockCommand cmd, boolean isAdmin) {
        if (cmd == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "阻塞信息不能为空");
        Task task = engine.requireTask(actor, projectId, taskId);
        engine.requireTaskExecutor(actor, projectId, task, false);
        if (task.cancelled() || task.status() == TaskStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务已取消或已完成，不能登记阻塞");
        }
        engine.participation().validate(actor, projectId, task.targetType(), task.targetId(), cmd.ownerUserId(), List.of());
        if (cmd.ownerUserId() != task.ownerUserId()
                && !store.findParticipantUserIds(actor.tenantId(), projectId, task.id()).contains(cmd.ownerUserId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先将阻塞负责人加入任务参与人员");
        }
        String description = requireText(cmd == null ? null : cmd.description(), "阻塞描述", 2000);
        String impact = trimToNull(cmd == null ? null : cmd.impact(), 2000);
        long blockId = nextId();
        store.insertBlock(actor.tenantId(), projectId, new Block(blockId, taskId, description, impact,
                cmd.ownerUserId(), cmd.expectedResolveAt(), false, null, null, null, actor.id()));
        store.insertActivity(actor.tenantId(), projectId, nextId(), "PLAN", task.planId(), "BLOCK", blockId,
                "BLOCK_REGISTERED", actor.id(), null,
                null, toJsonValue(Map.of("taskId", taskId, "description", description)));
        notificationService.notifyBlockRegistered(actor.tenantId(), planNo(actor, projectId, task.planId()),
                task.name(), recipients(actor, projectId, task));
        engine.recompute(actor.tenantId(), projectId, task.planId(), LocalDateTime.now());
        return requireBlock(actor, projectId, blockId);
    }

    @Transactional
    public Block updateBlock(AuthUser actor, long projectId, long blockId, BlockCommand cmd,
                             boolean isAdmin) {
        if (cmd == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "阻塞信息不能为空");
        Block block = requireBlock(actor, projectId, blockId);
        Task task = engine.requireTask(actor, projectId, block.taskId());
        if (!engine.participation().manager(actor, projectId, task.planId()))
            engine.requireTaskExecutor(actor, projectId, task, false);
        else {
            // 先锁系统资格，再锁任务，与执行和分派命令保持一致。
            engine.participation().validate(actor, projectId, task.targetType(), task.targetId(), cmd.ownerUserId(), List.of());
            Task current = store.lockTask(actor.tenantId(), projectId, task.id())
                    .orElseThrow(() -> new ArchitectureNotFoundException("任务不存在"));
            if (!current.equals(task)) throw new BusinessException(ErrorCode.CONFLICT, "任务已更新，请刷新后重试");
        }
        block = lockBlock(actor, projectId, blockId);
        if (block.resolved()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "阻塞已解除，不能修改");
        }
        engine.participation().validate(actor, projectId, task.targetType(), task.targetId(), cmd.ownerUserId(), List.of());
        if (cmd.ownerUserId() != task.ownerUserId()
                && !store.findParticipantUserIds(actor.tenantId(), projectId, task.id()).contains(cmd.ownerUserId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先将阻塞负责人加入任务参与人员");
        }
        String description = requireText(cmd == null ? null : cmd.description(), "阻塞描述", 2000);
        String impact = trimToNull(cmd == null ? null : cmd.impact(), 2000);
        store.updateBlock(actor.tenantId(), projectId, blockId, description, impact, cmd.ownerUserId(),
                cmd.expectedResolveAt());
        store.insertActivity(actor.tenantId(), projectId, nextId(), "PLAN", task.planId(), "BLOCK", blockId,
                "BLOCK_UPDATED", actor.id(), null, null,
                toJsonValue(Map.of("taskId", block.taskId(), "description", description)));
        engine.recompute(actor.tenantId(), projectId, task.planId(), LocalDateTime.now());
        return requireBlock(actor, projectId, blockId);
    }

    @Transactional
    public Block resolveBlock(AuthUser actor, long projectId, long blockId, String note, boolean isAdmin) {
        Block block = requireBlock(actor, projectId, blockId);
        Task task = engine.requireTask(actor, projectId, block.taskId());
        engine.requireTaskExecutor(actor, projectId, task, false);
        block = lockBlock(actor, projectId, blockId);
        if (block.resolved()) {
            throw new BusinessException(ErrorCode.CONFLICT, "阻塞已解除");
        }
        store.resolveBlock(actor.tenantId(), projectId, blockId, trimToNull(note, 1000), actor.id());
        store.insertActivity(actor.tenantId(), projectId, nextId(), "PLAN", task.planId(), "BLOCK", blockId,
                "BLOCK_RESOLVED", actor.id(), trimToNull(note, 1000), null, null);
        notificationService.notifyBlockResolved(actor.tenantId(), planNo(actor, projectId, task.planId()),
                task.name(), recipients(actor, projectId, task));
        engine.recompute(actor.tenantId(), projectId, task.planId(), LocalDateTime.now());
        return requireBlock(actor, projectId, blockId);
    }

    public List<Block> listBlocks(AuthUser actor, long projectId, long taskId) {
        engine.requireTask(actor, projectId, taskId);
        engine.participation().requireVisible(actor, projectId, engine.requireTask(actor, projectId, taskId));
        return store.findBlocks(actor.tenantId(), projectId, taskId);
    }

    private Block lockBlock(AuthUser actor, long projectId, long blockId) {
        return store.lockBlock(actor.tenantId(), projectId, blockId)
                .orElseThrow(() -> new ArchitectureNotFoundException("阻塞记录不存在"));
    }

    private Block requireBlock(AuthUser actor, long projectId, long blockId) {
        return store.findBlock(actor.tenantId(), projectId, blockId)
                .orElseThrow(() -> new ArchitectureNotFoundException("阻塞记录不存在"));
    }

    private List<Long> recipients(AuthUser actor, long projectId, Task task) {
        return engine.participation().recipients(actor, projectId, task);
    }

    private String planNo(AuthUser actor, long projectId, long planId) {
        return store.findPlan(actor.tenantId(), projectId, planId)
                .map(Plan::planNo).orElse(String.valueOf(planId));
    }

    private static String requireText(String value, String field, int maxLength) {
        String text = trimToNull(value, maxLength);
        if (text == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + "不能为空");
        }
        return text;
    }

    private static String trimToNull(String value, int maxLength) {
        String text = value == null || value.isBlank() ? null : value.trim();
        if (text != null && text.length() > maxLength) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容长度不能超过 " + maxLength);
        }
        return text;
    }

    private String toJsonValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    private long nextId() {
        long value = idSupplier.getAsLong();
        if (value <= 0) {
            throw new IllegalStateException("阻塞标识生成器返回无效值");
        }
        return value;
    }
}
