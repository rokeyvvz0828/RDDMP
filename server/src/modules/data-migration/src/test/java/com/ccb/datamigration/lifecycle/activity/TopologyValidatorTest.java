package com.ccb.datamigration.lifecycle.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ccb.common.exception.BusinessException;
import com.ccb.datamigration.lifecycle.activity.model.TopologyInput;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 拓扑判定器穷举（基线 9.7 L1 + 铁律 #12/#17）：非法必拒、合法必通、无前置派生语义双向断言。 */
class TopologyValidatorTest {

    private static TopologyInput.EdgeInput edge(long source, long target) {
        return new TopologyInput.EdgeInput(source, target);
    }

    @Test
    void emptyProcessesAreRejected() {
        assertThatThrownBy(() -> TopologyValidator.validateForSave(List.of(), List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.PROCESS_MIN_ONE));
        assertThatThrownBy(() -> TopologyValidator.validateForSave(null, List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.PROCESS_MIN_ONE));
    }

    @Test
    void overLimitProcessesAreRejected() {
        List<Long> thirtyOne = new ArrayList<>();
        for (long i = 1; i <= 31; i++) {
            thirtyOne.add(i);
        }
        assertThatThrownBy(() -> TopologyValidator.validateForSave(thirtyOne, List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.TOPOLOGY_LIMIT_EXCEEDED));
    }

    @Test
    void missingNodeEdgesAreRejected() {
        assertThatThrownBy(() -> TopologyValidator.validateForSave(List.of(1L, 2L), List.of(edge(1L, 99L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.TOPOLOGY_MISSING_NODE));
        assertThatThrownBy(() -> TopologyValidator.validateForSave(List.of(1L, 2L), List.of(edge(99L, 1L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.TOPOLOGY_MISSING_NODE));
    }

    @Test
    void selfLoopEdgesAreRejected() {
        assertThatThrownBy(() -> TopologyValidator.validateForSave(List.of(1L, 2L), List.of(edge(1L, 1L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.TOPOLOGY_SELF_LOOP));
    }

    @Test
    void duplicateEdgesAreRejected() {
        assertThatThrownBy(() -> TopologyValidator.validateForSave(List.of(1L, 2L), List.of(edge(1L, 2L), edge(1L, 2L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.TOPOLOGY_DUPLICATE_EDGE));
    }

    @Test
    void cyclicEdgesAreRejected() {
        assertThatThrownBy(() -> TopologyValidator.validateForSave(List.of(1L, 2L, 3L),
                List.of(edge(1L, 2L), edge(2L, 3L), edge(3L, 1L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.TOPOLOGY_CYCLE));
        assertThatThrownBy(() -> TopologyValidator.validateForSave(List.of(1L, 2L), List.of(edge(1L, 2L), edge(2L, 1L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.TOPOLOGY_CYCLE));
    }

    @Test
    void validTopologiesAreAccepted() {
        assertThatCode(() -> TopologyValidator.validateForSave(List.of(1L), List.of())).doesNotThrowAnyException();
        assertThatCode(() -> TopologyValidator.validateForSave(List.of(1L, 2L), List.of(edge(1L, 2L)))).doesNotThrowAnyException();
        assertThatCode(() -> TopologyValidator.validateForSave(List.of(1L, 2L, 3L),
                List.of(edge(1L, 2L), edge(1L, 3L), edge(2L, 3L)))).doesNotThrowAnyException();
    }

    @Test
    void thirtyNodeChainIsAccepted() {
        List<Long> nodes = new ArrayList<>();
        List<TopologyInput.EdgeInput> edges = new ArrayList<>();
        for (long i = 1; i <= 30; i++) {
            nodes.add(i);
            if (i > 1) {
                edges.add(edge(i - 1, i));
            }
        }
        assertThatCode(() -> TopologyValidator.validateForSave(nodes, edges)).doesNotThrowAnyException();
    }

    @Test
    void noPredecessorIsDerivedFromIncomingEdges() {
        List<TopologyInput.EdgeInput> edges = List.of(edge(1L, 2L), edge(2L, 3L));
        assertThat(TopologyValidator.deriveNoPredecessor(1L, edges)).isTrue();
        assertThat(TopologyValidator.deriveNoPredecessor(2L, edges)).isFalse();
        assertThat(TopologyValidator.deriveNoPredecessor(3L, edges)).isFalse();
        assertThat(TopologyValidator.deriveNoPredecessor(9L, edges)).isTrue();
        assertThat(TopologyValidator.deriveNoPredecessor(9L, null)).isTrue();
    }

    @Test
    void validationOrderIsFixedExistenceThenQuantityThenState() {
        List<Long> thirtyOne = new ArrayList<>();
        for (long i = 1; i <= 31; i++) {
            thirtyOne.add(i);
        }
        assertThatThrownBy(() -> TopologyValidator.validateForSave(thirtyOne, List.of(edge(1L, 999L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(LifecycleErrorCode.TOPOLOGY_LIMIT_EXCEEDED));
    }
}
