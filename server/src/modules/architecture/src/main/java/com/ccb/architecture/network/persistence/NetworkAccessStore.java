package com.ccb.architecture.network.persistence;

import com.ccb.architecture.network.model.NetworkAccessModels.AccessProtocol;
import com.ccb.architecture.network.model.NetworkAccessModels.AddressType;
import com.ccb.architecture.network.model.NetworkAccessModels.ApplicationStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.EndpointKind;
import com.ccb.architecture.network.model.NetworkAccessModels.EndpointInstanceStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.ExemptionRuleStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.ExternalNetworkAddress;
import com.ccb.architecture.network.model.NetworkAccessModels.ManagedEndpointInstance;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessActionType;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessApplication;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessExemptionRule;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessHistoryEvent;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessRelation;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkZone;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkZoneSubnet;
import com.ccb.architecture.network.model.NetworkAccessModels.RecordStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.RelationCloseType;
import com.ccb.architecture.network.model.NetworkAccessModels.RelationStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.ValidityType;
import com.ccb.architecture.network.model.NetworkAccessModels.WorkflowReceipt;
import com.ccb.architecture.network.model.NetworkAccessModels.WorkflowReceiptStart;
import com.ccb.architecture.network.model.NetworkAccessModels.WorkflowReceiptStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.WorkflowRound;
import com.ccb.architecture.network.model.NetworkAccessModels.WorkflowRoundStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 网络分区、外部地址、访问申请与关系的数据访问边界。 */
@Repository
public class NetworkAccessStore {
    private static final String ZONE_COLUMNS = """
            zone.id, zone.tenant_id, zone.parent_id, parent.name AS parent_name,
            zone.code, zone.name, zone.restriction_level, zone.status, zone.description, zone.remark,
            zone.row_version, zone.created_by, zone.updated_by, zone.created_at, zone.updated_at
            """;
    private static final String ADDRESS_COLUMNS = """
            id, tenant_id, address_type, address_value, display_name, purpose, status, remark,
            row_version, created_by, updated_by, created_at, updated_at
            """;
    private static final String SUBNET_COLUMNS = """
            subnet.id, subnet.tenant_id, subnet.network_zone_id,
            zone.code AS network_zone_code, zone.name AS network_zone_name,
            subnet.cidr_block, subnet.gateway_ip, subnet.purpose, subnet.status, subnet.remark,
            subnet.row_version, subnet.created_by, subnet.updated_by, subnet.created_at, subnet.updated_at
            """;
    private static final String APP_COLUMNS = """
            id, tenant_id, application_no, applicant_id, action_type, target_relation_id,
            source_kind, source_physical_subsystem_id, source_environment_id,
            source_deployment_unit_id, source_external_address_id, source_snapshot_json,
            target_kind, target_physical_subsystem_id, target_environment_id, target_deployment_unit_id,
            target_external_address_id, target_snapshot_json, protocol, ports, purpose, process_description,
            valid_from, valid_until, validity_type, status, current_business_round,
            current_workflow_definition_id, current_workflow_version_id, current_workflow_instance_id,
            current_payload_digest, cancellation_requested, row_version, created_by, updated_by,
            created_at, updated_at
            """;
    private static final String RELATION_COLUMNS = """
            id, tenant_id, relation_no, application_id, replaces_relation_id, replaced_by_relation_id,
            closed_application_id, source_kind, source_snapshot_json, target_kind, target_snapshot_json,
            protocol, ports, purpose, process_description, valid_from, valid_until, validity_type,
            status, close_reason, close_type, closed_by, closed_at, row_version, created_by, updated_by,
            created_at, updated_at
            """;
    private static final String EXEMPTION_RULE_COLUMNS = """
            rule.id, rule.tenant_id, rule.rule_code, rule.rule_name, rule.source_network_zone_id,
            source_zone.name AS source_network_zone_name, rule.target_network_zone_id,
            target_zone.name AS target_network_zone_name, rule.protocol, rule.ports, rule.valid_from,
            rule.valid_until, rule.validity_type, rule.status, rule.remark, rule.row_version,
            rule.created_by, rule.updated_by, rule.created_at, rule.updated_at
            """;

