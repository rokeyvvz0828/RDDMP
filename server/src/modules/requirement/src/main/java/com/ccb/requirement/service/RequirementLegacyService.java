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

import com.ccb.requirement.support.RequirementEnums;
import com.ccb.requirement.support.RequirementIds;
import com.ccb.requirement.support.RequirementSql;
import com.ccb.requirement.support.RequirementValues;

/** 存量项目常态化需求：8 阶段字段维护、阶段状态推进（接入审批流 legacy.stage.transition）与业务组数据范围。 */
@Service
public class RequirementLegacyService {
    private static final List<String> LEGACY_FIELDS = List.of(
            "legacy_doc_name", "requirement_no", "requirement_name", "content_summary",
            "propose_dept", "proposer", "monshang_ba", "monshang_architect",
            "expected_launch_date", "regulator", "regulation_doc_no", "regulation_desc",
            "regulation_launch_date", "requirement_received_date", "requirement_type",
            "regulation_category", "business_group", "sub_group", "jinke_contact",
            "need_jinke_arch_decision", "jinke_architect", "unified_managed", "ba_review_date",
            "workload_date", "finance_project_date", "soft_doc_name", "owner_conglomerate",
            "owner_system", "owner_contact", "involve_cooperation", "coord_conglomerate",
            "coord_system", "soft_submit_date", "soft_review_date", "planned_launch_date",
            "actual_launch_date", "launch_mode", "requirement_status", "remark",
            "change_involved", "change_info", "change_review_conclusion",
            "change_conclusion_status", "change_remark", "not_project_developed");

    private static final List<String> DATE_FIELDS = List.of(
            "expected_launch_date", "regulation_launch_date", "requirement_received_date",
            "ba_review_date", "workload_date", "finance_project_date", "soft_submit_date",
            "soft_review_date", "planned_launch_date", "actual_launch_date");

    private static final String SELECT_COLUMNS = """
            id, legacy_doc_name, requirement_no, requirement_name, content_summary, propose_dept,
            proposer, monshang_ba, monshang_architect, expected_launch_date, regulator,
            regulation_doc_no, regulation_desc, regulation_launch_date, requirement_received_date,
            requirement_type, regulation_category, business_group, sub_group, jinke_contact,
            need_jinke_arch_decision, jinke_architect, unified_managed, ba_review_date,
            workload_date, finance_project_date, soft_doc_name, owner_conglomerate, owner_system,
            owner_contact, involve_cooperation, coord_conglomerate, coord_system, soft_submit_date,
            soft_review_date, planned_launch_date, actual_launch_date, launch_mode,
            requirement_status, remark, change_involved, change_info, change_review_conclusion,
            change_conclusion_status, change_remark, not_project_developed, current_stage,
            propose_stage_status, docking_stage_status, workload_stage_status, project_stage_status,
            soft_stage_status, launch_stage_status, source, created_at, updated_at
            """;

    private final JdbcTemplate jdbc;
    private final RequirementChangeLogService changeLog;
    private final RequirementSecurityService security;
    private final WorkflowService workflowService;

    public RequirementLegacyService(JdbcTemplate jdbc, RequirementChangeLogService changeLog,
                                    RequirementSecurityService security,
                                    WorkflowService workflowService) {
        this.jdbc = jdbc;
        this.changeLog = changeLog;
        this.security = security;
        this.workflowService = workflowService;
    }

