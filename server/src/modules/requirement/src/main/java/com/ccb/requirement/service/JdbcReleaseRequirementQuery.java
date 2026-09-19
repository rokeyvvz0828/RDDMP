package com.ccb.requirement.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.requirement.integration.ReleaseRequirementQuery;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class JdbcReleaseRequirementQuery implements ReleaseRequirementQuery {
    private static final String COLUMNS =
            "SELECT r.id, r.requirement_no, r.name AS requirement_name, l.requirement_status";
    private static final String ACTIVE = " FROM req_requirement r"
            + " JOIN req_legacy_detail l ON l.requirement_id = r.id AND l.tenant_id = r.tenant_id"
            + " WHERE r.tenant_id = ? AND r.project_id = ?"
            + " AND r.deleted = 0 AND r.requirement_kind = 'LEGACY'"
            + " AND COALESCE(l.requirement_status, '') <> '需求终止'";
    private static final int MAX_SELECTION = 100;

    private final JdbcTemplate jdbc;

    public JdbcReleaseRequirementQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<RequirementRef> searchActive(AuthUser actor, String projectRef, PageQuery page, String keyword) {
        requireActor(actor);
        long projectId = projectId(actor, projectRef);
        PageQuery normalizedPage = page == null ? new PageQuery(1, 100) : page;
        StringBuilder filter = new StringBuilder(ACTIVE);
        List<Object> args = new ArrayList<>(List.of(actor.tenantId(), projectId));
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + escapeLike(keyword.trim()) + "%";
            filter.append(" AND (r.requirement_no LIKE ? ESCAPE '\\\\' OR r.name LIKE ? ESCAPE '\\\\')");
            args.add(value);
            args.add(value);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + filter, Long.class, args.toArray());
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(normalizedPage.size());
        listArgs.add(Math.multiplyExact(normalizedPage.page() - 1, normalizedPage.size()));
        List<RequirementRef> records = jdbc.queryForList(
                        COLUMNS + filter + " ORDER BY r.updated_at DESC, r.id DESC LIMIT ? OFFSET ?",
                        listArgs.toArray())
                .stream().map(JdbcReleaseRequirementQuery::requirement).toList();
        return new PageResult<>(records, total == null ? 0 : total, normalizedPage.page(), normalizedPage.size());
    }

    @Override
    public Optional<List<RequirementRef>> resolveActive(AuthUser actor, String projectRef,
                                                        Collection<String> numbers) {
        requireActor(actor);
        long projectId = projectId(actor, projectRef);
        Set<String> normalized = normalizeNumbers(numbers);
        if (normalized.isEmpty()) {
            return Optional.of(List.of());
        }
        List<Object> args = new ArrayList<>(List.of(actor.tenantId(), projectId));
        args.addAll(normalized);
        String placeholders = String.join(", ", normalized.stream().map(value -> "?").toList());
        List<RequirementRef> records = jdbc.queryForList(
                        COLUMNS + ACTIVE + " AND r.requirement_no IN (" + placeholders + ") ORDER BY r.id",
                        args.toArray())
                .stream().map(JdbcReleaseRequirementQuery::requirement).toList();
        Set<String> resolvedNumbers = new LinkedHashSet<>();
        records.forEach(item -> resolvedNumbers.add(item.number()));
        return records.size() == normalized.size() && resolvedNumbers.equals(normalized)
                ? Optional.of(List.copyOf(records))
                : Optional.empty();
    }

    private long projectId(AuthUser actor, String projectRef) {
        if (projectRef == null || projectRef.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择项目");
        }
        List<Map<String, Object>> projects = jdbc.queryForList(
                "SELECT id FROM pm_project WHERE tenant_id = ? AND project_code = ? AND deleted = 0",
                actor.tenantId(), projectRef.trim());
        if (projects.size() != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "需求项目编码未匹配或存在歧义，请维护项目关联");
        }
        return number(projects.get(0), "id");
    }

    private static Set<String> normalizeNumbers(Collection<String> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            return Set.of();
        }
        if (numbers.size() > MAX_SELECTION) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单张申请最多关联 100 个需求");
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String number : numbers) {
            String value = number == null ? "" : number.trim();
            if (value.isEmpty() || value.length() > 128) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "需求编号无效");
            }
            result.add(value);
        }
        return result;
    }

    private static RequirementRef requirement(Map<String, Object> row) {
        return new RequirementRef(number(row, "id"), text(row, "requirement_no"),
                text(row, "requirement_name"), text(row, "requirement_status"));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static String text(Map<String, Object> row, String key) {
        return Objects.toString(row.get(key), "");
    }

    private static void requireActor(AuthUser actor) {
        if (actor == null || !actor.enabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "用户不可访问需求主数据");
        }
    }
}
