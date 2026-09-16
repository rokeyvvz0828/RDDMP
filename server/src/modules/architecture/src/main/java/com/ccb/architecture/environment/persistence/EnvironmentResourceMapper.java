package com.ccb.architecture.environment.persistence;

import com.ccb.architecture.environment.model.EnvironmentResourceModels.Environment;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.EnvironmentInstance;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.HistoryEvent;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.InstanceDisasterRecovery;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceRequest;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceRequestItem;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.WorkflowReceipt;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.WorkflowRound;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/** XML-owned persistence contract for environments, resource requests, and instances. */
@Mapper
public interface EnvironmentResourceMapper {
    List<Environment> listEnvironments(Map<String, Object> params);
    Environment findEnvironment(Map<String, Object> params);
    Environment lockEnvironment(Map<String, Object> params);
    int countEnvironmentCode(Map<String, Object> params);
    int countEnvironmentName(Map<String, Object> params);
    int insertEnvironment(Map<String, Object> params);
    int updateEnvironment(Map<String, Object> params);
    int updateEnvironmentStatus(Map<String, Object> params);
    int deleteEnvironment(Map<String, Object> params);
    Map<String, Object> requestedSummary(Map<String, Object> params);
    Map<String, Object> actualSummary(Map<String, Object> params);
    EnvironmentResourceStore.PhysicalSubsystemRef findPhysical(Map<String, Object> params);
    EnvironmentResourceStore.DeploymentUnitRef findDeploymentUnit(Map<String, Object> params);
    List<EnvironmentResourceStore.DeploymentUnitRef> listDeploymentUnits(Map<String, Object> params);
    int insertRequest(Map<String, Object> params);
    ResourceRequest findRequest(Map<String, Object> params);
    ResourceRequest lockRequest(Map<String, Object> params);
    List<ResourceRequest> listRequests(Map<String, Object> params);
    List<ResourceRequestItem> listItems(Map<String, Object> params);
    Integer countInstancesForEnvironmentUnit(Map<String, Object> params);
    int deleteItems(Map<String, Object> params);
    int insertItem(Map<String, Object> params);
    int updateDraft(Map<String, Object> params);
    int compareAndSetStatus(Map<String, Object> params);
    int compareAndSetWorkflowContext(Map<String, Object> params);
    int compareAndSetCancellationRequested(Map<String, Object> params);
    int insertHistory(Map<String, Object> params);
    List<HistoryEvent> listHistory(Map<String, Object> params);
    int insertPendingWorkflowRound(Map<String, Object> params);
    WorkflowRound lockWorkflowRoundByInstance(Map<String, Object> params);
    Integer maxWorkflowRound(Map<String, Object> params);
    int bindWorkflowRoundStarted(Map<String, Object> params);
    int completeStartedWorkflowRound(Map<String, Object> params);
    int beginReceipt(Map<String, Object> params);
    int completeReceipt(Map<String, Object> params);
    WorkflowReceipt findReceipt(Map<String, Object> params);
    int insertInstance(Map<String, Object> params);
    EnvironmentInstance findInstance(Map<String, Object> params);
    EnvironmentInstance lockInstance(Map<String, Object> params);
    EnvironmentInstance findActiveInstanceByMachineOrIp(Map<String, Object> params);
    List<EnvironmentInstance> listInstances(Map<String, Object> params);
    int offlineInstance(Map<String, Object> params);
    int insertDisasterRecovery(Map<String, Object> params);
    InstanceDisasterRecovery findDisasterRecovery(Map<String, Object> params);
    InstanceDisasterRecovery findDisasterRecoveryPair(Map<String, Object> params);
    List<InstanceDisasterRecovery> listDisasterRecoveries(Map<String, Object> params);
    int deleteDisasterRecovery(Map<String, Object> params);
    List<EnvironmentInstance> listAvailableStandbyInstances(Map<String, Object> params);
}
