package com.ccb.system.audit;

import com.ccb.common.api.PageQuery;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.SystemPage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OperationAuditService {
    static final int VISIBLE_DAYS = 30;

    private final OperationAuditRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public OperationAuditService(OperationAuditRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, Clock.systemDefaultZone());
    }

    OperationAuditService(OperationAuditRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public SystemPage<OperationAuditRecord> operations(OperationAuditQuery query, AuthUser actor) {
        boolean superAdmin = isSuperAdmin(actor);
        if (!superAdmin) requireManageableProject(actor);
        TimeRange range = timeRange(query.startDate(), query.endDate());
        PageQuery page = new PageQuery(query.page(), query.size());
        Map<String, Object> params = operationParams(query, actor, superAdmin, range, page);
        long total = repository.countOperations(params);
        List<OperationAuditRecord> records = repository.operations(params).stream().map(this::mapOperation).toList();
        return new SystemPage<>(records, total, page.page(), page.size());
    }

    public SystemPage<LoginAuditRecord> logins(LoginAuditQuery query, AuthUser actor) {
        requireSuperAdmin(actor);
        TimeRange range = timeRange(query.startDate(), query.endDate());
        PageQuery page = new PageQuery(query.page(), query.size());
        Map<String, Object> params = params("tenantId", actor.tenantId(), "start", range.start(), "endExclusive", range.endExclusive(),
                "success", query.success(), "keyword", keyword(query.keyword()), "size", page.size(), "offset", (page.page() - 1) * page.size());
        long total = repository.countLogins(params);
        List<LoginAuditRecord> records = repository.logins(params).stream().map(this::mapLogin).toList();
        return new SystemPage<>(records, total, page.page(), page.size());
    }

    public List<AuditProjectOption> projects(AuthUser actor) {
        boolean superAdmin = isSuperAdmin(actor);
        if (!superAdmin) requireManageableProject(actor);
        return repository.auditProjects(params("tenantId", actor.tenantId(), "actorId", actor.id(), "superAdmin", superAdmin)).stream()
                .map(row -> new AuditProjectOption(number(row, "id"), text(row.get("project_code")), text(row.get("project_name")))).toList();
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

    private void requireManageableProject(AuthUser actor) {
        if (repository.manageableProjectCount(params("tenantId", actor.tenantId(), "actorId", actor.id())) == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "没有审计日志查看权限");
        }
    }

    private void requireSuperAdmin(AuthUser actor) {
        if (!isSuperAdmin(actor)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有超级管理员可以查看登录日志");
        }
    }

    private boolean isSuperAdmin(AuthUser actor) {
        return repository.superAdminCount(params("actorId", actor.id(), "tenantId", actor.tenantId())) > 0;
    }

    private Map<String, Object> operationParams(OperationAuditQuery query, AuthUser actor, boolean superAdmin,
                                                 TimeRange range, PageQuery page) {
        return params("tenantId", actor.tenantId(), "actorId", actor.id(), "superAdmin", superAdmin,
                "start", range.start(), "endExclusive", range.endExclusive(), "projectId", query.projectId(),
                "moduleCode", text(query.moduleCode()), "operationType", text(query.operationType()) == null ? null : text(query.operationType()).toUpperCase(),
                "success", query.success(), "keyword", keyword(query.keyword()), "size", page.size(),
                "offset", (page.page() - 1) * page.size());
    }

    private Map<String, Object> params(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) result.put(String.valueOf(entries[index]), entries[index + 1]);
        return result;
    }

    private long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private OperationAuditRecord mapOperation(Map<String, Object> row) {
        Long projectValue = row.get("project_id") instanceof Number number ? number.longValue() : null;
        Integer statusValue = row.get("http_status") instanceof Number number ? number.intValue() : null;
        return new OperationAuditRecord(
                number(row, "id"), number(row, "operator_id"), text(row.get("operator_name")), text(row.get("operation_code")), text(row.get("module_code")), text(row.get("module_name")), text(row.get("operation_type")), text(row.get("target_type")), text(row.get("target_id")), projectValue, text(row.get("project_name")), text(row.get("request_method")), text(row.get("request_path")), Boolean.TRUE.equals(row.get("success")) || Integer.valueOf(1).equals(row.get("success")), statusValue, number(row, "duration_ms"), text(row.get("error_message")), text(row.get("client_ip")), text(row.get("user_agent")), changedFields(text(row.get("changed_fields"))), text(row.get("trace_id")), ((Timestamp) row.get("created_at")).toLocalDateTime());
    }

    private LoginAuditRecord mapLogin(Map<String, Object> row) {
        return new LoginAuditRecord(number(row, "id"), text(row.get("username")), Boolean.TRUE.equals(row.get("success")) || Integer.valueOf(1).equals(row.get("success")), text(row.get("failure_reason")), text(row.get("client_ip")), text(row.get("user_agent")), ((Timestamp) row.get("created_at")).toLocalDateTime());
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

}
