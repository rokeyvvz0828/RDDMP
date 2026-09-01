package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.ccb.requirement.support.RequirementEnums;
import com.ccb.requirement.support.RequirementIds;
import com.ccb.requirement.support.RequirementSql;
import com.ccb.requirement.support.RequirementValues;

/**
 * 存量需求增强：工作量表/软需文档（版本替换保留历史）、协同事项（改造/测试）、评审记录。
 * 文件上传能力本期未开放：按钮占位，仅维护文本元数据，表结构预留文件引用字段。
 */
@Service
public class RequirementLegacyEnhanceService {
    private static final List<String> DELIVERABLE_COLUMNS = List.of(
            "id", "requirement_id", "system_item_id", "system_code", "doc_name", "version_no",
            "review_status", "review_record_id", "file_preview_id", "remark",
            "review_approver_ids", "review_approver_names", "review_report_name",
            "created_at", "updated_at");

    private final JdbcTemplate jdbc;
    private final RequirementSecurityService security;
    private final RequirementChangeLogService changeLog;

    public RequirementLegacyEnhanceService(JdbcTemplate jdbc, RequirementSecurityService security,
                                           RequirementChangeLogService changeLog) {
        this.jdbc = jdbc;
        this.security = security;
        this.changeLog = changeLog;
    }

    // ---------------- 工作量表 / 软需文档 ----------------

    public List<Map<String, Object>> deliverables(long requirementId, String type, AuthUser user) {
        String table = requireType(type);
        requireAccess(requirementId, user);
        return jdbc.queryForList("SELECT d." + String.join(", d.", DELIVERABLE_COLUMNS)
                + ", r.remark AS review_remark FROM " + table + " d"
                + " LEFT JOIN req_review_record r ON r.id = d.review_record_id AND r.deleted = 0"
                + " WHERE d.tenant_id = ? AND d.requirement_id = ? AND d.deleted = 0"
                + " ORDER BY d.system_item_id, d.version_no DESC, d.id DESC", user.tenantId(), requirementId);
    }

    /** 保存交付件记录；带 id 视为替换（新版本），无 id 新增版本 1.0。历史版本行保留。 */
    @Transactional
    public Map<String, Object> saveDeliverable(long requirementId, String type,
                                               Map<String, Object> body, AuthUser user) {
        String table = requireType(type);
        requireEditable(requirementId, user);
        String docName = RequirementValues.text(body, "doc_name");
        String systemCode = RequirementValues.text(body, "system_code");
        if (docName == null && systemCode == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写文档名称或系统");
        }
        Long systemItemId = body.get("system_item_id") == null || String.valueOf(body.get("system_item_id")).isBlank()
                ? null : Long.parseLong(String.valueOf(body.get("system_item_id")));
        String version = nextDeliverableVersion(requirementId, table, systemItemId, systemCode, user.tenantId());
        long id = RequirementIds.next();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("tenant_id", user.tenantId());
        values.put("requirement_id", requirementId);
        values.put("system_item_id", systemItemId);
        values.put("system_code", systemCode);
        values.put("doc_name", docName);
        values.put("version_no", version);
        values.put("review_status", "待评审");
        values.put("remark", RequirementValues.text(body, "remark"));
        values.put("created_by", user.id());
        values.put("deleted", 0);
        RequirementSql.insert(jdbc, table, values);
        changeLog.recordCreate(bizTypeOf(type), id, values, user, "ONLINE");
        return deliverableRow(table, id, user.tenantId());
    }

    @Transactional
    public void deleteDeliverable(long id, String type, AuthUser user) {
        String table = requireType(type);
        Map<String, Object> row = deliverableRow(table, id, user.tenantId());
        requireEditable(((Number) row.get("requirement_id")).longValue(), user);
        jdbc.update("UPDATE " + table + " SET deleted = 1 WHERE tenant_id = ? AND id = ?",
                user.tenantId(), id);
        changeLog.record(bizTypeOf(type), id, "DELETE", "deleted", "0", "1", user, "ONLINE");
    }

