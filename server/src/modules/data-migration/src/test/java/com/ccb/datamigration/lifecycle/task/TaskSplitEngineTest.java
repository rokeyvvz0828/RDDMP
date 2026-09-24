package com.ccb.datamigration.lifecycle.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ccb.common.exception.BusinessException;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import com.ccb.datamigration.lifecycle.task.TaskSplitEngine.SplitItem;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 矩阵拆分引擎穷举（基线 10.2.3 判定式，T4）：N 组件→N 工单、专题交集、非法必拒、去重与范围校验。 */
class TaskSplitEngineTest {

    @Test
    void componentNormalSelectsEachComponentAsOneOrder() {
        List<SplitItem> items = TaskSplitEngine.split("NORMAL", "COMPONENT", List.of(101L, 102L, 103L),
                Set.of(101L, 102L, 103L), Map.of(0L, Set.of(101L, 102L, 103L)), Map.of(0L, new String[]{"ACT-CMP-01", "数据核对"}));
        assertThat(items).hasSize(3);
        assertThat(items).extracting(SplitItem::componentId).containsExactly(101L, 102L, 103L);
        assertThat(items).allSatisfy(item -> assertThat(item.activityId()).isEqualTo(0L));
    }

    @Test
    void duplicateSelectedComponentsAreDeduplicated() {
        List<SplitItem> items = TaskSplitEngine.split("NORMAL", "COMPONENT", List.of(101L, 101L, 102L),
                Set.of(101L, 102L), Map.of(0L, Set.of(101L, 102L)), Map.of(0L, new String[]{"A", "B"}));
        assertThat(items).hasSize(2);
    }

    @Test
    void projectLevelWithComponentsIsRejected() {
        assertThatThrownBy(() -> TaskSplitEngine.split("NORMAL", "PROJECT", List.of(101L),
                Set.of(101L), Map.of(0L, Set.of(101L)), Map.of(0L, new String[]{"A", "B"})))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.TASK_PROJECT_COMPONENT_FORBIDDEN));
    }

    @Test
    void componentLevelWithoutSelectionIsRejected() {
        assertThatThrownBy(() -> TaskSplitEngine.split("NORMAL", "COMPONENT", List.of(),
                Set.of(), Map.of(0L, Set.of()), Map.of(0L, new String[]{"A", "B"})))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.TASK_COMPONENT_REQUIRED));
    }

    @Test
    void disabledComponentIsRejected() {
        assertThatThrownBy(() -> TaskSplitEngine.split("NORMAL", "COMPONENT", List.of(101L),
                Set.of(102L), Map.of(0L, Set.of(101L)), Map.of(0L, new String[]{"A", "B"})))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.TASK_COMPONENT_DISABLED));
    }

    @Test
    void componentOutsideActivityScopeIsRejected() {
        assertThatThrownBy(() -> TaskSplitEngine.split("NORMAL", "COMPONENT", List.of(101L),
                Set.of(101L), Map.of(0L, Set.of(102L)), Map.of(0L, new String[]{"A", "B"})))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.TASK_COMPONENT_NOT_IN_SCOPE));
    }

    @Test
    void topicUsesIntersectionOfSelectionAndEachMemberScope() {
        // 专题聚合 3 个普通活动；勾选 101/102/103/104
        // 活动1 范围 {101,103} → 101,103；活动2 范围 {102} → 102；活动3 范围 {101,104} → 101,104
        Map<Long, Set<Long>> scopes = Map.of(
                11L, Set.of(101L, 103L),
                12L, Set.of(102L),
                13L, Set.of(101L, 104L));
        Map<Long, String[]> meta = Map.of(
                11L, new String[]{"ACT-N-1", "逐一核对"},
                12L, new String[]{"ACT-N-2", "数据转换"},
                13L, new String[]{"ACT-N-3", "业务验证"});
        List<SplitItem> items = TaskSplitEngine.split("TOPIC", "COMPONENT", List.of(101L, 102L, 103L, 104L),
                Set.of(101L, 102L, 103L, 104L), scopes, meta);
        // 预期：101→活动1、103→活动1、102→活动2、101→活动3、104→活动3（交集拆分，共 5 条）
        assertThat(items).hasSize(5);
        assertThat(items).extracting(SplitItem::subActivityId).containsExactlyInAnyOrder(11L, 11L, 12L, 13L, 13L);
        assertThat(items).allSatisfy(item -> assertThat(item.subActivityId()).isEqualTo(item.activityId()));
    }

    @Test
    void topicWithoutMembersIsRejected() {
        assertThatThrownBy(() -> TaskSplitEngine.split("TOPIC", "COMPONENT", List.of(101L),
                Set.of(101L), Map.of(11L, Set.of(101L)), Map.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.TASK_COMPONENT_NOT_IN_SCOPE));
    }

    @Test
    void topicWithNoSelectedComponentInAnyScopeIsRejected() {
        assertThatThrownBy(() -> TaskSplitEngine.split("TOPIC", "COMPONENT", List.of(999L),
                Set.of(999L), Map.of(11L, Set.of(101L)), Map.of(11L, new String[]{"A", "B"})))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.TASK_COMPONENT_NOT_IN_SCOPE));
    }
}