    public PageResult<Map<String, Object>> list(String businessGroup, String stage, String stageStatus,
                                                String keyword, PageQuery query, AuthUser user) {
        StringBuilder where = new StringBuilder(" WHERE tenant_id = ? AND deleted = 0");
        List<Object> params = new ArrayList<>(List.of(user.tenantId()));
        if (businessGroup != null && !businessGroup.isBlank()) {
            security.requireLegacyAccess(user, businessGroup);
            where.append(" AND business_group = ?");
            params.add(businessGroup);
        } else if (!security.isAdmin(user)) {
            List<String> groups = security.myBusinessGroups(user);
            if (groups.isEmpty()) {
                return new PageResult<>(List.of(), 0, query.page(), query.size());
            }
            where.append(" AND business_group IN (");
            where.append(String.join(", ", groups.stream().map(group -> "?").toList()));
            where.append(")");
            params.addAll(groups);
        }
        if (stage != null && !stage.isBlank()) {
            if (!RequirementEnums.LEGACY_STAGES.contains(stage)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "阶段不在受控枚举内：" + stage);
            }
            where.append(" AND current_stage = ?");
            params.add(stage);
        }
        if (stageStatus != null && !stageStatus.isBlank() && stage != null && !stage.isBlank()) {
            where.append(" AND ").append(RequirementSql.quote(RequirementEnums.LEGACY_STAGE_COLUMNS.get(stage))).append(" = ?");
            params.add(stageStatus);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (requirement_name LIKE ? OR requirement_no LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM req_legacy_requirement" + where, Long.class, params.toArray());
        params.add(query.size());
        params.add((query.page() - 1) * query.size());
        List<Map<String, Object>> records = jdbc.queryForList(
                "SELECT " + SELECT_COLUMNS + " FROM req_legacy_requirement" + where
                        + " ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?", params.toArray());
        return new PageResult<>(records, total == null ? 0 : total, query.page(), query.size());
    }

    public Map<String, Object> get(long id, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireLegacyAccess(user, String.valueOf(row.get("business_group")));
        return row;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        String businessGroup = RequirementValues.requireText(body, "business_group", "业务组不能为空");
        security.requireLegacyAccess(user, businessGroup);
        RequirementValues.requireText(body, "requirement_no", "需求编号不能为空");
        RequirementValues.requireText(body, "requirement_name", "需求名称不能为空");
        Map<String, Object> values = normalized(body);
        validate(values);
        long id = RequirementIds.next();
        values.put("id", id);
        values.put("tenant_id", user.tenantId());
        values.putIfAbsent("current_stage", "PROPOSE");
        values.putIfAbsent("propose_stage_status", "未开始");
        values.putIfAbsent("docking_stage_status", "未开始");
        values.putIfAbsent("workload_stage_status", "未开始");
        values.putIfAbsent("project_stage_status", "未开始");
        values.putIfAbsent("soft_stage_status", "未开始");
        values.putIfAbsent("launch_stage_status", "未开始");
        values.putIfAbsent("source", "ONLINE");
        values.put("created_by", user.id());
        values.put("deleted", 0);
        RequirementSql.insert(jdbc, "req_legacy_requirement", values);
        changeLog.recordCreate("LEGACY_REQUIREMENT", id, values, user, "ONLINE");
        return get(id, user);
    }

    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> before = row(id, user);
        security.requireLegacyAccess(user, String.valueOf(before.get("business_group")));
        Map<String, Object> values = normalized(body);
        values.remove("business_group");
        validate(values);
        if (values.isEmpty()) {
            return before;
        }
        values.put("updated_by", user.id());
        RequirementSql.update(jdbc, "req_legacy_requirement", id, user.tenantId(), values);
        Map<String, Object> after = row(id, user);
        changeLog.recordFields("LEGACY_REQUIREMENT", id, "UPDATE", before, after, user, "ONLINE");
        return after;
    }

    @Transactional
    public void delete(long id, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireLegacyAccess(user, String.valueOf(row.get("business_group")));
        jdbc.update("UPDATE req_legacy_requirement SET deleted = 1, updated_by = ? WHERE tenant_id = ? AND id = ?",
                user.id(), user.tenantId(), id);
        changeLog.record("LEGACY_REQUIREMENT", id, "DELETE", "deleted", "0", "1", user, "ONLINE");
    }