    /** 提交评审：待评审/已退回 → 评审中；与新建项目差异一致，提交时选择审批人并填写评审报告文档名称。 */
    @Transactional
    public Map<String, Object> submitDeliverableReview(long id, String type, List<Long> approverIds,
                                                       String reportDocName, AuthUser user) {
        String table = requireType(type);
        Map<String, Object> row = deliverableRow(table, id, user.tenantId());
        requireEditable(((Number) row.get("requirement_id")).longValue(), user);
        if (approverIds == null || approverIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择审批人");
        }
        String status = String.valueOf(row.get("review_status"));
        if (!"待评审".equals(status) && !"已退回".equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可提交评审：" + status);
        }
        String approverNames = approverNames(user.tenantId(), approverIds);
        jdbc.update("UPDATE " + table
                        + " SET review_status = '评审中', review_approver_ids = ?, review_approver_names = ?, review_report_name = ?"
                        + " WHERE tenant_id = ? AND id = ?",
                joinIds(approverIds), approverNames,
                reportDocName == null || reportDocName.isBlank() ? null : reportDocName.substring(0, Math.min(200, reportDocName.length())),
                user.tenantId(), id);
        changeLog.record(bizTypeOf(type), id, "SUBMIT_REVIEW", "review_status", status, "评审中", user, "ONLINE");
        return deliverableRow(table, id, user.tenantId());
    }

    /** 评审确认（被选审批人或 PMO/管理员）：通过/退回，写评审记录并锁定或解锁交付件。 */
    @Transactional
    public Map<String, Object> reviewDeliverable(long id, String type, Map<String, Object> body, AuthUser user) {
        String table = requireType(type);
        Map<String, Object> row = deliverableRow(table, id, user.tenantId());
        if (!security.isPmo(user) && !security.isAdmin(user) && !isSelectedApprover(row, user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅被选审批人或 PMO 可确认交付件评审");
        }
        requireAccess(((Number) row.get("requirement_id")).longValue(), user);
        String conclusion = RequirementValues.requireText(body, "conclusion", "评审结论不能为空");
        RequirementValues.requireOption("reviewConclusions", conclusion);
        if (!"评审中".equals(String.valueOf(row.get("review_status")))) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可评审：" + row.get("review_status"));
        }
        long recordId = writeReviewRecord(bizTypeOf(type), id, conclusion,
                RequirementValues.text(body, "comment"),
                RequirementValues.text(body, "remark"),
                RequirementValues.text(body, "report_doc_name"), user);
        String newStatus = "通过".equals(conclusion) ? "已评审" : "已退回";
        jdbc.update("UPDATE " + table + " SET review_status = ?, review_record_id = ? WHERE tenant_id = ? AND id = ?",
                newStatus, recordId, user.tenantId(), id);
        changeLog.record(bizTypeOf(type), id, "REVIEW_RESULT", "review_status",
                String.valueOf(row.get("review_status")), newStatus, user, "ONLINE");
        return deliverableRow(table, id, user.tenantId());
    }

    // ---------------- 协同事项（改造/测试） ----------------

    public List<Map<String, Object>> coordinationItems(long requirementId, AuthUser user) {
        requireAccess(requirementId, user);
        return jdbc.queryForList("""
                SELECT id, system_item_id, item_type, system_code, system_name, owner_user_id,
                       owner_user_name, start_date, end_date, status, description, created_at
                FROM req_coordination_item WHERE tenant_id = ? AND requirement_id = ? AND deleted = 0
                ORDER BY id
                """, user.tenantId(), requirementId);
    }

