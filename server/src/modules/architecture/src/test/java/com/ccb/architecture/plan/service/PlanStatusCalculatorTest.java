package com.ccb.architecture.plan.service;

import com.ccb.architecture.plan.model.PlanModels.PlanStatus;
import com.ccb.architecture.plan.model.PlanModels.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 计划状态、逾期与进度确定性规则（REQ-20260830-056）。 */
class PlanStatusCalculatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 30, 10, 0);

    private static PlanStatusCalculator.TaskFact fact(boolean cancelled, boolean completionMet,
                                                      boolean hasOpenBlock, boolean hasOpenWorkOrder,
                                                      boolean missingPreceding, boolean started,
                                                      boolean hasCheckItems, boolean allCancelled,
                                                      LocalDateTime plannedEnd) {
        return new PlanStatusCalculator.TaskFact(cancelled, completionMet, hasOpenBlock,
                hasOpenWorkOrder, missingPreceding, started, hasCheckItems, allCancelled, plannedEnd);
    }

    @Test
    void cancelledWinsOverEverything() {
        var computed = PlanStatusCalculator.computeTask(fact(true, true, true, true, true, true,
                true, true, NOW.minusDays(1)), NOW);
        assertThat(computed.status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(computed.waivedAll()).isFalse();
        assertThat(computed.overdue()).isFalse();
    }

    @Test
    void completedTaskNeverOverdue() {
        var computed = PlanStatusCalculator.computeTask(fact(false, true, false, false, false, true,
                true, false, NOW.minusDays(1)), NOW);
        assertThat(computed.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(computed.overdue()).isFalse();
    }

    @Test
    void openBlockTakesPrecedenceOverWaitingAndStarted() {
        var computed = PlanStatusCalculator.computeTask(fact(false, false, true, false, true, true,
                true, false, null), NOW);
        assertThat(computed.status()).isEqualTo(TaskStatus.BLOCKED);
    }

    @Test
    void missingPrecedingYieldsWaiting() {
        var computed = PlanStatusCalculator.computeTask(fact(false, false, false, false, true, false,
                true, false, null), NOW);
        assertThat(computed.status()).isEqualTo(TaskStatus.WAITING_PRECEDING);
    }

    @Test
    void startedWithoutBlockOrWaitingIsInProgress() {
        var computed = PlanStatusCalculator.computeTask(fact(false, false, false, false, false, true,
                true, false, null), NOW);
        assertThat(computed.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void neverStartedIsNotStarted() {
        var computed = PlanStatusCalculator.computeTask(fact(false, false, false, false, false, false,
                true, false, null), NOW);
        assertThat(computed.status()).isEqualTo(TaskStatus.NOT_STARTED);
    }

    @Test
    void allCheckItemsCancelledMeansWaivedAllAndCompleted() {
        var computed = PlanStatusCalculator.computeTask(fact(false, true, false, false, false, false,
                true, true, null), NOW);
        assertThat(computed.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(computed.waivedAll()).isTrue();
    }

    @Test
    void overdueOnlyWhenNotCompletedAndPastDeadline() {
        var overdue = PlanStatusCalculator.computeTask(fact(false, false, false, false, false, true,
                true, false, NOW.minusMinutes(1)), NOW);
        assertThat(overdue.overdue()).isTrue();
        var notOverdue = PlanStatusCalculator.computeTask(fact(false, false, false, false, false, true,
                true, false, NOW.plusMinutes(1)), NOW);
        assertThat(notOverdue.overdue()).isFalse();
        var noDeadline = PlanStatusCalculator.computeTask(fact(false, false, false, false, false, true,
                true, false, null), NOW);
        assertThat(noDeadline.overdue()).isFalse();
    }

    @Test
    void progressUsesEffectiveCheckItems() {
        assertThat(PlanStatusCalculator.progressPercent(5, 10, 0)).isEqualTo(50L);
        assertThat(PlanStatusCalculator.progressPercent(3, 8, 2)).isEqualTo(50L);
        assertThat(PlanStatusCalculator.progressPercent(6, 6, 0)).isEqualTo(100L);
        assertThat(PlanStatusCalculator.progressPercent(0, 5, 5)).isEqualTo(100L);
        assertThat(PlanStatusCalculator.progressPercent(0, 0, 0)).isNull();
    }
}