    private static final RowMapper<NetworkZone> ZONE_MAPPER = (rs, rowNum) -> new NetworkZone(
            rs.getLong("id"),
            rs.getLong("tenant_id"),
            nullableLong(rs, "parent_id"),
            rs.getString("parent_name"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getInt("restriction_level"),
            RecordStatus.fromDatabase(rs.getString("status")),
            rs.getString("description"),
            rs.getString("remark"),
            rs.getLong("row_version"),
            rs.getLong("created_by"),
            rs.getLong("updated_by"),
            localDateTime(rs.getTimestamp("created_at")),
            localDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<ExternalNetworkAddress> ADDRESS_MAPPER = (rs, rowNum) ->
            new ExternalNetworkAddress(
                    rs.getLong("id"),
                    rs.getLong("tenant_id"),
                    AddressType.fromDatabase(rs.getString("address_type")),
                    rs.getString("address_value"),
                    rs.getString("display_name"),
                    rs.getString("purpose"),
                    RecordStatus.fromDatabase(rs.getString("status")),
                    rs.getString("remark"),
                    rs.getLong("row_version"),
                    rs.getLong("created_by"),
                    rs.getLong("updated_by"),
                    localDateTime(rs.getTimestamp("created_at")),
                    localDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<NetworkZoneSubnet> SUBNET_MAPPER = (rs, rowNum) ->
            new NetworkZoneSubnet(
                    rs.getLong("id"),
                    rs.getLong("tenant_id"),
                    rs.getLong("network_zone_id"),
                    rs.getString("network_zone_code"),
                    rs.getString("network_zone_name"),
                    rs.getString("cidr_block"),
                    rs.getString("gateway_ip"),
                    rs.getString("purpose"),
                    RecordStatus.fromDatabase(rs.getString("status")),
                    rs.getString("remark"),
                    rs.getLong("row_version"),
                    rs.getLong("created_by"),
                    rs.getLong("updated_by"),
                    localDateTime(rs.getTimestamp("created_at")),
                    localDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<ManagedEndpointInstance> INSTANCE_MAPPER = (rs, rowNum) ->
            new ManagedEndpointInstance(
                    rs.getLong("id"),
                    rs.getString("instance_no"),
                    rs.getLong("physical_subsystem_id"),
                    rs.getString("physical_subsystem_code"),
                    rs.getString("physical_subsystem_name"),
                    rs.getLong("environment_id"),
                    rs.getString("environment_code"),
                    rs.getString("environment_name"),
                    rs.getLong("deployment_unit_id"),
                    rs.getString("deployment_unit_code"),
                    rs.getString("deployment_unit_name"),
                    rs.getString("machine_name"),
                    rs.getString("ip_address"),
                    nullableLong(rs, "network_zone_id"),
                    rs.getString("network_zone_name"));

    private static final RowMapper<NetworkAccessApplication> APP_MAPPER = (rs, rowNum) ->
            new NetworkAccessApplication(
                    rs.getLong("id"),
                    rs.getLong("tenant_id"),
                    rs.getString("application_no"),
                    rs.getLong("applicant_id"),
                    NetworkAccessActionType.fromDatabase(rs.getString("action_type")),
                    nullableLong(rs, "target_relation_id"),
                    EndpointKind.fromDatabase(rs.getString("source_kind")),
                    nullableLong(rs, "source_physical_subsystem_id"),
                    nullableLong(rs, "source_environment_id"),
                    nullableLong(rs, "source_deployment_unit_id"),
                    nullableLong(rs, "source_external_address_id"),
                    rs.getString("source_snapshot_json"),
                    EndpointKind.fromDatabase(rs.getString("target_kind")),
                    nullableLong(rs, "target_physical_subsystem_id"),
                    nullableLong(rs, "target_environment_id"),
                    nullableLong(rs, "target_deployment_unit_id"),
                    nullableLong(rs, "target_external_address_id"),
                    rs.getString("target_snapshot_json"),
                    AccessProtocol.fromDatabase(rs.getString("protocol")),
                    rs.getString("ports"),
                    rs.getString("purpose"),
                    rs.getString("process_description"),
                    localDateTime(rs.getTimestamp("valid_from")),
                    localDateTime(rs.getTimestamp("valid_until")),
                    ValidityType.fromDatabase(rs.getString("validity_type")),
                    ApplicationStatus.fromDatabase(rs.getString("status")),
                    rs.getInt("current_business_round"),
                    nullableLong(rs, "current_workflow_definition_id"),
                    nullableLong(rs, "current_workflow_version_id"),
                    nullableLong(rs, "current_workflow_instance_id"),
                    rs.getString("current_payload_digest"),
                    rs.getBoolean("cancellation_requested"),
                    rs.getLong("row_version"),
                    rs.getLong("created_by"),
                    rs.getLong("updated_by"),
                    localDateTime(rs.getTimestamp("created_at")),
                    localDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<NetworkAccessRelation> RELATION_MAPPER = (rs, rowNum) ->
            new NetworkAccessRelation(
                    rs.getLong("id"),
                    rs.getLong("tenant_id"),
                    rs.getString("relation_no"),
                    rs.getLong("application_id"),
                    nullableLong(rs, "replaces_relation_id"),
                    nullableLong(rs, "replaced_by_relation_id"),
                    nullableLong(rs, "closed_application_id"),
                    EndpointKind.fromDatabase(rs.getString("source_kind")),
                    rs.getString("source_snapshot_json"),
                    EndpointKind.fromDatabase(rs.getString("target_kind")),
                    rs.getString("target_snapshot_json"),
                    AccessProtocol.fromDatabase(rs.getString("protocol")),
                    rs.getString("ports"),
                    rs.getString("purpose"),
                    rs.getString("process_description"),
                    localDateTime(rs.getTimestamp("valid_from")),
                    localDateTime(rs.getTimestamp("valid_until")),
                    ValidityType.fromDatabase(rs.getString("validity_type")),
                    RelationStatus.fromDatabase(rs.getString("status")),
                    rs.getString("close_reason"),
                    RelationCloseType.fromDatabase(rs.getString("close_type")),
                    nullableLong(rs, "closed_by"),
                    localDateTime(rs.getTimestamp("closed_at")),
                    false,
                    0,
                    List.of(),
                    rs.getLong("row_version"),
                    rs.getLong("created_by"),
                    rs.getLong("updated_by"),
                    localDateTime(rs.getTimestamp("created_at")),
                    localDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<NetworkAccessExemptionRule> EXEMPTION_RULE_MAPPER = (rs, rowNum) ->
            new NetworkAccessExemptionRule(
                    rs.getLong("id"),
                    rs.getLong("tenant_id"),
                    rs.getString("rule_code"),
                    rs.getString("rule_name"),
                    rs.getLong("source_network_zone_id"),
                    rs.getString("source_network_zone_name"),
                    rs.getLong("target_network_zone_id"),
                    rs.getString("target_network_zone_name"),
                    AccessProtocol.fromDatabase(rs.getString("protocol")),
                    rs.getString("ports"),
                    localDateTime(rs.getTimestamp("valid_from")),
                    localDateTime(rs.getTimestamp("valid_until")),
                    ValidityType.fromDatabase(rs.getString("validity_type")),
                    ExemptionRuleStatus.fromDatabase(rs.getString("status")),
                    rs.getString("remark"),
                    rs.getLong("row_version"),
                    rs.getLong("created_by"),
                    rs.getLong("updated_by"),
                    localDateTime(rs.getTimestamp("created_at")),
                    localDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<NetworkAccessHistoryEvent> HISTORY_MAPPER = (rs, rowNum) ->
            new NetworkAccessHistoryEvent(
                    rs.getLong("id"),
                    rs.getLong("tenant_id"),
                    rs.getLong("application_id"),
                    rs.getString("event_type"),
                    nullableStatus(rs, "from_status"),
                    nullableStatus(rs, "to_status"),
                    rs.getInt("business_round"),
                    rs.getString("summary"),
                    rs.getString("snapshot_json"),
                    rs.getString("diff_json"),
                    rs.getLong("operator_id"),
                    localDateTime(rs.getTimestamp("occurred_at")));

    private static final RowMapper<WorkflowRound> WORKFLOW_ROUND_MAPPER = (rs, rowNum) ->
            new WorkflowRound(
                    rs.getLong("id"),
                    rs.getLong("tenant_id"),
                    rs.getLong("application_id"),
                    rs.getInt("round_no"),
                    nullableLong(rs, "workflow_definition_id"),
                    nullableLong(rs, "workflow_version_id"),
                    nullableLong(rs, "workflow_instance_id"),
                    rs.getString("payload_digest"),
                    WorkflowRoundStatus.fromDatabase(rs.getString("status")),
                    localDateTime(rs.getTimestamp("started_at")),
                    localDateTime(rs.getTimestamp("ended_at")),
                    localDateTime(rs.getTimestamp("created_at")),
                    localDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<WorkflowReceipt> WORKFLOW_RECEIPT_MAPPER = (rs, rowNum) ->
            new WorkflowReceipt(
                    rs.getLong("id"),
                    rs.getLong("tenant_id"),
                    rs.getString("event_id"),
                    rs.getString("subscriber_key"),
                    nullableLong(rs, "application_id"),
                    nullableInteger(rs, "round_no"),
                    nullableLong(rs, "workflow_instance_id"),
                    rs.getString("event_type"),
                    WorkflowReceiptStatus.fromDatabase(rs.getString("processing_status")),
                    rs.getString("detail"),
                    localDateTime(rs.getTimestamp("received_at")),
                    localDateTime(rs.getTimestamp("processed_at")));

    private final NetworkAccessMapper mapper;

    public NetworkAccessStore(NetworkAccessMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "NetworkAccessMapper 不能为空");
    }

    public List<NetworkZone> listZones(long tenantId, RecordStatus status, String keyword) {
        return mapper.listZones(params("tenantId",tenantId,"status",status == null ? null : status.name(),"keyword",escapedKeyword(keyword)));
    }

    public Optional<NetworkZone> findZone(long tenantId, long id) {
        return Optional.ofNullable(mapper.findZone(params("tenantId",tenantId,"id",id)));
    }

    public Optional<NetworkZone> lockZone(long tenantId, long id) {
        requireTransaction();
        return Optional.ofNullable(mapper.lockZone(params("tenantId",tenantId,"id",id)));
    }

    public boolean zoneCodeExists(long tenantId, String code, Long excludeId) {
        return mapper.countZoneCode(params("tenantId",tenantId,"value",code,"excludeId",excludeId)) > 0;
    }

    public boolean zoneNameExists(long tenantId, Long parentId, String name, Long excludeId) {
        Integer count = mapper.countZoneName(params("tenantId",tenantId,"parentId",parentId,"value",name,"excludeId",excludeId));
        return count != null && count > 0;
    }

    public boolean hasActiveChildZones(long tenantId, long zoneId) {
        Integer count = mapper.countActiveChildren(params("tenantId",tenantId,"zoneId",zoneId));
        return count != null && count > 0;
    }

    public boolean hasActiveSubnets(long tenantId, long zoneId) {
        Integer count = mapper.countActiveSubnets(params("tenantId",tenantId,"zoneId",zoneId));
        return count != null && count > 0;
    }

    public void insertZone(NetworkZone zone) {
        requireTransaction();
        mapper.insertZone(params("zone",zone));
    }

    public boolean updateZone(long tenantId, long id, long rowVersion, Long parentId, String code, String name,
                              int restrictionLevel, String description, String remark, long actorId) {
        requireTransaction();
        return mapper.updateZone(params("tenantId",tenantId,"id",id,"rowVersion",rowVersion,"parentId",parentId,"code",code,"name",name,"restrictionLevel",restrictionLevel,"description",description,"remark",remark,"actorId",actorId)) == 1;
    }

    public boolean updateZoneStatus(long tenantId, long id, RecordStatus from, RecordStatus to, long actorId) {
        requireTransaction();
        return mapper.updateZoneStatus(params("tenantId",tenantId,"id",id,"from",from.name(),"to",to.name(),"actorId",actorId)) == 1;
    }

    public List<NetworkZoneSubnet> listSubnets(long tenantId, Long zoneId, RecordStatus status) {
        return mapper.listSubnets(params("tenantId",tenantId,"zoneId",zoneId,"status",status == null ? null : status.name()));
    }

    public Optional<NetworkZoneSubnet> findSubnet(long tenantId, long id) {
        return Optional.ofNullable(mapper.findSubnet(params("tenantId",tenantId,"id",id)));
    }

    public Optional<NetworkZoneSubnet> lockSubnet(long tenantId, long id) {
        requireTransaction();
        return Optional.ofNullable(mapper.lockSubnet(params("tenantId",tenantId,"id",id)));
    }

    public boolean subnetCidrExists(long tenantId, String cidrBlock, Long excludeId) {
        return mapper.countSubnetCidr(params("tenantId",tenantId,"value",cidrBlock,"excludeId",excludeId)) > 0;
    }

    public void insertSubnet(NetworkZoneSubnet subnet) {
        requireTransaction();
        mapper.insertSubnet(params("subnet",subnet));
    }

    public boolean updateSubnet(long tenantId, long id, long rowVersion, String cidrBlock, String gatewayIp,
                                String purpose, String remark, long actorId) {
        requireTransaction();
        return mapper.updateSubnet(params("tenantId",tenantId,"id",id,"rowVersion",rowVersion,"cidrBlock",cidrBlock,"gatewayIp",gatewayIp,"purpose",purpose,"remark",remark,"actorId",actorId)) == 1;
    }

    public boolean updateSubnetStatus(long tenantId, long id, RecordStatus from, RecordStatus to, long actorId) {
        requireTransaction();
        return mapper.updateSubnetStatus(params("tenantId",tenantId,"id",id,"from",from.name(),"to",to.name(),"actorId",actorId)) == 1;
    }

    public List<ExternalNetworkAddress> listAddresses(long tenantId, RecordStatus status, String keyword) {
        return mapper.listAddresses(params("tenantId",tenantId,"status",status == null ? null : status.name(),"keyword",escapedKeyword(keyword)));
    }

    public Optional<ExternalNetworkAddress> findAddress(long tenantId, long id) {
        return Optional.ofNullable(mapper.findAddress(params("tenantId",tenantId,"id",id)));
    }

    public Optional<ExternalNetworkAddress> lockAddress(long tenantId, long id) {
        requireTransaction();
        return Optional.ofNullable(mapper.lockAddress(params("tenantId",tenantId,"id",id)));
    }

    public boolean addressExists(long tenantId, AddressType type, String value, Long excludeId) {
        Integer count = mapper.countAddress(params("tenantId",tenantId,"type",type.name(),"value",value,"excludeId",excludeId));
        return count != null && count > 0;
    }

    public void insertAddress(ExternalNetworkAddress address) {
        requireTransaction();
        mapper.insertAddress(params("address",address));
    }

    public boolean updateAddress(long tenantId, long id, long rowVersion, AddressType type, String value,
                                 String displayName, String purpose, String remark, long actorId) {
        requireTransaction();
        return mapper.updateAddress(params("tenantId",tenantId,"id",id,"rowVersion",rowVersion,"type",type.name(),"value",value,"displayName",displayName,"purpose",purpose,"remark",remark,"actorId",actorId)) == 1;
    }

    public boolean updateAddressStatus(long tenantId, long id, RecordStatus from, RecordStatus to, long actorId) {
        requireTransaction();
        return mapper.updateAddressStatus(params("tenantId",tenantId,"id",id,"from",from.name(),"to",to.name(),"actorId",actorId)) == 1;
    }

    public List<ManagedEndpointInstance> listEndpointInstances(long tenantId, Long physicalSubsystemId,
                                                               Long environmentId, Long deploymentUnitId,
                                                               List<Long> instanceIds) {
        return mapper.listEndpointInstances(params("tenantId",tenantId,"physicalSubsystemId",physicalSubsystemId,"environmentId",environmentId,"deploymentUnitId",deploymentUnitId,"instanceIds",instanceIds));
    }

    public void insertApplication(NetworkAccessApplication application) {
        requireTransaction();
        mapper.insertApplication(params("application",application));
    }

    public Optional<NetworkAccessApplication> findApplication(long tenantId, long id) {
        return Optional.ofNullable(mapper.findApplication(params("tenantId",tenantId,"id",id)));
    }

    public Optional<NetworkAccessApplication> lockApplication(long tenantId, long id) {
        requireTransaction();
        return Optional.ofNullable(mapper.lockApplication(params("tenantId",tenantId,"id",id)));
    }

    public List<NetworkAccessApplication> listApplications(long tenantId, Long applicantId,
                                                           ApplicationStatus status, int limit, int offset) {
        return mapper.listApplications(params("tenantId",tenantId,"applicantId",applicantId,"status",status == null ? null : status.name(),"limit",limit,"offset",offset));
    }

    public boolean updateApplicationStatus(long tenantId, long id, ApplicationStatus from,
                                           long expectedRowVersion, ApplicationStatus to, long actorId) {
        requireTransaction();
        return mapper.updateApplicationStatus(params("tenantId",tenantId,"id",id,"from",from.name(),"to",to.name(),"expectedRowVersion",expectedRowVersion,"actorId",actorId)) == 1;
    }

    public boolean compareAndSetApplicationWorkflowContext(long tenantId, long applicationId,
                                                           int expectedCurrentBusinessRound,
                                                           long expectedRowVersion,
                                                           int nextBusinessRound,
                                                           long workflowDefinitionId,
                                                           long workflowVersionId,
                                                           long workflowInstanceId,
                                                           String payloadDigest,
                                                           long updatedBy) {
        requireTransaction();
        return mapper.compareAndSetApplicationWorkflowContext(params("tenantId",tenantId,"applicationId",applicationId,"expectedCurrentBusinessRound",expectedCurrentBusinessRound,"expectedRowVersion",expectedRowVersion,"nextBusinessRound",nextBusinessRound,"workflowDefinitionId",workflowDefinitionId,"workflowVersionId",workflowVersionId,"workflowInstanceId",workflowInstanceId,"payloadDigest",payloadDigest,"updatedBy",updatedBy)) == 1;
    }

    public boolean compareAndSetCancellationRequested(long tenantId, long applicationId,
                                                      long expectedRowVersion, boolean requested,
                                                      long updatedBy) {
        requireTransaction();
        return mapper.compareAndSetCancellationRequested(params("tenantId",tenantId,"applicationId",applicationId,"expectedRowVersion",expectedRowVersion,"requested",requested,"updatedBy",updatedBy)) == 1;
    }

    public void insertRelation(NetworkAccessRelation relation) {
        requireTransaction();
        mapper.insertRelation(params("relation",relation));
    }

    public Optional<NetworkAccessRelation> findRelation(long tenantId, long id) {
        return Optional.ofNullable(mapper.findRelation(params("tenantId",tenantId,"id",id)));
    }

    public List<NetworkAccessRelation> listRelations(long tenantId, RelationStatus status, int limit, int offset) {
        return mapper.listRelations(params("tenantId",tenantId,"status",status == null ? null : status.name(),"limit",limit,"offset",offset));
    }

    public Optional<NetworkAccessRelation> lockRelation(long tenantId, long id) {
        requireTransaction();
        return Optional.ofNullable(mapper.lockRelation(params("tenantId",tenantId,"id",id)));
    }

    public boolean closeRelation(long tenantId, long id, long rowVersion, String reason,
                                 long actorId, LocalDateTime closedAt) {
        requireTransaction();
        return mapper.closeRelation(params("tenantId",tenantId,"id",id,"rowVersion",rowVersion,"reason",reason,"actorId",actorId,"closedAt",closedAt)) == 1;
    }

    public boolean closeRelationByApplication(long tenantId, long id, Long replacedByRelationId,
                                              long applicationId, RelationCloseType closeType,
                                              String reason, long actorId, LocalDateTime closedAt) {
        requireTransaction();
        Objects.requireNonNull(closeType, "关闭类型不能为空");
        return mapper.closeRelationByApplication(params("tenantId",tenantId,"id",id,"replacedByRelationId",replacedByRelationId,"applicationId",applicationId,"closeType",closeType.name(),"reason",reason,"actorId",actorId,"closedAt",closedAt)) == 1;
    }

    public List<NetworkAccessExemptionRule> listExemptionRules(long tenantId, ExemptionRuleStatus status) {
        return mapper.listExemptionRules(params("tenantId",tenantId,"status",status == null ? null : status.name()));
    }

    public Optional<NetworkAccessExemptionRule> findExemptionRule(long tenantId, long id) {
        return Optional.ofNullable(mapper.findExemptionRule(params("tenantId",tenantId,"id",id)));
    }

    public Optional<NetworkAccessExemptionRule> lockExemptionRule(long tenantId, long id) {
        requireTransaction();
        return Optional.ofNullable(mapper.lockExemptionRule(params("tenantId",tenantId,"id",id)));
    }

    public boolean exemptionRuleCodeExists(long tenantId, String ruleCode, Long excludeId) {
        return mapper.countRuleCode(params("tenantId",tenantId,"value",ruleCode,"excludeId",excludeId)) > 0;
    }

    public void insertExemptionRule(NetworkAccessExemptionRule rule) {
        requireTransaction();
        mapper.insertExemptionRule(params("rule",rule));
    }

    public boolean updateExemptionRule(long tenantId, long id, long rowVersion, String ruleCode, String ruleName,
                                       long sourceNetworkZoneId, long targetNetworkZoneId,
                                       AccessProtocol protocol, String ports, LocalDateTime validFrom,
                                       LocalDateTime validUntil, ValidityType validityType,
                                       String remark, long actorId) {
        requireTransaction();
        return mapper.updateExemptionRule(params("tenantId",tenantId,"id",id,"rowVersion",rowVersion,"ruleCode",ruleCode,"ruleName",ruleName,"sourceNetworkZoneId",sourceNetworkZoneId,"targetNetworkZoneId",targetNetworkZoneId,"protocol",protocol.name(),"ports",ports,"validFrom",validFrom,"validUntil",validUntil,"validityType",validityType.name(),"remark",remark,"actorId",actorId)) == 1;
    }

    public boolean updateExemptionRuleStatus(long tenantId, long id, long rowVersion,
                                             ExemptionRuleStatus from, ExemptionRuleStatus to, long actorId) {
        requireTransaction();
        return mapper.updateExemptionRuleStatus(params("tenantId",tenantId,"id",id,"rowVersion",rowVersion,"from",from.name(),"to",to.name(),"actorId",actorId)) == 1;
    }

    public List<EndpointInstanceStatus> listEndpointInstanceStatuses(long tenantId, List<Long> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return List.of();
        }
        return mapper.listEndpointInstanceStatuses(params("tenantId",tenantId,"instanceIds",instanceIds));
    }

    public void insertHistory(NetworkAccessHistoryEvent event) {
        requireTransaction();
        Objects.requireNonNull(event, "历史事件不能为空");
        mapper.insertHistory(params("event",event));
    }

    public void insertPendingWorkflowRound(WorkflowRound round) {
        requireTransaction();
        Objects.requireNonNull(round, "工作流轮次不能为空");
        mapper.insertPendingWorkflowRound(params("round",round));
    }

    public Optional<WorkflowRound> lockWorkflowRoundByInstance(long tenantId, long workflowInstanceId) {
        requireTransaction();
        return Optional.ofNullable(mapper.lockWorkflowRoundByInstance(params("tenantId",tenantId,"workflowInstanceId",workflowInstanceId)));
    }

    public boolean isLatestWorkflowRound(long tenantId, long applicationId, int roundNo) {
        Integer latest = mapper.maxWorkflowRound(params("tenantId",tenantId,"applicationId",applicationId));
        return latest != null && latest == roundNo;
    }

    public boolean bindWorkflowRoundStarted(long tenantId, long applicationId, int roundNo,
                                            long workflowDefinitionId, long workflowVersionId,
                                            long workflowInstanceId, String payloadDigest,
                                            LocalDateTime startedAt) {
        requireTransaction();
        return mapper.bindWorkflowRoundStarted(params("tenantId",tenantId,"applicationId",applicationId,"roundNo",roundNo,"workflowDefinitionId",workflowDefinitionId,"workflowVersionId",workflowVersionId,"workflowInstanceId",workflowInstanceId,"payloadDigest",payloadDigest,"startedAt",startedAt)) == 1;
    }

    public boolean completeStartedWorkflowRound(long tenantId, long applicationId, int roundNo,
                                                WorkflowRoundStatus nextStatus, LocalDateTime endedAt) {
        requireTransaction();
        Objects.requireNonNull(nextStatus, "轮次目标状态不能为空");
        return mapper.completeStartedWorkflowRound(params("tenantId",tenantId,"applicationId",applicationId,"roundNo",roundNo,"nextStatus",nextStatus.name(),"endedAt",endedAt)) == 1;
    }

    public boolean beginReceipt(WorkflowReceiptStart receipt) {
        requireTransaction();
        Objects.requireNonNull(receipt, "工作流回执不能为空");
        return mapper.beginReceipt(params("receipt",receipt)) == 1;
    }

    public boolean completeReceipt(long tenantId, String eventId, String subscriberKey,
                                   WorkflowReceiptStatus status, String detail) {
        requireTransaction();
        Objects.requireNonNull(status, "回执状态不能为空");
        return mapper.completeReceipt(params("tenantId",tenantId,"eventId",eventId,"subscriberKey",subscriberKey,"status",status.name(),"detail",detail)) == 1;
    }

    public Optional<WorkflowReceipt> findReceipt(long tenantId, String eventId, String subscriberKey) {
        return Optional.ofNullable(mapper.findReceipt(params("tenantId",tenantId,"eventId",eventId,"subscriberKey",subscriberKey)));
    }

    private String zoneSelect(String filter) {
        return "SELECT " + ZONE_COLUMNS + " FROM arch_network_zone zone "
                + "LEFT JOIN arch_network_zone parent ON parent.tenant_id = zone.tenant_id "
                + "AND parent.id = zone.parent_id " + filter;
    }

    private String subnetSelect(String filter) {
        return "SELECT " + SUBNET_COLUMNS + " FROM arch_network_zone_subnet subnet "
                + "JOIN arch_network_zone zone ON zone.tenant_id = subnet.tenant_id "
                + "AND zone.id = subnet.network_zone_id " + filter;
    }

    private String instanceSelect(String filter) {
        return """
                SELECT instance.id, instance.instance_no,
                       instance.physical_subsystem_id, physical.code AS physical_subsystem_code,
                       physical.name AS physical_subsystem_name,
                       instance.environment_id, environment.code AS environment_code,
                       environment.name AS environment_name,
                       instance.deployment_unit_id, unit.code AS deployment_unit_code,
                       unit.name AS deployment_unit_name, instance.machine_name, instance.ip_address,
                       instance.network_zone_id, COALESCE(instance.network_zone_name, instance.network_zone) AS network_zone_name
                FROM arch_environment_instance instance
                JOIN arch_physical_subsystem physical
                  ON physical.tenant_id = instance.tenant_id AND physical.id = instance.physical_subsystem_id
                JOIN arch_environment environment
                  ON environment.tenant_id = instance.tenant_id AND environment.id = instance.environment_id
                JOIN arch_deployment_unit unit
                  ON unit.tenant_id = instance.tenant_id AND unit.id = instance.deployment_unit_id
                """ + filter;
    }

    private String exemptionRuleSelect(String filter) {
        return "SELECT " + EXEMPTION_RULE_COLUMNS + " FROM arch_network_access_exemption_rule rule "
                + "JOIN arch_network_zone source_zone ON source_zone.tenant_id = rule.tenant_id "
                + "AND source_zone.id = rule.source_network_zone_id "
                + "JOIN arch_network_zone target_zone ON target_zone.tenant_id = rule.tenant_id "
                + "AND target_zone.id = rule.target_network_zone_id " + filter;
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static ApplicationStatus nullableStatus(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : ApplicationStatus.fromDatabase(value);
    }

    private static LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("该数据操作必须在事务内执行");
        }
    }

    private static java.util.Map<String, Object> params(Object... values) {
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            params.put((String) values[index], values[index + 1]);
        }
        return params;
    }

    private static String escapedKeyword(String value) {
        return value == null || value.isBlank() ? null : escapeLike(value.trim());
    }
}