    @Transactional
    public Map<String, Object> saveCoordination(long requirementId, Map<String, Object> body, AuthUser user) {
        requireEditable(requirementId, user);
        String itemType = RequirementValues.requireText(body, "item_type", "协同事项类型不能为空");
        RequirementValues.requireOption("coordTypes", itemType);
        RequirementValues.requireOption("coordStatuses", RequirementValues.text(body, "status"));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("system_item_id", body.get("system_item_id") == null || String.valueOf(body.get("system_item_id")).isBlank()
                ? null : Long.parseLong(String.valueOf(body.get("system_item_id"))));
        values.put("item_type", itemType);
        values.put("system_code", RequirementValues.text(body, "system_code"));
        values.put("system_name", RequirementValues.text(body, "system_name"));
        Object ownerIdRaw = body.get("owner_user_id");
        values.put("owner_user_id", ownerIdRaw == null || String.valueOf(ownerIdRaw).isBlank()
                ? null : Long.parseLong(String.valueOf(ownerIdRaw)));
        values.put("owner_user_name", RequirementValues.text(body, "owner_user_name"));
        values.put("start_date", RequirementValues.date(body.get("start_date")));
        values.put("end_date", RequirementValues.date(body.get("end_date")));
        values.put("status", RequirementValues.text(body, "status") == null ? "未开始" : RequirementValues.text(body, "status"));
        values.put("description", RequirementValues.text(body, "description"));
        Object idRaw = body.get("id");
        if (idRaw != null) {
            long id = Long.parseLong(String.valueOf(idRaw));
            Map<String, Object> before = coordinationRow(id, user.tenantId());
            values.put("updated_by", user.id());
            RequirementSql.update(jdbc, "req_coordination_item", id, user.tenantId(), values);
            Map<String, Object> after = coordinationRow(id, user.tenantId());
            changeLog.recordFields("LEGACY_COORDINATION", id, "UPDATE", before, after, user, "ONLINE");
            return after;
        }
        long id = RequirementIds.next();
        values.put("id", id);
        values.put("tenant_id", user.tenantId());
        values.put("requirement_id", requirementId);
        values.put("created_by", user.id());
        values.put("deleted", 0);
        RequirementSql.insert(jdbc, "req_coordination_item", values);
        changeLog.recordCreate("LEGACY_COORDINATION", id, values, user, "ONLINE");
        return coordinationRow(id, user.tenantId());
    }

    @Transactional
    public void deleteCoordination(long id, AuthUser user) {
        Map<String, Object> row = coordinationRow(id, user.tenantId());
        requireEditable(((Number) row.get("requirement_id")).longValue(), user);
        jdbc.update("UPDATE req_coordination_item SET deleted = 1 WHERE tenant_id = ? AND id = ?",
                user.tenantId(), id);
        changeLog.record("LEGACY_COORDINATION", id, "DELETE", "deleted", "0", "1", user, "ONLINE");
    }

    // ---------------- 评审记录 ----------------

