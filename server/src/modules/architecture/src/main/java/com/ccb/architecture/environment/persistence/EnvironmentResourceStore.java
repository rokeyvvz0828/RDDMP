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
import org.springframework.jdbc.core.JdbcTemplate;
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
            unit.kind AS deployment_unit_kind, item.related_deployment_unit_name,
            item.deployment_unit_description, item.deployment_unit_type,
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
            rs.getString("related_deployment_unit_name"),
            rs.getString("deployment_unit_description"),
            rs.getString("deployment_unit_type"),
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
                                    long physicalSubsystemId, String relatedDeploymentUnitName,
                                    String deploymentUnitType, String description,
                                    Long defaultNetworkZoneId, String defaultNetworkZoneName,
                                    Long currentVersionId, int currentVersion) {
        public DeploymentUnitRef(long id, String code, String name, String kind, String status,
                                 long physicalSubsystemId, String relatedDeploymentUnitName,
                                 String deploymentUnitType, String description,
                                 Long currentVersionId, int currentVersion) {
            this(id, code, name, kind, status, physicalSubsystemId, relatedDeploymentUnitName,
                    deploymentUnitType, description, null, null, currentVersionId, currentVersion);
        }
    }

    private final JdbcTemplate jdbc;

    public EnvironmentResourceStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JdbcTemplate 不能为空");
    }

    public List<Environment> listEnvironments(long tenantId, String typeCode, RecordStatus status,
                                              String keyword, int limit, int offset) {
        if (limit <= 0 || offset < 0) {
            throw new IllegalArgumentException("分页参数无效");
        }
        StringBuilder sql = new StringBuilder("SELECT ").append(ENVIRONMENT_COLUMNS)
                .append(" FROM arch_environment environment")
                .append(" WHERE environment.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (typeCode != null && !typeCode.isBlank()) {
            sql.append(" AND environment.type_code = ?");
            args.add(typeCode.trim());
        }
        if (status != null) {
            sql.append(" AND environment.status = ?");
            args.add(status.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + escapeLike(keyword.trim()) + "%";
            sql.append(" AND (environment.code LIKE ? ESCAPE '\\\\' OR environment.name LIKE ? ESCAPE '\\\\')");
            args.add(pattern);
            args.add(pattern);
        }
        sql.append(" ORDER BY environment.updated_at DESC, environment.id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), ENVIRONMENT_MAPPER, args.toArray());
    }

    public Optional<Environment> findEnvironment(long tenantId, long id) {
        return jdbc.query("SELECT " + ENVIRONMENT_COLUMNS + " FROM arch_environment environment "
                        + "WHERE environment.tenant_id = ? AND environment.id = ?",
                ENVIRONMENT_MAPPER, tenantId, id).stream().findFirst();
    }

    public Optional<Environment> lockEnvironment(long tenantId, long id) {
        requireTransaction();
        return jdbc.query("SELECT " + ENVIRONMENT_COLUMNS + " FROM arch_environment environment "
                        + "WHERE environment.tenant_id = ? AND environment.id = ? FOR UPDATE",
                ENVIRONMENT_MAPPER, tenantId, id).stream().findFirst();
    }

    public boolean environmentCodeExists(long tenantId, String code, Long excludeId) {
        return exists("arch_environment", "code", tenantId, code, excludeId);
    }

    public boolean environmentNameExists(long tenantId, String name, Long excludeId) {
        return exists("arch_environment", "name", tenantId, name, excludeId);
    }

    public void insertEnvironment(Environment environment) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_environment
                    (id, tenant_id, code, name, type_code, status, description, remark,
                     row_version, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, environment.id(), environment.tenantId(), environment.code(), environment.name(),
                environment.typeCode(), environment.status().name(), environment.description(),
                environment.remark(), environment.rowVersion(), environment.createdBy(), environment.updatedBy());
    }

    public boolean updateEnvironment(long tenantId, long id, long expectedRowVersion, String code,
                                     String name, String typeCode, String description, String remark,
                                     long actorId) {
        requireTransaction();
        return jdbc.update("""
                UPDATE arch_environment
                SET code = ?, name = ?, type_code = ?, description = ?, remark = ?, updated_by = ?,
                    row_version = row_version + 1
                WHERE tenant_id = ? AND id = ? AND row_version = ?
                """, code, name, typeCode, description, remark, actorId, tenantId, id, expectedRowVersion) == 1;
    }

    public boolean updateEnvironmentStatus(long tenantId, long id, long expectedRowVersion,
                                           RecordStatus fromStatus, RecordStatus toStatus, long actorId) {
        requireTransaction();
        return jdbc.update("""
                UPDATE arch_environment
                SET status = ?, updated_by = ?, row_version = row_version + 1
                WHERE tenant_id = ? AND id = ? AND status = ? AND row_version = ?
                """, toStatus.name(), actorId, tenantId, id, fromStatus.name(), expectedRowVersion) == 1;
    }

    public boolean deleteEnvironment(long tenantId, long id, long expectedRowVersion) {
        requireTransaction();
        return jdbc.update("""
                DELETE FROM arch_environment
                WHERE tenant_id = ? AND id = ? AND row_version = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM arch_resource_request request
                      WHERE request.tenant_id = arch_environment.tenant_id
                        AND request.environment_id = arch_environment.id
                  )
                """, tenantId, id, expectedRowVersion) == 1;
    }

    public ResourceSummary resourceSummary(long tenantId, long environmentId) {
        ResourceSummary requested = jdbc.query("""
                SELECT
                    COUNT(DISTINCT request.id) AS request_count,
                    COUNT(DISTINCT CASE WHEN request.status IN ('APPROVED', 'FULFILLED', 'DIFF_FULFILLED') THEN request.id END) AS approved_count,
                    COUNT(DISTINCT CASE WHEN request.status = 'IN_REVIEW' THEN request.id END) AS pending_count,
                    COALESCE(SUM(CASE WHEN request.status IN ('APPROVED', 'FULFILLED', 'DIFF_FULFILLED')
                        THEN item.cpu_cores * item.planned_node_count
                            + CASE WHEN item.has_sidecar = 1 THEN item.sidecar_cpu_cores ELSE 0 END ELSE 0 END), 0) AS cpu_sum,
                    COALESCE(SUM(CASE WHEN request.status IN ('APPROVED', 'FULFILLED', 'DIFF_FULFILLED')
                        THEN item.memory_gb * item.planned_node_count
                            + CASE WHEN item.has_sidecar = 1 THEN item.sidecar_memory_gb ELSE 0 END ELSE 0 END), 0) AS memory_sum,
                    COALESCE(SUM(CASE WHEN request.status IN ('APPROVED', 'FULFILLED', 'DIFF_FULFILLED')
                        THEN item.database_storage_gb + item.storage_gb + item.extra_cbs_gb + item.local_disk_gb ELSE 0 END), 0) AS storage_sum,
                    COALESCE(SUM(CASE WHEN request.status IN ('APPROVED', 'FULFILLED', 'DIFF_FULFILLED')
                        THEN item.planned_node_count ELSE 0 END), 0) AS node_sum
                FROM arch_resource_request request
                LEFT JOIN arch_resource_request_item item
                  ON item.tenant_id = request.tenant_id AND item.request_id = request.id
                WHERE request.tenant_id = ?
                  AND request.environment_id = ?
                  AND request.status NOT IN ('REJECTED', 'CANCELLED')
                """, rs -> {
            if (!rs.next()) {
                return emptySummary(environmentId);
            }
            return new ResourceSummary(environmentId,
                    rs.getLong("request_count"),
                    rs.getLong("approved_count"),
                    rs.getLong("pending_count"),
                    decimal(rs, "cpu_sum"),
                    decimal(rs, "memory_sum"),
                    decimal(rs, "storage_sum"),
                    rs.getLong("node_sum"),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0);
        }, tenantId, environmentId);

        ActualSummary actual = jdbc.query("""
                SELECT
                    COALESCE(SUM(cpu_cores), 0) AS actual_cpu_sum,
                    COALESCE(SUM(memory_gb), 0) AS actual_memory_sum,
                    COALESCE(SUM(database_storage_gb + file_storage_gb + extra_cbs_gb + local_disk_gb), 0) AS actual_storage_sum,
                    COUNT(id) AS actual_node_count
                FROM arch_environment_instance
                WHERE tenant_id = ? AND environment_id = ? AND status = 'ACTIVE'
                """, rs -> {
            if (!rs.next()) {
                return new ActualSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L);
            }
            return new ActualSummary(decimal(rs, "actual_cpu_sum"), decimal(rs, "actual_memory_sum"),
                    decimal(rs, "actual_storage_sum"), rs.getLong("actual_node_count"));
        }, tenantId, environmentId);

        if (requested == null) {
            requested = emptySummary(environmentId);
        }
        if (actual == null) {
            actual = new ActualSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L);
        }
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
        return jdbc.query("""
                SELECT id, code, short_name, name, business_group_name, deployment_platform, system_level_code,
                       disaster_recovery_mode, status, deleted
                FROM arch_physical_subsystem
                WHERE tenant_id = ? AND id = ?
                """, (rs, rowNum) -> new PhysicalSubsystemRef(rs.getLong("id"), rs.getString("code"),
                rs.getString("short_name"), rs.getString("name"), rs.getString("business_group_name"),
                rs.getString("deployment_platform"), rs.getString("system_level_code"),
                rs.getString("disaster_recovery_mode"), rs.getString("status"), rs.getBoolean("deleted")),
                tenantId, physicalSubsystemId).stream().findFirst();
    }

    public Optional<DeploymentUnitRef> findDeploymentUnit(long tenantId, long deploymentUnitId) {
        return jdbc.query("""
                SELECT unit.id, unit.code, unit.name, unit.kind, unit.status, unit.physical_subsystem_id,
                       unit.related_deployment_unit_name, unit.deployment_unit_type, unit.description,
                       unit.default_network_zone_id, unit.default_network_zone_name,
                       version.id AS current_version_id, unit.current_version
                FROM arch_deployment_unit unit
                LEFT JOIN arch_deployment_unit_version version
                  ON version.tenant_id = unit.tenant_id
                 AND version.unit_id = unit.id
                 AND version.version_no = unit.current_version
                WHERE unit.tenant_id = ? AND unit.id = ?
                """, (rs, rowNum) -> new DeploymentUnitRef(rs.getLong("id"), rs.getString("code"),
                rs.getString("name"), rs.getString("kind"), rs.getString("status"),
                rs.getLong("physical_subsystem_id"), rs.getString("related_deployment_unit_name"),
                rs.getString("deployment_unit_type"), rs.getString("description"),
                nullableLong(rs, "default_network_zone_id"), rs.getString("default_network_zone_name"),
                nullableLong(rs, "current_version_id"), rs.getInt("current_version")),
                tenantId, deploymentUnitId).stream().findFirst();
    }

    public List<DeploymentUnitRef> listDeploymentUnits(long tenantId, long physicalSubsystemId, int limit) {
        return jdbc.query("""
                SELECT unit.id, unit.code, unit.name, unit.kind, unit.status, unit.physical_subsystem_id,
                       unit.related_deployment_unit_name, unit.deployment_unit_type, unit.description,
                       unit.default_network_zone_id, unit.default_network_zone_name,
                       version.id AS current_version_id, unit.current_version
                FROM arch_deployment_unit unit
                LEFT JOIN arch_deployment_unit_version version
                  ON version.tenant_id = unit.tenant_id
                 AND version.unit_id = unit.id
                 AND version.version_no = unit.current_version
                WHERE unit.tenant_id = ? AND unit.physical_subsystem_id = ? AND unit.status = 'ACTIVE'
                ORDER BY unit.code ASC, unit.id ASC
                LIMIT ?
                """, (rs, rowNum) -> new DeploymentUnitRef(rs.getLong("id"), rs.getString("code"),
                rs.getString("name"), rs.getString("kind"), rs.getString("status"),
                rs.getLong("physical_subsystem_id"), rs.getString("related_deployment_unit_name"),
                rs.getString("deployment_unit_type"), rs.getString("description"),
                nullableLong(rs, "default_network_zone_id"), rs.getString("default_network_zone_name"),
                nullableLong(rs, "current_version_id"), rs.getInt("current_version")),
                tenantId, physicalSubsystemId, limit);
    }

    public void insertResourceRequest(ResourceRequest request) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_resource_request
                    (id, tenant_id, request_no, physical_subsystem_id, environment_id, applicant_id,
                     contact_user_id, request_type, reason, status, current_business_round,
                     current_workflow_definition_id, current_workflow_version_id,
                     current_workflow_instance_id, current_payload_digest, cancellation_requested,
                     row_version, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, request.id(), request.tenantId(), request.requestNo(), request.physicalSubsystemId(),
                request.environmentId(), request.applicantId(), request.contactUserId(),
                request.requestType().name(), request.reason(),
                request.status().name(), request.currentBusinessRound(),
                request.currentWorkflowDefinitionId(), request.currentWorkflowVersionId(),
                request.currentWorkflowInstanceId(), request.currentPayloadDigest(),
                request.cancellationRequested(), request.rowVersion(), request.createdBy(), request.updatedBy());
    }

    public Optional<ResourceRequest> findRequest(long tenantId, long requestId) {
        return jdbc.query(requestSelect("WHERE request.tenant_id = ? AND request.id = ?"),
                REQUEST_MAPPER, tenantId, requestId).stream().findFirst();
    }

    public Optional<ResourceRequest> lockRequest(long tenantId, long requestId) {
        requireTransaction();
        return jdbc.query(requestSelect("WHERE request.tenant_id = ? AND request.id = ? FOR UPDATE"),
                REQUEST_MAPPER, tenantId, requestId).stream().findFirst();
    }

    public List<ResourceRequest> listRequests(long tenantId, Long applicantId, RequestStatus status,
                                              Long environmentId, Long physicalSubsystemId,
                                              int limit, int offset) {
        if (limit <= 0 || offset < 0) {
            throw new IllegalArgumentException("分页参数无效");
        }
        StringBuilder filter = new StringBuilder("WHERE request.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (applicantId != null) {
            filter.append(" AND request.applicant_id = ?");
            args.add(applicantId);
        }
        if (status != null) {
            filter.append(" AND request.status = ?");
            args.add(status.name());
        }
        if (environmentId != null) {
            filter.append(" AND request.environment_id = ?");
            args.add(environmentId);
        }
        if (physicalSubsystemId != null) {
            filter.append(" AND request.physical_subsystem_id = ?");
            args.add(physicalSubsystemId);
        }
        filter.append(" ORDER BY request.updated_at DESC, request.id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(requestSelect(filter.toString()), REQUEST_MAPPER, args.toArray());
    }

    public List<ResourceRequestItem> listItems(long tenantId, long requestId) {
        return jdbc.query("SELECT " + ITEM_COLUMNS + " FROM arch_resource_request_item item "
                        + "JOIN arch_deployment_unit unit "
                        + "ON unit.tenant_id = item.tenant_id AND unit.id = item.deployment_unit_id "
                        + "WHERE item.tenant_id = ? AND item.request_id = ? ORDER BY item.item_seq ASC",
                ITEM_MAPPER, tenantId, requestId);
    }

    public int countInstancesForEnvironmentUnit(long tenantId, long environmentId, long deploymentUnitId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM arch_environment_instance
                WHERE tenant_id = ? AND environment_id = ? AND deployment_unit_id = ?
                """, Integer.class, tenantId, environmentId, deploymentUnitId);
        return count == null ? 0 : count;
    }

    public void replaceItems(long tenantId, long requestId, List<ResourceRequestItem> items) {
        requireTransaction();
        jdbc.update("DELETE FROM arch_resource_request_item WHERE tenant_id = ? AND request_id = ?",
                tenantId, requestId);
        for (ResourceRequestItem item : items) {
            jdbc.update("""
                    INSERT INTO arch_resource_request_item
                        (id, tenant_id, request_id, item_seq, deployment_unit_id,
                         related_deployment_unit_name, deployment_unit_description,
                         deployment_unit_type, database_storage_gb,
                         storage_gb, network_zone_id, network_zone_name, network_zone,
                         server_type, cpu_cores, memory_gb,
                         app_web_group_count, planned_node_count, sidecar_cpu_cores,
                         sidecar_memory_gb, has_sidecar, database_name, database_version,
                         jdk_version, middleware, operating_system, extra_cbs_gb, local_disk_gb,
                         needs_nft, needs_fserver, needs_jobexecutor, remark)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? ,?)
                    """, item.id(), item.tenantId(), item.requestId(), item.itemSeq(),
                    item.deploymentUnitId(), item.relatedDeploymentUnitName(),
                    item.deploymentUnitDescription(), item.deploymentUnitType(),
                    item.databaseStorageGb(), item.fileStorageGb(),
                    item.networkZoneId(), item.networkZoneName(), item.networkZone(),
                    item.serverType(), item.cpuCores(), item.memoryGb(),
                    item.appWebGroupCount(), item.plannedNodeCount(), item.sidecarCpuCores(),
                    item.sidecarMemoryGb(), item.hasSidecar(), item.databaseName(), item.databaseVersion(),
                    item.jdkVersion(), item.middleware(), item.operatingSystem(), item.extraCbsGb(),
                    item.localDiskGb(), item.needsNft(), item.needsFserver(), item.needsJobexecutor(),
                    item.remark());
        }
    }

    public boolean updateDraft(long tenantId, long requestId, RequestStatus expectedStatus,
                               long expectedRowVersion, long physicalSubsystemId, long environmentId,
                               long contactUserId, RequestType requestType, String reason, long actorId) {
        requireTransaction();
        return jdbc.update("""
                UPDATE arch_resource_request
                SET physical_subsystem_id = ?, environment_id = ?, request_type = ?, reason = ?,
                    contact_user_id = ?, row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND status = ? AND row_version = ?
                """, physicalSubsystemId, environmentId, requestType.name(), reason, contactUserId,
                actorId, tenantId, requestId, expectedStatus.name(), expectedRowVersion) == 1;
    }

    public boolean compareAndSetStatus(long tenantId, long requestId,
                                       RequestStatus expectedStatus, long expectedRowVersion,
                                       RequestStatus nextStatus, long actorId) {
        requireTransaction();
        return jdbc.update("""
                UPDATE arch_resource_request
                SET status = ?, row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND status = ? AND row_version = ?
                """, nextStatus.name(), actorId, tenantId, requestId,
                expectedStatus.name(), expectedRowVersion) == 1;
    }

    public boolean compareAndSetWorkflowContext(long tenantId, long requestId,
                                                int expectedCurrentBusinessRound,
                                                long expectedRowVersion, int nextBusinessRound,
                                                long workflowDefinitionId, long workflowVersionId,
                                                long workflowInstanceId, String payloadDigest,
                                                long actorId) {
        requireTransaction();
        return jdbc.update("""
                UPDATE arch_resource_request
                SET current_business_round = ?, current_workflow_definition_id = ?,
                    current_workflow_version_id = ?, current_workflow_instance_id = ?,
                    current_payload_digest = ?, row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND current_business_round = ? AND row_version = ?
                  AND status = 'IN_REVIEW'
                """, nextBusinessRound, workflowDefinitionId, workflowVersionId, workflowInstanceId,
                payloadDigest, actorId, tenantId, requestId, expectedCurrentBusinessRound,
                expectedRowVersion) == 1;
    }

    public boolean compareAndSetCancellationRequested(long tenantId, long requestId,
                                                      long expectedRowVersion, boolean requested,
                                                      long actorId) {
        requireTransaction();
        return jdbc.update("""
                UPDATE arch_resource_request
                SET cancellation_requested = ?, row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND row_version = ? AND status = 'IN_REVIEW'
                """, requested, actorId, tenantId, requestId, expectedRowVersion) == 1;
    }

    public void insertHistory(HistoryEvent event) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_resource_request_history
                    (id, tenant_id, request_id, event_type, from_status, to_status, business_round,
                     summary, snapshot_json, diff_json, operator_id, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, event.id(), event.tenantId(), event.requestId(), event.eventType(),
                event.fromStatus() == null ? null : event.fromStatus().name(),
                event.toStatus() == null ? null : event.toStatus().name(),
                event.businessRound(), event.summary(), event.snapshotJson(), event.diffJson(),
                event.operatorId(), timestamp(event.occurredAt()));
    }

    public List<HistoryEvent> listHistory(long tenantId, long requestId) {
        return jdbc.query("""
                SELECT id, tenant_id, request_id, event_type, from_status, to_status, business_round,
                       summary, snapshot_json, diff_json, operator_id, occurred_at
                FROM arch_resource_request_history
                WHERE tenant_id = ? AND request_id = ?
                ORDER BY occurred_at ASC, id ASC
                """, HISTORY_MAPPER, tenantId, requestId);
    }

    public void insertPendingWorkflowRound(WorkflowRound round) {
        requireTransaction();
        if (round.status() != WorkflowRoundStatus.PENDING
                || round.workflowDefinitionId() != null || round.workflowVersionId() != null
                || round.workflowInstanceId() != null || round.payloadDigest() != null
                || round.startedAt() != null || round.endedAt() != null) {
            throw new IllegalArgumentException("PENDING 工作流轮次不得预先绑定平台上下文");
        }
        jdbc.update("""
                INSERT INTO arch_resource_request_workflow_round
                    (id, tenant_id, request_id, round_no, workflow_definition_id,
                     workflow_version_id, workflow_instance_id, payload_digest, status,
                     started_at, ended_at)
                VALUES (?, ?, ?, ?, NULL, NULL, NULL, NULL, 'PENDING', NULL, NULL)
                """, round.id(), round.tenantId(), round.requestId(), round.roundNo());
    }

    public Optional<WorkflowRound> lockWorkflowRoundByInstance(long tenantId, long workflowInstanceId) {
        requireTransaction();
        return jdbc.query("""
                SELECT id, tenant_id, request_id, round_no, workflow_definition_id,
                       workflow_version_id, workflow_instance_id, payload_digest, status,
                       started_at, ended_at, created_at, updated_at
                FROM arch_resource_request_workflow_round
                WHERE tenant_id = ? AND workflow_instance_id = ? FOR UPDATE
                """, WORKFLOW_ROUND_MAPPER, tenantId, workflowInstanceId).stream().findFirst();
    }

    public boolean isLatestWorkflowRound(long tenantId, long requestId, int roundNo) {
        Integer latest = jdbc.queryForObject("""
                SELECT MAX(round_no) FROM arch_resource_request_workflow_round
                WHERE tenant_id = ? AND request_id = ?
                """, Integer.class, tenantId, requestId);
        return latest != null && latest == roundNo;
    }

    public boolean bindWorkflowRoundStarted(long tenantId, long requestId, int roundNo,
                                            long workflowDefinitionId, long workflowVersionId,
                                            long workflowInstanceId, String payloadDigest,
                                            LocalDateTime startedAt) {
        requireTransaction();
        return jdbc.update("""
                UPDATE arch_resource_request_workflow_round
                SET workflow_definition_id = ?, workflow_version_id = ?, workflow_instance_id = ?,
                    payload_digest = ?, status = 'STARTED', started_at = ?
                WHERE tenant_id = ? AND request_id = ? AND round_no = ? AND status = 'PENDING'
                """, workflowDefinitionId, workflowVersionId, workflowInstanceId, payloadDigest,
                timestamp(startedAt), tenantId, requestId, roundNo) == 1;
    }

    public boolean completeStartedWorkflowRound(long tenantId, long requestId, int roundNo,
                                                WorkflowRoundStatus nextStatus, LocalDateTime endedAt) {
        requireTransaction();
        return jdbc.update("""
                UPDATE arch_resource_request_workflow_round
                SET status = ?, ended_at = ?
                WHERE tenant_id = ? AND request_id = ? AND round_no = ? AND status = 'STARTED'
                """, nextStatus.name(), timestamp(endedAt), tenantId, requestId, roundNo) == 1;
    }

    public boolean beginReceipt(WorkflowReceiptStart receipt) {
        requireTransaction();
        return jdbc.update("""
                INSERT IGNORE INTO arch_resource_request_workflow_receipt
                    (id, tenant_id, event_id, subscriber_key, request_id, round_no,
                     workflow_instance_id, event_type, processing_status, detail)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, receipt.id(), receipt.tenantId(), receipt.eventId(), receipt.subscriberKey(),
                receipt.requestId(), receipt.roundNo(), receipt.workflowInstanceId(), receipt.eventType(),
                WorkflowReceiptStatus.FAILED.name(), "事务内事件尚未完成") == 1;
    }

    public boolean completeReceipt(long tenantId, String eventId, String subscriberKey,
                                   WorkflowReceiptStatus status, String detail) {
        requireTransaction();
        return jdbc.update("""
                UPDATE arch_resource_request_workflow_receipt
                SET processing_status = ?, detail = ?, processed_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND event_id = ? AND subscriber_key = ?
                  AND processing_status = 'FAILED'
                """, status.name(), detail, tenantId, eventId, subscriberKey) == 1;
    }

    public Optional<WorkflowReceipt> findReceipt(long tenantId, String eventId, String subscriberKey) {
        return jdbc.query("""
                SELECT id, tenant_id, event_id, subscriber_key, request_id, round_no,
                       workflow_instance_id, event_type, processing_status, detail,
                       received_at, processed_at
                FROM arch_resource_request_workflow_receipt
                WHERE tenant_id = ? AND event_id = ? AND subscriber_key = ?
                """, WORKFLOW_RECEIPT_MAPPER, tenantId, eventId, subscriberKey).stream().findFirst();
    }

    // ===== 环境部署实例与灾备关系持久化 =====

    public void insertInstance(EnvironmentInstance instance) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_environment_instance
                    (id, tenant_id, instance_no, environment_id, deployment_unit_id,
                     deployment_unit_version_id, deployment_unit_version_no,
                     physical_subsystem_id, source_request_id, source_item_id,
                     machine_name, ip_address, server_type, deployment_platform,
                     network_zone_id, network_zone_name, network_zone, status,
                     cpu_cores, memory_gb, database_storage_gb,
                     file_storage_gb, extra_cbs_gb, local_disk_gb, database_name,
                     database_version, jdk_version, middleware, operating_system,
                     needs_nft, needs_fserver, needs_jobexecutor, fulfillment_mode,
                     difference_reason, remark, offlined_at, offlined_by, offline_reason,
                     row_version, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, instance.id(), instance.tenantId(), instance.instanceNo(), instance.environmentId(),
                instance.deploymentUnitId(), instance.deploymentUnitVersionId(), instance.deploymentUnitVersionNo(),
                instance.physicalSubsystemId(), instance.sourceRequestId(), instance.sourceItemId(),
                instance.machineName(), instance.ipAddress(), instance.serverType(), instance.deploymentPlatform(),
                instance.networkZoneId(), instance.networkZoneName(), instance.networkZone(),
                instance.status().name(), instance.cpuCores(), instance.memoryGb(),
                instance.databaseStorageGb(), instance.fileStorageGb(), instance.extraCbsGb(), instance.localDiskGb(),
                instance.databaseName(), instance.databaseVersion(), instance.jdkVersion(), instance.middleware(),
                instance.operatingSystem(), instance.needsNft(), instance.needsFserver(), instance.needsJobexecutor(),
                instance.fulfillmentMode().name(), instance.differenceReason(), instance.remark(),
                instance.offlinedAt() == null ? null : Timestamp.valueOf(instance.offlinedAt()),
                instance.offlinedBy(), instance.offlineReason(), instance.rowVersion(),
                instance.createdBy(), instance.updatedBy());
    }

    public Optional<EnvironmentInstance> findInstance(long tenantId, long id) {
        return jdbc.query(instanceSelect("WHERE instance.tenant_id = ? AND instance.id = ?"),
                INSTANCE_MAPPER, tenantId, id).stream().findFirst();
    }

    public Optional<EnvironmentInstance> lockInstance(long tenantId, long id) {
        requireTransaction();
        return jdbc.query(instanceSelect("WHERE instance.tenant_id = ? AND instance.id = ? FOR UPDATE"),
                INSTANCE_MAPPER, tenantId, id).stream().findFirst();
    }

    public Optional<EnvironmentInstance> findActiveInstanceByMachineOrIp(long tenantId, long environmentId,
                                                                         String machineName, String ipAddress,
                                                                         Long excludeInstanceId) {
        StringBuilder sql = new StringBuilder(instanceSelect(
                "WHERE instance.tenant_id = ? AND instance.environment_id = ? AND instance.status = 'ACTIVE' "
                        + "AND (instance.machine_name = ? OR instance.ip_address = ?)"));
        List<Object> args = new ArrayList<>(List.of(tenantId, environmentId, machineName, ipAddress));
        if (excludeInstanceId != null) {
            sql.append(" AND instance.id <> ?");
            args.add(excludeInstanceId);
        }
        return jdbc.query(sql.toString(), INSTANCE_MAPPER, args.toArray()).stream().findFirst();
    }

    public List<EnvironmentInstance> listInstances(long tenantId, Long environmentId, Long physicalSubsystemId,
                                                   Long deploymentUnitId, InstanceStatus status,
                                                   String keyword, int limit, int offset) {
        if (limit <= 0 || offset < 0) {
            throw new IllegalArgumentException("分页参数无效");
        }
        StringBuilder filter = new StringBuilder("WHERE instance.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (environmentId != null) {
            filter.append(" AND instance.environment_id = ?");
            args.add(environmentId);
        }
        if (physicalSubsystemId != null) {
            filter.append(" AND instance.physical_subsystem_id = ?");
            args.add(physicalSubsystemId);
        }
        if (deploymentUnitId != null) {
            filter.append(" AND instance.deployment_unit_id = ?");
            args.add(deploymentUnitId);
        }
        if (status != null) {
            filter.append(" AND instance.status = ?");
            args.add(status.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + escapeLike(keyword.trim()) + "%";
            filter.append(" AND (instance.machine_name LIKE ? ESCAPE '\\\\' OR instance.ip_address LIKE ? ESCAPE '\\\\' OR instance.instance_no LIKE ? ESCAPE '\\\\')");
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        filter.append(" ORDER BY instance.updated_at DESC, instance.id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(instanceSelect(filter.toString()), INSTANCE_MAPPER, args.toArray());
    }

    public boolean offlineInstance(long tenantId, long id, long expectedRowVersion,
                                   String offlineReason, long actorId, LocalDateTime offlinedAt) {
        requireTransaction();
        return jdbc.update("""
                UPDATE arch_environment_instance
                SET status = 'OFFLINE', offline_reason = ?, offlined_by = ?, offlined_at = ?,
                    updated_by = ?, row_version = row_version + 1
                WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE' AND row_version = ?
                """, offlineReason, actorId, Timestamp.valueOf(offlinedAt), actorId,
                tenantId, id, expectedRowVersion) == 1;
    }

    public void insertDisasterRecovery(InstanceDisasterRecovery dr) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_instance_disaster_recovery
                    (id, tenant_id, deployment_unit_id, primary_instance_id, standby_instance_id,
                     dr_mode, description, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, dr.id(), dr.tenantId(), dr.deploymentUnitId(), dr.primaryInstanceId(),
                dr.standbyInstanceId(), dr.drMode().name(), dr.description(), dr.createdBy());
    }

    public Optional<InstanceDisasterRecovery> findDisasterRecovery(long tenantId, long id) {
        return jdbc.query(drSelect("WHERE dr.tenant_id = ? AND dr.id = ?"),
                DR_MAPPER, tenantId, id).stream().findFirst();
    }

    public Optional<InstanceDisasterRecovery> findDisasterRecoveryPair(long tenantId,
                                                                       long primaryInstanceId,
                                                                       long standbyInstanceId) {
        return jdbc.query(drSelect("WHERE dr.tenant_id = ? AND dr.primary_instance_id = ? AND dr.standby_instance_id = ?"),
                DR_MAPPER, tenantId, primaryInstanceId, standbyInstanceId).stream().findFirst();
    }

    public List<InstanceDisasterRecovery> listDisasterRecoveries(long tenantId, Long deploymentUnitId, Long instanceId) {
        StringBuilder filter = new StringBuilder("WHERE dr.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (deploymentUnitId != null) {
            filter.append(" AND dr.deployment_unit_id = ?");
            args.add(deploymentUnitId);
        }
        if (instanceId != null) {
            filter.append(" AND (dr.primary_instance_id = ? OR dr.standby_instance_id = ?)");
            args.add(instanceId);
            args.add(instanceId);
        }
        filter.append(" ORDER BY dr.id DESC");
        return jdbc.query(drSelect(filter.toString()), DR_MAPPER, args.toArray());
    }

    public boolean deleteDisasterRecovery(long tenantId, long id) {
        requireTransaction();
        return jdbc.update("DELETE FROM arch_instance_disaster_recovery WHERE tenant_id = ? AND id = ?",
                tenantId, id) == 1;
    }

    public List<EnvironmentInstance> listAvailableStandbyInstances(long tenantId, long deploymentUnitId, Long excludeInstanceId) {
        StringBuilder filter = new StringBuilder(
                "WHERE instance.tenant_id = ? AND instance.deployment_unit_id = ? AND instance.status = 'ACTIVE'");
        List<Object> args = new ArrayList<>(List.of(tenantId, deploymentUnitId));
        if (excludeInstanceId != null) {
            filter.append(" AND instance.id <> ?");
            args.add(excludeInstanceId);
        }
        filter.append(" ORDER BY instance.environment_id ASC, instance.id ASC");
        return jdbc.query(instanceSelect(filter.toString()), INSTANCE_MAPPER, args.toArray());
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

    private boolean exists(String tableName, String columnName, long tenantId, String value, Long excludeId) {
        String exclude = excludeId == null ? "" : " AND id <> ?";
        List<Object> args = new ArrayList<>(List.of(tenantId, value));
        if (excludeId != null) {
            args.add(excludeId);
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + tableName
                + " WHERE tenant_id = ? AND " + columnName + " = ?" + exclude,
                Long.class, args.toArray());
        return count != null && count > 0;
    }

    private ResourceSummary emptySummary(long environmentId) {
        return new ResourceSummary(environmentId, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
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
