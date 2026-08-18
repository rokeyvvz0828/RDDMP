package com.ccb.requirement.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ccb.requirement.support.RequirementIds;
import com.ccb.requirement.support.RequirementSql;
import com.ccb.requirement.support.RequirementValues;

/** 新建项目需求差异清单：生命周期状态机（待评审→评审中→已评审/已退回）、数据范围与改动记录。 */
@Service
public class RequirementDifferenceService {
    private static final List<String> DIFF_FIELDS = List.of(
            "seq_no", "business_conglomerate", "business_section", "business_group",
            "requirement_no", "category", "name", "system_id", "jinke_practice",
            "difference_type", "monshang_practice", "difference_desc", "monshang_dept",
            "monshang_analyst", "jinke_analyst", "adapt_mode", "handle_status", "coord_group",
            "solution", "is_special", "decision_level", "decision_conclusion",
            "monshang_confirm_dept", "jinke_confirmer", "dev_status", "test_status");

    private static final String SELECT_COLUMNS = """
            id, project_id, seq_no, business_conglomerate, business_section, business_group,
            requirement_no, category, name, system_id, jinke_practice, difference_type,
            monshang_practice, difference_desc, monshang_dept, monshang_analyst, jinke_analyst,
            adapt_mode, handle_status, coord_group, solution, is_special, decision_level,
            decision_conclusion, monshang_confirm_dept, jinke_confirmer, review_status,
            review_comment, reviewed_by, reviewed_at, workflow_instance_id, dev_status,
            test_status, baseline_id, source, import_batch_id, created_at, updated_at
            """;

    private final JdbcTemplate jdbc;
    private final RequirementChangeLogService changeLog;
    private final RequirementSecurityService security;
    private final RequirementSystemService systemService;

    public RequirementDifferenceService(JdbcTemplate jdbc, RequirementChangeLogService changeLog,
                                        RequirementSecurityService security,
                                        RequirementSystemService systemService) {
        this.jdbc = jdbc;
        this.changeLog = changeLog;
        this.security = security;
        this.systemService = systemService;
    }

