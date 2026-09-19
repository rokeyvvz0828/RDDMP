package com.ccb.boot.requirement;

import com.ccb.requirement.integration.RequirementSystemDirectory;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 需求域系统目录适配器：把架构管理的物理子系统主数据只读地暴露给需求模块。
 * <p>架构模块现有的 {@code PhysicalSystemDirectoryQuery} 面向开发任务，会按"我负责的系统"过滤，
 * 不适合需求侧"项目内全部有效系统"的口径，因此这里由组合根直接只读架构库表；
 * 待架构模块开放"按项目列系统"的公开契约后，本适配器应改为委托该契约。
 */
@Component
public class RequirementSystemDirectoryAdapter implements RequirementSystemDirectory {
    private static final String COLUMNS = """
            SELECT id, code, name, business_group_name, owner_user_id, status
            FROM arch_physical_subsystem
            WHERE tenant_id = ? AND project_id = ? AND deleted = 0 AND status = 'ACTIVE'
            """;

    private final JdbcTemplate jdbc;

    public RequirementSystemDirectoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SystemRef> searchActive(AuthUser actor, long projectId, String keyword) {
        requireActor(actor);
        if (projectId <= 0) {
            return List.of();
        }
        List<Object> args = new ArrayList<>(List.of(actor.tenantId(), projectId));
        StringBuilder sql = new StringBuilder(COLUMNS);
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (code LIKE ? OR name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY code, id");
        return jdbc.queryForList(sql.toString(), args.toArray()).stream()
                .map(RequirementSystemDirectoryAdapter::map)
                .toList();
    }

    @Override
    public Optional<SystemRef> find(AuthUser actor, long projectId, long systemId) {
        requireActor(actor);
        if (projectId <= 0 || systemId <= 0) {
            return Optional.empty();
        }
        return jdbc.queryForList(COLUMNS + " AND id = ?", actor.tenantId(), projectId, systemId).stream()
                .findFirst()
                .map(RequirementSystemDirectoryAdapter::map);
    }

    @Override
    public Optional<SystemRef> findByCode(AuthUser actor, long projectId, String code) {
        requireActor(actor);
        if (projectId <= 0 || code == null || code.isBlank()) {
            return Optional.empty();
        }
        return jdbc.queryForList(COLUMNS + " AND code = ?", actor.tenantId(), projectId, code.trim()).stream()
                .findFirst()
                .map(RequirementSystemDirectoryAdapter::map);
    }

    private static SystemRef map(Map<String, Object> row) {
        Number owner = (Number) row.get("owner_user_id");
        return new SystemRef(
                ((Number) row.get("id")).longValue(),
                row.get("code") == null ? null : String.valueOf(row.get("code")),
                row.get("name") == null ? null : String.valueOf(row.get("name")),
                row.get("business_group_name") == null ? null : String.valueOf(row.get("business_group_name")),
                owner == null ? null : owner.longValue(),
                row.get("status") == null ? null : String.valueOf(row.get("status")));
    }

    private static void requireActor(AuthUser actor) {
        if (actor == null) {
            throw new IllegalStateException("actor 不能为空");
        }
    }
}