    /**
     * 阶段推进审批：未开始/进行中 → 审批中 → 进行中/已完成/未开始。
     * 不直接写最终状态：先启动 legacy.stage.transition 审批流，
     * 业务阶段列置为"审批中"并落 workflow_instance_id；
     * 审批结果由 RequirementWorkflowListener 幂等回写。
     * "审批中"阶段拒绝再次发起，避免并发审批。
     * 发起人必须指定审批人（approverIds），写入流程变量供 VARIABLE 类型审批节点解析。
     */
    @Transactional
    public Map<String, Object> stageTransition(long id, String stage, String action, String comment,
                                                List<Long> approverIds, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireLegacyAccess(user, String.valueOf(row.get("business_group")));
        if (!RequirementEnums.LEGACY_STAGES.contains(stage)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "阶段不在受控枚举内：" + stage);
        }
        if (approverIds == null || approverIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "至少选择一个审批人");
        }
        // 阶段必填字段校验：从 PROPOSE 到目标 stage 的所有阶段必填字段都要完成
        validateStageFieldsReady(row, stage);
        String column = RequirementEnums.LEGACY_STAGE_COLUMNS.get(stage);
        String fromStatus = String.valueOf(row.get(column));
        if ("审批中".equals(fromStatus)) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前阶段处于审批中，禁止重复发起");
        }
        // 仍按原状态机校验目标动作合法性（避免审批通过后写入非法状态）
        String toStatus = nextStatus(fromStatus, action);
        String oldStage = String.valueOf(row.get("current_stage"));

        long definitionId = lookupDefinitionId(user.tenantId(), "legacy.stage.transition");
        String businessKey = "req-legacy:" + id + ":" + stage + ":" + action.toUpperCase();
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("requirementId", id);
        variables.put("stage", stage);
        variables.put("action", action.toUpperCase());
        variables.put("fromStatus", fromStatus);
        variables.put("toStatus", toStatus);
        variables.put("submitterId", user.id());
        variables.put("submitterName", user.displayName());
        variables.put("comment", comment == null ? "" : comment);
        variables.put("approverIds", approverIds);
        Map<String, Object> instance = workflowService.start(definitionId, businessKey, variables, user);
        long instanceId = ((Number) instance.get("id")).longValue();
        jdbc.update("UPDATE req_legacy_requirement SET " + RequirementSql.quote(column)
                        + " = '审批中', workflow_instance_id = ?, current_stage = ?, updated_by = ? WHERE tenant_id = ? AND id = ?",
                String.valueOf(instanceId), stage, user.id(), user.tenantId(), id);
        long logId = RequirementIds.next();
        jdbc.update("""
                INSERT INTO req_stage_log (id, tenant_id, requirement_id, from_stage, to_stage, from_status, to_status, operator_id, operator_name, comment, approval_result, workflow_instance_id, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, logId, user.tenantId(), id, oldStage, stage, fromStatus, "审批中",
                user.id(), user.displayName(), "发起审批：" + action, "PENDING", String.valueOf(instanceId));
        changeLog.record("LEGACY_REQUIREMENT", id, "STAGE_TRANSITION", column, fromStatus, "审批中", user, "ONLINE");
        return get(id, user);
    }

    /** 阶段推进可选审批人：能编辑存量需求的人 + 协调员 + 管理员。 */
    public List<Map<String, Object>> reviewers(AuthUser user) {
        return jdbc.queryForList("""
                SELECT DISTINCT u.id, u.username, u.display_name
                FROM sys_user u
                LEFT JOIN sys_user_role ur ON ur.user_id = u.id AND ur.tenant_id = u.tenant_id
                LEFT JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id
                LEFT JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.tenant_id = r.tenant_id
                LEFT JOIN sys_menu_permission mp ON mp.id = rp.permission_id AND mp.tenant_id = rp.tenant_id
                WHERE u.tenant_id = ? AND u.deleted = 0 AND u.status = 1
                  AND (mp.permission_code = 'requirement:legacy:update'
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

    public List<Map<String, Object>> stageLogs(long id, AuthUser user) {
        get(id, user);
        return jdbc.queryForList("""
                SELECT from_stage, to_stage, from_status, to_status, operator_id, operator_name, comment, approval_result, workflow_instance_id, created_at
                FROM req_stage_log WHERE tenant_id = ? AND requirement_id = ? AND deleted = 0
                ORDER BY created_at DESC, id DESC
                """, user.tenantId(), id);
    }

    public List<Map<String, Object>> changes(long id, AuthUser user) {
        get(id, user);
        return changeLog.list("LEGACY_REQUIREMENT", id, user);
    }

    Map<String, Object> row(long id, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + SELECT_COLUMNS + " FROM req_legacy_requirement WHERE tenant_id = ? AND id = ? AND deleted = 0",
                user.tenantId(), id);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存量需求不存在");
        }
        return rows.get(0);
    }

    /**
     * 阶段推进前的必填字段校验：仅校验当前阶段（含）之前所有阶段的必填字段。
     * 即从 PROPOSE 到 currentStage 为止的必填字段都必须非空；目标阶段及之后的字段不校验。
     * 这样 PROPOSE→DOCKING 只校验 PROPOSE 字段；DOCKING→WORKLOAD 校验 PROPOSE+DOCKING 字段。
     * 缺失字段会拼接中文标签列表抛 BAD_REQUEST，前端可直接展示。
     */
    private void validateStageFieldsReady(Map<String, Object> row, String targetStage) {
        List<String> missing = new ArrayList<>();
        // 找到 currentStage 在 LEGACY_STAGES 中的位置
        String currentStage = String.valueOf(row.get("current_stage"));
        int currentIdx = RequirementEnums.LEGACY_STAGES.indexOf(currentStage);
        if (currentIdx < 0) currentIdx = 0;
        // 校验从 PROPOSE 到 currentStage 为止的所有阶段必填字段
        for (int i = 0; i <= currentIdx && i < RequirementEnums.LEGACY_STAGES.size(); i++) {
            String stage = RequirementEnums.LEGACY_STAGES.get(i);
            List<String> fields = RequirementEnums.LEGACY_STAGE_REQUIRED_FIELDS.get(stage);
            if (fields == null) continue;
            for (String field : fields) {
                Object v = row.get(field);
                if (v == null || (v instanceof String s && s.isBlank())) {
                    String label = RequirementEnums.FIELD_LABELS.getOrDefault(field, field);
                    missing.add(label);
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "以下字段必填，完成后方可推进阶段：" +
                            String.join("、", missing));
        }
    }

    private String nextStatus(String fromStatus, String action) {
        if ("START".equalsIgnoreCase(action)) {
            if (!"未开始".equals(fromStatus)) {
                throw new BusinessException(ErrorCode.CONFLICT, "仅未开始阶段可启动：" + fromStatus);
            }
            return "进行中";
        }
        if ("COMPLETE".equalsIgnoreCase(action)) {
            if (!"进行中".equals(fromStatus)) {
                throw new BusinessException(ErrorCode.CONFLICT, "仅进行中阶段可完成：" + fromStatus);
            }
            return "已完成";
        }
        if ("BACK".equalsIgnoreCase(action)) {
            if (!"进行中".equals(fromStatus)) {
                throw new BusinessException(ErrorCode.CONFLICT, "仅进行中阶段可回退：" + fromStatus);
            }
            return "未开始";
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "阶段动作必须为 START/COMPLETE/BACK");
    }

    private void validate(Map<String, Object> values) {
        RequirementValues.requireOption("requirementTypes", RequirementValues.text(values, "requirement_type"));
        RequirementValues.requireOption("regulationCategories", RequirementValues.text(values, "regulation_category"));
        RequirementValues.requireOption("requirementStatuses", RequirementValues.text(values, "requirement_status"));
        RequirementValues.requireOption("launchModes", RequirementValues.text(values, "launch_mode"));
        RequirementValues.requireOption("changeReviewConclusions", RequirementValues.text(values, "change_review_conclusion"));
        RequirementValues.requireOption("changeConclusionStatuses", RequirementValues.text(values, "change_conclusion_status"));
        for (String yesNoField : List.of("need_jinke_arch_decision", "unified_managed",
                "involve_cooperation", "change_involved", "not_project_developed")) {
            RequirementValues.requireOption("yesNo", RequirementValues.text(values, yesNoField));
        }
    }

    private Map<String, Object> normalized(Map<String, Object> body) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : LEGACY_FIELDS) {
            Object value = body.get(field);
            if (value == null) {
                continue;
            }
            values.put(field, DATE_FIELDS.contains(field) ? RequirementValues.date(value) : value);
        }
        return values;
    }
}
