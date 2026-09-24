package com.ccb.datamigration.lifecycle.order;

import java.time.LocalDateTime;

/**
 * 任务时效与 SLA 判定器（基线 18.3，独立维度）：
 * 三档：剩余>24h=NORMAL；0<剩余≤24h=NEAR_OVERDUE；剩余≤0=OVERDUE。
 * 八类口径：豁免即静默(sla_exempt→NORMAL)；仅「在办工单」参与预警（CLOSED/ARCHIVED/CANCELLED 不预警）；
 * 暂停期间不重算；仅写 deadline_status 绝不触碰 order_status。
 */
public final class SlaAdjudicator {
    public static final int NEAR_OVERDUE_HOURS = 24;

    private SlaAdjudicator() {
    }

    /** 三档判定（纯函数）。planFinishTime 为空时返回 NORMAL（无时效约束）。 */
    public static String adjudicate(LocalDateTime now, LocalDateTime planFinishTime) {
        if (planFinishTime == null) {
            return "NORMAL";
        }
        if (planFinishTime.isAfter(now.plusHours(NEAR_OVERDUE_HOURS))) {
            return "NORMAL";
        }
        if (planFinishTime.isAfter(now)) {
            return "NEAR_OVERDUE";
        }
        return "OVERDUE";
    }

    /** 预警参与判定（18.3 八·1/八·2）：豁免或非在办工单一律不预警、标记恒 NORMAL。 */
    public static boolean participatesInWarning(String orderStatus, boolean slaExempt) {
        if (slaExempt) {
            return false;
        }
        if ("CLOSED".equals(orderStatus) || "ARCHIVED".equals(orderStatus) || "CANCELLED".equals(orderStatus)) {
            return false;
        }
        if ("SUSPENDED".equals(orderStatus)) {
            return false;
        }
        return true;
    }
}
