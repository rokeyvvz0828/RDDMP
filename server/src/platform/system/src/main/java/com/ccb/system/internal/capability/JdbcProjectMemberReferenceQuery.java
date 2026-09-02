package com.ccb.system.internal.capability;

import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectMemberReference;
import com.ccb.system.capability.ProjectMemberReferenceQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class JdbcProjectMemberReferenceQuery implements ProjectMemberReferenceQuery {
    private final JdbcTemplate jdbc;

    public JdbcProjectMemberReferenceQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ProjectMemberReference> findActiveMembers(AuthUser actor, long projectId) {
        Objects.requireNonNull(actor, "actor 不能为空");
        if (projectId <= 0) return List.of();
        return jdbc.query("""
                SELECT m.id, m.user_id, u.display_name, u.username
                FROM pm_project_member m
                JOIN sys_user u ON u.id = m.user_id AND u.tenant_id = m.tenant_id
                    AND u.deleted = 0 AND u.status = 1
                WHERE m.tenant_id = ? AND m.project_id = ? AND m.status = 1 AND m.deleted = 0
                ORDER BY u.display_name ASC, m.id ASC
                """, (rs, rowNum) -> new ProjectMemberReference(
                rs.getLong("id"), rs.getLong("user_id"), rs.getString("display_name"), rs.getString("username")),
                actor.tenantId(), projectId);
    }

    @Override
    public Optional<ProjectMemberReference> findActiveMember(AuthUser actor, long projectId, long projectMemberId) {
        Objects.requireNonNull(actor, "actor 不能为空");
        if (projectId <= 0 || projectMemberId <= 0) return Optional.empty();
        return jdbc.query("""
                SELECT m.id, m.user_id, u.display_name, u.username
                FROM pm_project_member m
                JOIN sys_user u ON u.id = m.user_id AND u.tenant_id = m.tenant_id
                    AND u.deleted = 0 AND u.status = 1
                WHERE m.id = ? AND m.tenant_id = ? AND m.project_id = ?
                  AND m.status = 1 AND m.deleted = 0
                """, (rs, rowNum) -> new ProjectMemberReference(
                rs.getLong("id"), rs.getLong("user_id"), rs.getString("display_name"), rs.getString("username")),
                projectMemberId, actor.tenantId(), projectId).stream().findFirst();
    }
}
