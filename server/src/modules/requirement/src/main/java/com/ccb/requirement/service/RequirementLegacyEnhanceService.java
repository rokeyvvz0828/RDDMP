package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.requirement.support.RequirementEnums;
import com.ccb.requirement.support.RequirementIds;
import com.ccb.requirement.support.RequirementSql;
import com.ccb.requirement.support.RequirementValues;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowBusinessGateway;
import com.ccb.workflow.integration.WorkflowStartDefinitionCommand;
import com.ccb.workflow.integration.WorkflowStartResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 存量需求增强：交付件（工作量表/软需文档，合并存于 req_legacy_deliverable，版本替换保留历史）、
 * 协同事项（改造/测试，与主责系统同存 req_requirement_system 关联行）、评审记录。
 * 文件上传能力本期未开放：按钮占位，仅维护文本元数据，表结构预留文件引用字段。
 */
@Service
public class RequirementLegacyEnhanceService {
    private static final List<String> DELIVERABLE_COLUMNS = List.of(
            "id", "requirement_id", "deliverable_type", "system_item_id", "system_code", "doc_name",
            "version_no", "review_status", "review_record_id", "workflow_instance_id", "file_preview_id",
            "remark", "review_approver_ids", "review_approver_names", "review_report_name", "review_report_attachment_id",
            "created_at", "updated_at");

    private final JdbcTemplate jdbc;
    private final RequirementSecurityService security;
    private final RequirementChangeLogService changeLog;
    private final RequirementProjectMemberService memberDirectory;
    private final RequirementReviewWorkflowSupport reviewWorkflow;
    private final RequirementReviewReportService reviewReportService;
    private final WorkflowBusinessGateway workflowGateway;

    public RequirementLegacyEnhanceService(JdbcTemplate jdbc, RequirementSecurityService security,
                                           RequirementChangeLogService changeLog,
                                           RequirementProjectMemberService memberDirectory,
                                           RequirementReviewWorkflowSupport reviewWorkflow,
                                           RequirementReviewReportService reviewReportService,
                                           WorkflowBusinessGateway workflowGateway) {
        this.jdbc = jdbc;
        this.security = security;
        this.changeLog = changeLog;
        this.memberDirectory = memberDirectory;
        this.reviewWorkflow = reviewWorkflow;
        this.reviewReportService = reviewReportService;
        this.workflowGateway = workflowGateway;
    }

    // ---------------- 工作量表 / 软需文档 ----------------

    public List<Map<String, Object>> deliverables(long requirementId, String type, AuthUser user) {
        String deliverableType = requireType(type);
        requireAccess(requirementId, user);
        return jdbc.queryForList("SELECT d." + String.join(", d.", DELIVERABLE_COLUMNS)
                + ", r.remark AS review_remark FROM req_legacy_deliverable d"
                + " LEFT JOIN req_review_record r ON r.id = d.review_record_id AND r.deleted = 0"
                + " WHERE d.tenant_id = ? AND d.requirement_id = ? AND d.deliverable_type = ? AND d.deleted = 0"
                + " ORDER BY d.system_item_id, d.version_no DESC, d.id DESC",
                user.tenantId(), requirementId, deliverableType);
    }

