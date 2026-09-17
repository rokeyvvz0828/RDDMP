package com.ccb.architecture.network.persistence;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/** MyBatis SQL contract for network work orders, workflow rounds, and receipts. */
@Mapper
public interface NetworkWorkOrderMapper {
    int insertWorkOrder(Map<String, Object> params);
    Map<String, Object> findWorkOrder(Map<String, Object> params);
    Map<String, Object> lockWorkOrder(Map<String, Object> params);
    List<Map<String, Object>> listWorkOrders(Map<String, Object> params);
    int updateDraft(Map<String, Object> params);
    int compareAndSetStatus(Map<String, Object> params);
    int compareAndSetWorkflowContext(Map<String, Object> params);
    int compareAndSetCancellationRequested(Map<String, Object> params);
    int updateHandlingResult(Map<String, Object> params);
    int insertHistory(Map<String, Object> params);
    List<Map<String, Object>> listHistory(Map<String, Object> params);
    int insertPendingWorkflowRound(Map<String, Object> params);
    Map<String, Object> findWorkflowRound(Map<String, Object> params);
    Map<String, Object> lockWorkflowRoundByInstance(Map<String, Object> params);
    Integer maxWorkflowRoundNo(Map<String, Object> params);
    int bindWorkflowRoundStarted(Map<String, Object> params);
    int completeStartedWorkflowRound(Map<String, Object> params);
    int beginReceipt(Map<String, Object> params);
    int completeReceipt(Map<String, Object> params);
    Map<String, Object> findReceipt(Map<String, Object> params);
}
