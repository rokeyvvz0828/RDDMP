package com.ccb.architecture.plan.service;

import com.ccb.architecture.plan.model.PlanModels.PlanStatus;
import com.ccb.architecture.plan.model.PlanModels.TaskStatus;

import java.time.LocalDateTime;

/**
 * 计划状态、逾期与进度确定性计算（REQ-20260830-056）。
 * 规则：任务状态优先级=已取消>已完成>阻塞>等待前置>进行中>未开始；
 * 进度=已完成检查项数/全部未取消检查项数；逾期=超过计划结束时间且尚未完成且未取消。
 */
public final class PlanStatusCalculator {

    public record TaskFact(boolean cancelled, boolean completionMet, boolean hasOpenBlock,
                           boolean hasOpenWorkOrder, boolean missingPreceding, boolean started,
                           boolean hasCheckItems, boolean allCheckItemsCancelled,
                           LocalDateTime plannedEnd) {
    }

    public record TaskComputed(TaskStatus status, boolean waivedAll, boolean overdue) {
    }

    public static TaskComputed computeTask(TaskFact fact, LocalDateTime now) {
        TaskStatus status;
        if (fact.cancelled()) {
            status = TaskStatus.CANCELLED;
        } else if (fact.completionMet()) {
            status = TaskStatus.COMPLETED;
        } else if (fact.hasOpenBlock()) {
            status = TaskStatus.BLOCKED;
        } else if (fact.missingPreceding()) {
            status = TaskStatus.WAITING_PRECEDING;
        } else if (fact.started()) {
            status = TaskStatus.IN_PROGRESS;
        } else {
            status = TaskStatus.NOT_STARTED;
        }
        boolean waivedAll = !fact.cancelled() && fact.hasCheckItems() && fact.allCheckItemsCancelled();
        boolean overdue = !fact.cancelled() && status != TaskStatus.COMPLETED
                && fact.plannedEnd() != null && now.isAfter(fact.plannedEnd());
        return new TaskComputed(status, waivedAll, overdue);
    }

    /** 任务进度：已完成检查项数 / 全部未取消检查项数；无检查项返回 null；全部取消返回 100。 */
    public static Long progressPercent(int completed, int total, int cancelledTotal) {
        if (total <= 0) {
            return null;
        }
        int effective = total - cancelledTotal;
        if (effective <= 0) {
            return 100L;
        }
        return Math.round(completed * 100.0 / effective);
    }

    private PlanStatusCalculator() {
    }
}