    /** 保存交付件记录；带 id 视为替换（新版本），无 id 新增版本 1.0。历史版本行保留。 */
    @Transactional
    public Map<String, Object> saveDeliverable(long requirementId, String type,
                                               Map<String, Object> body, AuthUser user) {
        String deliverableType = requireType(type);
        requireEditable(requirementId, user);
        // 交付件自身的文件在新增记录时上传（评审时直接预览该文件，不再单独上传评审报告）
        Map<String, Object> project = memberDirectory.requireLegacyProjectContext(requirementId, user);
        RequirementReviewReportService.BoundReport file = reviewReportService.requireBound(user, requirementId,
                String.valueOf(project.get("project_code")), longValue(body.get("documentAttachmentId")),
                "请先上传文档文件，再保存记录");
        String docName = RequirementValues.text(body, "doc_name");
        if (docName == null) {
            docName = file.fileName();
        }
        String systemCode = RequirementValues.text(body, "system_code");
        if (docName == null && systemCode == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写文档名称或系统");
        }
        Long systemItemId = body.get("system_item_id") == null || String.valueOf(body.get("system_item_id")).isBlank()
                ? null : Long.parseLong(String.valueOf(body.get("system_item_id")));
        String version = nextDeliverableVersion(requirementId, deliverableType, systemItemId, systemCode, user.tenantId());
        long id = RequirementIds.next();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("tenant_id", user.tenantId());
        values.put("requirement_id", requirementId);
        values.put("deliverable_type", deliverableType);
        values.put("system_item_id", systemItemId);
        values.put("system_code", systemCode);
        values.put("doc_name", docName);
        values.put("version_no", version);
        values.put("review_status", "待评审");
        values.put("review_report_attachment_id", file.attachmentId());
        values.put("review_report_name", file.fileName());
        values.put("remark", RequirementValues.text(body, "remark"));
        values.put("created_by", user.id());
        values.put("deleted", 0);
        RequirementSql.insert(jdbc, "req_legacy_deliverable", values);
        changeLog.recordCreate(bizTypeOf(deliverableType), id, values, user, "ONLINE");
        return deliverableRow(deliverableType, id, user.tenantId());
    }

    @Transactional
    public void deleteDeliverable(long id, String type, AuthUser user) {
        String deliverableType = requireType(type);
        Map<String, Object> row = deliverableRow(deliverableType, id, user.tenantId());
        requireEditable(((Number) row.get("requirement_id")).longValue(), user);
        jdbc.update("UPDATE req_legacy_deliverable SET deleted = 1 WHERE tenant_id = ? AND id = ?",
                user.tenantId(), id);
        changeLog.record(bizTypeOf(deliverableType), id, "DELETE", "deleted", "0", "1", user, "ONLINE");
    }

    /**
     * 提交评审：待评审/已退回 → 评审中，并启动平台审批流。
     * 审批人只能来自当前项目组织架构的有效成员；审批结论只能由工作流任务产生，
     * 业务接口不再直接写入评审结论（见 {@link RequirementWorkflowListener} 回写）。
     */
    @Transactional
    public Map<String, Object> submitDeliverableReview(long id, String type, List<Long> approverIds, AuthUser user) {
        String deliverableType = requireType(type);
        Map<String, Object> row = deliverableRow(deliverableType, id, user.tenantId());
        long requirementId = ((Number) row.get("requirement_id")).longValue();
        requireEditable(requirementId, user);
        if (approverIds == null || approverIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择审批人");
        }
        // 评审不再要求临时上传：直接使用记录创建时上传的文档文件
        if (reviewReportService.required() && longValue(row.get("review_report_attachment_id")) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该记录尚未上传文档文件，请先补充文件后再提交评审");
        }
        String status = String.valueOf(row.get("review_status"));
        if (!"待评审".equals(status) && !"已退回".equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可提交评审：" + status);
        }
        Map<String, Object> project = memberDirectory.requireLegacyProjectContext(requirementId, user);
        String projectCode = String.valueOf(project.get("project_code"));
        String projectName = String.valueOf(project.get("project_name"));
        // 审批人只能来自当前项目组织架构的有效成员（项目管理维护）
        memberDirectory.requireActiveMembers(projectCode, approverIds, user);
        String approverNames = approverNames(user.tenantId(), approverIds);
        String businessKey = "req-deliverable:" + deliverableType + ":" + id;
        reviewWorkflow.terminateResidualInstances(user.tenantId(), businessKey);
        long definitionId = reviewWorkflow
                .requirePublishedDefinitionId(user.tenantId(), "requirement.legacy.deliverable.review");
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("deliverableId", id);
        variables.put("deliverableType", deliverableType);
        variables.put("requirementId", requirementId);
        variables.put("submitterId", user.id());
        variables.put("submitterName", user.displayName());
        variables.put("approverIds", approverIds);
        variables.put("fromStatus", status);
        String docName = row.get("doc_name") == null ? "" : String.valueOf(row.get("doc_name"));
        String title = ("WORKLOAD".equals(deliverableType) ? "工作量表评审 - " : "软需文档评审 - ") + docName;
        WorkflowBusinessContext context = new WorkflowBusinessContext(
                "requirement", "需求管理", "requirement_deliverable_review", businessKey, title, 1,
                projectCode, projectName, "/requirements/legacy?legacyId=" + requirementId,
                reviewWorkflow.digest(businessKey + "|" + status + "|" + title));
        WorkflowStartResult instance = workflowGateway.startByDefinitionId(
                new WorkflowStartDefinitionCommand(definitionId, context, variables), user);
        jdbc.update("UPDATE req_legacy_deliverable"
                        + " SET review_status = '评审中', review_approver_ids = ?, review_approver_names = ?,"
                        + " review_report_name = ?, review_report_attachment_id = ?, workflow_instance_id = ?"
                        + " WHERE tenant_id = ? AND id = ?",
                joinIds(approverIds), approverNames,
                row.get("review_report_name"), longValue(row.get("review_report_attachment_id")),
                instance.instanceId(), user.tenantId(), id);
        changeLog.record(bizTypeOf(deliverableType), id, "SUBMIT_REVIEW", "review_status", status, "评审中", user, "ONLINE");
        return deliverableRow(deliverableType, id, user.tenantId());
    }

