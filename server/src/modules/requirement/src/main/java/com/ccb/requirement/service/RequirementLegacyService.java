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

import com.ccb.requirement.support.RequirementEnums;
import com.ccb.requirement.support.RequirementIds;
import com.ccb.requirement.support.RequirementSql;
import com.ccb.requirement.support.RequirementValues;

/** 存量项目常态化需求：8 阶段字段维护、阶段状态推进（不走审批流）与业务组数据范围。 */
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

    public RequirementLegacyService(JdbcTemplate jdbc, RequirementChangeLogService changeLog,
                                    RequirementSecurityService security) {
        this.jdbc = jdbc;
        this.changeLog = changeLog;
        this.security = security;
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

    /** 阶段推进：未开始→进行中→已完成；已完成不可回退，进行中可回退到未开始；全程留痕。 */
    @Transactional
    public Map<String, Object> stageTransition(long id, String stage, String action, String comment, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireLegacyAccess(user, String.valueOf(row.get("business_group")));
        if (!RequirementEnums.LEGACY_STAGES.contains(stage)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "阶段不在受控枚举内：" + stage);
        }
        String column = RequirementEnums.LEGACY_STAGE_COLUMNS.get(stage);
        String fromStatus = String.valueOf(row.get(column));
        String toStatus = nextStatus(fromStatus, action);
        String oldStage = String.valueOf(row.get("current_stage"));
        jdbc.update("UPDATE req_legacy_requirement SET " + RequirementSql.quote(column) + " = ?, current_stage = ?, updated_by = ? WHERE tenant_id = ? AND id = ?",
                toStatus, stage, user.id(), user.tenantId(), id);
        long logId = RequirementIds.next();
        jdbc.update("""
                INSERT INTO req_stage_log (id, tenant_id, requirement_id, from_stage, to_stage, from_status, to_status, operator_id, operator_name, comment, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, logId, user.tenantId(), id, oldStage, stage, fromStatus, toStatus,
                user.id(), user.displayName(), comment);
        changeLog.record("LEGACY_REQUIREMENT", id, "STAGE_TRANSITION", column, fromStatus, toStatus, user, "ONLINE");
        return get(id, user);
    }

    public List<Map<String, Object>> stageLogs(long id, AuthUser user) {
        get(id, user);
        return jdbc.queryForList("""
                SELECT from_stage, to_stage, from_status, to_status, operator_id, operator_name, comment, created_at
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
