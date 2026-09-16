package com.ccb.architecture.environment.persistence;

import com.ccb.architecture.environment.model.EnvironmentResourceModels.DisasterRecoveryMode;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.Environment;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.EnvironmentInstance;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.FulfillmentMode;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.HistoryEvent;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.InstanceDisasterRecovery;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.InstanceStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.RecordStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.RequestStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.RequestType;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceRequest;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceRequestItem;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceSummary;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.WorkflowReceipt;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.WorkflowReceiptStart;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.WorkflowReceiptStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.WorkflowRound;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.WorkflowRoundStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** REQ-20260824-052 与 REQ-20260825-053 的数据访问边界。 */
@Repository
public class EnvironmentResourceStore {
    private static final String ENVIRONMENT_COLUMNS = """
            environment.id, environment.tenant_id, environment.code, environment.name,
            environment.type_code, environment.type_code AS type_name,
            environment.status, environment.description, environment.remark, environment.row_version,
            environment.created_by, environment.updated_by, environment.created_at, environment.updated_at
            """;

    private static final String INSTANCE_COLUMNS = """
            instance.id, instance.tenant_id, instance.instance_no, instance.environment_id,
            environment.code AS environment_code, environment.name AS environment_name,
            environment.type_code AS environment_type_name,
            instance.deployment_unit_id, unit.code AS deployment_unit_code, unit.name AS deployment_unit_name,
            unit.kind AS deployment_unit_kind, instance.deployment_unit_version_id,
            instance.deployment_unit_version_no, unit.current_version AS latest_deployment_unit_version_no,
            instance.physical_subsystem_id, physical.code AS physical_subsystem_code,
            physical.name AS physical_subsystem_name, instance.source_request_id,
            request.request_no AS source_request_no, instance.source_item_id,
            instance.machine_name, instance.ip_address, instance.server_type,
            instance.deployment_platform, instance.network_zone_id,
            COALESCE(instance.network_zone_name, instance.network_zone) AS network_zone_name,
            instance.network_zone, instance.status,
            instance.cpu_cores, instance.memory_gb, instance.database_storage_gb,
            instance.file_storage_gb, instance.extra_cbs_gb, instance.local_disk_gb,
            instance.database_name, instance.database_version, instance.jdk_version,
            instance.middleware, instance.operating_system, instance.needs_nft,
            instance.needs_fserver, instance.needs_jobexecutor, instance.fulfillment_mode,
            instance.difference_reason, instance.remark, instance.offlined_at,
            instance.offlined_by, instance.offline_reason, instance.row_version,
            instance.created_by, instance.updated_by, instance.created_at, instance.updated_at
            """;

    private static final String DR_COLUMNS = """
            dr.id, dr.tenant_id, dr.deployment_unit_id, unit.code AS deployment_unit_code,
            unit.name AS deployment_unit_name, dr.primary_instance_id,
            p_inst.machine_name AS primary_machine_name, p_inst.ip_address AS primary_ip_address,
            p_env.code AS primary_environment_code, p_env.name AS primary_environment_name,
            dr.standby_instance_id, s_inst.machine_name AS standby_machine_name,
            s_inst.ip_address AS standby_ip_address, s_env.code AS standby_environment_code,
            s_env.name AS standby_environment_name, dr.dr_mode, dr.description,
            dr.created_by, dr.created_at, dr.updated_at
            """;

    private static final String REQUEST_COLUMNS = """
            request.id, request.tenant_id, request.request_no, request.physical_subsystem_id,
            physical.code AS physical_subsystem_code,
            physical.short_name AS physical_subsystem_short_name,
            physical.name AS physical_subsystem_name,
            physical.business_group_name AS physical_subsystem_business_group_name,
            physical.system_level_code AS physical_subsystem_system_level_code,
            physical.deployment_platform AS physical_subsystem_deployment_platform,
            physical.disaster_recovery_mode AS physical_subsystem_disaster_recovery_mode,
            request.environment_id, environment.code AS environment_code, environment.name AS environment_name,
            environment.type_code AS environment_type_name, request.applicant_id, request.contact_user_id,
            request.request_type, request.reason, request.status, request.current_business_round,
            request.current_workflow_definition_id, request.current_workflow_version_id,
            request.current_workflow_instance_id, request.current_payload_digest,
            request.cancellation_requested, request.row_version, request.created_by, request.updated_by,
            request.created_at, request.updated_at
            """;

