package com.ccb.datamigration.lifecycle.activity;

import com.ccb.common.exception.BusinessException;
import com.ccb.datamigration.lifecycle.activity.model.TopologyInput;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 拓扑判定器（基线 9.2.4 + 铁律 #12/#17）：固定校验顺序 = 存在性 → 数量约束 → 状态约束。
 * 校验项：至少 1 道工序、引用缺失节点、超限（≤30）、自连、重复依赖、成环；
 * is_no_predecessor 为依赖连线的派生值（无入边 → true，有入边 → false）。
 * 每个非法必拒均有唯一错误码；合法输入必须放行（双向断言配对）。
 */
public final class TopologyValidator {
    public static final int MAX_PROCESS_COUNT = 30;

    private TopologyValidator() {
    }

    /** 拓扑结构校验：存在性 → 数量约束 → 状态约束（基于工序 id 集合与依赖边）。 */
    public static void validateForSave(List<Long> processIds, List<TopologyInput.EdgeInput> edges) {
        if (processIds == null || processIds.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.PROCESS_MIN_ONE, "活动至少保留 1 道工序");
        }
        if (processIds.size() > MAX_PROCESS_COUNT) {
            throw new BusinessException(LifecycleErrorCode.TOPOLOGY_LIMIT_EXCEEDED, "单活动工序数不得超过 30 节点");
        }
        Set<Long> ids = new HashSet<>(processIds);
        List<TopologyInput.EdgeInput> normalized = edges == null ? List.of() : edges;
        for (TopologyInput.EdgeInput edge : normalized) {
            if (!ids.contains(edge.sourceProcessId()) || !ids.contains(edge.targetProcessId())) {
                throw new BusinessException(LifecycleErrorCode.TOPOLOGY_MISSING_NODE, "禁止引用缺失节点");
            }
        }
        Set<String> edgeKeys = new HashSet<>();
        for (TopologyInput.EdgeInput edge : normalized) {
            if (edge.sourceProcessId() == edge.targetProcessId()) {
                throw new BusinessException(LifecycleErrorCode.TOPOLOGY_SELF_LOOP, "禁止自连依赖");
            }
            if (!edgeKeys.add(edge.sourceProcessId() + "->" + edge.targetProcessId())) {
                throw new BusinessException(LifecycleErrorCode.TOPOLOGY_DUPLICATE_EDGE, "禁止重复依赖连线");
            }
        }
        assertNoCycle(ids, normalized);
    }

    /** 派生 is_no_predecessor：工序无入边 → true（有入边 → false）。权威来源为依赖连线。 */
    public static boolean deriveNoPredecessor(long processId, List<TopologyInput.EdgeInput> edges) {
        if (edges == null) {
            return true;
        }
        for (TopologyInput.EdgeInput edge : edges) {
            if (edge.targetProcessId() == processId) {
                return false;
            }
        }
        return true;
    }

    private static void assertNoCycle(Set<Long> ids, List<TopologyInput.EdgeInput> edges) {
        Map<Long, List<Long>> adjacency = new HashMap<>();
        for (long id : ids) {
            adjacency.put(id, new ArrayList<>());
        }
        for (TopologyInput.EdgeInput edge : edges) {
            adjacency.get(edge.sourceProcessId()).add(edge.targetProcessId());
        }
        Set<Long> visiting = new HashSet<>();
        Set<Long> visited = new HashSet<>();
        for (long id : ids) {
            if (!visited.contains(id)) {
                detectCycle(id, adjacency, visiting, visited);
            }
        }
    }

    private static void detectCycle(long node, Map<Long, List<Long>> adjacency,
                                    Set<Long> visiting, Set<Long> visited) {
        if (visiting.contains(node)) {
            throw new BusinessException(LifecycleErrorCode.TOPOLOGY_CYCLE, "禁止成环依赖");
        }
        if (visited.contains(node)) {
            return;
        }
        visiting.add(node);
        for (long next : adjacency.getOrDefault(node, List.of())) {
            detectCycle(next, adjacency, visiting, visited);
        }
        visiting.remove(node);
        visited.add(node);
    }
}
