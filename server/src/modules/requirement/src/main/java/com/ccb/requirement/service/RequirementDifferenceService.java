package com.ccb.requirement.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.service.WorkflowService;
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
    private final WorkflowService workflowService;

    public RequirementDifferenceService(JdbcTemplate jdbc, RequirementChangeLogService changeLog,
                                        RequirementSecurityService security,
                                        RequirementSystemService systemService,
                                        WorkflowService workflowService) {
        this.jdbc = jdbc;
        this.changeLog = changeLog;
        this.security = security;
        this.systemService = systemService;
        this.workflowService = workflowService;
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
     * 提交评审：待评审/已退回 → 评审中，并启动审批流（requirement.diff.review）。
     * 业务字段先改为"评审中"并锁定；审批人在工作流中心 APPROVE/REJECT 后，
     * 由 RequirementWorkflowListener 接收 WorkflowInstanceCompletedEvent 幂等回写"已评审/已退回"。
     */
    @Transactional
    public Map<String, Object> submitReview(long id, List<Long> approverIds, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireProjectAccess(user, ((Number) row.get("project_id")).longValue());
        String status = String.valueOf(row.get("review_status"));
        if (!"待评审".equals(status) && !"已退回".equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可提交评审：" + status);
        }
        if (approverIds == null || approverIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择审批人");
        }
        // 启动审批流，businessKey 编码业务单号；variables 携带 approverIds 供 VARIABLE 审批节点解析
        long definitionId = lookupDefinitionId(user.tenantId(), "requirement.diff.review");
        String businessKey = "req-diff:" + id;
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("differenceId", id);
        variables.put("submitterId", user.id());
        variables.put("submitterName", user.displayName());
        variables.put("approverIds", approverIds);
        Map<String, Object> instance = workflowService.start(definitionId, businessKey, variables, user);
        long instanceId = ((Number) instance.get("id")).longValue();
        jdbc.update("UPDATE req_difference SET review_status = '评审中', review_comment = NULL, workflow_instance_id = ?, updated_by = ? WHERE tenant_id = ? AND id = ?",
                instanceId, user.id(), user.tenantId(), id);
        changeLog.record("NEW_PROJECT_DIFF", id, "SUBMIT_REVIEW", "review_status", status, "评审中", user, "ONLINE");
        return get(id, user);
    }

    /** 可选审批人列表：有差异评审权限（requirement:diff:review）或需求统筹管理员角色的启用用户。 */
    public List<Map<String, Object>> reviewers(AuthUser user) {
        return jdbc.queryForList("""
                SELECT DISTINCT u.id, u.username, u.display_name
                FROM sys_user u
                LEFT JOIN sys_user_role ur ON ur.user_id = u.id AND ur.tenant_id = u.tenant_id
                LEFT JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id
                LEFT JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.tenant_id = r.tenant_id
                LEFT JOIN sys_menu_permission mp ON mp.id = rp.permission_id AND mp.tenant_id = rp.tenant_id
                WHERE u.tenant_id = ? AND u.deleted = 0 AND u.status = 1
                  AND (mp.permission_code = 'requirement:diff:review'
                       OR r.role_code = 'REQUIREMENT_COORDINATOR'
                       OR u.id = 1)
                ORDER BY u.id
                """, user.tenantId());
    }

    /** 按流程编码取已发布流程定义 id；不存在或未发布抛业务异常。 */
    private long lookupDefinitionId(long tenantId, String code) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id FROM wf_definition WHERE tenant_id = ? AND code = ? AND status = 'PUBLISHED' AND deleted = 0",
                tenantId, code);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "流程定义未发布：" + code);
        }
        return ((Number) rows.get(0).get("id")).longValue();
    }

    public List<Map<String, Object>> changes(long id, AuthUser user) {
        get(id, user);
        return changeLog.list("NEW_PROJECT_DIFF", id, user);
    }

    /**
     * 审批记录：按差异关联的 workflow_instance_id 查 wf_task_action，
     * 返回审批人、审批动作、意见、目标用户（加签场景）、任务类型、时间。
     * workflow_instance_id 为空或非数字（stub）时返回空列表，不进入工作流中心。
     */
    public List<Map<String, Object>> approvalLogs(long id, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireProjectAccess(user, ((Number) row.get("project_id")).longValue());
        Object raw = row.get("workflow_instance_id");
        if (raw == null) return List.of();
        long instanceId;
        try {
            instanceId = Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return List.of();  // 兼容 V39 种子里的 stub 字符串（如 WF-LEGACY-STUB-xxx）
        }
        return jdbc.queryForList("""
                SELECT a.id, a.action_code, a.operator_id, u.display_name AS operator_name,
                       a.target_user_id, tu.display_name AS target_user_name,
                       a.comment, a.created_at,
                       t.task_type, t.assignee_name, t.status AS task_status, t.node_id
                FROM wf_task_action a
                LEFT JOIN sys_user u  ON u.id = a.operator_id AND u.tenant_id = a.tenant_id
                LEFT JOIN sys_user tu ON tu.id = a.target_user_id AND tu.tenant_id = a.tenant_id
                LEFT JOIN wf_task t   ON t.id = a.task_id AND t.tenant_id = a.tenant_id
                WHERE a.tenant_id = ? AND a.instance_id = ?
                ORDER BY a.created_at, a.id
                """, user.tenantId(), instanceId);
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
