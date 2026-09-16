package com.ccb.requirement.service;

import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.requirement.integration.RequirementDevelopmentQuery;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

@Service
@Transactional(readOnly = true)
public class JdbcRequirementDevelopmentQuery implements RequirementDevelopmentQuery {
    private static final String COLUMNS = "SELECT r.id, r.project_id, r.requirement_no, r.requirement_name, r.content_summary";
    private static final String ACTIVE = " FROM req_legacy_requirement r WHERE r.tenant_id = ? AND r.project_id = ?"
            + " AND r.deleted = 0 AND COALESCE(r.requirement_status, '') <> '需求终止'";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;

    public JdbcRequirementDevelopmentQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public PageResult<SourceRequirement> search(AuthUser actor, Query query) {
        requireActor(actor);
        if (query.systemCodes().isEmpty()) {
            return new PageResult<>(List.of(), 0, query.page().page(), query.page().size());
        }
        if (query.systemCodes().size() > 1000 || query.systemCodes().stream().anyMatch(String::isBlank)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "授权系统范围无效");
        }
        long projectId = projectId(actor, query.projectRef());
        var codes = new TreeSet<>(query.systemCodes());
        String marks = placeholders(codes.size());
        StringBuilder where = new StringBuilder(ACTIVE).append("""
                 AND (EXISTS (SELECT 1 FROM req_legacy_system_item s
                     WHERE s.tenant_id = r.tenant_id AND s.requirement_id = r.id AND s.deleted = 0
                     AND s.system_code IN (%s))
                 OR EXISTS (SELECT 1 FROM req_coordination_item c
                     WHERE c.tenant_id = r.tenant_id AND c.requirement_id = r.id AND c.deleted = 0
                     AND c.system_code IN (%s)))
                """.formatted(marks, marks));
        List<Object> args = new ArrayList<>(List.of(actor.tenantId(), projectId));
        args.addAll(codes);
        args.addAll(codes);
        if (query.keyword() != null && !query.keyword().isBlank()) {
            where.append(" AND (r.requirement_no LIKE ? OR r.requirement_name LIKE ?)");
            String keyword = "%" + query.keyword().trim() + "%";
            args.add(keyword);
            args.add(keyword);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        args.add(query.page().size());
        args.add(Math.multiplyExact(query.page().page() - 1, query.page().size()));
        var rows = jdbc.queryForList(COLUMNS + where + " ORDER BY r.updated_at DESC, r.id DESC LIMIT ? OFFSET ?", args.toArray());
        return new PageResult<>(project(actor, rows), total == null ? 0 : total, query.page().page(), query.page().size());
    }

    @Override
    public Optional<SourceRequirement> find(AuthUser actor, String projectRef, long requirementId) {
        requireActor(actor);
        long projectId = projectId(actor, projectRef);
        var rows = jdbc.queryForList(COLUMNS + ACTIVE + " AND r.id = ?", actor.tenantId(), projectId, requirementId);
        return project(actor, rows).stream().findFirst();
    }

    private long projectId(AuthUser actor, String projectRef) {
        if (projectRef == null || projectRef.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择项目");
        }
        var projects = jdbc.queryForList("SELECT id FROM req_project WHERE tenant_id = ? AND project_code = ? AND deleted = 0",
                actor.tenantId(), projectRef.trim());
        if (projects.size() != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "需求项目编码未匹配或存在歧义，请维护项目关联");
        }
        return number(projects.get(0), "id");
    }

    private List<SourceRequirement> project(AuthUser actor, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return List.of();
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            args.add(actor.tenantId());
            rows.forEach(r -> args.add(number(r, "id")));
        }
        String marks = placeholders(rows.size());
        var links = jdbc.queryForList("""
                SELECT requirement_id, system_code,
                    CASE system_role WHEN '主责' THEN 'LEAD' ELSE 'UNKNOWN' END AS role, owner_user_id
                FROM req_legacy_system_item WHERE tenant_id = ? AND deleted = 0 AND requirement_id IN (%s)
                UNION ALL
                SELECT requirement_id, system_code,
                    CASE item_type WHEN '改造' THEN 'CHANGE' WHEN '测试' THEN 'TEST' ELSE 'UNKNOWN' END AS role, owner_user_id
                FROM req_coordination_item WHERE tenant_id = ? AND deleted = 0 AND requirement_id IN (%s)
                """.formatted(marks, marks), args.toArray());
        Map<Long, Map<String, SystemRoles>> grouped = new TreeMap<>();
        for (var link : links) {
            String code = text(link, "system_code");
            if (code == null || code.isBlank()) throw invalidAssociation();
            Role role;
            try { role = Role.valueOf(text(link, "role")); }
            catch (IllegalArgumentException | NullPointerException ex) { throw invalidAssociation(); }
            var system = grouped.computeIfAbsent(number(link, "requirement_id"), key -> new TreeMap<>())
                    .computeIfAbsent(code.trim(), key -> new SystemRoles());
            system.roles.add(role);
            if (link.get("owner_user_id") instanceof Number owner) system.owners.add(owner.longValue());
        }
        List<SourceRequirement> result = new ArrayList<>();
        for (var row : rows) {
            long id = number(row, "id");
            var systems = grouped.getOrDefault(id, Map.of());
            List<SourceSystem> refs = systems.entrySet().stream().map(e -> new SourceSystem(e.getKey(), e.getValue().roles,
                    e.getValue().owners.size() == 1 ? e.getValue().owners.first() : null)).toList();
            // 规范化集合后取摘要，不把查询行顺序、重复关联或数据库时间当作业务版本。
            List<CanonicalSystem> canonicalSystems = systems.entrySet().stream().map(e -> new CanonicalSystem(e.getKey(),
                    e.getValue().roles.stream().map(Enum::name).sorted().toList(), List.copyOf(e.getValue().owners))).toList();
            String number = text(row, "requirement_no");
            String name = text(row, "requirement_name");
            String summary = text(row, "content_summary");
            long projectId = number(row, "project_id");
            String revision = digest(new CanonicalSource(id, projectId, number, name, summary, canonicalSystems));
            result.add(new SourceRequirement(id, number, name, summary, projectId, revision, true, refs));
        }
        return List.copyOf(result);
    }

    private static String digest(CanonicalSource source) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(source))); }
        catch (NoSuchAlgorithmException | JsonProcessingException ex) { throw new IllegalStateException("无法生成来源版本", ex); }
    }

    private static void requireActor(AuthUser actor) {
        if (actor == null || !actor.enabled()) throw new BusinessException(ErrorCode.FORBIDDEN, "用户不可访问来源");
    }

    private static BusinessException invalidAssociation() {
        return new BusinessException(ErrorCode.CONFLICT, "需求系统关联缺少明确编码或角色，请维护来源数据");
    }

    private static String placeholders(int count) { return String.join(",", java.util.Collections.nCopies(count, "?")); }
    private static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static String text(Map<String, Object> row, String key) { return Objects.toString(row.get(key), null); }
    private static class SystemRoles {
        final EnumSet<Role> roles = EnumSet.noneOf(Role.class);
        final TreeSet<Long> owners = new TreeSet<>();
    }
    private record CanonicalSystem(String code, List<String> roles, List<Long> owners) {}
    private record CanonicalSource(long id, long projectId, String number, String name, String summary,
                                   List<CanonicalSystem> systems) {}
}
