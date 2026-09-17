package com.ccb.requirement.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 存量项目常态化需求：需求头 + 系统子表（主责/协同多行）、整条需求流转、版本历史。
 * 页面只显示一条需求，库内按系统子表存放多行；PMO/管理员全量可见，
 * 业务人员仅业务组或系统行负责人可见，且不能推进整体阶段。
 */
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
            "change_conclusion_status", "change_remark", "not_project_developed",
            "version_no", "workload_change", "workload_person_months");

    private static final List<String> DATE_FIELDS = List.of(
            "expected_launch_date", "regulation_launch_date", "requirement_received_date",
            "ba_review_date", "workload_date", "finance_project_date", "soft_submit_date",
            "soft_review_date", "planned_launch_date", "actual_launch_date");

    private final RequirementLegacyRepository repository;
    private final RequirementChangeLogService changeLog;
    private final RequirementSecurityService security;

    public RequirementLegacyService(RequirementLegacyRepository repository, RequirementChangeLogService changeLog,
                                    RequirementSecurityService security) {
        this.repository = repository;
        this.changeLog = changeLog;
        this.security = security;
    }

    public PageResult<Map<String, Object>> list(Long projectId, String businessGroup, String stage, String stageStatus,
                                                String keyword, PageQuery query, AuthUser user) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("tenantId", user.tenantId()); filter.put("projectId", projectId);
        if (businessGroup != null && !businessGroup.isBlank()) {
            filter.put("businessGroup", businessGroup);
        }
        if (stage != null && !stage.isBlank()) {
            if (!RequirementEnums.LEGACY_STAGES.contains(stage)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "阶段不在受控枚举内：" + stage);
            }
            filter.put("stage", stage);
        }
        if (stageStatus != null && !stageStatus.isBlank() && stage != null && !stage.isBlank()) {
            filter.put("stageStatus", stageStatus);
        }
        if (keyword != null && !keyword.isBlank()) {
            filter.put("keyword", keyword);
        }
        filter.put("size", query.size()); filter.put("offset", (query.page() - 1) * query.size());
        long total = repository.count(filter);
        List<Map<String, Object>> records = repository.page(filter);
        Map<Long, List<Map<String, Object>>> itemsByReq = loadSystemItemsByRequirements(records, user);
        boolean admin = security.isAdmin(user);
        for (Map<String, Object> record : records) {
            long reqId = ((Number) record.get("id")).longValue();
            record.put("system_items", itemsByReq.getOrDefault(reqId, List.of()));
            record.put("can_edit", security.canEditLegacy(user, record, admin));
        }
        return new PageResult<>(records, total, query.page(), query.size());
    }

    public Map<String, Object> get(long id, AuthUser user) {
        Map<String, Object> row = row(id, user);
        requireAccessById(id, user);
        row.put("can_edit", security.canEditLegacy(user, row, security.isAdmin(user)));
        return enrich(row, user);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        String businessGroup = RequirementValues.text(body, "business_group");
        // 业务组改为非必填：填写时按业务组做数据范围校验，未填写则仅要求模块访问权限
        if (businessGroup != null && !businessGroup.isBlank()
                && !security.isPmo(user) && !security.isAdmin(user)) {
            security.requireLegacyAccess(user, businessGroup);
        }
        RequirementValues.requireText(body, "requirement_no", "需求编号不能为空");
        RequirementValues.requireText(body, "requirement_name", "需求名称不能为空");
        Map<String, Object> values = normalized(body);
        validate(values);
        long id = RequirementIds.next();
        values.put("id", id);
        values.put("tenant_id", user.tenantId());
        // 创建时绑定当前项目（左上角项目下拉），列表按 project_id 归属展示
        Object projectIdRaw = body.get("project_id");
        if (projectIdRaw != null && !String.valueOf(projectIdRaw).isBlank()) {
            values.put("project_id", Long.parseLong(String.valueOf(projectIdRaw)));
        }
        values.putIfAbsent("current_stage", "PROPOSE");
        // 创建需求后“需求提出”阶段默认为进行中
        values.putIfAbsent("propose_stage_status", "进行中");
        values.putIfAbsent("docking_stage_status", "未开始");
        values.putIfAbsent("workload_stage_status", "未开始");
        values.putIfAbsent("project_stage_status", "未开始");
        values.putIfAbsent("soft_stage_status", "未开始");
        values.putIfAbsent("launch_stage_status", "未开始");
        values.putIfAbsent("source", "ONLINE");
        values.putIfAbsent("version_no", "1.0");
        values.put("created_by", user.id());
        values.put("deleted", 0);
        repository.insert(values);
        saveSystemItems(id, body, user);
        changeLog.recordCreate("LEGACY_REQUIREMENT", id, values, user, "ONLINE");
        Map<String, Object> created = get(id, user);
        writeVersionSnapshot(id, "1.0", "初始版本", created, user);
        return created;
    }

    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> before = row(id, user);
        security.requireLegacyEditable(user, before);
        Map<String, Object> values = normalized(body);
        values.remove("business_group");
        validate(values);
        if (!values.isEmpty()) {
            Map<String, Object> merged = new LinkedHashMap<>(before);
            merged.putAll(values);
            validateCoreFields(merged);
            values.put("updated_by", user.id());
            repository.update(values);
        }
        if (body.containsKey("system_items")) {
            saveSystemItems(id, body, user);
        }
        Map<String, Object> after = row(id, user);
        changeLog.recordFields("LEGACY_REQUIREMENT", id, "UPDATE", before, after, user, "ONLINE");
        return enrich(after, user);
    }

    @Transactional
    public void delete(long id, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireLegacyEditable(user, row);
        repository.softDelete(user.tenantId(), id, user.id());
        repository.deleteChildren(user.tenantId(), id);
        changeLog.record("LEGACY_REQUIREMENT", id, "DELETE", "deleted", "0", "1", user, "ONLINE");
    }

    /**
     * 阶段推进：PMO/管理员或当前流转处理人可推进；普通业务人员不可推进整体阶段。
     * START：未开始→进行中；COMPLETE：进行中→已完成；BACK：进行中→未开始。
     */
    @Transactional
    public Map<String, Object> stageTransition(long id, String stage, String action, String comment,
                                                boolean ignoreMissingStageFields, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireLegacyEditable(user, row);
        if (!RequirementEnums.LEGACY_STAGES.contains(stage)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "阶段不在受控枚举内：" + stage);
        }
        validateCoreFields(row);
        String column = RequirementEnums.LEGACY_STAGE_COLUMNS.get(stage);
        String fromStatus = String.valueOf(row.get(column));
        String toStatus = nextStatus(fromStatus, action);
        if (!"BACK".equalsIgnoreCase(action)) {
            List<String> missing = missingStageFields(row, stage);
            if (!missing.isEmpty() && !ignoreMissingStageFields) {
                Map<String, Object> reminder = new LinkedHashMap<>();
                reminder.put("confirmed", false);
                reminder.put("missingFields", missing);
                return reminder;
            }
        }
        String oldStage = String.valueOf(row.get("current_stage"));
        String oldRequirementStatus = row.get("requirement_status") == null ? null : String.valueOf(row.get("requirement_status"));
        String newRequirementStatus = RequirementEnums.LEGACY_STAGE_ACTION_TO_REQ_STATUS.get(stage + ":" + action);
        if (newRequirementStatus == null) newRequirementStatus = oldRequirementStatus;
        Map<String, Object> transition = new LinkedHashMap<>();
        transition.put("tenantId", user.tenantId()); transition.put("id", id); transition.put("stage", stage);
        transition.put("toStatus", toStatus); transition.put("requirementStatus", newRequirementStatus); transition.put("operatorId", user.id());
        repository.transitionStage(transition);
        long logId = RequirementIds.next();
        Map<String, Object> stageLog = new LinkedHashMap<>(); stageLog.put("id", logId); stageLog.put("tenant_id", user.tenantId()); stageLog.put("requirement_id", id); stageLog.put("from_stage", oldStage); stageLog.put("to_stage", stage); stageLog.put("from_status", fromStatus); stageLog.put("to_status", toStatus); stageLog.put("operator_id", user.id()); stageLog.put("operator_name", user.displayName()); stageLog.put("comment", comment == null ? "" : comment); stageLog.put("approval_result", "MANUAL"); stageLog.put("workflow_instance_id", null); repository.insertStageLog(stageLog);
        changeLog.record("LEGACY_REQUIREMENT", id, "STAGE_TRANSITION", column, fromStatus, toStatus, user, "ONLINE");
        if (newRequirementStatus != null && (oldRequirementStatus == null || !oldRequirementStatus.equals(newRequirementStatus))) {
            changeLog.record("LEGACY_REQUIREMENT", id, "STAGE_TRANSITION", "requirement_status",
                    oldRequirementStatus, newRequirementStatus, user, "ONLINE");
        }
        if ("COMPLETE".equalsIgnoreCase(action)) {
            insertFlowLog(id, "COMPLETE", user, null, null, "阶段完成（进入下一阶段）", user.tenantId());
        }
        return get(id, user);
    }

    public List<Map<String, Object>> stageLogs(long id, AuthUser user) {
        get(id, user);
        return repository.stageLogs(user.tenantId(), id);
    }

    public List<Map<String, Object>> changes(long id, AuthUser user) {
        get(id, user);
        return changeLog.list("LEGACY_REQUIREMENT", id, user);
    }

    // ---------------- 需求成员（参考新建项目 req_project_member） ----------------

    public List<Map<String, Object>> members(long id, AuthUser user) {
        get(id, user);
        return repository.members(user.tenantId(), id);
    }

    @Transactional
    public Map<String, Object> addMember(long id, Map<String, Object> body, AuthUser user) {
        security.requireLegacyMemberManage(user);
        get(id, user);
        long userId = RequirementValues.intOf(body.get("userId"), 0);
        String role = RequirementValues.text(body, "memberRole");
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "成员用户不能为空");
        }
        if (repository.userCount(user.tenantId(), userId) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "成员用户不存在");
        }
        if (repository.legacyMemberCount(user.tenantId(), id, userId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该用户已是需求成员");
        }
        long memberId = RequirementIds.next();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", memberId);
        values.put("tenant_id", user.tenantId());
        values.put("requirement_id", id);
        values.put("user_id", userId);
        values.put("user_name", repository.userName(user.tenantId(), userId));
        values.put("member_role", role == null || role.isBlank() ? "MEMBER" : role);
        values.put("created_by", user.id());
        values.put("deleted", 0);
        repository.insertMember(values);
        return repository.member(user.tenantId(), memberId);
    }

    @Transactional
    public void removeMember(long memberId, AuthUser user) {
        security.requireLegacyMemberManage(user);
        repository.deleteMember(user.tenantId(), memberId);
    }

    // ---------------- 系统子表 ----------------

    public List<Map<String, Object>> systemItems(long id, AuthUser user) {
        requireAccessById(id, user);
        List<Map<String, Object>> items = repository.systemItems(user.tenantId(), id);
        if (!items.isEmpty()) {
            Map<Long, List<Map<String, Object>>> membersByItem = loadMembersByItems(items, user.tenantId());
            for (Map<String, Object> item : items) {
                item.put("members", membersByItem.getOrDefault(((Number) item.get("id")).longValue(), List.of()));
            }
        }
        return items;
    }

    private Map<Long, List<Map<String, Object>>> loadSystemItemsByRequirements(List<Map<String, Object>> records,
                                                                               AuthUser user) {
        Map<Long, List<Map<String, Object>>> result = new LinkedHashMap<>();
        if (records.isEmpty()) {
            return result;
        }
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> record : records) {
            ids.add(((Number) record.get("id")).longValue());
        }
        List<Map<String, Object>> items = repository.systemItemsForRequirements(user.tenantId(), ids);
        for (Map<String, Object> item : items) {
            result.computeIfAbsent(((Number) item.get("requirement_id")).longValue(),
                    key -> new ArrayList<>()).add(item);
        }
        return result;
    }

    private Map<Long, List<Map<String, Object>>> loadMembersByItems(List<Map<String, Object>> items, long tenantId) {
        Map<Long, List<Map<String, Object>>> result = new LinkedHashMap<>();
        if (items.isEmpty()) {
            return result;
        }
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> item : items) {
            ids.add(((Number) item.get("id")).longValue());
        }
        List<Map<String, Object>> members = repository.systemMembersForItems(tenantId, ids);
        for (Map<String, Object> member : members) {
            result.computeIfAbsent(((Number) member.get("system_item_id")).longValue(),
                    key -> new ArrayList<>()).add(member);
        }
        return result;
    }

    /** 替换式保存系统子表：先软删旧行再插入新行（事务内，校验失败整体回滚）。 */
    private void saveSystemItems(long requirementId, Map<String, Object> body, AuthUser user) {
        Object raw = body.get("system_items");
        if (!(raw instanceof List<?> list)) {
            return;
        }
        repository.replaceSystemItems(user.tenantId(), requirementId);
        boolean hasOwner = false;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) m;
            String role = RequirementValues.text(item, "system_role");
            if (!"主责".equals(role)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "系统子表行仅支持主责，不支持协同");
            }
            String code = RequirementValues.text(item, "system_code");
            String name = RequirementValues.text(item, "system_name");
            if (code == null && name == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "系统子表行缺少系统编号或名称");
            }
            if ("主责".equals(role)) {
                hasOwner = true;
            }
            Object ownerIdRaw = item.get("owner_user_id");
            Long ownerUserId = ownerIdRaw == null || String.valueOf(ownerIdRaw).isBlank()
                    ? null : Long.parseLong(String.valueOf(ownerIdRaw));
            long itemId = RequirementIds.next();
            Map<String, Object> value = new LinkedHashMap<>(); value.put("id", itemId); value.put("tenant_id", user.tenantId()); value.put("requirement_id", requirementId); value.put("system_role", role); value.put("system_code", code); value.put("system_name", name); value.put("owner_user_id", ownerUserId); value.put("owner_user_name", RequirementValues.text(item, "owner_user_name")); value.put("remark", RequirementValues.text(item, "remark")); value.put("created_by", user.id()); repository.insertSystemItem(value);
            saveSystemMembers(itemId, item.get("members"), user);
        }
        if (!hasOwner) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "至少需要一个主责系统");
        }
    }

    /** 保存系统行成员（可重复提交；同人重复时幂等恢复未删除）。 */
    private void saveSystemMembers(long systemItemId, Object rawMembers, AuthUser user) {
        if (!(rawMembers instanceof List<?> members)) {
            return;
        }
        for (Object m : members) {
            if (m == null) {
                continue;
            }
            Long memberUserId;
            if (m instanceof Number number) {
                memberUserId = number.longValue();
            } else if (m instanceof Map<?, ?> map && map.get("user_id") != null) {
                memberUserId = Long.parseLong(String.valueOf(map.get("user_id")));
            } else {
                memberUserId = Long.parseLong(String.valueOf(m));
            }
            String userName = lookupUserName(memberUserId, user.tenantId());
            Map<String, Object> value = new LinkedHashMap<>(); value.put("id", RequirementIds.next()); value.put("tenant_id", user.tenantId()); value.put("system_item_id", systemItemId); value.put("user_id", memberUserId); value.put("user_name", userName); value.put("created_by", user.id()); repository.upsertSystemMember(value);
        }
    }

    private String lookupUserName(long userId, long tenantId) {
        return repository.userName(tenantId, userId);
    }

    // ---------------- 整条需求流转 ----------------

    public List<Map<String, Object>> flowLogs(long id, AuthUser user) {
        requireAccessById(id, user);
        return repository.flowLogs(user.tenantId(), id);
    }

    @Transactional
    public Map<String, Object> sendFlow(long id, long toUserId, String comment, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireLegacyEditable(user, row);
        Map<String, Object> target = repository.activeUser(user.tenantId(), toUserId);
        if (target == null || target.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "流转目标用户不存在或已停用");
        }
        String targetName = target.get("display_name") == null ? "" : String.valueOf(target.get("display_name"));
        Object oldAssignee = row.get("current_flow_user_id");
        repository.updateFlow(user.tenantId(), id, toUserId, targetName, user.id());
        insertFlowLog(id, "SEND", user, toUserId, targetName, comment, user.tenantId());
        changeLog.record("LEGACY_REQUIREMENT", id, "FLOW_SEND", "current_flow_user_id",
                String.valueOf(oldAssignee), String.valueOf(toUserId), user, "ONLINE");
        return get(id, user);
    }

    @Transactional
    public Map<String, Object> returnFlow(long id, String comment, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireLegacyEditable(user, row);
        Object current = row.get("current_flow_user_id");
        if (current == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前无流转处理人，无需回传");
        }
        // 回传给最近一次 SEND 的发起人（无则清空）
        Long backUserId = null;
        String backUserName = null;
        Map<String, Object> sender = repository.latestSender(user.tenantId(), id);
        if (sender != null && sender.get("from_user_id") != null) {
            backUserId = ((Number) sender.get("from_user_id")).longValue();
            backUserName = sender.get("from_user_name") == null ? null : String.valueOf(sender.get("from_user_name"));
        }
        repository.updateFlow(user.tenantId(), id, backUserId, backUserName, user.id());
        insertFlowLog(id, "RETURN", user, backUserId, backUserName, comment, user.tenantId());
        changeLog.record("LEGACY_REQUIREMENT", id, "FLOW_RETURN", "current_flow_user_id",
                String.valueOf(current), String.valueOf(backUserId), user, "ONLINE");
        return get(id, user);
    }

    private boolean isCurrentFlowAssignee(Map<String, Object> row, AuthUser user) {
        Object current = row.get("current_flow_user_id");
        return current != null && ((Number) current).longValue() == user.id();
    }

    private void insertFlowLog(long requirementId, String action, AuthUser user,
                               Long toUserId, String toUserName, String comment, long tenantId) {
        Map<String, Object> value = new LinkedHashMap<>(); value.put("id", RequirementIds.next()); value.put("tenant_id", tenantId); value.put("requirement_id", requirementId); value.put("action", action); value.put("from_user_id", user.id()); value.put("from_user_name", user.displayName()); value.put("to_user_id", toUserId); value.put("to_user_name", toUserName); value.put("comment", comment == null ? "" : comment); repository.insertFlowLog(value);
    }

    // ---------------- 版本历史 ----------------

    public List<Map<String, Object>> versions(long id, AuthUser user) {
        requireAccessById(id, user);
        return repository.versions(user.tenantId(), id);
    }

    /** 需求变更：保存变更字段；涉及变更时版本递增并写版本快照（历史版本保留）。 */
    @Transactional
    public Map<String, Object> saveChange(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> before = row(id, user);
        security.requireLegacyEditable(user, before);
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : List.of("change_involved", "change_info", "change_review_conclusion",
                "change_conclusion_status", "change_remark", "workload_change")) {
            Object v = body.get(field);
            if (v != null) {
                values.put(field, v);
            }
        }
        RequirementValues.requireOption("yesNo", RequirementValues.text(values, "change_involved"));
        RequirementValues.requireOption("changeReviewConclusions", RequirementValues.text(values, "change_review_conclusion"));
        RequirementValues.requireOption("changeConclusionStatuses", RequirementValues.text(values, "change_conclusion_status"));
        if (!values.isEmpty()) {
            values.put("updated_by", user.id());
            repository.update(values);
        }
        Map<String, Object> after = row(id, user);
        changeLog.recordFields("LEGACY_REQUIREMENT", id, "CHANGE", before, after, user, "ONLINE");
        if ("是".equals(String.valueOf(after.get("change_involved")))) {
            String currentVersion = String.valueOf(after.get("version_no") == null ? "1.0" : after.get("version_no"));
            String next = nextVersion(currentVersion);
            if (!next.equals(currentVersion)) {
                repository.updateVersion(user.tenantId(), id, next, user.id());
            }
            String summary = after.get("change_info") == null ? "" : String.valueOf(after.get("change_info"));
            writeVersionSnapshot(id, next, summary, after, user);
            after = row(id, user);
        }
        return enrich(after, user);
    }

    private String nextVersion(String current) {
        try {
            double version = Double.parseDouble(current.trim());
            return String.format(Locale.ROOT, "%.1f", version + 1.0);
        } catch (NumberFormatException e) {
            return "2.0";
        }
    }

    private void writeVersionSnapshot(long id, String versionNo, String summary,
                                      Map<String, Object> row, AuthUser user) {
        try {
            String snapshot = new ObjectMapper().writeValueAsString(row);
            Map<String, Object> value = new LinkedHashMap<>(); value.put("id", RequirementIds.next()); value.put("tenant_id", user.tenantId()); value.put("requirement_id", id); value.put("version_no", versionNo); value.put("change_summary", summary == null ? "" : summary); value.put("snapshot_json", snapshot); value.put("created_by", user.id()); repository.upsertVersion(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "版本快照保存失败：" + e.getMessage());
        }
    }

    // ---------------- 内部工具 ----------------

    private Map<String, Object> enrich(Map<String, Object> row, AuthUser user) {
        long id = ((Number) row.get("id")).longValue();
        row.put("system_items", systemItems(id, user));
        row.put("flow_logs", flowLogs(id, user));
        row.put("versions", versions(id, user));
        return row;
    }

    private void requireAccessById(long id, AuthUser user) {
        if (!repository.exists(user.tenantId(), id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存量需求不存在");
        }
    }

    Map<String, Object> row(long id, AuthUser user) {
        Map<String, Object> row = repository.find(user.tenantId(), id);
        if (row == null || row.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存量需求不存在");
        }
        return row;
    }

    private void validateCoreFields(Map<String, Object> row) {
        for (String field : RequirementEnums.LEGACY_CORE_REQUIRED_FIELDS) {
            Object v = row.get(field);
            if (v == null || (v instanceof String s && s.isBlank())) {
                String label = RequirementEnums.FIELD_LABELS.getOrDefault(field, field);
                throw new BusinessException(ErrorCode.BAD_REQUEST, "核心标识字段不能为空：" + label);
            }
        }
    }

    private List<String> missingStageFields(Map<String, Object> row, String targetStage) {
        List<String> missing = new ArrayList<>();
        int targetIdx = RequirementEnums.LEGACY_STAGES.indexOf(targetStage);
        for (int i = 0; i <= targetIdx && i < RequirementEnums.LEGACY_STAGES.size(); i++) {
            String stage = RequirementEnums.LEGACY_STAGES.get(i);
            List<String> fields = RequirementEnums.LEGACY_STAGE_FIELDS.get(stage);
            if (fields == null) continue;
            for (String field : fields) {
                if (RequirementEnums.LEGACY_CORE_REQUIRED_FIELDS.contains(field)) continue;
                Object v = row.get(field);
                if (v == null || (v instanceof String s && s.isBlank())) {
                    missing.add(RequirementEnums.FIELD_LABELS.getOrDefault(field, field));
                }
            }
        }
        return missing;
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
