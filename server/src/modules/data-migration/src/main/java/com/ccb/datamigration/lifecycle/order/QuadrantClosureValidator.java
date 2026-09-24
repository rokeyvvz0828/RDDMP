package com.ccb.datamigration.lifecycle.order;

/**
 * 准出校验四象限穷举判定器（基线 18.2，跨模块唯一闭环判定依据）：
 * 因子 A=must_audit、因子 B=must_submit_deliverable，取自工单固化快照（禁止实时活动配置）。
 * 判定规则：
 *  A0∩B0：准出已填 → 自动闭环
 *  A0∩B1：准出已填 且 交付物齐全 → 自动闭环
 *  A1∩B0：准出已填 → 置 REVIEWING 等待审核；PASSED → 闭环
 *  A1∩B1：准出已填 且 交付物齐全 → 置 REVIEWING；PASSED → 闭环
 * 打回分支：REJECTED → 工序退回 EXECUTING，锁止后置。
 * 纯函数；调用出口必须遵守「先落库、后判定」（D-14）。
 */
public final class QuadrantClosureValidator {
    public enum Outcome {
        /** 未达准出前置（缺准出内容或必选交付物）。 */
        NOT_READY,
        /** 免审核且准出齐备：系统自动闭环。 */
        AUTO_CLOSED,
        /** 需审核：置 REVIEWING 等待审核。 */
        WAIT_AUDIT
    }

    private QuadrantClosureValidator() {
    }

    public static Outcome evaluate(boolean mustAudit, boolean mustSubmitDeliverable,
                                   boolean exitFilled, boolean deliverableSubmitted) {
        if (!exitFilled) {
            return Outcome.NOT_READY;
        }
        if (mustSubmitDeliverable && !deliverableSubmitted) {
            return Outcome.NOT_READY;
        }
        return mustAudit ? Outcome.WAIT_AUDIT : Outcome.AUTO_CLOSED;
    }
}