    public List<Map<String, Object>> reviewRecords(String bizType, long bizId, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, biz_type, biz_id, review_no, reviewer_id, reviewer_name, review_time,
                       conclusion, comment, remark, report_doc_name, created_at
                FROM req_review_record WHERE tenant_id = ? AND biz_type = ? AND biz_id = ? AND deleted = 0
                ORDER BY created_at DESC, id DESC
                """, user.tenantId(), bizType, bizId);
        return rows;
    }

    // ---------------- 内部工具 ----------------

    private long writeReviewRecord(String bizType, long bizId, String conclusion, String comment, String remark,
                                   String reportDocName, AuthUser user) {
        long recordId = RequirementIds.next();
        jdbc.update("""
                INSERT INTO req_review_record (id, tenant_id, biz_type, biz_id, reviewer_id, reviewer_name,
                    review_time, conclusion, comment, remark, report_doc_name, created_by, deleted)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, 0)
                """, recordId, user.tenantId(), bizType, bizId, user.id(), user.displayName(),
                conclusion, comment, remark, reportDocName, user.id());
        return recordId;
    }

    private boolean isSelectedApprover(Map<String, Object> row, AuthUser user) {
        Object raw = row.get("review_approver_ids");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return false;
        }
        String target = "," + String.valueOf(raw).replaceAll("\\s", "") + ",";
        return target.contains("," + user.id() + ",");
    }

    private String approverNames(long tenantId, List<Long> approverIds) {
        if (approverIds.isEmpty()) {
            return null;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(approverIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(approverIds);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, COALESCE(display_name, username) AS name FROM sys_user"
                        + " WHERE tenant_id = ? AND id IN (" + placeholders + ") AND deleted = 0",
                args.toArray());
        java.util.Set<Long> ids = new java.util.HashSet<>(approverIds);
        java.util.Map<Long, String> nameByUser = new java.util.LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            nameByUser.put(((Number) r.get("id")).longValue(), String.valueOf(r.get("name")));
        }
        List<String> names = new ArrayList<>();
        for (Long id : ids) {
            String name = nameByUser.get(id);
            if (name != null) {
                names.add(name);
            }
        }
        return names.isEmpty() ? null : String.join("、", names);
    }

    private String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(null);
    }

    private void requireAccess(long requirementId, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id FROM req_legacy_requirement WHERE tenant_id = ? AND id = ? AND deleted = 0",
                user.tenantId(), requirementId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存量需求不存在");
        }
    }

    private void requireEditable(long requirementId, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT created_by, current_flow_user_id FROM req_legacy_requirement WHERE tenant_id = ? AND id = ? AND deleted = 0",
                user.tenantId(), requirementId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存量需求不存在");
        }
        security.requireLegacyEditable(user, rows.get(0));
    }

    private String nextDeliverableVersion(long requirementId, String table, Long systemItemId,
                                          String systemCode, long tenantId) {
        List<Object> params = new ArrayList<>(List.of(tenantId, requirementId));
        String condition = "";
        if (systemItemId != null) {
            condition = " AND system_item_id = ?";
            params.add(systemItemId);
        } else if (systemCode != null) {
            condition = " AND system_code = ?";
            params.add(systemCode);
        }
        List<String> versions = jdbc.queryForList(
                "SELECT version_no FROM " + table + " WHERE tenant_id = ? AND requirement_id = ?"
                        + condition + " AND deleted = 0", String.class, params.toArray());
        double max = 0.0;
        for (String version : versions) {
            try {
                max = Math.max(max, Double.parseDouble(version.trim()));
            } catch (NumberFormatException ignored) {
                // ignore non-numeric legacy versions
            }
        }
        return String.format(Locale.ROOT, "%.1f", max + 1.0);
    }

    private Map<String, Object> deliverableRow(String table, long id, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT d." + String.join(", d.", DELIVERABLE_COLUMNS)
                        + ", r.remark AS review_remark FROM " + table + " d"
                        + " LEFT JOIN req_review_record r ON r.id = d.review_record_id AND r.deleted = 0"
                        + " WHERE d.tenant_id = ? AND d.id = ? AND d.deleted = 0", tenantId, id);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "交付件记录不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> coordinationRow(long id, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, requirement_id, system_item_id, item_type, system_code, system_name,
                       owner_user_id, owner_user_name, start_date, end_date, status, description, created_at
                FROM req_coordination_item WHERE tenant_id = ? AND id = ? AND deleted = 0
                """, tenantId, id);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "协同事项不存在");
        }
        return rows.get(0);
    }

    private String requireType(String type) {
        if ("WORKLOAD".equals(type)) {
            return "req_workload";
        }
        if ("SOFT".equals(type)) {
            return "req_soft_doc";
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "交付件类型必须为 WORKLOAD 或 SOFT");
    }

    private String bizTypeOf(String type) {
        return "WORKLOAD".equals(type) ? "LEGACY_WORKLOAD" : "LEGACY_SOFT";
    }
}