    // ---------------- 协同事项（改造/测试，落在统一系统关联行） ----------------

    public List<Map<String, Object>> coordinationItems(long requirementId, AuthUser user) {
        requireAccess(requirementId, user);
        return jdbc.queryForList("""
                SELECT id, requirement_id, system_role AS item_type, subsystem_code AS system_code,
                       subsystem_name AS system_name, owner_user_id, owner_user_name,
                       start_date, end_date, status, description, created_at
                FROM req_requirement_system
                WHERE tenant_id = ? AND requirement_id = ? AND deleted = 0
                  AND system_role IN ('CHANGE', 'TEST')
                ORDER BY id
                """, user.tenantId(), requirementId).stream()
                .map(row -> {
                    Map<String, Object> item = new LinkedHashMap<>(row);
                    item.put("item_type", RequirementEnums.systemRoleLabel(String.valueOf(row.get("item_type"))));
                    return item;
                }).toList();
    }

    @Transactional
    public Map<String, Object> saveCoordination(long requirementId, Map<String, Object> body, AuthUser user) {
        requireEditable(requirementId, user);
        String itemType = RequirementValues.requireText(body, "item_type", "协同事项类型不能为空");
        RequirementValues.requireOption("coordTypes", itemType);
        RequirementValues.requireOption("coordStatuses", RequirementValues.text(body, "status"));
        String roleCode = RequirementEnums.systemRoleCode(itemType);
        if (roleCode == null || RequirementEnums.SYSTEM_ROLE_LEAD.equals(roleCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "协同事项类型必须为改造或测试");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("subsystem_code", RequirementValues.text(body, "system_code"));
        values.put("subsystem_name", RequirementValues.text(body, "system_name"));
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
            RequirementSql.update(jdbc, "req_requirement_system", id, user.tenantId(), values);
            Map<String, Object> after = coordinationRow(id, user.tenantId());
            changeLog.recordFields("LEGACY_COORDINATION", id, "UPDATE", before, after, user, "ONLINE");
            return after;
        }
        long id = RequirementIds.next();
        values.put("id", id);
        values.put("tenant_id", user.tenantId());
        values.put("requirement_id", requirementId);
        values.put("system_role", roleCode);
        values.put("created_by", user.id());
        values.put("deleted", 0);
        RequirementSql.insert(jdbc, "req_requirement_system", values);
        changeLog.recordCreate("LEGACY_COORDINATION", id, values, user, "ONLINE");
        return coordinationRow(id, user.tenantId());
    }

