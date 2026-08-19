package com.ccb.project.repository;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.project.model.ProjectRole;
import com.ccb.project.model.ProjectStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ProjectRepository {
    public record ProjectRecord(
            long id,
            long tenantId,
            String projectCode,
            String projectName,
            ProjectStatus status,
            long ownerUserId,
            long version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record MemberRecord(long projectId, long userId, ProjectRole role) {
    }

    public record ProjectAccessRecord(ProjectRecord project, ProjectRole role) {
    }

    private static final String PROJECT_COLUMNS = """
            p.id, p.tenant_id, p.project_code, p.project_name, p.status,
            p.owner_user_id, p.version, p.created_at, p.updated_at
            """;

    private final JdbcTemplate jdbc;

    public ProjectRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResult<ProjectAccessRecord> listAvailable(long tenantId, long userId, PageQuery pageQuery,
                                                          String keyword, ProjectStatus status) {
        String normalized = keyword == null ? "" : keyword.trim();
        String keywordFilter = normalized.isEmpty() ? "" : " AND (p.project_code LIKE ? OR p.project_name LIKE ?)";
        String statusFilter = status == null ? "" : " AND p.status = ?";
        List<Object> filterArgs = new ArrayList<>(List.of(tenantId, userId));
        if (!normalized.isEmpty()) {
            String like = "%" + normalized + "%";
            filterArgs.add(like);
            filterArgs.add(like);
        }
        if (status != null) {
            filterArgs.add(status.name());
        }
        String from = """
                FROM pm_project p
                JOIN pm_project_member m
                  ON m.tenant_id = p.tenant_id AND m.project_id = p.id
                WHERE p.tenant_id = ? AND m.user_id = ? AND p.deleted = 0
                """;
        List<Object> queryArgs = new ArrayList<>(filterArgs);
        queryArgs.add((pageQuery.page() - 1) * pageQuery.size());
        queryArgs.add(pageQuery.size());
        List<ProjectAccessRecord> records = jdbc.query(
                "SELECT " + PROJECT_COLUMNS + ", m.role " + from + keywordFilter + statusFilter
                        + " ORDER BY p.updated_at DESC, p.id DESC LIMIT ?, ?",
                this::mapAccess,
                queryArgs.toArray());
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) " + from + keywordFilter + statusFilter,
                Long.class,
                filterArgs.toArray());
        return new PageResult<>(records, total == null ? 0L : total, pageQuery.page(), pageQuery.size());
    }

    public List<ProjectAccessRecord> findAvailable(long tenantId, long userId) {
        return jdbc.query("""
                SELECT %s, m.role
                FROM pm_project p
                JOIN pm_project_member m
                  ON m.tenant_id = p.tenant_id AND m.project_id = p.id
                WHERE p.tenant_id = ? AND m.user_id = ? AND p.deleted = 0
                ORDER BY p.project_name, p.id
                """.formatted(PROJECT_COLUMNS), this::mapAccess, tenantId, userId);
    }

    public Optional<ProjectRecord> findById(long tenantId, long projectId) {
        return jdbc.query("SELECT " + PROJECT_COLUMNS
                        + " FROM pm_project p WHERE p.tenant_id = ? AND p.id = ? AND p.deleted = 0",
                this::mapProject,
                tenantId,
                projectId).stream().findFirst();
    }

    public Optional<MemberRecord> findMembership(long tenantId, long projectId, long userId) {
        return jdbc.query("""
                SELECT project_id, user_id, role
                FROM pm_project_member
                WHERE tenant_id = ? AND project_id = ? AND user_id = ?
                """, this::mapMember, tenantId, projectId, userId).stream().findFirst();
    }

    public List<MemberRecord> findMembers(long tenantId, long projectId) {
        return jdbc.query("""
                SELECT project_id, user_id, role
                FROM pm_project_member
                WHERE tenant_id = ? AND project_id = ?
                ORDER BY CASE role WHEN 'OWNER' THEN 1 WHEN 'ADMIN' THEN 2 WHEN 'MEMBER' THEN 3 ELSE 4 END, user_id
                """, this::mapMember, tenantId, projectId);
    }

    public boolean existsByCode(long tenantId, String projectCode, Long excludedProjectId) {
        String exclusion = excludedProjectId == null ? "" : " AND id <> ?";
        List<Object> args = new ArrayList<>(List.of(tenantId, projectCode));
        if (excludedProjectId != null) {
            args.add(excludedProjectId);
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pm_project WHERE tenant_id = ? AND project_code = ? AND deleted = 0" + exclusion,
                Integer.class,
                args.toArray());
        return count != null && count > 0;
    }

    public void insertProject(ProjectRecord project, long actorId) {
        jdbc.update("""
                INSERT INTO pm_project
                    (id, tenant_id, project_code, project_name, status, owner_user_id, version, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, project.id(), project.tenantId(), project.projectCode(), project.projectName(),
                project.status().name(), project.ownerUserId(), project.version(), actorId, actorId);
    }

    public void insertMember(long tenantId, long projectId, long userId, ProjectRole role, long actorId) {
        jdbc.update("""
                INSERT INTO pm_project_member
                    (tenant_id, project_id, user_id, role, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """, tenantId, projectId, userId, role.name(), actorId, actorId);
    }

    public int updateProjectName(long tenantId, long projectId, String projectName, long expectedVersion, long actorId) {
        return jdbc.update("""
                UPDATE pm_project
                SET project_name = ?, version = version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND version = ? AND deleted = 0
                """, projectName, actorId, tenantId, projectId, expectedVersion);
    }

    public int updateStatus(long tenantId, long projectId, ProjectStatus currentStatus, ProjectStatus nextStatus,
                            long expectedVersion, long actorId) {
        return jdbc.update("""
                UPDATE pm_project
                SET status = ?, version = version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND status = ? AND version = ? AND deleted = 0
                """, nextStatus.name(), actorId, tenantId, projectId, currentStatus.name(), expectedVersion);
    }

    public int claimVersion(long tenantId, long projectId, long expectedVersion, long actorId) {
        return jdbc.update("""
                UPDATE pm_project
                SET version = version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND version = ? AND deleted = 0
                """, actorId, tenantId, projectId, expectedVersion);
    }

    public int updateOwner(long tenantId, long projectId, long ownerUserId, long actorId) {
        return jdbc.update("""
                UPDATE pm_project SET owner_user_id = ?, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND deleted = 0
                """, ownerUserId, actorId, tenantId, projectId);
    }

    public int updateMemberRole(long tenantId, long projectId, long userId, ProjectRole role, long actorId) {
        return jdbc.update("""
                UPDATE pm_project_member SET role = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND project_id = ? AND user_id = ?
                """, role.name(), actorId, tenantId, projectId, userId);
    }

    public int deleteMember(long tenantId, long projectId, long userId) {
        return jdbc.update("""
                DELETE FROM pm_project_member
                WHERE tenant_id = ? AND project_id = ? AND user_id = ? AND role <> 'OWNER'
                """, tenantId, projectId, userId);
    }

    public void audit(long tenantId, long projectId, long actorId, String action, String changeSummary) {
        jdbc.update("""
                INSERT INTO pm_project_audit_event
                    (id, tenant_id, project_id, actor_user_id, action_code, result, trace_id, change_summary)
                VALUES (?, ?, ?, ?, ?, 'SUCCESS', ?, ?)
                """, nextId(), tenantId, projectId, actorId, action, TraceId.getOrCreate(), changeSummary);
    }

    private ProjectRecord mapProject(ResultSet rs, int rowNum) throws SQLException {
        return new ProjectRecord(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getString("project_code"),
                rs.getString("project_name"),
                ProjectStatus.valueOf(rs.getString("status")),
                rs.getLong("owner_user_id"),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime());
    }

    private ProjectAccessRecord mapAccess(ResultSet rs, int rowNum) throws SQLException {
        return new ProjectAccessRecord(mapProject(rs, rowNum), ProjectRole.valueOf(rs.getString("role")));
    }

    private MemberRecord mapMember(ResultSet rs, int rowNum) throws SQLException {
        return new MemberRecord(rs.getLong("project_id"), rs.getLong("user_id"),
                ProjectRole.valueOf(rs.getString("role")));
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(1000);
    }
}
