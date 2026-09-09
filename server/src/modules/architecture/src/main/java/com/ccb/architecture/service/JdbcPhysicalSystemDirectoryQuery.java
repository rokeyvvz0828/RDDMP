package com.ccb.architecture.service;

import com.ccb.architecture.integration.PhysicalSystemDirectoryQuery;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class JdbcPhysicalSystemDirectoryQuery implements PhysicalSystemDirectoryQuery {
    private static final String COLUMNS = "SELECT id, code, name, owner_user_id, status, row_version";
    private final JdbcTemplate jdbc;

    public JdbcPhysicalSystemDirectoryQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public PageResult<SystemRef> searchManageable(AuthUser actor, long projectId, PageQuery page, String keyword) {
        requireActor(actor);
        requireProject(projectId);
        StringBuilder where = new StringBuilder(" FROM arch_physical_subsystem WHERE tenant_id = ? AND project_id = ? AND deleted = 0 AND status = 'ACTIVE'");
        List<Object> args = new ArrayList<>(List.of(actor.tenantId(), projectId));
        if (!isDevelopmentAdmin(actor)) {
            where.append(" AND owner_user_id = ?");
            args.add(actor.id());
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (code LIKE ? OR name LIKE ?)");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        args.add(page.size());
        args.add(Math.multiplyExact(page.page() - 1, page.size()));
        var records = jdbc.queryForList(COLUMNS + where + " ORDER BY code, id LIMIT ? OFFSET ?", args.toArray())
                .stream().map(JdbcPhysicalSystemDirectoryQuery::map).toList();
        return new PageResult<>(records, total == null ? 0 : total, page.page(), page.size());
    }

    @Override
    public Optional<SystemRef> find(AuthUser actor, long projectId, long systemId) {
        requireActor(actor);
        requireProject(projectId);
        return jdbc.queryForList(COLUMNS + " FROM arch_physical_subsystem WHERE tenant_id = ? AND project_id = ? AND id = ? AND deleted = 0",
                actor.tenantId(), projectId, systemId).stream().findFirst().map(JdbcPhysicalSystemDirectoryQuery::map);
    }

    private static void requireProject(long projectId) {
        if (projectId <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "项目标识无效");
    }

    private static boolean isDevelopmentAdmin(AuthUser actor) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthUser principal
                && principal.id() == actor.id() && principal.tenantId() == actor.tenantId()
                && authentication.getAuthorities().stream().anyMatch(a -> "development:admin".equals(a.getAuthority()));
    }

    private static void requireActor(AuthUser actor) {
        if (actor == null || !actor.enabled()) throw new BusinessException(ErrorCode.FORBIDDEN, "用户不可访问系统");
    }

    private static SystemRef map(Map<String, Object> row) {
        Number owner = (Number) row.get("owner_user_id");
        return new SystemRef(((Number) row.get("id")).longValue(), (String) row.get("code"), (String) row.get("name"),
                owner == null ? null : owner.longValue(), (String) row.get("status"), ((Number) row.get("row_version")).longValue());
    }
}