    @Transactional
    public void deleteCoordination(long id, AuthUser user) {
        Map<String, Object> row = coordinationRow(id, user.tenantId());
        requireEditable(((Number) row.get("requirement_id")).longValue(), user);
        jdbc.update("UPDATE req_requirement_system SET deleted = 1 WHERE tenant_id = ? AND id = ?",
                user.tenantId(), id);
        changeLog.record("LEGACY_COORDINATION", id, "DELETE", "deleted", "0", "1", user, "ONLINE");
    }

    // ---------------- 评审记录 ----------------

    public List<Map<String, Object>> reviewRecords(String bizType, long bizId, AuthUser user) {
        return jdbc.queryForList("""
                SELECT id, biz_type, biz_id, review_no, reviewer_id, reviewer_name, review_time,
                       conclusion, comment, remark, report_doc_name, report_preview_id, created_at
                FROM req_review_record WHERE tenant_id = ? AND biz_type = ? AND biz_id = ? AND deleted = 0
                ORDER BY created_at DESC, id DESC
                """, user.tenantId(), bizType, bizId);
    }

    // ---------------- 内部工具 ----------------

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
        Map<Long, String> nameByUser = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            nameByUser.put(((Number) r.get("id")).longValue(), String.valueOf(r.get("name")));
        }
        List<String> names = new ArrayList<>();
        for (Long id : new java.util.LinkedHashSet<>(approverIds)) {
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
                "SELECT id FROM req_requirement WHERE tenant_id = ? AND id = ? AND deleted = 0"
                        + " AND requirement_kind = 'LEGACY'",
                user.tenantId(), requirementId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存量需求不存在");
        }
    }

    private void requireEditable(long requirementId, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT created_by, current_handler_user_id AS current_flow_user_id FROM req_requirement"
                        + " WHERE tenant_id = ? AND id = ? AND deleted = 0 AND requirement_kind = 'LEGACY'",
                user.tenantId(), requirementId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存量需求不存在");
        }
        security.requireLegacyEditable(user, rows.get(0));
    }

    private String nextDeliverableVersion(long requirementId, String deliverableType, Long systemItemId,
                                          String systemCode, long tenantId) {
        List<Object> params = new ArrayList<>(List.of(tenantId, requirementId, deliverableType));
        String condition = "";
        if (systemItemId != null) {
            condition = " AND system_item_id = ?";
            params.add(systemItemId);
        } else if (systemCode != null) {
            condition = " AND system_code = ?";
            params.add(systemCode);
        }
        List<String> versions = jdbc.queryForList(
                "SELECT version_no FROM req_legacy_deliverable WHERE tenant_id = ? AND requirement_id = ?"
                        + " AND deliverable_type = ?" + condition + " AND deleted = 0",
                String.class, params.toArray());
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

    private Map<String, Object> deliverableRow(String deliverableType, long id, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT d." + String.join(", d.", DELIVERABLE_COLUMNS)
                        + ", r.remark AS review_remark FROM req_legacy_deliverable d"
                        + " LEFT JOIN req_review_record r ON r.id = d.review_record_id AND r.deleted = 0"
                        + " WHERE d.tenant_id = ? AND d.id = ? AND d.deliverable_type = ? AND d.deleted = 0",
                tenantId, id, deliverableType);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "交付件记录不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> coordinationRow(long id, long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, requirement_id, system_role AS item_type, subsystem_code AS system_code,
                       subsystem_name AS system_name, owner_user_id, owner_user_name,
                       start_date, end_date, status, description, created_at
                FROM req_requirement_system WHERE tenant_id = ? AND id = ? AND deleted = 0
                """, tenantId, id);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "协同事项不存在");
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> item = new LinkedHashMap<>(row);
        item.put("item_type", RequirementEnums.systemRoleLabel(String.valueOf(row.get("item_type"))));
        return item;
    }

    private String requireType(String type) {
        if ("WORKLOAD".equals(type) || "SOFT".equals(type)) {
            return type;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "交付件类型必须为 WORKLOAD 或 SOFT");
    }

    private static Long longValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.parseLong(String.valueOf(value).trim());
    }

    private String bizTypeOf(String type) {
        return "WORKLOAD".equals(type) ? "LEGACY_WORKLOAD" : "LEGACY_SOFT";
    }
}
