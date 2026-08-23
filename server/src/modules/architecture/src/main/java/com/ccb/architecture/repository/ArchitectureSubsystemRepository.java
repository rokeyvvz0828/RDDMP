package com.ccb.architecture.repository;

import com.ccb.architecture.model.LogicalSubsystem;
import com.ccb.architecture.model.LogicalSubsystemCommand;
import com.ccb.architecture.model.LogicalSubsystemLock;
import com.ccb.architecture.model.LogicalSubsystemQuery;
import com.ccb.architecture.model.PhysicalSubsystem;
import com.ccb.architecture.model.PhysicalSubsystemCommand;
import com.ccb.architecture.model.PhysicalSubsystemQuery;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ArchitectureSubsystemRepository {
    private static final String LOGICAL_COLUMNS = """
            id, code, short_name, name, business_org_id, deployment_platform_code,
            system_type_code, system_ownership_code, contact_user_id, description, remark,
            created_by, updated_by, created_at, updated_at,
            number_sequence, status, sort_no, row_version
            """;
    private static final String PHYSICAL_COLUMNS = """
            id, code, short_name, name, logical_subsystem_id, business_group_name,
            responsible_team_org_id, responsible_team_name_snapshot, runtime_code, system_level_code,
            development_framework_code, owner_user_id, description, remark,
            created_by, updated_by, created_at, updated_at,
            number_slot, english_name, status, row_version
            """;

    private static final RowMapper<LogicalSubsystem> LOGICAL_MAPPER = (rs, rowNum) -> new LogicalSubsystem(
            rs.getLong("id"), rs.getString("code"), rs.getString("short_name"), rs.getString("name"),
            rs.getLong("business_org_id"), rs.getString("deployment_platform_code"),
            rs.getString("system_type_code"), rs.getString("system_ownership_code"),
            rs.getLong("contact_user_id"), rs.getString("description"), rs.getString("remark"),
            rs.getLong("created_by"), rs.getLong("updated_by"),
            localDateTime(rs.getTimestamp("created_at")), localDateTime(rs.getTimestamp("updated_at")),
            rs.getObject("number_sequence", Integer.class), rs.getString("status"), rs.getInt("sort_no"),
            rs.getLong("row_version"), List.of());

    private static final RowMapper<PhysicalSubsystem> PHYSICAL_MAPPER = (rs, rowNum) -> new PhysicalSubsystem(
            rs.getLong("id"), rs.getString("code"), rs.getString("short_name"), rs.getString("name"),
            rs.getLong("logical_subsystem_id"), rs.getString("business_group_name"),
            rs.getLong("responsible_team_org_id"), rs.getString("responsible_team_name_snapshot"),
            rs.getString("runtime_code"), rs.getString("system_level_code"),
            rs.getString("development_framework_code"), nullableLong(rs, "owner_user_id"),
            rs.getString("description"), rs.getString("remark"),
            rs.getLong("created_by"), rs.getLong("updated_by"),
            localDateTime(rs.getTimestamp("created_at")), localDateTime(rs.getTimestamp("updated_at")),
            rs.getString("number_slot"), rs.getString("english_name"), rs.getString("status"),
            rs.getLong("row_version"));
    private static final RowMapper<LogicalSubsystemLock> LOGICAL_LOCK_MAPPER = (rs, rowNum) ->
            new LogicalSubsystemLock(rs.getLong("id"), rs.getBoolean("deleted"));

    private final JdbcTemplate jdbc;

    public ArchitectureSubsystemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResult<LogicalSubsystem> pageLogical(long tenantId, PageQuery page, LogicalSubsystemQuery query) {
        PageQuery normalizedPage = page == null ? new PageQuery(1, 20) : page;
        LogicalSubsystemQuery normalizedQuery = query == null ? LogicalSubsystemQuery.empty() : query;
        StringBuilder filter = new StringBuilder();
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        addLike(filter, args, "code", normalizedQuery.code());
        addLike(filter, args, "short_name", normalizedQuery.shortName());
        addLike(filter, args, "name", normalizedQuery.name());
        if (normalizedQuery.businessOrgId() != null) {
            filter.append(" AND business_org_id = ?");
            args.add(normalizedQuery.businessOrgId());
        }
        addStatus(filter, args, normalizedQuery.status());
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM arch_logical_subsystem WHERE tenant_id = ? AND deleted = 0" + filter,
                Long.class,
                args.toArray());
        List<Object> listArgs = pageArgs(args, normalizedPage);
        List<LogicalSubsystem> records = jdbc.query(
                "SELECT " + LOGICAL_COLUMNS + " FROM arch_logical_subsystem WHERE tenant_id = ? AND deleted = 0"
                        + filter + " ORDER BY sort_no ASC, id DESC LIMIT ? OFFSET ?",
                LOGICAL_MAPPER,
                listArgs.toArray());
        return new PageResult<>(records, total == null ? 0 : total, normalizedPage.page(), normalizedPage.size());
    }

    public Optional<LogicalSubsystem> findLogical(long tenantId, long id) {
        return jdbc.query("SELECT " + LOGICAL_COLUMNS + " FROM arch_logical_subsystem WHERE tenant_id = ? AND id = ? AND deleted = 0",
                LOGICAL_MAPPER, tenantId, id).stream().findFirst();
    }

    /** 父子并发锁读取必须能看见已软删除父记录，以区分普通无效引用与并发删除。 */
    public Optional<LogicalSubsystemLock> lockLogical(long tenantId, long id) {
        return jdbc.query("SELECT id, deleted FROM arch_logical_subsystem WHERE tenant_id = ? AND id = ? FOR UPDATE",
                LOGICAL_LOCK_MAPPER, tenantId, id).stream().findFirst();
    }

    public boolean logicalCodeExists(long tenantId, String code, Long excludeId) {
        return exists("arch_logical_subsystem", "code", tenantId, code, excludeId);
    }

    public boolean logicalNameExists(long tenantId, String name, Long excludeId) {
        return exists("arch_logical_subsystem", "name", tenantId, name, excludeId);
    }

    public void insertLogical(long id, long tenantId, LogicalSubsystemCommand command, long actorId) {
        jdbc.update("""
                INSERT INTO arch_logical_subsystem
                    (id, tenant_id, code, short_name, name, business_org_id, deployment_platform_code,
                     system_type_code, system_ownership_code, contact_user_id, description, remark, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, command.code(), command.shortName(), command.name(), command.businessOrgId(),
                command.deploymentPlatformCode(), command.systemTypeCode(), command.systemOwnershipCode(),
                command.contactUserId(), command.description(), command.remark(), actorId, actorId);
    }

    public int updateLogical(long tenantId, long id, LogicalSubsystemCommand command, long actorId) {
        return jdbc.update("""
                UPDATE arch_logical_subsystem
                SET code = ?, short_name = ?, name = ?, business_org_id = ?, deployment_platform_code = ?,
                    system_type_code = ?, system_ownership_code = ?, contact_user_id = ?, description = ?,
                    remark = ?, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND deleted = 0
                """, command.code(), command.shortName(), command.name(), command.businessOrgId(),
                command.deploymentPlatformCode(), command.systemTypeCode(), command.systemOwnershipCode(),
                command.contactUserId(), command.description(), command.remark(), actorId, tenantId, id);
    }

    public int softDeleteLogical(long tenantId, long id, long actorId) {
        return jdbc.update("UPDATE arch_logical_subsystem SET deleted = 1, updated_by = ? WHERE tenant_id = ? AND id = ? AND deleted = 0",
                actorId, tenantId, id);
    }

    public long countActivePhysicalByLogical(long tenantId, long logicalSubsystemId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM arch_physical_subsystem "
                        + "WHERE tenant_id = ? AND logical_subsystem_id = ? AND deleted = 0 AND status = 'ACTIVE'",
                Long.class, tenantId, logicalSubsystemId);
        return count == null ? 0 : count;
    }

    /**
     * 统计逻辑子系统名下全部物理历史，包括已软删除、下线和作废记录。
     * 逻辑作废规则使用该事实，避免通过软删除历史绕过不可逆约束。
     */
    public long countPhysicalHistoryByLogical(long tenantId, long logicalSubsystemId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM arch_physical_subsystem WHERE tenant_id = ? AND logical_subsystem_id = ?",
                Long.class, tenantId, logicalSubsystemId);
        return count == null ? 0 : count;
    }

    public PageResult<PhysicalSubsystem> pagePhysical(long tenantId, PageQuery page, PhysicalSubsystemQuery query) {
        PageQuery normalizedPage = page == null ? new PageQuery(1, 20) : page;
        PhysicalSubsystemQuery normalizedQuery = query == null ? PhysicalSubsystemQuery.empty() : query;
        StringBuilder filter = new StringBuilder();
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        addLike(filter, args, "code", normalizedQuery.code());
        addLike(filter, args, "short_name", normalizedQuery.shortName());
        addLike(filter, args, "name", normalizedQuery.name());
        addLike(filter, args, "business_group_name", normalizedQuery.businessGroupName());
        if (normalizedQuery.responsibleTeamOrgId() != null) {
            filter.append(" AND responsible_team_org_id = ?");
            args.add(normalizedQuery.responsibleTeamOrgId());
        }
        if (normalizedQuery.logicalSubsystemId() != null) {
            filter.append(" AND logical_subsystem_id = ?");
            args.add(normalizedQuery.logicalSubsystemId());
        }
        addStatus(filter, args, normalizedQuery.status());
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM arch_physical_subsystem WHERE tenant_id = ? AND deleted = 0" + filter,
                Long.class,
                args.toArray());
        List<Object> listArgs = pageArgs(args, normalizedPage);
        List<PhysicalSubsystem> records = jdbc.query(
                "SELECT " + PHYSICAL_COLUMNS + " FROM arch_physical_subsystem WHERE tenant_id = ? AND deleted = 0"
                        + filter + " ORDER BY id DESC LIMIT ? OFFSET ?",
                PHYSICAL_MAPPER,
                listArgs.toArray());
        return new PageResult<>(records, total == null ? 0 : total, normalizedPage.page(), normalizedPage.size());
    }

    public Optional<PhysicalSubsystem> findPhysical(long tenantId, long id) {
        return jdbc.query("SELECT " + PHYSICAL_COLUMNS + " FROM arch_physical_subsystem WHERE tenant_id = ? AND id = ? AND deleted = 0",
                PHYSICAL_MAPPER, tenantId, id).stream().findFirst();
    }

    /** 逻辑详情只汇总当前可查看的已发布物理事实，不包含软删除历史。 */
    public List<PhysicalSubsystem> findPhysicalByLogical(long tenantId, long logicalSubsystemId) {
        return jdbc.query("SELECT " + PHYSICAL_COLUMNS + " FROM arch_physical_subsystem "
                        + "WHERE tenant_id = ? AND logical_subsystem_id = ? AND deleted = 0 "
                        + "ORDER BY number_slot ASC, id ASC",
                PHYSICAL_MAPPER, tenantId, logicalSubsystemId);
    }

    public boolean physicalCodeExists(long tenantId, String code, Long excludeId) {
        return exists("arch_physical_subsystem", "code", tenantId, code, excludeId);
    }

    public boolean physicalNameExists(long tenantId, String name, Long excludeId) {
        return exists("arch_physical_subsystem", "name", tenantId, name, excludeId);
    }

    public void insertPhysical(long id, long tenantId, PhysicalSubsystemCommand command,
                               String responsibleTeamNameSnapshot, long actorId) {
        jdbc.update("""
                INSERT INTO arch_physical_subsystem
                    (id, tenant_id, code, short_name, name, logical_subsystem_id, business_group_name,
                     responsible_team_org_id, responsible_team_name_snapshot, runtime_code, system_level_code,
                     development_framework_code, owner_user_id, description, remark, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, command.code(), command.shortName(), command.name(), command.logicalSubsystemId(),
                command.businessGroupName(), command.responsibleTeamOrgId(), responsibleTeamNameSnapshot,
                command.runtimeCode(), command.systemLevelCode(), command.developmentFrameworkCode(),
                command.ownerUserId(), command.description(), command.remark(), actorId, actorId);
    }

    public int updatePhysical(long tenantId, long id, PhysicalSubsystemCommand command,
                              String responsibleTeamNameSnapshot, long actorId) {
        return jdbc.update("""
                UPDATE arch_physical_subsystem
                SET code = ?, short_name = ?, name = ?, logical_subsystem_id = ?, business_group_name = ?,
                    responsible_team_org_id = ?, responsible_team_name_snapshot = ?, runtime_code = ?,
                    system_level_code = ?, development_framework_code = ?, owner_user_id = ?,
                    description = ?, remark = ?, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND deleted = 0
                """, command.code(), command.shortName(), command.name(), command.logicalSubsystemId(),
                command.businessGroupName(), command.responsibleTeamOrgId(), responsibleTeamNameSnapshot,
                command.runtimeCode(), command.systemLevelCode(), command.developmentFrameworkCode(),
                command.ownerUserId(), command.description(), command.remark(),
                actorId, tenantId, id);
    }

    public int softDeletePhysical(long tenantId, long id, long actorId) {
        return jdbc.update("UPDATE arch_physical_subsystem SET deleted = 1, updated_by = ? WHERE tenant_id = ? AND id = ? AND deleted = 0",
                actorId, tenantId, id);
    }

    private boolean exists(String table, String column, long tenantId, String value, Long excludeId) {
        String exclude = excludeId == null ? "" : " AND id <> ?";
        List<Object> args = new ArrayList<>(List.of(tenantId, value));
        if (excludeId != null) {
            args.add(excludeId);
        }
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ? AND " + column + " = ?" + exclude,
                Long.class,
                args.toArray());
        return count != null && count > 0;
    }

    private void addLike(StringBuilder filter, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        filter.append(" AND ").append(column).append(" LIKE ? ESCAPE '\\\\'");
        args.add("%" + escapeLike(value.trim()) + "%");
    }

    private void addStatus(StringBuilder filter, List<Object> args, String status) {
        if (status == null) {
            return;
        }
        filter.append(" AND status = ?");
        args.add(status);
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private List<Object> pageArgs(List<Object> args, PageQuery page) {
        List<Object> result = new ArrayList<>(args);
        result.add(page.size());
        result.add((page.page() - 1) * page.size());
        return result;
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static java.time.LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
