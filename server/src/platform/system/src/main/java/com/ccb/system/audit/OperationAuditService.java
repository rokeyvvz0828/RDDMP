package com.ccb.system.audit;

import com.ccb.common.api.PageQuery;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.SystemPage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OperationAuditService {
    static final int VISIBLE_DAYS = 30;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public OperationAuditService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this(jdbc, objectMapper, Clock.systemDefaultZone());
    }

    OperationAuditService(JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public SystemPage<OperationAuditRecord> operations(OperationAuditQuery query, AuthUser actor) {
        boolean superAdmin = isSuperAdmin(actor);
        if (!superAdmin) requireManageableProject(actor);
        TimeRange range = timeRange(query.startDate(), query.endDate());
        PageQuery page = new PageQuery(query.page(), query.size());
        SqlParts parts = operationFilters(query, actor, superAdmin, range);
        long total = count("SELECT COUNT(*) FROM sys_operation_log l " + parts.where(), parts.arguments());
        List<Object> pageArguments = new ArrayList<>(parts.arguments());
        pageArguments.add(page.size());
        pageArguments.add((page.page() - 1) * page.size());
        List<OperationAuditRecord> records = jdbc.query("""
                        SELECT l.id, l.operator_id, l.operator_name, l.operation_code,
                               l.module_code, l.module_name, l.operation_type, l.target_type, l.target_id,
                               l.project_id, l.project_name, l.request_method, l.request_path, l.success,
                               l.http_status, l.duration_ms, l.error_message, l.client_ip, l.user_agent,
                               l.changed_fields, l.trace_id, l.created_at
                        FROM sys_operation_log l
                        """ + parts.where() + " ORDER BY l.created_at DESC, l.id DESC LIMIT ? OFFSET ?",
                this::mapOperation, pageArguments.toArray());
        return new SystemPage<>(records, total, page.page(), page.size());
    }

    public SystemPage<LoginAuditRecord> logins(LoginAuditQuery query, AuthUser actor) {
        requireSuperAdmin(actor);
        TimeRange range = timeRange(query.startDate(), query.endDate());
        PageQuery page = new PageQuery(query.page(), query.size());
        List<Object> arguments = new ArrayList<>(List.of(actor.tenantId(), range.start(), range.endExclusive()));
        StringBuilder where = new StringBuilder("WHERE l.tenant_id = ? AND l.created_at >= ? AND l.created_at < ?");
        if (query.success() != null) {
            where.append(" AND l.success = ?");
            arguments.add(query.success() ? 1 : 0);
        }
        String keyword = keyword(query.keyword());
        if (keyword != null) {
            where.append(" AND (l.username LIKE ? OR l.client_ip LIKE ?)");
            arguments.add(keyword);
            arguments.add(keyword);
        }
        long total = count("SELECT COUNT(*) FROM sys_login_log l " + where, arguments);
        List<Object> pageArguments = new ArrayList<>(arguments);
        pageArguments.add(page.size());
        pageArguments.add((page.page() - 1) * page.size());
        List<LoginAuditRecord> records = jdbc.query("""
                        SELECT l.id, l.username, l.success, l.failure_reason, l.client_ip,
                               l.user_agent, l.created_at
                        FROM sys_login_log l
                        """ + where + " ORDER BY l.created_at DESC, l.id DESC LIMIT ? OFFSET ?",
                this::mapLogin, pageArguments.toArray());
        return new SystemPage<>(records, total, page.page(), page.size());
    }

    public List<AuditProjectOption> projects(AuthUser actor) {
        boolean superAdmin = isSuperAdmin(actor);
        if (!superAdmin) requireManageableProject(actor);
        String scope = superAdmin ? "" : manageableProjectClause("p", actor);
        List<Object> arguments = new ArrayList<>();
        arguments.add(actor.tenantId());
        if (!superAdmin) addManageableArguments(arguments, actor);
        return jdbc.query("""
                        SELECT p.id, p.project_code, p.project_name
                        FROM pm_project p
                        WHERE p.tenant_id = ? AND p.deleted = 0
                        """ + scope + " ORDER BY p.project_name, p.id",
                (rs, rowNum) -> new AuditProjectOption(rs.getLong("id"), rs.getString("project_code"),
                        rs.getString("project_name")), arguments.toArray());
    }

    public boolean superAdmin(AuthUser actor) {
        return isSuperAdmin(actor);
    }

    TimeRange timeRange(LocalDate requestedStart, LocalDate requestedEnd) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime earliest = now.minusDays(VISIBLE_DAYS);
        LocalDateTime start = requestedStart == null ? earliest : requestedStart.atStartOfDay();
        if (start.isBefore(earliest)) start = earliest;
        LocalDateTime endExclusive = requestedEnd == null ? now : requestedEnd.plusDays(1).atStartOfDay();
        if (endExclusive.isAfter(now)) endExclusive = now;
        if (!start.isBefore(endExclusive)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "查询开始时间必须早于结束时间");
        }
        return new TimeRange(Timestamp.valueOf(start), Timestamp.valueOf(endExclusive));
    }

    private SqlParts operationFilters(OperationAuditQuery query, AuthUser actor, boolean superAdmin, TimeRange range) {
        StringBuilder where = new StringBuilder("WHERE l.tenant_id = ? AND l.created_at >= ? AND l.created_at < ?");
        List<Object> arguments = new ArrayList<>(List.of(actor.tenantId(), range.start(), range.endExclusive()));
        if (!superAdmin) {
            where.append(" AND EXISTS (SELECT 1 FROM pm_project p WHERE p.id = l.project_id AND p.tenant_id = l.tenant_id AND p.deleted = 0")
                    .append(manageableProjectClause("p", actor)).append(")");
            addManageableArguments(arguments, actor);
        }
        if (query.projectId() != null && query.projectId() > 0) {
            where.append(" AND l.project_id = ?");
            arguments.add(query.projectId());
        }
        if (text(query.moduleCode()) != null) {
            where.append(" AND l.module_code = ?");
            arguments.add(text(query.moduleCode()));
        }
        if (text(query.operationType()) != null) {
            where.append(" AND l.operation_type = ?");
            arguments.add(text(query.operationType()).toUpperCase());
        }
        if (query.success() != null) {
            where.append(" AND l.success = ?");
            arguments.add(query.success() ? 1 : 0);
        }
        String keyword = keyword(query.keyword());
        if (keyword != null) {
            where.append(" AND (l.operator_name LIKE ? OR l.operation_code LIKE ? OR l.request_path LIKE ?")
                    .append(" OR l.target_id LIKE ? OR l.project_name LIKE ?)");
            for (int index = 0; index < 5; index++) arguments.add(keyword);
        }
        return new SqlParts(where.toString(), arguments);
    }

    private String manageableProjectClause(String alias, AuthUser actor) {
        return " AND (" + alias + ".owner_id = ? OR EXISTS (" +
                "SELECT 1 FROM pm_project_member m " +
                "JOIN pm_project_member_role mr ON mr.member_id = m.id AND mr.tenant_id = m.tenant_id " +
                "JOIN pm_project_role r ON r.id = mr.role_id AND r.tenant_id = mr.tenant_id " +
                "WHERE m.project_id = " + alias + ".id AND m.tenant_id = " + alias + ".tenant_id " +
                "AND m.user_id = ? AND m.status = 1 AND m.deleted = 0 " +
                "AND r.project_id = " + alias + ".id AND r.role_code = 'PM' AND r.deleted = 0))";
    }

    private void addManageableArguments(List<Object> arguments, AuthUser actor) {
        arguments.add(actor.id());
        arguments.add(actor.id());
    }

    private void requireManageableProject(AuthUser actor) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pm_project p
                WHERE p.tenant_id = ? AND p.deleted = 0
                """ + manageableProjectClause("p", actor), Integer.class,
                actor.tenantId(), actor.id(), actor.id());
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "没有审计日志查看权限");
        }
    }

    private void requireSuperAdmin(AuthUser actor) {
        if (!isSuperAdmin(actor)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有超级管理员可以查看登录日志");
        }
    }

    private boolean isSuperAdmin(AuthUser actor) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_user_role ur
                JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id
                WHERE ur.user_id = ? AND ur.tenant_id = ? AND r.role_code = 'SUPER_ADMIN'
                  AND r.status = 1 AND r.deleted = 0
                """, Integer.class, actor.id(), actor.tenantId());
        return count != null && count > 0;
    }

    private long count(String sql, List<Object> arguments) {
        Long result = jdbc.queryForObject(sql, Long.class, arguments.toArray());
        return result == null ? 0 : result;
    }

    private OperationAuditRecord mapOperation(ResultSet rs, int rowNum) throws SQLException {
        Long projectValue = rs.getObject("project_id", Long.class);
        Integer statusValue = rs.getObject("http_status", Integer.class);
        return new OperationAuditRecord(
                rs.getLong("id"), rs.getLong("operator_id"), rs.getString("operator_name"),
                rs.getString("operation_code"), rs.getString("module_code"), rs.getString("module_name"),
                rs.getString("operation_type"), rs.getString("target_type"), rs.getString("target_id"),
                projectValue, rs.getString("project_name"), rs.getString("request_method"),
                rs.getString("request_path"), rs.getBoolean("success"), statusValue,
                rs.getLong("duration_ms"), rs.getString("error_message"), rs.getString("client_ip"),
                rs.getString("user_agent"), changedFields(rs.getString("changed_fields")),
                rs.getString("trace_id"), rs.getTimestamp("created_at").toLocalDateTime());
    }

    private LoginAuditRecord mapLogin(ResultSet rs, int rowNum) throws SQLException {
        return new LoginAuditRecord(rs.getLong("id"), rs.getString("username"), rs.getBoolean("success"),
                rs.getString("failure_reason"), rs.getString("client_ip"), rs.getString("user_agent"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }

    private List<String> changedFields(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String keyword(String value) {
        String normalized = text(value);
        return normalized == null ? null : "%" + normalized.substring(0, Math.min(normalized.length(), 100)) + "%";
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    record TimeRange(Timestamp start, Timestamp endExclusive) {
    }

    private record SqlParts(String where, List<Object> arguments) {
    }
}
