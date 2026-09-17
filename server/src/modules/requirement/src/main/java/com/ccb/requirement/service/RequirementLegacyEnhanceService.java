package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.ccb.requirement.support.RequirementEnums;
import com.ccb.requirement.support.RequirementIds;
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

    private final RequirementLegacyEnhanceRepository repository;
    private final RequirementSecurityService security;
    private final RequirementChangeLogService changeLog;

    public RequirementLegacyEnhanceService(RequirementLegacyEnhanceRepository repository, RequirementSecurityService security,
                                           RequirementChangeLogService changeLog) {
        this.repository = repository;
        this.security = security;
        this.changeLog = changeLog;
    }

    // ---------------- 工作量表 / 软需文档 ----------------

    public List<Map<String, Object>> deliverables(long requirementId, String type, AuthUser user) {
        requireType(type);
        requireAccess(requirementId, user);
        return repository.deliverables(user.tenantId(), requirementId, type);
    }

    /** 保存交付件记录；带 id 视为替换（新版本），无 id 新增版本 1.0。历史版本行保留。 */
    @Transactional
    public Map<String, Object> saveDeliverable(long requirementId, String type,
                                               Map<String, Object> body, AuthUser user) {
        requireType(type);
        requireEditable(requirementId, user);
        String docName = RequirementValues.text(body, "doc_name");
        String systemCode = RequirementValues.text(body, "system_code");
        if (docName == null && systemCode == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写文档名称或系统");
        }
        Long systemItemId = body.get("system_item_id") == null || String.valueOf(body.get("system_item_id")).isBlank()
                ? null : Long.parseLong(String.valueOf(body.get("system_item_id")));
        String version = nextDeliverableVersion(requirementId, type, systemItemId, systemCode, user.tenantId());
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
        repository.insertDeliverable(type, values);
        changeLog.recordCreate(bizTypeOf(type), id, values, user, "ONLINE");
        return deliverableRow(type, id, user.tenantId());
    }

    @Transactional
    public void deleteDeliverable(long id, String type, AuthUser user) {
        requireType(type);
        Map<String, Object> row = deliverableRow(type, id, user.tenantId());
        requireEditable(((Number) row.get("requirement_id")).longValue(), user);
        repository.deleteDeliverable(user.tenantId(), id, type);
        changeLog.record(bizTypeOf(type), id, "DELETE", "deleted", "0", "1", user, "ONLINE");
    }

    /** 提交评审：待评审/已退回 → 评审中；与新建项目差异一致，提交时选择审批人并填写评审报告文档名称。 */
    @Transactional
    public Map<String, Object> submitDeliverableReview(long id, String type, List<Long> approverIds,
                                                       String reportDocName, AuthUser user) {
        requireType(type);
        Map<String, Object> row = deliverableRow(type, id, user.tenantId());
        requireEditable(((Number) row.get("requirement_id")).longValue(), user);
        if (approverIds == null || approverIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择审批人");
        }
        String status = String.valueOf(row.get("review_status"));
        if (!"待评审".equals(status) && !"已退回".equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可提交评审：" + status);
        }
        String approverNames = approverNames(user.tenantId(), approverIds);
        repository.submitReview(user.tenantId(), id, type, joinIds(approverIds), approverNames,
                reportDocName == null || reportDocName.isBlank() ? null : reportDocName.substring(0, Math.min(200, reportDocName.length())));
        changeLog.record(bizTypeOf(type), id, "SUBMIT_REVIEW", "review_status", status, "评审中", user, "ONLINE");
        return deliverableRow(type, id, user.tenantId());
    }

    /** 评审确认（被选审批人或 PMO/管理员）：通过/退回，写评审记录并锁定或解锁交付件。 */
    @Transactional
    public Map<String, Object> reviewDeliverable(long id, String type, Map<String, Object> body, AuthUser user) {
        requireType(type);
        Map<String, Object> row = deliverableRow(type, id, user.tenantId());
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
        repository.reviewDeliverable(user.tenantId(), id, type, newStatus, recordId);
        changeLog.record(bizTypeOf(type), id, "REVIEW_RESULT", "review_status",
                String.valueOf(row.get("review_status")), newStatus, user, "ONLINE");
        return deliverableRow(type, id, user.tenantId());
    }

    // ---------------- 协同事项（改造/测试） ----------------

    public List<Map<String, Object>> coordinationItems(long requirementId, AuthUser user) {
        requireAccess(requirementId, user);
        return repository.coordinationItems(user.tenantId(), requirementId);
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
            values.put("id", id); values.put("tenant_id", user.tenantId());
            repository.updateCoordination(values);
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
        repository.insertCoordination(values);
        changeLog.recordCreate("LEGACY_COORDINATION", id, values, user, "ONLINE");
        return coordinationRow(id, user.tenantId());
    }

    @Transactional
    public void deleteCoordination(long id, AuthUser user) {
        Map<String, Object> row = coordinationRow(id, user.tenantId());
        requireEditable(((Number) row.get("requirement_id")).longValue(), user);
        repository.deleteCoordination(user.tenantId(), id);
        changeLog.record("LEGACY_COORDINATION", id, "DELETE", "deleted", "0", "1", user, "ONLINE");
    }

    // ---------------- 评审记录 ----------------

    public List<Map<String, Object>> reviewRecords(String bizType, long bizId, AuthUser user) {
        return repository.reviewRecords(user.tenantId(), bizType, bizId);
    }

    // ---------------- 内部工具 ----------------

    private long writeReviewRecord(String bizType, long bizId, String conclusion, String comment, String remark,
                                   String reportDocName, AuthUser user) {
        long recordId = RequirementIds.next();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", recordId); values.put("tenant_id", user.tenantId()); values.put("biz_type", bizType);
        values.put("biz_id", bizId); values.put("reviewer_id", user.id()); values.put("reviewer_name", user.displayName());
        values.put("conclusion", conclusion); values.put("comment", comment); values.put("remark", remark);
        values.put("report_doc_name", reportDocName); values.put("created_by", user.id());
        repository.insertReviewRecord(values);
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
        List<Map<String, Object>> rows = repository.approverNames(tenantId, approverIds);
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
        if (repository.legacyRequirement(user.tenantId(), requirementId) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存量需求不存在");
        }
    }

    private void requireEditable(long requirementId, AuthUser user) {
        Map<String, Object> row = repository.legacyRequirement(user.tenantId(), requirementId);
        if (row == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存量需求不存在");
        }
        security.requireLegacyEditable(user, row);
    }

    private String nextDeliverableVersion(long requirementId, String type, Long systemItemId,
                                          String systemCode, long tenantId) {
        List<String> versions = repository.versions(tenantId, requirementId, systemItemId, systemCode, type);
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

    private Map<String, Object> deliverableRow(String type, long id, long tenantId) {
        Map<String,Object> row = repository.deliverable(tenantId, id, type);
        if (row == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "交付件记录不存在");
        }
        return row;
    }

    private Map<String, Object> coordinationRow(long id, long tenantId) {
        Map<String,Object> row = repository.coordination(tenantId, id);
        if (row == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "协同事项不存在");
        }
        return row;
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
