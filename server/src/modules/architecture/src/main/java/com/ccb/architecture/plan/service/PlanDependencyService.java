package com.ccb.architecture.plan.service;

import com.ccb.architecture.plan.model.PlanModels.Dependency;
import com.ccb.architecture.plan.model.PlanModels.Plan;
import com.ccb.architecture.plan.model.PlanModels.Task;
import com.ccb.architecture.plan.persistence.PlanStore;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/** 任务前置依赖管理（REQ-20260830-056）：建立、移除、改绑与循环校验。 */
@Service
public class PlanDependencyService {
    private final PlanStore store;
    private final PlanEngine engine;
    private final LongSupplier idSupplier;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public PlanDependencyService(PlanStore store, PlanEngine engine,
                                 com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this(store, engine, objectMapper,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000));
    }

    PlanDependencyService(PlanStore store, PlanEngine engine,
                          com.fasterxml.jackson.databind.ObjectMapper objectMapper, LongSupplier idSupplier) {
        this.store = Objects.requireNonNull(store, "计划存储不能为空");
        this.engine = Objects.requireNonNull(engine, "计算引擎不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 能力不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
    }

    /** 全量替换任务前置依赖（含移除与改绑）；移除的前置以“待处理”语义保留置为 REMOVED。 */
    @Transactional
    public List<Dependency> setDependencies(AuthUser actor, long taskId, List<Long> predecessorTaskIds,
                                            String reason, boolean isAdmin) {
        Task task = engine.requireTask(actor, taskId);
        Plan plan = engine.requirePlan(actor, task.planId());
        engine.requirePlanOwner(actor, plan, isAdmin);
        String changeReason = reason == null || reason.isBlank() ? "依赖调整" : reason.trim();
        List<Long> target = predecessorTaskIds == null ? List.of()
                : predecessorTaskIds.stream().filter(Objects::nonNull).distinct().toList();
        for (Long predecessorId : target) {
            if (predecessorId == taskId) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "任务不能依赖自身");
            }
            Task predecessor = store.findTask(actor.tenantId(), predecessorId)
                    .orElseThrow(() -> new ArchitectureNotFoundException("前置任务不存在"));
            if (predecessor.planId() != task.planId()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "前置任务必须属于同一计划");
            }
        }
        List<Dependency> current = store.findDependencies(actor.tenantId(), taskId, false);
        List<Long> keepIds = target.stream().filter(id -> current.stream()
                .anyMatch(dep -> dep.predecessorId() == id)).toList();
        for (Dependency dep : current) {
            if (!keepIds.contains(dep.predecessorId())) {
                store.removeDependency(actor.tenantId(), dep.id(), changeReason, actor.id());
            }
        }
        // 循环校验：现有未移除依赖 + 新增依赖构成图
        Map<Long, List<Long>> adjacency = loadAdjacency(actor, task.planId(), taskId);
        for (Long predecessorId : target) {
            if (current.stream().noneMatch(dep -> dep.predecessorId() == predecessorId)) {
                adjacency.computeIfAbsent(taskId, k -> new ArrayList<>()).add(predecessorId);
                if (hasCycle(adjacency)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "依赖关系存在循环，已拒绝保存");
                }
                store.insertDependency(actor.tenantId(), nextId(), taskId, predecessorId, actor.id());
            }
        }
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", task.planId(), "DEPENDENCY", taskId,
                "DEPENDENCY_CHANGED", actor.id(), changeReason,
                null, toJson(Map.of("taskId", taskId, "predecessors", target)));
        engine.recompute(actor.tenantId(), task.planId(), LocalDateTime.now());
        return store.findDependencies(actor.tenantId(), taskId, false);
    }

    @Transactional
    public void removeDependency(AuthUser actor, long dependencyId, String reason, boolean isAdmin) {
        Dependency dependency = requireDependency(actor, dependencyId);
        Task task = engine.requireTask(actor, dependency.taskId());
        Plan plan = engine.requirePlan(actor, task.planId());
        engine.requirePlanOwner(actor, plan, isAdmin);
        String removeReason = reason == null || reason.isBlank() ? "移除依赖" : reason.trim();
        store.removeDependency(actor.tenantId(), dependencyId, removeReason, actor.id());
        store.insertActivity(actor.tenantId(), nextId(), "PLAN", task.planId(), "DEPENDENCY",
                dependencyId, "DEPENDENCY_REMOVED", actor.id(), removeReason,
                toJson(Map.of("predecessorId", dependency.predecessorId())), null);
        engine.recompute(actor.tenantId(), task.planId(), LocalDateTime.now());
    }

    private Map<Long, List<Long>> loadAdjacency(AuthUser actor, long planId, long excludeChangesForTask) {
        Map<Long, List<Long>> adjacency = new HashMap<>();
        for (Task task : store.findTasks(actor.tenantId(), planId, null)) {
            if (task.id() == excludeChangesForTask) {
                continue;
            }
            for (Dependency dep : store.findDependencies(actor.tenantId(), task.id(), false)) {
                adjacency.computeIfAbsent(task.id(), k -> new ArrayList<>()).add(dep.predecessorId());
            }
        }
        return adjacency;
    }

    private boolean hasCycle(Map<Long, List<Long>> adjacency) {
        Map<Long, Integer> visited = new HashMap<>();
        for (Long node : adjacency.keySet()) {
            if (visit(node, adjacency, visited)) {
                return true;
            }
        }
        return false;
    }

    private boolean visit(Long node, Map<Long, List<Long>> adjacency, Map<Long, Integer> visited) {
        int state = visited.getOrDefault(node, 0);
        if (state == 1) {
            return true;
        }
        if (state == 2) {
            return false;
        }
        visited.put(node, 1);
        for (Long next : adjacency.getOrDefault(node, List.of())) {
            if (visit(next, adjacency, visited)) {
                return true;
            }
        }
        visited.put(node, 2);
        return false;
    }

    private Dependency requireDependency(AuthUser actor, long dependencyId) {
        return store.findDependencyById(actor.tenantId(), dependencyId)
                .orElseThrow(() -> new ArchitectureNotFoundException("依赖记录不存在"));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    private long nextId() {
        long value = idSupplier.getAsLong();
        if (value <= 0) {
            throw new IllegalStateException("依赖标识生成器返回无效值");
        }
        return value;
    }
}
