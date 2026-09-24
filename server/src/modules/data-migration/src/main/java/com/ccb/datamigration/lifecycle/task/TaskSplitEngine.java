package com.ccb.datamigration.lifecycle.task;

import com.ccb.common.exception.BusinessException;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务矩阵拆分引擎（基线 10.2.3 判定式，唯一拆分口径，可复核）：
 * 工单集合 = {(普通活动 A, 组件 C) | A ∈ 专题被聚合普通活动集合，且 C ∈ A.component_scope ∩ 勾选集合}。
 * 项目级 → 1 张无组件工单；组件级普通活动 → 按勾选组件拆 N 张；专题 → 交集拆分（同一 (A,C) 仅 1 张）。
 * 禁止用专题并集对全部活动做笛卡尔积；项目级携带组件必拒。
 */
public final class TaskSplitEngine {
    private TaskSplitEngine() {
    }

    /** 拆分单元：subActivityId 仅专题下发时必填（被展开的普通活动 id）。 */
    public record SplitItem(long activityId, Long subActivityId, Long componentId, String activityCode, String activityName) {
    }

    /**
     * 矩阵拆分。
     *
     * @param activityType      NORMAL/TOPIC
     * @param granularity       PROJECT/COMPONENT
     * @param selected         勾选组件 id（有序去重）
     * @param enabledComponents 可用组件 id 集合（dm_component.deleted=0）
     * @param activityScope     key=普通活动 id，value=该活动关联组件 id 集合（组件级普通活动的 activity_component_rel 范围）
     * @param memberMeta        key=普通活动 id，value=[activityCode, activityName]（专题被聚合活动，按序）
     */
    public static List<SplitItem> split(String activityType, String granularity, List<Long> selected,
                                        Set<Long> enabledComponents, Map<Long, Set<Long>> activityScope,
                                        Map<Long, String[]> memberMeta) {
        if ("PROJECT".equals(granularity)) {
            if (selected != null && !selected.isEmpty()) {
                throw new BusinessException(LifecycleErrorCode.TASK_PROJECT_COMPONENT_FORBIDDEN, "项目级活动/专题禁止携带组件下发");
            }
            return List.of();
        }
        if (selected == null || selected.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.TASK_COMPONENT_REQUIRED, "组件级活动/专题必须勾选至少 1 个有效组件");
        }
        List<Long> normalized = new ArrayList<>(new LinkedHashSet<>(selected));
        for (Long componentId : normalized) {
            if (enabledComponents != null && !enabledComponents.contains(componentId)) {
                throw new BusinessException(LifecycleErrorCode.TASK_COMPONENT_DISABLED, "停用组件不参与任务下发：" + componentId);
            }
        }
        List<SplitItem> items = new ArrayList<>();
        // NORMAL：以活动自身关联组件为范围
        if ("NORMAL".equals(activityType)) {
            Set<Long> scope = activityScope == null ? Set.of() : activityScope.getOrDefault(0L, Set.of());
            if (scope.isEmpty()) {
                throw new BusinessException(LifecycleErrorCode.TASK_COMPONENT_NOT_IN_SCOPE, "该活动尚未关联任何组件，禁止组件级下发");
            }
            for (Long componentId : normalized) {
                if (!scope.contains(componentId)) {
                    throw new BusinessException(LifecycleErrorCode.TASK_COMPONENT_NOT_IN_SCOPE, "组件不在活动关联范围内：" + componentId);
                }
                items.add(new SplitItem(0L, null, componentId, null, null));
            }
            return items;
        }
        // TOPIC：普通活动 × 组件 交集
        if (memberMeta == null || memberMeta.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.TASK_COMPONENT_NOT_IN_SCOPE, "专题未聚合任何普通活动，禁止下发");
        }
        boolean anyInScope = false;
        for (Map.Entry<Long, String[]> entry : memberMeta.entrySet()) {
            Long activityId = entry.getKey();
            Set<Long> scope = activityScope == null ? Set.of() : activityScope.getOrDefault(activityId, Set.of());
            for (Long componentId : normalized) {
                if (scope.contains(componentId)) {
                    anyInScope = true;
                    String[] meta = entry.getValue();
                    items.add(new SplitItem(activityId, activityId, componentId, meta == null ? null : meta[0],
                            meta == null ? null : meta[1]));
                }
            }
        }
        if (!anyInScope) {
            throw new BusinessException(LifecycleErrorCode.TASK_COMPONENT_NOT_IN_SCOPE, "勾选组件均不在专题被聚合活动的组件范围内");
        }
        return items;
    }
}
