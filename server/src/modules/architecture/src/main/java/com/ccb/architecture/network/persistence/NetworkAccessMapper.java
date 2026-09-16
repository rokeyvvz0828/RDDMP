package com.ccb.architecture.network.persistence;

import com.ccb.architecture.network.model.NetworkAccessModels.*;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

/** XML-owned persistence contract for network access administration. */
@Mapper
interface NetworkAccessMapper {
    List<NetworkZone> listZones(Map<String,Object> p); NetworkZone findZone(Map<String,Object> p); NetworkZone lockZone(Map<String,Object> p);
    Integer countZoneCode(Map<String,Object> p); Integer countZoneName(Map<String,Object> p); Integer countActiveChildren(Map<String,Object> p); Integer countActiveSubnets(Map<String,Object> p);
    int insertZone(Map<String,Object> p); int updateZone(Map<String,Object> p); int updateZoneStatus(Map<String,Object> p);
    List<NetworkZoneSubnet> listSubnets(Map<String,Object> p); NetworkZoneSubnet findSubnet(Map<String,Object> p); NetworkZoneSubnet lockSubnet(Map<String,Object> p); Integer countSubnetCidr(Map<String,Object> p); int insertSubnet(Map<String,Object> p); int updateSubnet(Map<String,Object> p); int updateSubnetStatus(Map<String,Object> p);
    List<ExternalNetworkAddress> listAddresses(Map<String,Object> p); ExternalNetworkAddress findAddress(Map<String,Object> p); ExternalNetworkAddress lockAddress(Map<String,Object> p); Integer countAddress(Map<String,Object> p); int insertAddress(Map<String,Object> p); int updateAddress(Map<String,Object> p); int updateAddressStatus(Map<String,Object> p);
    List<ManagedEndpointInstance> listEndpointInstances(Map<String,Object> p);
    int insertApplication(Map<String,Object> p); NetworkAccessApplication findApplication(Map<String,Object> p); NetworkAccessApplication lockApplication(Map<String,Object> p); List<NetworkAccessApplication> listApplications(Map<String,Object> p); int updateApplicationStatus(Map<String,Object> p); int compareAndSetApplicationWorkflowContext(Map<String,Object> p); int compareAndSetCancellationRequested(Map<String,Object> p);
    int insertRelation(Map<String,Object> p); NetworkAccessRelation findRelation(Map<String,Object> p); List<NetworkAccessRelation> listRelations(Map<String,Object> p); NetworkAccessRelation lockRelation(Map<String,Object> p); int closeRelation(Map<String,Object> p); int closeRelationByApplication(Map<String,Object> p);
    List<NetworkAccessExemptionRule> listExemptionRules(Map<String,Object> p); NetworkAccessExemptionRule findExemptionRule(Map<String,Object> p); NetworkAccessExemptionRule lockExemptionRule(Map<String,Object> p); Integer countRuleCode(Map<String,Object> p); int insertExemptionRule(Map<String,Object> p); int updateExemptionRule(Map<String,Object> p); int updateExemptionRuleStatus(Map<String,Object> p);
    List<EndpointInstanceStatus> listEndpointInstanceStatuses(Map<String,Object> p); int insertHistory(Map<String,Object> p); int insertPendingWorkflowRound(Map<String,Object> p); WorkflowRound lockWorkflowRoundByInstance(Map<String,Object> p); Integer maxWorkflowRound(Map<String,Object> p); int bindWorkflowRoundStarted(Map<String,Object> p); int completeStartedWorkflowRound(Map<String,Object> p); int beginReceipt(Map<String,Object> p); int completeReceipt(Map<String,Object> p); WorkflowReceipt findReceipt(Map<String,Object> p);
}
