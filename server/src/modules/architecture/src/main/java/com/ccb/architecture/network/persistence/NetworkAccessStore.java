package com.ccb.architecture.network.persistence;

import com.ccb.architecture.network.model.NetworkAccessModels.AccessProtocol;
import com.ccb.architecture.network.model.NetworkAccessModels.AddressType;
import com.ccb.architecture.network.model.NetworkAccessModels.ApplicationStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.EndpointKind;
import com.ccb.architecture.network.model.NetworkAccessModels.ExternalNetworkAddress;
import com.ccb.architecture.network.model.NetworkAccessModels.ManagedEndpointInstance;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessApplication;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessRelation;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkZone;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkZoneSubnet;
import com.ccb.architecture.network.model.NetworkAccessModels.RecordStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.RelationStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
            id, tenant_id, application_no, applicant_id, source_kind, source_physical_subsystem_id,
            source_environment_id, source_deployment_unit_id, source_external_address_id, source_snapshot_json,
            target_kind, target_physical_subsystem_id, target_environment_id, target_deployment_unit_id,
            target_external_address_id, target_snapshot_json, protocol, ports, purpose, process_description,
            valid_from, valid_until, status, row_version, created_by, updated_by, created_at, updated_at
            """;
    private static final String RELATION_COLUMNS = """
            id, tenant_id, relation_no, application_id, source_kind, source_snapshot_json, target_kind,
            target_snapshot_json, protocol, ports, purpose, process_description, valid_from, valid_until,
            status, close_reason, closed_by, closed_at, row_version, created_by, updated_by, created_at, updated_at
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
                    ApplicationStatus.fromDatabase(rs.getString("status")),
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
                    RelationStatus.fromDatabase(rs.getString("status")),
                    rs.getString("close_reason"),
                    nullableLong(rs, "closed_by"),
                    localDateTime(rs.getTimestamp("closed_at")),
                    rs.getLong("row_version"),
                    rs.getLong("created_by"),
                    rs.getLong("updated_by"),
                    localDateTime(rs.getTimestamp("created_at")),
                    localDateTime(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbc;

    public NetworkAccessStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JdbcTemplate 不能为空");
    }

    public List<NetworkZone> listZones(long tenantId, RecordStatus status, String keyword) {
        StringBuilder filter = new StringBuilder("WHERE zone.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (status != null) {
            filter.append(" AND zone.status = ?");
            args.add(status.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + escapeLike(keyword.trim()) + "%";
            filter.append(" AND (zone.code LIKE ? ESCAPE '\\\\' OR zone.name LIKE ? ESCAPE '\\\\')");
            args.add(pattern);
            args.add(pattern);
        }
        filter.append(" ORDER BY COALESCE(zone.parent_id, 0), zone.restriction_level, zone.code, zone.id");
        return jdbc.query(zoneSelect(filter.toString()), ZONE_MAPPER, args.toArray());
    }

    public Optional<NetworkZone> findZone(long tenantId, long id) {
        return jdbc.query(zoneSelect("WHERE zone.tenant_id = ? AND zone.id = ?"),
                ZONE_MAPPER, tenantId, id).stream().findFirst();
    }

    public Optional<NetworkZone> lockZone(long tenantId, long id) {
        requireTransaction();
        return jdbc.query(zoneSelect("WHERE zone.tenant_id = ? AND zone.id = ? FOR UPDATE"),
                ZONE_MAPPER, tenantId, id).stream().findFirst();
    }

    public boolean zoneCodeExists(long tenantId, String code, Long excludeId) {
        return exists("arch_network_zone", "code", tenantId, code, excludeId);
    }

    public boolean zoneNameExists(long tenantId, Long parentId, String name, Long excludeId) {
        String exclude = excludeId == null ? "" : " AND id <> ?";
        List<Object> args = new ArrayList<>(List.of(tenantId, name));
        String parentPredicate;
        if (parentId == null) {
            parentPredicate = " AND parent_id IS NULL";
        } else {
            parentPredicate = " AND parent_id = ?";
            args.add(parentId);
        }
        if (excludeId != null) {
            args.add(excludeId);
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM arch_network_zone "
                + "WHERE tenant_id = ? AND name = ?" + parentPredicate + exclude, Integer.class, args.toArray());
        return count != null && count > 0;
    }

    public boolean hasActiveChildZones(long tenantId, long zoneId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM arch_network_zone "
                        + "WHERE tenant_id = ? AND parent_id = ? AND status = 'ACTIVE'",
                Integer.class, tenantId, zoneId);
        return count != null && count > 0;
    }

    public boolean hasActiveSubnets(long tenantId, long zoneId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM arch_network_zone_subnet "
                        + "WHERE tenant_id = ? AND network_zone_id = ? AND status = 'ACTIVE'",
                Integer.class, tenantId, zoneId);
        return count != null && count > 0;
    }

    public void insertZone(NetworkZone zone) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_network_zone
                    (id, tenant_id, parent_id, code, name, restriction_level, status,
                     description, remark, row_version, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, zone.id(), zone.tenantId(), zone.parentId(), zone.code(), zone.name(),
                zone.restrictionLevel(), zone.status().name(), zone.description(), zone.remark(),
                zone.rowVersion(), zone.createdBy(), zone.updatedBy());
    }

    public boolean updateZone(long tenantId, long id, long rowVersion, Long parentId, String code, String name,
                              int restrictionLevel, String description, String remark, long actorId) {
        requireTransaction();
        return jdbc.update("""
                UPDATE arch_network_zone
                SET parent_id = ?, code = ?, name = ?, restriction_level = ?, description = ?,
                    remark = ?, updated_by = ?, row_version = row_version + 1
                WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE' AND row_version = ?
                """, parentId, code, name, restrictionLevel, description, remark, actorId,
                tenantId, id, rowVersion) == 1;
    }

    public boolean updateZoneStatus(long tenantId, long id, RecordStatus from, RecordStatus to, long actorId) {
        requireTransaction();
        return jdbc.update("""
                UPDATE arch_network_zone
                SET status = ?, updated_by = ?, row_version = row_version + 1
                WHERE tenant_id = ? AND id = ? AND status = ?
                """, to.name(), actorId, tenantId, id, from.name()) == 1;
    }

    public List<NetworkZoneSubnet> listSubnets(long tenantId, Long zoneId, RecordStatus status) {
        StringBuilder filter = new StringBuilder("WHERE subnet.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (zoneId != null) {
            filter.append(" AND subnet.network_zone_id = ?");
            args.add(zoneId);
        }
        if (status != null) {
            filter.append(" AND subnet.status = ?");
            args.add(status.name());
        }
        filter.append(" ORDER BY zone.code, subnet.cidr_block, subnet.id");
        return jdbc.query(subnetSelect(filter.toString()), SUBNET_MAPPER, args.toArray());
    }

    public Optional<NetworkZoneSubnet> findSubnet(long tenantId, long id) {
        return jdbc.query(subnetSelect("WHERE subnet.tenant_id = ? AND subnet.id = ?"),
                SUBNET_MAPPER, tenantId, id).stream().findFirst();
    }

    public Optional<NetworkZoneSubnet> lockSubnet(long tenantId, long id) {
        requireTransaction();
        return jdbc.query(subnetSelect("WHERE subnet.tenant_id = ? AND subnet.id = ? FOR UPDATE"),
                SUBNET_MAPPER, tenantId, id).stream().findFirst();
    }

    public boolean subnetCidrExists(long tenantId, String cidrBlock, Long excludeId) {
        return exists("arch_network_zone_subnet", "cidr_block", tenantId, cidrBlock, excludeId);
    }

    public void insertSubnet(NetworkZoneSubnet subnet) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_network_zone_subnet
                    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status,
                     remark, row_version, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, subnet.id(), subnet.tenantId(), subnet.networkZoneId(), subnet.cidrBlock(),
                subnet.gatewayIp(), subnet.purpose(), subnet.status().name(), subnet.remark(),
                subnet.rowVersion(), subnet.createdBy(), subnet.updatedBy());
    }

    public boolean updateSubnet(long tenantId, long id, long rowVersion, String cidrBlock, String gatewayIp,
                                String purpose, String remark, long actorId) {
        requireTransaction();
        return jdbc.update("""
                UPDATE arch_network_zone_subnet
                SET cidr_block = ?, gateway_ip = ?, purpose = ?, remark = ?,
                    updated_by = ?, row_version = row_version + 1
                WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE' AND row_version = ?
                """, cidrBlock, gatewayIp, purpose, remark, actorId, tenantId, id, rowVersion) == 1;
    }

    public boolean updateSubnetStatus(long tenantId, long id, RecordStatus from, RecordStatus to, long actorId) {
        requireTransaction();
        return jdbc.update("""
                UPDATE arch_network_zone_subnet
                SET status = ?, updated_by = ?, row_version = row_version + 1
                WHERE tenant_id = ? AND id = ? AND status = ?
                """, to.name(), actorId, tenantId, id, from.name()) == 1;
    }

    public List<ExternalNetworkAddress> listAddresses(long tenantId, RecordStatus status, String keyword) {
        StringBuilder filter = new StringBuilder("WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (status != null) {
            filter.append(" AND status = ?");
            args.add(status.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + escapeLike(keyword.trim()) + "%";
            filter.append(" AND (address_value LIKE ? ESCAPE '\\\\' OR display_name LIKE ? ESCAPE '\\\\')");
            args.add(pattern);
            args.add(pattern);
        }
        filter.append(" ORDER BY updated_at DESC, id DESC");
        return jdbc.query("SELECT " + ADDRESS_COLUMNS + " FROM arch_external_network_address " + filter,
                ADDRESS_MAPPER, args.toArray());
    }

    public Optional<ExternalNetworkAddress> findAddress(long tenantId, long id) {
        return jdbc.query("SELECT " + ADDRESS_COLUMNS
                        + " FROM arch_external_network_address WHERE tenant_id = ? AND id = ?",
                ADDRESS_MAPPER, tenantId, id).stream().findFirst();
    }

    public Optional<ExternalNetworkAddress> lockAddress(long tenantId, long id) {
        requireTransaction();
        return jdbc.query("SELECT " + ADDRESS_COLUMNS
                        + " FROM arch_external_network_address WHERE tenant_id = ? AND id = ? FOR UPDATE",
                ADDRESS_MAPPER, tenantId, id).stream().findFirst();
    }

    public boolean addressExists(long tenantId, AddressType type, String value, Long excludeId) {
        String exclude = excludeId == null ? "" : " AND id <> ?";
        List<Object> args = new ArrayList<>(List.of(tenantId, type.name(), value));
        if (excludeId != null) {
            args.add(excludeId);
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM arch_external_network_address "
                        + "WHERE tenant_id = ? AND address_type = ? AND address_value = ?" + exclude,
                Integer.class, args.toArray());
        return count != null && count > 0;
    }

    public void insertAddress(ExternalNetworkAddress address) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_external_network_address
                    (id, tenant_id, address_type, address_value, display_name, purpose, status,
                     remark, row_version, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, address.id(), address.tenantId(), address.addressType().name(), address.addressValue(),
                address.displayName(), address.purpose(), address.status().name(), address.remark(),
                address.rowVersion(), address.createdBy(), address.updatedBy());
    }

    public boolean updateAddress(long tenantId, long id, long rowVersion, AddressType type, String value,
                                 String displayName, String purpose, String remark, long actorId) {
        requireTransaction();
        return jdbc.update("""
                UPDATE arch_external_network_address
                SET address_type = ?, address_value = ?, display_name = ?, purpose = ?, remark = ?,
                    updated_by = ?, row_version = row_version + 1
                WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE' AND row_version = ?
                """, type.name(), value, displayName, purpose, remark, actorId, tenantId, id, rowVersion) == 1;
    }

    public boolean updateAddressStatus(long tenantId, long id, RecordStatus from, RecordStatus to, long actorId) {
        requireTransaction();
        return jdbc.update("""
                UPDATE arch_external_network_address
                SET status = ?, updated_by = ?, row_version = row_version + 1
                WHERE tenant_id = ? AND id = ? AND status = ?
                """, to.name(), actorId, tenantId, id, from.name()) == 1;
    }

    public List<ManagedEndpointInstance> listEndpointInstances(long tenantId, Long physicalSubsystemId,
                                                               Long environmentId, Long deploymentUnitId,
                                                               List<Long> instanceIds) {
        StringBuilder filter = new StringBuilder("WHERE instance.tenant_id = ? AND instance.status = 'ACTIVE'");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (physicalSubsystemId != null) {
            filter.append(" AND instance.physical_subsystem_id = ?");
            args.add(physicalSubsystemId);
        }
        if (environmentId != null) {
            filter.append(" AND instance.environment_id = ?");
            args.add(environmentId);
        }
        if (deploymentUnitId != null) {
            filter.append(" AND instance.deployment_unit_id = ?");
            args.add(deploymentUnitId);
        }
        if (instanceIds != null && !instanceIds.isEmpty()) {
            filter.append(" AND instance.id IN (");
            for (int i = 0; i < instanceIds.size(); i++) {
                if (i > 0) {
                    filter.append(", ");
                }
                filter.append("?");
                args.add(instanceIds.get(i));
            }
            filter.append(")");
        }
        filter.append(" ORDER BY environment.code, unit.code, instance.machine_name, instance.id");
        return jdbc.query(instanceSelect(filter.toString()), INSTANCE_MAPPER, args.toArray());
    }

    public void insertApplication(NetworkAccessApplication application) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_network_access_application
                    (id, tenant_id, application_no, applicant_id, source_kind,
                     source_physical_subsystem_id, source_environment_id, source_deployment_unit_id,
                     source_external_address_id, source_snapshot_json, target_kind,
                     target_physical_subsystem_id, target_environment_id, target_deployment_unit_id,
                     target_external_address_id, target_snapshot_json, protocol, ports, purpose,
                     process_description, valid_from, valid_until, status, row_version, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, application.id(), application.tenantId(), application.applicationNo(), application.applicantId(),
                application.sourceKind().name(), application.sourcePhysicalSubsystemId(), application.sourceEnvironmentId(),
                application.sourceDeploymentUnitId(), application.sourceExternalAddressId(), application.sourceSnapshotJson(),
                application.targetKind().name(), application.targetPhysicalSubsystemId(), application.targetEnvironmentId(),
                application.targetDeploymentUnitId(), application.targetExternalAddressId(), application.targetSnapshotJson(),
                application.protocol().name(), application.ports(), application.purpose(), application.processDescription(),
                timestamp(application.validFrom()), timestamp(application.validUntil()), application.status().name(),
                application.rowVersion(), application.createdBy(), application.updatedBy());
    }

    public Optional<NetworkAccessApplication> findApplication(long tenantId, long id) {
        return jdbc.query("SELECT " + APP_COLUMNS
                        + " FROM arch_network_access_application WHERE tenant_id = ? AND id = ?",
                APP_MAPPER, tenantId, id).stream().findFirst();
    }

    public Optional<NetworkAccessApplication> lockApplication(long tenantId, long id) {
        requireTransaction();
        return jdbc.query("SELECT " + APP_COLUMNS
                        + " FROM arch_network_access_application WHERE tenant_id = ? AND id = ? FOR UPDATE",
                APP_MAPPER, tenantId, id).stream().findFirst();
    }

    public List<NetworkAccessApplication> listApplications(long tenantId, Long applicantId,
                                                           ApplicationStatus status, int limit, int offset) {
        StringBuilder filter = new StringBuilder("WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (applicantId != null) {
            filter.append(" AND applicant_id = ?");
            args.add(applicantId);
        }
        if (status != null) {
            filter.append(" AND status = ?");
            args.add(status.name());
        }
        filter.append(" ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query("SELECT " + APP_COLUMNS + " FROM arch_network_access_application " + filter,
                APP_MAPPER, args.toArray());
    }

    public boolean updateApplicationStatus(long tenantId, long id, ApplicationStatus from,
                                           long expectedRowVersion, ApplicationStatus to, long actorId) {
        requireTransaction();
        return jdbc.update("""
                UPDATE arch_network_access_application
                SET status = ?, updated_by = ?, row_version = row_version + 1
                WHERE tenant_id = ? AND id = ? AND status = ? AND row_version = ?
                """, to.name(), actorId, tenantId, id, from.name(), expectedRowVersion) == 1;
    }

    public void insertRelation(NetworkAccessRelation relation) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_network_access_relation
                    (id, tenant_id, relation_no, application_id, source_kind, source_snapshot_json,
                     target_kind, target_snapshot_json, protocol, ports, purpose, process_description,
                     valid_from, valid_until, status, row_version, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, relation.id(), relation.tenantId(), relation.relationNo(), relation.applicationId(),
                relation.sourceKind().name(), relation.sourceSnapshotJson(), relation.targetKind().name(),
                relation.targetSnapshotJson(), relation.protocol().name(), relation.ports(), relation.purpose(),
                relation.processDescription(), timestamp(relation.validFrom()), timestamp(relation.validUntil()),
                relation.status().name(), relation.rowVersion(), relation.createdBy(), relation.updatedBy());
    }

    public List<NetworkAccessRelation> listRelations(long tenantId, RelationStatus status, int limit, int offset) {
        StringBuilder filter = new StringBuilder("WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (status != null) {
            filter.append(" AND status = ?");
            args.add(status.name());
        }
        filter.append(" ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query("SELECT " + RELATION_COLUMNS + " FROM arch_network_access_relation " + filter,
                RELATION_MAPPER, args.toArray());
    }

    public Optional<NetworkAccessRelation> lockRelation(long tenantId, long id) {
        requireTransaction();
        return jdbc.query("SELECT " + RELATION_COLUMNS
                        + " FROM arch_network_access_relation WHERE tenant_id = ? AND id = ? FOR UPDATE",
                RELATION_MAPPER, tenantId, id).stream().findFirst();
    }

    public boolean closeRelation(long tenantId, long id, long rowVersion, String reason,
                                 long actorId, LocalDateTime closedAt) {
        requireTransaction();
        return jdbc.update("""
                UPDATE arch_network_access_relation
                SET status = 'CLOSED', close_reason = ?, closed_by = ?, closed_at = ?,
                    updated_by = ?, row_version = row_version + 1
                WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE' AND row_version = ?
                """, reason, actorId, Timestamp.valueOf(closedAt), actorId, tenantId, id, rowVersion) == 1;
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

    private boolean exists(String table, String column, long tenantId, String value, Long excludeId) {
        String exclude = excludeId == null ? "" : " AND id <> ?";
        List<Object> args = new ArrayList<>(List.of(tenantId, value));
        if (excludeId != null) {
            args.add(excludeId);
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table
                + " WHERE tenant_id = ? AND " + column + " = ?" + exclude, Integer.class, args.toArray());
        return count != null && count > 0;
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
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
}