    public PageResult<Map<String, Object>> list(long projectId, String reviewStatus, String devStatus,
                                                String testStatus, String keyword, PageQuery query, AuthUser user) {
        security.requireProjectAccess(user, projectId);
        StringBuilder where = new StringBuilder(" WHERE tenant_id = ? AND project_id = ? AND deleted = 0");
        List<Object> params = new ArrayList<>(List.of(user.tenantId(), projectId));
        appendLike(where, params, "review_status", reviewStatus);
        appendLike(where, params, "dev_status", devStatus);
        appendLike(where, params, "test_status", testStatus);
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (name LIKE ? OR requirement_no LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM req_difference" + where, Long.class, params.toArray());
        params.add(query.size());
        params.add((query.page() - 1) * query.size());
        List<Map<String, Object>> records = jdbc.queryForList(
                "SELECT " + SELECT_COLUMNS + " FROM req_difference" + where
                        + " ORDER BY seq_no, id LIMIT ? OFFSET ?", params.toArray());
        return new PageResult<>(records, total == null ? 0 : total, query.page(), query.size());
    }

    public Map<String, Object> get(long id, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireProjectAccess(user, ((Number) row.get("project_id")).longValue());
        return row;
    }

    @Transactional
    public Map<String, Object> create(long projectId, Map<String, Object> body, AuthUser user) {
        security.requireProjectAccess(user, projectId);
        Map<String, Object> values = normalized(body);
        validate(values, user);
        values.putIfAbsent("name", null);
        String name = RequirementValues.text(values, "name");
        if (name == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "差异点名称不能为空");
        }
        long id = RequirementIds.next();
        values.put("id", id);
        values.put("tenant_id", user.tenantId());
        values.put("project_id", projectId);
        values.putIfAbsent("seq_no", nextSeq(projectId, user));
        values.putIfAbsent("review_status", "待评审");
        values.putIfAbsent("dev_status", "未开始");
        values.putIfAbsent("test_status", "未开始");
        values.putIfAbsent("source", "ONLINE");
        values.put("created_by", user.id());
        values.put("deleted", 0);
        RequirementSql.insert(jdbc, "req_difference", values);
        changeLog.recordCreate("NEW_PROJECT_DIFF", id, values, user, "ONLINE");
        return get(id, user);
    }

    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> before = row(id, user);
        requireEditable(before);
        security.requireProjectAccess(user, ((Number) before.get("project_id")).longValue());
        Map<String, Object> values = normalized(body);
        validate(values, user);
        if (values.isEmpty()) {
            return before;
        }
        values.put("updated_by", user.id());
        RequirementSql.update(jdbc, "req_difference", id, user.tenantId(), values);
        Map<String, Object> after = row(id, user);
        changeLog.recordFields("NEW_PROJECT_DIFF", id, "UPDATE", before, after, user, "ONLINE");
        return after;
    }

    @Transactional
    public void delete(long id, AuthUser user) {
        Map<String, Object> row = row(id, user);
        requireEditable(row);
        security.requireProjectAccess(user, ((Number) row.get("project_id")).longValue());
        jdbc.update("UPDATE req_difference SET deleted = 1, updated_by = ? WHERE tenant_id = ? AND id = ?",
                user.id(), user.tenantId(), id);
        changeLog.record("NEW_PROJECT_DIFF", id, "DELETE", "deleted", "0", "1", user, "ONLINE");
    }

    /**
     * 提交评审：待评审/已退回 → 评审中。
     * 审批流（requirement.diff.review）接入后，此处将启动平台流程并按审批结果回写；
     * 当前审批引擎未接入，先完成业务状态流转与留痕（workflow_instance_id 已预留）。
     */
    @Transactional
    public Map<String, Object> submitReview(long id, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireProjectAccess(user, ((Number) row.get("project_id")).longValue());
        String status = String.valueOf(row.get("review_status"));
        if (!"待评审".equals(status) && !"已退回".equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可提交评审：" + status);
        }
        jdbc.update("UPDATE req_difference SET review_status = '评审中', review_comment = NULL, updated_by = ? WHERE tenant_id = ? AND id = ?",
                user.id(), user.tenantId(), id);
        changeLog.record("NEW_PROJECT_DIFF", id, "SUBMIT_REVIEW", "review_status", status, "评审中", user, "ONLINE");
        return get(id, user);
    }

    /**
     * 评审结果处理（临时模拟审批回调）：评审中 → 已评审（锁定）或已退回。
     * 仅统筹/管理员可执行；审批流接入后由流程生命周期事件幂等回写替代。
     */
    @Transactional
    public Map<String, Object> reviewResult(long id, String decision, String comment, AuthUser user) {
        if (!security.isAdmin(user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅统筹/管理员可执行评审处理");
        }
        Map<String, Object> row = row(id, user);
        String status = String.valueOf(row.get("review_status"));
        if (!"评审中".equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅评审中的差异可处理评审结果");
        }
        if ("APPROVE".equalsIgnoreCase(decision)) {
            jdbc.update("UPDATE req_difference SET review_status = '已评审', review_comment = ?, reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP, updated_by = ? WHERE tenant_id = ? AND id = ?",
                    comment, user.id(), user.id(), user.tenantId(), id);
            changeLog.record("NEW_PROJECT_DIFF", id, "REVIEW_PASS", "review_status", status, "已评审", user, "ONLINE");
        } else if ("RETURN".equalsIgnoreCase(decision)) {
            jdbc.update("UPDATE req_difference SET review_status = '已退回', review_comment = ?, reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP, updated_by = ? WHERE tenant_id = ? AND id = ?",
                    comment, user.id(), user.id(), user.tenantId(), id);
            changeLog.record("NEW_PROJECT_DIFF", id, "REVIEW_RETURN", "review_status", status, "已退回", user, "ONLINE");
            changeLog.record("NEW_PROJECT_DIFF", id, "REVIEW_RETURN", "review_comment", null, comment, user, "ONLINE");
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评审结论必须为 APPROVE 或 RETURN");
        }
        return get(id, user);
    }

    public List<Map<String, Object>> changes(long id, AuthUser user) {
        get(id, user);
        return changeLog.list("NEW_PROJECT_DIFF", id, user);
    }

    Map<String, Object> row(long id, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + SELECT_COLUMNS + " FROM req_difference WHERE tenant_id = ? AND id = ? AND deleted = 0",
                user.tenantId(), id);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "差异不存在");
        }
        return rows.get(0);
    }

    private void requireEditable(Map<String, Object> row) {
        String status = String.valueOf(row.get("review_status"));
        Object baselineId = row.get("baseline_id");
        if (!"待评审".equals(status) && !"已退回".equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "已提交评审或已评审的差异不可修改：" + status);
        }
        if (baselineId != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "已纳入基线的差异不可修改");
        }
    }

    private void validate(Map<String, Object> values, AuthUser user) {
        RequirementValues.requireOption("categories", RequirementValues.text(values, "category"));
        RequirementValues.requireOption("differenceTypes", RequirementValues.text(values, "difference_type"));
        RequirementValues.requireOption("adaptModes", RequirementValues.text(values, "adapt_mode"));
        RequirementValues.requireOption("handleStatuses", RequirementValues.text(values, "handle_status"));
        RequirementValues.requireOption("decisionLevels", RequirementValues.text(values, "decision_level"));
        RequirementValues.requireOption("yesNo", RequirementValues.text(values, "is_special"));
        RequirementValues.requireOption("devStatuses", RequirementValues.text(values, "dev_status"));
        RequirementValues.requireOption("testStatuses", RequirementValues.text(values, "test_status"));
        Object systemId = values.get("system_id");
        if (systemId != null) {
            long resolved = RequirementValues.intOf(systemId, 0);
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM req_system WHERE tenant_id = ? AND id = ? AND deleted = 0",
                    Integer.class, user.tenantId(), resolved);
            if (resolved > 0 && (count == null || count == 0)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "涉及系统不存在");
            }
        }
    }

    private Map<String, Object> normalized(Map<String, Object> body) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : DIFF_FIELDS) {
            Object value = body.get(field);
            if (value == null) {
                continue;
            }
            if ("seq_no".equals(field)) {
                values.put(field, RequirementValues.intOf(value, 0));
            } else {
                values.put(field, value);
            }
        }
        return values;
    }

    private int nextSeq(long projectId, AuthUser user) {
        Long max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(seq_no), 0) FROM req_difference WHERE tenant_id = ? AND project_id = ? AND deleted = 0",
                Long.class, user.tenantId(), projectId);
        return max == null ? 1 : max.intValue() + 1;
    }

    private void appendLike(StringBuilder where, List<Object> params, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(RequirementSql.quote(column)).append(" = ?");
            params.add(value);
        }
    }
}
