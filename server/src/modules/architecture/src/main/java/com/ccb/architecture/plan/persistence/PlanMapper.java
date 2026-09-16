package com.ccb.architecture.plan.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
interface PlanMapper {
    int insertPlan(Map<String, Object> p); Map<String, Object> findPlan(Map<String, Object> p); Map<String, Object> lockPlan(Map<String, Object> p);
    int updatePlanStatus(Map<String, Object> p); int updatePlanSchedule(Map<String, Object> p); int updatePlanActual(Map<String, Object> p);
    int insertTarget(Map<String, Object> p); List<Map<String, Object>> findTargets(Map<String, Object> p); Map<String, Object> findTarget(Map<String, Object> p); int removeTarget(Map<String, Object> p);
    int insertStage(Map<String, Object> p); List<Map<String, Object>> findStages(Map<String, Object> p); Map<String, Object> findStage(Map<String, Object> p);
    int updateStageStatus(Map<String, Object> p); int updateStageSchedule(Map<String, Object> p); int updateStageActual(Map<String, Object> p);
    int insertTask(Map<String, Object> p); Map<String, Object> findTask(Map<String, Object> p); Map<String, Object> lockTask(Map<String, Object> p); List<Map<String, Object>> findTasks(Map<String, Object> p);
    int updateTaskExecution(Map<String, Object> p); int updateTaskCancel(Map<String, Object> p); int updateTaskSchedule(Map<String, Object> p); int updateTaskOwner(Map<String, Object> p); int deleteTask(Map<String, Object> p);
    int insertCheckItem(Map<String, Object> p); List<Map<String, Object>> findCheckItems(Map<String, Object> p); Map<String, Object> findCheckItem(Map<String, Object> p); int updateCheckItemCompletion(Map<String, Object> p); int updateCheckItemCancel(Map<String, Object> p); int deleteCheckItem(Map<String, Object> p);
    int insertParticipant(Map<String, Object> p); List<Long> findParticipantUserIds(Map<String, Object> p); int deleteParticipants(Map<String, Object> p);
    int insertDependency(Map<String, Object> p); List<Map<String, Object>> findDependencies(Map<String, Object> p); Map<String, Object> findDependencyById(Map<String, Object> p); int removeDependency(Map<String, Object> p); int deleteDependenciesByTask(Map<String, Object> p);
    int insertBlock(Map<String, Object> p); List<Map<String, Object>> findBlocks(Map<String, Object> p); Map<String, Object> findBlock(Map<String, Object> p); int updateBlock(Map<String, Object> p); int resolveBlock(Map<String, Object> p); int deleteBlocksByTask(Map<String, Object> p);
    int insertCancelSuggestion(Map<String, Object> p); List<Map<String, Object>> findPendingSuggestions(Map<String, Object> p); Map<String, Object> findSuggestion(Map<String, Object> p); int handleSuggestion(Map<String, Object> p);
    int insertEvent(Map<String, Object> p); List<Map<String, Object>> findEvents(Map<String, Object> p); Map<String, Object> findEvent(Map<String, Object> p);
    int insertWorkOrder(Map<String, Object> p); List<Map<String, Object>> findWorkOrders(Map<String, Object> p); Map<String, Object> findWorkOrder(Map<String, Object> p); int removeWorkOrder(Map<String, Object> p);
    List<Long> openResourceRequestIds(Map<String, Object> p); List<Map<String, Object>> resourceRequestRefs(Map<String, Object> p); Long countNetworkWorkOrderRefs(Map<String, Object> p); List<Long> openNetworkWorkOrderIds(Map<String, Object> p);
    List<Map<String, Object>> searchPlans(Map<String, Object> p); Long countPlans(Map<String, Object> p); Long findPlanIdByTask(Map<String, Object> p); Map<String, Object> envReference(Map<String, Object> p); List<Map<String, Object>> listPhysicalSubsystemRefs(Map<String, Object> p); List<Map<String, Object>> listDeploymentUnitRefs(Map<String, Object> p); List<Map<String, Object>> planIdsNeedingAlert(); Long countOverdueTasks(Map<String, Object> p);
    int insertStageDependency(Map<String, Object> p); List<Map<String, Object>> findStageDependencies(Map<String, Object> p); int insertActivity(Map<String, Object> p);
}