    private static final String ITEM_COLUMNS = """
            item.id, item.tenant_id, item.request_id, item.item_seq, item.deployment_unit_id,
            unit.code AS deployment_unit_code, unit.name AS deployment_unit_name,
            unit.kind AS deployment_unit_kind, item.deployment_unit_description,
            item.database_storage_gb, item.storage_gb AS file_storage_gb,
            item.network_zone_id, COALESCE(item.network_zone_name, item.network_zone) AS network_zone_name,
            item.network_zone, item.server_type, item.cpu_cores, item.memory_gb,
            item.app_web_group_count, item.planned_node_count, item.sidecar_cpu_cores,
            item.sidecar_memory_gb, item.has_sidecar, item.database_name, item.database_version,
            item.jdk_version, item.middleware, item.operating_system, item.extra_cbs_gb,
            item.local_disk_gb, item.needs_nft, item.needs_fserver, item.needs_jobexecutor,
            item.remark, item.created_at, item.updated_at
            """;

    private static final RowMapper<Environment> ENVIRONMENT_MAPPER = (rs, rowNum) -> new Environment(
            rs.getLong("id"),
            rs.getLong("tenant_id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("type_code"),
            rs.getString("type_name"),
            RecordStatus.fromDatabase(rs.getString("status")),
            rs.getString("description"),
            rs.getString("remark"),
            rs.getLong("row_version"),
            rs.getLong("created_by"),
            rs.getLong("updated_by"),
            localDateTime(rs.getTimestamp("created_at")),
            localDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<ResourceRequest> REQUEST_MAPPER = (rs, rowNum) -> new ResourceRequest(
            rs.getLong("id"),
            rs.getLong("tenant_id"),
            rs.getString("request_no"),
            rs.getLong("physical_subsystem_id"),
            rs.getString("physical_subsystem_code"),
            rs.getString("physical_subsystem_short_name"),
            rs.getString("physical_subsystem_name"),
            rs.getString("physical_subsystem_business_group_name"),
            rs.getString("physical_subsystem_system_level_code"),
            rs.getString("physical_subsystem_deployment_platform"),
            rs.getString("physical_subsystem_disaster_recovery_mode"),
            rs.getLong("environment_id"),
            rs.getString("environment_code"),
            rs.getString("environment_name"),
            rs.getString("environment_type_name"),
            rs.getLong("applicant_id"),
            rs.getLong("contact_user_id"),
            RequestType.fromDatabase(rs.getString("request_type")),
            rs.getString("reason"),
            RequestStatus.fromDatabase(rs.getString("status")),
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

    private static final RowMapper<ResourceRequestItem> ITEM_MAPPER = (rs, rowNum) -> new ResourceRequestItem(
            rs.getLong("id"),
            rs.getLong("tenant_id"),
            rs.getLong("request_id"),
            rs.getInt("item_seq"),
            rs.getLong("deployment_unit_id"),
            rs.getString("deployment_unit_code"),
            rs.getString("deployment_unit_name"),
            rs.getString("deployment_unit_kind"),
            rs.getString("deployment_unit_description"),
            decimal(rs, "database_storage_gb"),
            decimal(rs, "file_storage_gb"),
            nullableLong(rs, "network_zone_id"),
            rs.getString("network_zone_name"),
            rs.getString("network_zone"),
            rs.getString("server_type"),
            rs.getBigDecimal("cpu_cores"),
            rs.getBigDecimal("memory_gb"),
            rs.getInt("app_web_group_count"),
            rs.getInt("planned_node_count"),
            decimal(rs, "sidecar_cpu_cores"),
            decimal(rs, "sidecar_memory_gb"),
            rs.getBoolean("has_sidecar"),
            rs.getString("database_name"),
            rs.getString("database_version"),
            rs.getString("jdk_version"),
            rs.getString("middleware"),
            rs.getString("operating_system"),
            decimal(rs, "extra_cbs_gb"),
            decimal(rs, "local_disk_gb"),
            rs.getBoolean("needs_nft"),
            rs.getBoolean("needs_fserver"),
            rs.getBoolean("needs_jobexecutor"),
            rs.getString("remark"),
            localDateTime(rs.getTimestamp("created_at")),
            localDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<EnvironmentInstance> INSTANCE_MAPPER = (rs, rowNum) -> {
        int versionNo = rs.getInt("deployment_unit_version_no");
        int latestVersionNo = rs.getInt("latest_deployment_unit_version_no");
        return new EnvironmentInstance(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getString("instance_no"),
                rs.getLong("environment_id"),
                rs.getString("environment_code"),
                rs.getString("environment_name"),
                rs.getString("environment_type_name"),
                rs.getLong("deployment_unit_id"),
                rs.getString("deployment_unit_code"),
                rs.getString("deployment_unit_name"),
                rs.getString("deployment_unit_kind"),
                nullableLong(rs, "deployment_unit_version_id"),
                versionNo,
                latestVersionNo,
                versionNo != latestVersionNo,
                rs.getLong("physical_subsystem_id"),
                rs.getString("physical_subsystem_code"),
                rs.getString("physical_subsystem_name"),
                rs.getLong("source_request_id"),
                rs.getString("source_request_no"),
                nullableLong(rs, "source_item_id"),
                rs.getString("machine_name"),
                rs.getString("ip_address"),
                rs.getString("server_type"),
                rs.getString("deployment_platform"),
                nullableLong(rs, "network_zone_id"),
                rs.getString("network_zone_name"),
                rs.getString("network_zone"),
                InstanceStatus.fromDatabase(rs.getString("status")),
                decimal(rs, "cpu_cores"),
                decimal(rs, "memory_gb"),
                decimal(rs, "database_storage_gb"),
                decimal(rs, "file_storage_gb"),
                decimal(rs, "extra_cbs_gb"),
                decimal(rs, "local_disk_gb"),
                rs.getString("database_name"),
                rs.getString("database_version"),
                rs.getString("jdk_version"),
                rs.getString("middleware"),
                rs.getString("operating_system"),
                rs.getBoolean("needs_nft"),
                rs.getBoolean("needs_fserver"),
                rs.getBoolean("needs_jobexecutor"),
                FulfillmentMode.fromDatabase(rs.getString("fulfillment_mode")),
                rs.getString("difference_reason"),
                rs.getString("remark"),
                localDateTime(rs.getTimestamp("offlined_at")),
                nullableLong(rs, "offlined_by"),
                rs.getString("offline_reason"),
                rs.getLong("row_version"),
                rs.getLong("created_by"),
                rs.getLong("updated_by"),
                localDateTime(rs.getTimestamp("created_at")),
                localDateTime(rs.getTimestamp("updated_at")));
    };

    private static final RowMapper<InstanceDisasterRecovery> DR_MAPPER = (rs, rowNum) -> new InstanceDisasterRecovery(
            rs.getLong("id"),
            rs.getLong("tenant_id"),
            rs.getLong("deployment_unit_id"),
            rs.getString("deployment_unit_code"),
            rs.getString("deployment_unit_name"),
            rs.getLong("primary_instance_id"),
            rs.getString("primary_machine_name"),
            rs.getString("primary_ip_address"),
            rs.getString("primary_environment_code"),
            rs.getString("primary_environment_name"),
            rs.getLong("standby_instance_id"),
            rs.getString("standby_machine_name"),
            rs.getString("standby_ip_address"),
            rs.getString("standby_environment_code"),
            rs.getString("standby_environment_name"),
            DisasterRecoveryMode.fromDatabase(rs.getString("dr_mode")),
            rs.getString("description"),
            rs.getLong("created_by"),
            localDateTime(rs.getTimestamp("created_at")),
            localDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<HistoryEvent> HISTORY_MAPPER = (rs, rowNum) -> new HistoryEvent(
            rs.getLong("id"),
            rs.getLong("tenant_id"),
            rs.getLong("request_id"),
            rs.getString("event_type"),
            nullableRequestStatus(rs, "from_status"),
            nullableRequestStatus(rs, "to_status"),
            rs.getInt("business_round"),
            rs.getString("summary"),
            rs.getString("snapshot_json"),
            rs.getString("diff_json"),
            rs.getLong("operator_id"),
            localDateTime(rs.getTimestamp("occurred_at")));

    private static final RowMapper<WorkflowRound> WORKFLOW_ROUND_MAPPER = (rs, rowNum) -> new WorkflowRound(
            rs.getLong("id"),
            rs.getLong("tenant_id"),
            rs.getLong("request_id"),
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

    private static final RowMapper<WorkflowReceipt> WORKFLOW_RECEIPT_MAPPER = (rs, rowNum) -> new WorkflowReceipt(
            rs.getLong("id"),
            rs.getLong("tenant_id"),
            rs.getString("event_id"),
            rs.getString("subscriber_key"),
            nullableLong(rs, "request_id"),
            nullableInteger(rs, "round_no"),
            nullableLong(rs, "workflow_instance_id"),
            rs.getString("event_type"),
            WorkflowReceiptStatus.fromDatabase(rs.getString("processing_status")),
            rs.getString("detail"),
            localDateTime(rs.getTimestamp("received_at")),
            localDateTime(rs.getTimestamp("processed_at")));

    public record PhysicalSubsystemRef(long id, String code, String shortName, String name,
                                       String businessGroupName, String deploymentPlatform,
                                       String systemLevelCode, String disasterRecoveryMode,
                                       String status, boolean deleted) {
    }

    public record DeploymentUnitRef(long id, String code, String name, String kind, String status,
                                    long physicalSubsystemId, String description,
                                    Long defaultNetworkZoneId, String defaultNetworkZoneName,
                                    Long currentVersionId, int currentVersion) {
        public DeploymentUnitRef(long id, String code, String name, String kind, String status,
                                 long physicalSubsystemId, String description,
                                 Long currentVersionId, int currentVersion) {
            this(id, code, name, kind, status, physicalSubsystemId,
                    description, null, null, currentVersionId, currentVersion);
        }
    }

    private final EnvironmentResourceMapper mapper;

    public EnvironmentResourceStore(EnvironmentResourceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "EnvironmentResourceMapper 不能为空");
    }

    public List<Environment> listEnvironments(long tenantId, String typeCode, RecordStatus status,
                                              String keyword, int limit, int offset) {
        if (limit <= 0 || offset < 0) {
            throw new IllegalArgumentException("分页参数无效");
        }
        return mapper.listEnvironments(params("tenantId", tenantId, "typeCode", trimToNull(typeCode),
                "status", status == null ? null : status.name(), "keyword", escapedKeyword(keyword), "limit", limit, "offset", offset));
    }

    public Optional<Environment> findEnvironment(long tenantId, long id) {
        return Optional.ofNullable(mapper.findEnvironment(params("tenantId", tenantId, "id", id)));
    }

    public Optional<Environment> lockEnvironment(long tenantId, long id) {
        requireTransaction();
        return Optional.ofNullable(mapper.lockEnvironment(params("tenantId", tenantId, "id", id)));
    }

    public boolean environmentCodeExists(long tenantId, String code, Long excludeId) {
        return mapper.countEnvironmentCode(params("tenantId", tenantId, "value", code, "excludeId", excludeId)) > 0;
    }

    public boolean environmentNameExists(long tenantId, String name, Long excludeId) {
        return mapper.countEnvironmentName(params("tenantId", tenantId, "value", name, "excludeId", excludeId)) > 0;
    }

    public void insertEnvironment(Environment environment) {
        requireTransaction();
        mapper.insertEnvironment(params("environment", environment));
    }

    public boolean updateEnvironment(long tenantId, long id, long expectedRowVersion, String code,
                                     String name, String typeCode, String description, String remark,
                                     long actorId) {
        requireTransaction();
        return mapper.updateEnvironment(params("tenantId",tenantId,"id",id,"expectedRowVersion",expectedRowVersion,"code",code,"name",name,"typeCode",typeCode,"description",description,"remark",remark,"actorId",actorId)) == 1;
    }

    public boolean updateEnvironmentStatus(long tenantId, long id, long expectedRowVersion,
                                           RecordStatus fromStatus, RecordStatus toStatus, long actorId) {
        requireTransaction();
        return mapper.updateEnvironmentStatus(params("tenantId",tenantId,"id",id,"expectedRowVersion",expectedRowVersion,"fromStatus",fromStatus.name(),"toStatus",toStatus.name(),"actorId",actorId)) == 1;
    }

    public boolean deleteEnvironment(long tenantId, long id, long expectedRowVersion) {
        requireTransaction();
        return mapper.deleteEnvironment(params("tenantId", tenantId, "id", id, "expectedRowVersion", expectedRowVersion)) == 1;
    }

    public ResourceSummary resourceSummary(long tenantId, long environmentId) {
        Map<String, Object> summaryParams = params("tenantId", tenantId, "environmentId", environmentId);
        Map<String, Object> requestedMap = mapper.requestedSummary(summaryParams);
        Map<String, Object> actualMap = mapper.actualSummary(summaryParams);
        ResourceSummary requested = new ResourceSummary(environmentId,
                number(requestedMap, "request_count").longValue(), number(requestedMap, "approved_count").longValue(),
                number(requestedMap, "pending_count").longValue(), decimal(requestedMap, "cpu_sum"),
                decimal(requestedMap, "memory_sum"), decimal(requestedMap, "storage_sum"),
                number(requestedMap, "node_sum").longValue(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        ActualSummary actual = new ActualSummary(decimal(actualMap, "actual_cpu_sum"), decimal(actualMap, "actual_memory_sum"),
                decimal(actualMap, "actual_storage_sum"), number(actualMap, "actual_node_count").longValue());
        return new ResourceSummary(environmentId,
                requested.requestCount(),
                requested.approvedRequestCount(),
                requested.pendingRequestCount(),
                requested.requestedCpuCores(),
                requested.requestedMemoryGb(),
                requested.requestedStorageGb(),
                requested.requestedNodeCount(),
                actual.cpu(),
                actual.memory(),
                actual.storage(),
                actual.nodeCount());
    }

    private record ActualSummary(BigDecimal cpu, BigDecimal memory, BigDecimal storage, long nodeCount) {
    }

    public Optional<PhysicalSubsystemRef> findPhysical(long tenantId, long physicalSubsystemId) {
        return Optional.ofNullable(mapper.findPhysical(params("tenantId", tenantId, "physicalSubsystemId", physicalSubsystemId)));
    }

    public Optional<DeploymentUnitRef> findDeploymentUnit(long tenantId, long deploymentUnitId) {
        return Optional.ofNullable(mapper.findDeploymentUnit(params("tenantId", tenantId, "deploymentUnitId", deploymentUnitId)));
    }

    public List<DeploymentUnitRef> listDeploymentUnits(long tenantId, long physicalSubsystemId, int limit) {
        return mapper.listDeploymentUnits(params("tenantId", tenantId, "physicalSubsystemId", physicalSubsystemId, "limit", limit));
    }

    public void insertResourceRequest(ResourceRequest request) {
        requireTransaction();
        mapper.insertRequest(params("request", request));
    }

    public Optional<ResourceRequest> findRequest(long tenantId, long requestId) {
        return Optional.ofNullable(mapper.findRequest(params("tenantId", tenantId, "requestId", requestId)));
    }

    public Optional<ResourceRequest> lockRequest(long tenantId, long requestId) {
        requireTransaction();
        return Optional.ofNullable(mapper.lockRequest(params("tenantId", tenantId, "requestId", requestId)));
    }

    public List<ResourceRequest> listRequests(long tenantId, Long applicantId, RequestStatus status,
                                              Long environmentId, Long physicalSubsystemId,
                                              int limit, int offset) {
        if (limit <= 0 || offset < 0) {
            throw new IllegalArgumentException("分页参数无效");
        }
        return mapper.listRequests(params("tenantId",tenantId,"applicantId",applicantId,"status",status == null ? null : status.name(),"environmentId",environmentId,"physicalSubsystemId",physicalSubsystemId,"limit",limit,"offset",offset));
    }

    public List<ResourceRequestItem> listItems(long tenantId, long requestId) {
        return mapper.listItems(params("tenantId", tenantId, "requestId", requestId));
    }

    public int countInstancesForEnvironmentUnit(long tenantId, long environmentId, long deploymentUnitId) {
        Integer count = mapper.countInstancesForEnvironmentUnit(params("tenantId", tenantId, "environmentId", environmentId, "deploymentUnitId", deploymentUnitId));
        return count == null ? 0 : count;
    }

    public void replaceItems(long tenantId, long requestId, List<ResourceRequestItem> items) {
        requireTransaction();
        mapper.deleteItems(params("tenantId", tenantId, "requestId", requestId));
        for (ResourceRequestItem item : items) {
            mapper.insertItem(params("item", item));
        }
    }

    public boolean updateDraft(long tenantId, long requestId, RequestStatus expectedStatus,
                               long expectedRowVersion, long physicalSubsystemId, long environmentId,
                               long contactUserId, RequestType requestType, String reason, long actorId) {
        requireTransaction();
        return mapper.updateDraft(params("tenantId",tenantId,"requestId",requestId,"expectedStatus",expectedStatus.name(),"expectedRowVersion",expectedRowVersion,"physicalSubsystemId",physicalSubsystemId,"environmentId",environmentId,"contactUserId",contactUserId,"requestType",requestType.name(),"reason",reason,"actorId",actorId)) == 1;
    }

    public boolean compareAndSetStatus(long tenantId, long requestId,
                                       RequestStatus expectedStatus, long expectedRowVersion,
                                       RequestStatus nextStatus, long actorId) {
        requireTransaction();
        return mapper.compareAndSetStatus(params("tenantId",tenantId,"requestId",requestId,"expectedStatus",expectedStatus.name(),"expectedRowVersion",expectedRowVersion,"nextStatus",nextStatus.name(),"actorId",actorId)) == 1;
    }

    public boolean compareAndSetWorkflowContext(long tenantId, long requestId,
                                                int expectedCurrentBusinessRound,
                                                long expectedRowVersion, int nextBusinessRound,
                                                long workflowDefinitionId, long workflowVersionId,
                                                long workflowInstanceId, String payloadDigest,
                                                long actorId) {
        requireTransaction();
        return mapper.compareAndSetWorkflowContext(params("tenantId",tenantId,"requestId",requestId,"expectedCurrentBusinessRound",expectedCurrentBusinessRound,"expectedRowVersion",expectedRowVersion,"nextBusinessRound",nextBusinessRound,"workflowDefinitionId",workflowDefinitionId,"workflowVersionId",workflowVersionId,"workflowInstanceId",workflowInstanceId,"payloadDigest",payloadDigest,"actorId",actorId)) == 1;
    }

    public boolean compareAndSetCancellationRequested(long tenantId, long requestId,
                                                      long expectedRowVersion, boolean requested,
                                                      long actorId) {
        requireTransaction();
        return mapper.compareAndSetCancellationRequested(params("tenantId",tenantId,"requestId",requestId,"expectedRowVersion",expectedRowVersion,"requested",requested,"actorId",actorId)) == 1;
    }

    public void insertHistory(HistoryEvent event) {
        requireTransaction();
        mapper.insertHistory(params("event", event));
    }

    public List<HistoryEvent> listHistory(long tenantId, long requestId) {
        return mapper.listHistory(params("tenantId", tenantId, "requestId", requestId));
    }

    public void insertPendingWorkflowRound(WorkflowRound round) {
        requireTransaction();
        if (round.status() != WorkflowRoundStatus.PENDING
                || round.workflowDefinitionId() != null || round.workflowVersionId() != null
                || round.workflowInstanceId() != null || round.payloadDigest() != null
                || round.startedAt() != null || round.endedAt() != null) {
            throw new IllegalArgumentException("PENDING 工作流轮次不得预先绑定平台上下文");
        }
        mapper.insertPendingWorkflowRound(params("round", round));
    }

    public Optional<WorkflowRound> lockWorkflowRoundByInstance(long tenantId, long workflowInstanceId) {
        requireTransaction();
        return Optional.ofNullable(mapper.lockWorkflowRoundByInstance(params("tenantId", tenantId, "workflowInstanceId", workflowInstanceId)));
    }

    public boolean isLatestWorkflowRound(long tenantId, long requestId, int roundNo) {
        Integer latest = mapper.maxWorkflowRound(params("tenantId", tenantId, "requestId", requestId));
        return latest != null && latest == roundNo;
    }

    public boolean bindWorkflowRoundStarted(long tenantId, long requestId, int roundNo,
                                            long workflowDefinitionId, long workflowVersionId,
                                            long workflowInstanceId, String payloadDigest,
                                            LocalDateTime startedAt) {
        requireTransaction();
        return mapper.bindWorkflowRoundStarted(params("tenantId",tenantId,"requestId",requestId,"roundNo",roundNo,"workflowDefinitionId",workflowDefinitionId,"workflowVersionId",workflowVersionId,"workflowInstanceId",workflowInstanceId,"payloadDigest",payloadDigest,"startedAt",startedAt)) == 1;
    }

    public boolean completeStartedWorkflowRound(long tenantId, long requestId, int roundNo,
                                                WorkflowRoundStatus nextStatus, LocalDateTime endedAt) {
        requireTransaction();
        return mapper.completeStartedWorkflowRound(params("tenantId",tenantId,"requestId",requestId,"roundNo",roundNo,"nextStatus",nextStatus.name(),"endedAt",endedAt)) == 1;
    }

    public boolean beginReceipt(WorkflowReceiptStart receipt) {
        requireTransaction();
        return mapper.beginReceipt(params("receipt", receipt)) == 1;
    }

    public boolean completeReceipt(long tenantId, String eventId, String subscriberKey,
                                   WorkflowReceiptStatus status, String detail) {
        requireTransaction();
        return mapper.completeReceipt(params("tenantId",tenantId,"eventId",eventId,"subscriberKey",subscriberKey,"status",status.name(),"detail",detail)) == 1;
    }

    public Optional<WorkflowReceipt> findReceipt(long tenantId, String eventId, String subscriberKey) {
        return Optional.ofNullable(mapper.findReceipt(params("tenantId",tenantId,"eventId",eventId,"subscriberKey",subscriberKey)));
    }

    // ===== 环境部署实例与灾备关系持久化 =====

    public void insertInstance(EnvironmentInstance instance) {
        requireTransaction();
        mapper.insertInstance(params("instance", instance));
    }

    public Optional<EnvironmentInstance> findInstance(long tenantId, long id) {
        return Optional.ofNullable(mapper.findInstance(params("tenantId",tenantId,"id",id)));
    }

    public Optional<EnvironmentInstance> lockInstance(long tenantId, long id) {
        requireTransaction();
        return Optional.ofNullable(mapper.lockInstance(params("tenantId",tenantId,"id",id)));
    }

    public Optional<EnvironmentInstance> findActiveInstanceByMachineOrIp(long tenantId, long environmentId,
                                                                         String machineName, String ipAddress,
                                                                         Long excludeInstanceId) {
        return Optional.ofNullable(mapper.findActiveInstanceByMachineOrIp(params("tenantId",tenantId,"environmentId",environmentId,"machineName",machineName,"ipAddress",ipAddress,"excludeInstanceId",excludeInstanceId)));
    }

    public List<EnvironmentInstance> listInstances(long tenantId, Long environmentId, Long physicalSubsystemId,
                                                   Long deploymentUnitId, InstanceStatus status,
                                                   String keyword, int limit, int offset) {
        if (limit <= 0 || offset < 0) {
            throw new IllegalArgumentException("分页参数无效");
        }
        return mapper.listInstances(params("tenantId",tenantId,"environmentId",environmentId,"physicalSubsystemId",physicalSubsystemId,"deploymentUnitId",deploymentUnitId,"status",status == null ? null : status.name(),"keyword",escapedKeyword(keyword),"limit",limit,"offset",offset));
    }

    public boolean offlineInstance(long tenantId, long id, long expectedRowVersion,
                                   String offlineReason, long actorId, LocalDateTime offlinedAt) {
        requireTransaction();
        return mapper.offlineInstance(params("tenantId",tenantId,"id",id,"expectedRowVersion",expectedRowVersion,"offlineReason",offlineReason,"actorId",actorId,"offlinedAt",offlinedAt)) == 1;
    }

    public void insertDisasterRecovery(InstanceDisasterRecovery dr) {
        requireTransaction();
        mapper.insertDisasterRecovery(params("dr", dr));
    }

    public Optional<InstanceDisasterRecovery> findDisasterRecovery(long tenantId, long id) {
        return Optional.ofNullable(mapper.findDisasterRecovery(params("tenantId",tenantId,"id",id)));
    }

    public Optional<InstanceDisasterRecovery> findDisasterRecoveryPair(long tenantId,
                                                                       long primaryInstanceId,
                                                                       long standbyInstanceId) {
        return Optional.ofNullable(mapper.findDisasterRecoveryPair(params("tenantId",tenantId,"primaryInstanceId",primaryInstanceId,"standbyInstanceId",standbyInstanceId)));
    }

    public List<InstanceDisasterRecovery> listDisasterRecoveries(long tenantId, Long deploymentUnitId, Long instanceId) {
        return mapper.listDisasterRecoveries(params("tenantId",tenantId,"deploymentUnitId",deploymentUnitId,"instanceId",instanceId));
    }

    public boolean deleteDisasterRecovery(long tenantId, long id) {
        requireTransaction();
        return mapper.deleteDisasterRecovery(params("tenantId",tenantId,"id",id)) == 1;
    }

    public List<EnvironmentInstance> listAvailableStandbyInstances(long tenantId, long deploymentUnitId, Long excludeInstanceId) {
        return mapper.listAvailableStandbyInstances(params("tenantId",tenantId,"deploymentUnitId",deploymentUnitId,"excludeInstanceId",excludeInstanceId));
    }

    private String instanceSelect(String suffix) {
        return "SELECT " + INSTANCE_COLUMNS
                + " FROM arch_environment_instance instance "
                + "JOIN arch_environment environment "
                + "  ON environment.tenant_id = instance.tenant_id AND environment.id = instance.environment_id "
                + "JOIN arch_deployment_unit unit "
                + "  ON unit.tenant_id = instance.tenant_id AND unit.id = instance.deployment_unit_id "
                + "JOIN arch_physical_subsystem physical "
                + "  ON physical.tenant_id = instance.tenant_id AND physical.id = instance.physical_subsystem_id "
                + "JOIN arch_resource_request request "
                + "  ON request.tenant_id = instance.tenant_id AND request.id = instance.source_request_id "
                + suffix;
    }

    private String drSelect(String suffix) {
        return "SELECT " + DR_COLUMNS
                + " FROM arch_instance_disaster_recovery dr "
                + "JOIN arch_deployment_unit unit "
                + "  ON unit.tenant_id = dr.tenant_id AND unit.id = dr.deployment_unit_id "
                + "JOIN arch_environment_instance p_inst "
                + "  ON p_inst.tenant_id = dr.tenant_id AND p_inst.id = dr.primary_instance_id "
                + "JOIN arch_environment p_env "
                + "  ON p_env.tenant_id = p_inst.tenant_id AND p_env.id = p_inst.environment_id "
                + "JOIN arch_environment_instance s_inst "
                + "  ON s_inst.tenant_id = dr.tenant_id AND s_inst.id = dr.standby_instance_id "
                + "JOIN arch_environment s_env "
                + "  ON s_env.tenant_id = s_inst.tenant_id AND s_env.id = s_inst.environment_id "
                + suffix;
    }

    private String requestSelect(String suffix) {
        return "SELECT " + REQUEST_COLUMNS
                + " FROM arch_resource_request request "
                + "JOIN arch_physical_subsystem physical "
                + "  ON physical.tenant_id = request.tenant_id AND physical.id = request.physical_subsystem_id "
                + "JOIN arch_environment environment "
                + "  ON environment.tenant_id = request.tenant_id AND environment.id = request.environment_id "
                + suffix;
    }

    private static Map<String, Object> params(Object... values) {
        Map<String, Object> params = new java.util.HashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            params.put((String) values[index], values[index + 1]);
        }
        return params;
    }

    private static Number number(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value instanceof Number number ? number : 0;
    }

    private static BigDecimal decimal(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value instanceof BigDecimal decimal ? decimal
                : value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : BigDecimal.ZERO;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String escapedKeyword(String value) {
        return value == null || value.isBlank() ? null : escapeLike(value.trim());
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("环境资源持久化必须在事务内执行");
        }
    }

    private static BigDecimal decimal(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static RequestStatus nullableRequestStatus(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : RequestStatus.fromDatabase(value);
    }

    private static LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
