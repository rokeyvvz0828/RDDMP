package com.ccb.datamigration.lifecycle.order.model;

import java.time.LocalDateTime;
import java.util.List;

/** 工序流转实例视图（工序流转面板，11.2.2）。 */
public record OrderProcessView(long id, long orderId, int processSeq, String processName, String processStatus,
                               String preDependStatus, boolean exitFilled, boolean deliverableSubmitted,
                               String auditStatus, boolean mustAudit, boolean mustSubmitDeliverable,
                               int rejectCount, LocalDateTime unlockedAt, LocalDateTime closedAt,
                               List<Integer> predecessorSeqList, String blockReason) {
}
