package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ccb.requirement.support.RequirementIds;
import com.ccb.requirement.support.RequirementSql;
import com.ccb.requirement.support.RequirementValues;

/** 新建项目主数据与项目组成员数据范围。 */
@Service
public class RequirementProjectService {
    private static final List<String> PROJECT_FIELDS = List.of(
            "project_code", "project_name", "project_type", "start_time", "status", "description");

    private final JdbcTemplate jdbc;
    private final RequirementChangeLogService changeLog;
    private final RequirementSecurityService security;

    public RequirementProjectService(JdbcTemplate jdbc, RequirementChangeLogService changeLog,
                                     RequirementSecurityService security) {
        this.jdbc = jdbc;
        this.changeLog = changeLog;
        this.security = security;
    }

    public List<Map<String, Object>> list(String keyword, AuthUser user) {
        String sql = """
                SELECT p.id, p.project_code, p.project_name, p.project_type, p.start_time, p.status,
                       p.description, p.created_at, p.updated_at,
                       (SELECT COUNT(*) FROM req_difference d WHERE d.project_id = p.id AND d.tenant_id = p.tenant_id AND d.deleted = 0) AS difference_count,
                       (SELECT COUNT(*) FROM req_difference d WHERE d.project_id = p.id AND d.tenant_id = p.tenant_id AND d.deleted = 0 AND d.review_status = '已评审') AS reviewed_count
                FROM req_project p
                WHERE p.tenant_id = ? AND p.deleted = 0
                """;
        List<Object> params = new java.util.ArrayList<>(List.of(user.tenantId()));
        if (keyword != null && !keyword.isBlank()) {
            sql += " AND (p.project_code LIKE ? OR p.project_name LIKE ?)";
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        if (!security.isAdmin(user)) {
            sql += " AND EXISTS (SELECT 1 FROM req_project_member pm WHERE pm.project_id = p.id AND pm.user_id = ? AND pm.tenant_id = p.tenant_id AND pm.deleted = 0)";
            params.add(user.id());
        }
        sql += " ORDER BY p.created_at DESC, p.id DESC";
        return jdbc.queryForList(sql, params.toArray());
    }

    public Map<String, Object> get(long id, AuthUser user) {
        security.requireProjectAccess(user, id);
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT p.id, p.project_code, p.project_name, p.project_type, p.start_time, p.status,
                       p.description, p.created_at, p.updated_at,
                       (SELECT COUNT(*) FROM req_difference d WHERE d.project_id = p.id AND d.tenant_id = p.tenant_id AND d.deleted = 0) AS difference_count,
                       (SELECT COUNT(*) FROM req_difference d WHERE d.project_id = p.id AND d.tenant_id = p.tenant_id AND d.deleted = 0 AND d.review_status = '已评审') AS reviewed_count
                FROM req_project p WHERE p.tenant_id = ? AND p.id = ? AND p.deleted = 0
                """, user.tenantId(), id);
        if (row == null || row.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目不存在");
        }
        return row;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        String code = RequirementValues.requireText(body, "project_code", "项目编码不能为空");
        RequirementValues.requireText(body, "project_name", "项目名称不能为空");
        Integer duplicate = jdbc.queryForObject(
                "SELECT COUNT(*) FROM req_project WHERE tenant_id = ? AND project_code = ? AND deleted = 0",
                Integer.class, user.tenantId(), code);
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "项目编码已存在：" + code);
        }
        RequirementValues.requireOption("projectTypes", RequirementValues.text(body, "project_type"));
        RequirementValues.requireOption("projectStatuses", RequirementValues.text(body, "status"));
        long id = RequirementIds.next();
        Map<String, Object> values = normalized(body);
        values.put("id", id);
        values.put("tenant_id", user.tenantId());
        values.putIfAbsent("project_type", "0~1 新建");
        values.putIfAbsent("status", "进行中");
        values.put("created_by", user.id());
        values.put("deleted", 0);
        RequirementSql.insert(jdbc, "req_project", values);
        changeLog.recordCreate("PROJECT", id, values, user, "ONLINE");
        return get(id, user);
    }

    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> before = get(id, user);
        RequirementValues.requireOption("projectTypes", RequirementValues.text(body, "project_type"));
        RequirementValues.requireOption("projectStatuses", RequirementValues.text(body, "status"));
        Map<String, Object> changes = normalized(body);
        changes.remove("project_code");
        if (changes.isEmpty()) {
            return before;
        }
        changes.put("updated_by", user.id());
        RequirementSql.update(jdbc, "req_project", id, user.tenantId(), changes);
        Map<String, Object> after = get(id, user);
        changeLog.recordFields("PROJECT", id, "UPDATE", before, after, user, "ONLINE");
        return after;
    }

    @Transactional
    public void delete(long id, AuthUser user) {
        Map<String, Object> row = get(id, user);
        Integer differenceCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM req_difference WHERE tenant_id = ? AND project_id = ? AND deleted = 0",
                Integer.class, user.tenantId(), id);
        if (differenceCount != null && differenceCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "项目下存在差异数据，不能删除");
        }
        jdbc.update("UPDATE req_project SET deleted = 1, updated_by = ? WHERE tenant_id = ? AND id = ?",
                user.id(), user.tenantId(), id);
        changeLog.record("PROJECT", id, "DELETE", "deleted", "0", "1", user, "ONLINE");
    }

    public List<Map<String, Object>> members(long projectId, AuthUser user) {
        security.requireProjectAccess(user, projectId);
        return jdbc.queryForList("""
                SELECT pm.id, pm.project_id, pm.user_id, pm.member_role, u.username, u.display_name
                FROM req_project_member pm
                LEFT JOIN sys_user u ON u.id = pm.user_id AND u.tenant_id = pm.tenant_id
                WHERE pm.tenant_id = ? AND pm.project_id = ? AND pm.deleted = 0
                ORDER BY pm.created_at, pm.id
                """, user.tenantId(), projectId);
    }

    @Transactional
    public Map<String, Object> addMember(long projectId, Map<String, Object> body, AuthUser user) {
        if (!security.isAdmin(user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅统筹/管理员可维护项目成员");
        }
        get(projectId, user);
        long userId = RequirementValues.intOf(body.get("userId"), 0);
        String role = RequirementValues.text(body, "memberRole");
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "成员用户不能为空");
        }
        Integer userCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE tenant_id = ? AND id = ? AND deleted = 0",
                Integer.class, user.tenantId(), userId);
        if (userCount == null || userCount == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "成员用户不存在");
        }
        Integer duplicate = jdbc.queryForObject(
                "SELECT COUNT(*) FROM req_project_member WHERE tenant_id = ? AND project_id = ? AND user_id = ? AND deleted = 0",
                Integer.class, user.tenantId(), projectId, userId);
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该用户已是项目成员");
        }
        long id = RequirementIds.next();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("tenant_id", user.tenantId());
        values.put("project_id", projectId);
        values.put("user_id", userId);
        values.put("member_role", role == null || role.isBlank() ? "MEMBER" : role);
        values.put("created_by", user.id());
        values.put("deleted", 0);
        RequirementSql.insert(jdbc, "req_project_member", values);
        return jdbc.queryForMap("""
                SELECT pm.id, pm.project_id, pm.user_id, pm.member_role, u.username, u.display_name
                FROM req_project_member pm
                LEFT JOIN sys_user u ON u.id = pm.user_id AND u.tenant_id = pm.tenant_id
                WHERE pm.tenant_id = ? AND pm.id = ?
                """, user.tenantId(), id);
    }

    @Transactional
    public void removeMember(long memberId, AuthUser user) {
        if (!security.isAdmin(user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅统筹/管理员可维护项目成员");
        }
        jdbc.update("UPDATE req_project_member SET deleted = 1 WHERE tenant_id = ? AND id = ?",
                user.tenantId(), memberId);
    }

    private Map<String, Object> normalized(Map<String, Object> body) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : PROJECT_FIELDS) {
            Object value = body.get(field);
            if (value == null) {
                continue;
            }
            if ("start_time".equals(field)) {
                values.put(field, RequirementValues.date(value));
            } else {
                values.put(field, value);
            }
        }
        return values;
    }
}
