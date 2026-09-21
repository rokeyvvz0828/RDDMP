package com.ccb.requirement.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.requirement.support.RequirementEnums;
import com.ccb.requirement.support.RequirementIds;
import com.ccb.requirement.support.RequirementSql;
import com.ccb.requirement.support.RequirementValues;
import com.ccb.security.model.AuthUser;
import com.ccb.system.notification.NotificationLevel;
import com.ccb.system.notification.NotificationPublishCommand;
import com.ccb.system.notification.SystemNotificationPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 存量项目常态化需求：一条主表记录（requirement_kind = LEGACY）+ 存量专有字段
 * + 需求-物理子系统关联行（主责 1 行、改造/测试若干行，不含系统人员）；
 * 整条需求流转与版本历史沿用旧口径，仅把存储切到统一主表与统一流转日志。
 */
@Service
public class RequirementLegacyService {
    private static final Logger log = LoggerFactory.getLogger(RequirementLegacyService.class);
    private static final String KIND = "LEGACY";

    /** 主表/详情字段拆分口径与导入共用同一份定义。 */
    private static final List<String> LEGACY_MAIN_FIELDS = RequirementEnums.LEGACY_MAIN_FIELDS;
    private static final List<String> LEGACY_DETAIL_FIELDS = RequirementEnums.LEGACY_DETAIL_FIELDS;
    private static final List<String> DATE_FIELDS = RequirementEnums.LEGACY_DATE_FIELDS;

    private static final List<String> SELECT_COLUMNS = List.of(
            "r.id", "r.project_id", "d.legacy_doc_name", "r.requirement_no",
            "r.name AS requirement_name", "r.summary AS content_summary", "d.propose_dept",
            "d.proposer", "d.monshang_ba", "d.monshang_architect", "d.expected_launch_date",
            "d.regulator", "d.regulation_doc_no", "d.regulation_desc", "d.regulation_launch_date",
            "d.requirement_received_date", "d.requirement_type", "d.regulation_category",
            "r.business_group", "d.sub_group", "d.jinke_contact", "d.need_jinke_arch_decision",
            "d.jinke_architect", "d.unified_managed", "d.ba_review_date", "d.workload_date",
            "d.finance_project_date", "d.soft_doc_name", "d.owner_conglomerate", "d.owner_system",
            "d.owner_contact", "d.involve_cooperation", "d.coord_conglomerate", "d.coord_system",
            "d.soft_submit_date", "d.soft_review_date", "d.planned_launch_date",
            "d.actual_launch_date", "d.launch_mode", "d.requirement_status", "d.remark",
            "d.change_involved", "d.change_info", "d.change_review_conclusion",
            "d.change_conclusion_status", "d.change_remark", "d.not_project_developed",
            "r.version_no", "d.workload_change", "d.workload_person_months",
            "r.current_handler_user_id AS current_flow_user_id",
            "r.current_handler_user_name AS current_flow_user_name", "r.created_by", "r.current_stage",
            "d.propose_stage_status", "d.docking_stage_status", "d.workload_stage_status",
            "d.project_stage_status", "d.soft_stage_status", "d.launch_stage_status",
            "r.source", "r.created_at", "r.updated_at");

    private static final String FROM = """
            FROM req_requirement r
            JOIN req_legacy_detail d ON d.requirement_id = r.id AND d.tenant_id = r.tenant_id
            """;

    private final JdbcTemplate jdbc;
    private final RequirementChangeLogService changeLog;
    private final RequirementSecurityService security;
    private final RequirementProjectMemberService memberDirectory;
    private final RequirementSystemService systemService;
    private final SystemNotificationPublisher notifications;

    public RequirementLegacyService(JdbcTemplate jdbc, RequirementChangeLogService changeLog,
                                    RequirementSecurityService security,
                                    RequirementProjectMemberService memberDirectory,
                                    RequirementSystemService systemService,
                                    SystemNotificationPublisher notifications) {
        this.jdbc = jdbc;
        this.changeLog = changeLog;
        this.security = security;
        this.memberDirectory = memberDirectory;
        this.systemService = systemService;
        this.notifications = notifications;
    }

    public PageResult<Map<String, Object>> list(Long projectId, String businessGroup, String stage, String stageStatus,
                                                String keyword, PageQuery query, AuthUser user) {
        StringBuilder where = new StringBuilder(" WHERE r.tenant_id = ? AND r.deleted = 0"
                + " AND r.requirement_kind = 'LEGACY'");
        List<Object> params = new ArrayList<>(List.of(user.tenantId()));
        if (projectId != null) {
            // 存量需求与顶部项目关联：project_id 为项目管理主键
            where.append(" AND r.project_id = ?");
            params.add(projectId);
        }
        if (businessGroup != null && !businessGroup.isBlank()) {
            where.append(" AND r.business_group = ?");
            params.add(businessGroup);
        }
        if (stage != null && !stage.isBlank()) {
            if (!RequirementEnums.LEGACY_STAGES.contains(stage)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "阶段不在受控枚举内：" + stage);
            }
            where.append(" AND r.current_stage = ?");
            params.add(stage);
        }
        if (stageStatus != null && !stageStatus.isBlank() && stage != null && !stage.isBlank()) {
            where.append(" AND d.").append(RequirementSql.quote(RequirementEnums.LEGACY_STAGE_COLUMNS.get(stage)))
                    .append(" = ?");
            params.add(stageStatus);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (r.name LIKE ? OR r.requirement_no LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        // 数据范围：统筹/管理员可见项目全量；其余人只看本人提交、当前在本人名下或曾流转经手的需求
        where.append(security.legacyScopeSql("r", user, params));
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM + where, Long.class, params.toArray());
        params.add(query.size());
        params.add((query.page() - 1) * query.size());
        List<Map<String, Object>> records = jdbc.queryForList(
                "SELECT " + String.join(", ", SELECT_COLUMNS) + " " + FROM + where
                        + " ORDER BY r.updated_at DESC, r.id DESC LIMIT ? OFFSET ?", params.toArray());
        Map<Long, List<Map<String, Object>>> itemsByReq = loadSystemItemsByRequirements(records, user);
        boolean admin = security.isAdmin(user);
        for (Map<String, Object> record : records) {
            long reqId = ((Number) record.get("id")).longValue();
            record.put("system_items", itemsByReq.getOrDefault(reqId, List.of()));
            record.put("can_edit", security.canEditLegacy(user, record, admin));
        }
        return new PageResult<>(records, total == null ? 0 : total, query.page(), query.size());
    }

    public Map<String, Object> get(long id, AuthUser user) {
        Map<String, Object> row = row(id, user);
        requireAccessById(id, user);
        row.put("can_edit", security.canEditLegacy(user, row, security.isAdmin(user)));
        return enrich(row, user);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        RequirementValues.requireText(body, "requirement_no", "需求编号不能为空");
        RequirementValues.requireText(body, "requirement_name", "需求名称不能为空");
        Map<String, Object> values = normalized(body);
        validate(values);
        long id = RequirementIds.next();
        Map<String, Object> main = new LinkedHashMap<>();
        main.put("id", id);
        main.put("tenant_id", user.tenantId());
        Object projectIdRaw = body.get("project_id");
        if (projectIdRaw == null || String.valueOf(projectIdRaw).isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先切换到所属项目后再新增存量需求");
        }
        main.put("project_id", Long.parseLong(String.valueOf(projectIdRaw)));
        main.put("requirement_kind", KIND);
        main.put("requirement_no", RequirementValues.text(values, "requirement_no"));
        main.put("name", RequirementValues.text(values, "requirement_name"));
        main.put("summary", RequirementValues.text(values, "content_summary"));
        main.put("business_group", RequirementValues.text(values, "business_group"));
        main.put("current_stage", "PROPOSE");
        main.put("version_no", "1.0");
        main.put("source", "ONLINE");
        main.put("created_by", user.id());
        // 谁创建谁就是初始处理人
        main.put("current_handler_user_id", user.id());
        main.put("current_handler_user_name", user.displayName());
        main.put("deleted", 0);
        RequirementSql.insert(jdbc, "req_requirement", main);

        Map<String, Object> detail = detailValues(id, user.tenantId(), values);
        // 创建需求后"需求提出"阶段默认为进行中
        detail.putIfAbsent("propose_stage_status", "进行中");
        for (String statusField : RequirementEnums.LEGACY_STAGE_COLUMNS.values()) {
            if (!"propose_stage_status".equals(statusField)) {
                detail.putIfAbsent(statusField, "未开始");
            }
        }
        RequirementSql.insert(jdbc, "req_legacy_detail", detail);

        // 存量需求创建时不强制系统行：离开"需求对接"阶段前必须齐备（主责唯一 + 协同至少一个）
        saveSystemItems(id, ((Number) main.get("project_id")).longValue(), body, user);
        changeLog.recordCreate(KIND, id, main, user, "ONLINE");
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
        Map<String, Object> main = new LinkedHashMap<>();
        if (values.containsKey("requirement_name")) {
            main.put("name", values.get("requirement_name"));
        }
        if (values.containsKey("content_summary")) {
            main.put("summary", values.get("content_summary"));
        }
        if (!main.isEmpty()) {
            Map<String, Object> merged = new LinkedHashMap<>(before);
            merged.putAll(main);
            validateCoreFields(merged);
            main.put("updated_by", user.id());
            RequirementSql.update(jdbc, "req_requirement", id, user.tenantId(), main);
        }
        Map<String, Object> detail = detailValues(id, user.tenantId(), values);
        detail.remove("requirement_id");
        detail.remove("tenant_id");
        if (!detail.isEmpty()) {
            RequirementSql.updateBy(jdbc, "req_legacy_detail", "requirement_id", id, user.tenantId(), detail);
        }
        if (body.containsKey("system_items")) {
            saveSystemItems(id, ((Number) before.get("project_id")).longValue(), body, user);
        }
        Map<String, Object> after = row(id, user);
        changeLog.recordFields(KIND, id, "UPDATE", before, after, user, "ONLINE");
        return enrich(after, user);
    }

    @Transactional
    public void delete(long id, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireLegacyEditable(user, row);
        jdbc.update("UPDATE req_requirement SET deleted = 1, updated_by = ? WHERE tenant_id = ? AND id = ?",
                user.id(), user.tenantId(), id);
        jdbc.update("UPDATE req_requirement_system SET deleted = 1 WHERE tenant_id = ? AND requirement_id = ?",
                user.tenantId(), id);
        jdbc.update("UPDATE req_flow_log SET deleted = 1 WHERE tenant_id = ? AND requirement_id = ?",
                user.tenantId(), id);
        jdbc.update("UPDATE req_requirement_version SET deleted = 1 WHERE tenant_id = ? AND requirement_id = ?",
                user.tenantId(), id);
        jdbc.update("UPDATE req_legacy_deliverable SET deleted = 1 WHERE tenant_id = ? AND requirement_id = ?",
                user.tenantId(), id);
        changeLog.record(KIND, id, "DELETE", "deleted", "0", "1", user, "ONLINE");
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
            // 需求对接要进入下一阶段：必须已有主责系统与至少一个协同系统（硬拦截，不提供确认继续）
            if ("DOCKING".equals(stage) && "COMPLETE".equalsIgnoreCase(action)) {
                requireDockingSystemItems(id, user);
            }
            List<String> missing = missingStageFields(row, stage);
            if (!missing.isEmpty() && !ignoreMissingStageFields) {
                Map<String, Object> reminder = new LinkedHashMap<>();
                reminder.put("confirmed", false);
                reminder.put("missingFields", missing);
                return reminder;
            }
        }
        String oldStage = String.valueOf(row.get("current_stage"));
        String oldRequirementStatus = row.get("requirement_status") == null
                ? null : String.valueOf(row.get("requirement_status"));
        String newRequirementStatus = RequirementEnums.LEGACY_STAGE_ACTION_TO_REQ_STATUS.get(stage + ":" + action);
        if (newRequirementStatus == null) newRequirementStatus = oldRequirementStatus;
        jdbc.update("UPDATE req_legacy_detail SET " + RequirementSql.quote(column)
                        + " = ?, requirement_status = ? WHERE tenant_id = ? AND requirement_id = ?",
                toStatus, newRequirementStatus, user.tenantId(), id);
        jdbc.update("UPDATE req_requirement SET current_stage = ?, workflow_instance_id = NULL, updated_by = ?"
                        + " WHERE tenant_id = ? AND id = ?",
                stage, user.id(), user.tenantId(), id);
        long logId = RequirementIds.next();
        jdbc.update("""
                INSERT INTO req_stage_log (id, tenant_id, requirement_id, from_stage, to_stage, from_status, to_status, operator_id, operator_name, comment, approval_result, workflow_instance_id, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, logId, user.tenantId(), id, oldStage, stage, fromStatus, toStatus,
                user.id(), user.displayName(), comment == null ? "" : comment, "MANUAL", null);
        changeLog.record(KIND, id, "STAGE_TRANSITION", column, fromStatus, toStatus, user, "ONLINE");
        if (newRequirementStatus != null && (oldRequirementStatus == null
                || !oldRequirementStatus.equals(newRequirementStatus))) {
            changeLog.record(KIND, id, "STAGE_TRANSITION", "requirement_status",
                    oldRequirementStatus, newRequirementStatus, user, "ONLINE");
        }
        if ("COMPLETE".equalsIgnoreCase(action)) {
            insertFlowLog(id, "COMPLETE", user, null, null, "阶段完成（进入下一阶段）", user.tenantId());
        }
        return get(id, user);
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
        return changeLog.list(KIND, id, user);
    }

    // ---------------- 系统行（需求 ↔ 架构物理子系统） ----------------

    public List<Map<String, Object>> systemItems(long id, AuthUser user) {
        requireAccessById(id, user);
        return selectSystemItems(" AND requirement_id = ?", user.tenantId(), id);
    }

    private List<Map<String, Object>> selectSystemItems(String extra, Object... args) {
        return jdbc.queryForList("""
                SELECT id, requirement_id, physical_subsystem_id, subsystem_code AS system_code,
                       subsystem_name AS system_name, system_role, owner_user_id, owner_user_name,
                       start_date, end_date, status, description, remark, created_at
                FROM req_requirement_system
                WHERE tenant_id = ? AND deleted = 0
                """ + extra + " ORDER BY FIELD(system_role, 'LEAD', 'CHANGE', 'TEST'), id", args)
                .stream().map(RequirementLegacyService::toItemView).toList();
    }

    private static Map<String, Object> toItemView(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>(row);
        item.put("system_role", RequirementEnums.systemRoleLabel(String.valueOf(row.get("system_role"))));
        return item;
    }

    private Map<Long, List<Map<String, Object>>> loadSystemItemsByRequirements(List<Map<String, Object>> records,
                                                                               AuthUser user) {
        Map<Long, List<Map<String, Object>>> result = new LinkedHashMap<>();
        if (records.isEmpty()) {
            return result;
        }
        List<Object> params = new ArrayList<>(List.of(user.tenantId()));
        StringBuilder in = new StringBuilder(" (");
        for (Map<String, Object> record : records) {
            if (in.length() > 2) in.append(", ");
            in.append("?");
            params.add(((Number) record.get("id")).longValue());
        }
        in.append(")");
        for (Map<String, Object> item : selectSystemItems(" AND requirement_id IN " + in, params.toArray())) {
            result.computeIfAbsent(((Number) item.get("requirement_id")).longValue(),
                    key -> new ArrayList<>()).add(item);
        }
        return result;
    }

    /** 替换式保存系统行：先软删旧行再插入新行（事务内，校验失败整体回滚）。 */
    private void saveSystemItems(long requirementId, long projectId, Map<String, Object> body, AuthUser user) {
        Object raw = body.get("system_items");
        if (!(raw instanceof List<?> list)) {
            return;
        }
        jdbc.update("UPDATE req_requirement_system SET deleted = 1, updated_by = ?"
                + " WHERE tenant_id = ? AND requirement_id = ?", user.id(), user.tenantId(), requirementId);
        int leadCount = 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) m;
            String roleLabel = RequirementValues.text(item, "system_role");
            if (roleLabel == null) {
                roleLabel = RequirementValues.text(item, "item_type");
            }
            String roleCode = RequirementEnums.systemRoleCode(roleLabel);
            if (roleCode == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "系统事项类型仅支持：主责/改造/测试");
            }
            if (RequirementEnums.SYSTEM_ROLE_LEAD.equals(roleCode)) {
                leadCount++;
                if (leadCount > 1) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "一个需求只能有一个主责系统");
                }
            }
            String code = RequirementValues.text(item, "system_code");
            String name = RequirementValues.text(item, "system_name");
            if (code == null && name == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "系统事项行缺少系统编号或名称");
            }
            String status = RequirementValues.text(item, "status");
            if (status == null) {
                status = "未开始";
            }
            if (!RequirementEnums.SYSTEM_ROLE_LEAD.equals(roleCode)
                    && !RequirementEnums.COORD_STATUSES.contains(status)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "事项状态不在受控枚举内：" + status);
            }
            Object ownerIdRaw = item.get("owner_user_id");
            Long ownerUserId = ownerIdRaw == null || String.valueOf(ownerIdRaw).isBlank()
                    ? null : Long.parseLong(String.valueOf(ownerIdRaw));
            // 物理子系统主数据来自架构管理：按主键/编码校验并回填编码与名称快照
            RequirementSystemService.SystemSelection system = systemService.resolveSelection(
                    projectId, item, user);
            jdbc.update("""
                    INSERT INTO req_requirement_system
                    (id, tenant_id, requirement_id, physical_subsystem_id, subsystem_code, subsystem_name,
                     system_role, owner_user_id, owner_user_name, status, start_date, end_date,
                     description, remark, created_by, deleted)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, RequirementIds.next(), user.tenantId(), requirementId, system.subsystemId(),
                    system.subsystemCode(), system.subsystemName(), roleCode, ownerUserId,
                    RequirementValues.text(item, "owner_user_name"),
                    status,
                    RequirementEnums.SYSTEM_ROLE_LEAD.equals(roleCode) ? null : RequirementValues.date(item.get("start_date")),
                    RequirementEnums.SYSTEM_ROLE_LEAD.equals(roleCode) ? null : RequirementValues.date(item.get("end_date")),
                    RequirementEnums.SYSTEM_ROLE_LEAD.equals(roleCode) ? null : RequirementValues.text(item, "description"),
                    RequirementValues.text(item, "remark"), user.id());
        }
    }

    private String lookupUserName(long userId, long tenantId) {
        List<String> names = jdbc.queryForList(
                "SELECT display_name FROM sys_user WHERE tenant_id = ? AND id = ? AND deleted = 0",
                String.class, tenantId, userId);
        return names.isEmpty() ? null : names.get(0);
    }

    // ---------------- 整条需求流转 ----------------

    public List<Map<String, Object>> flowLogs(long id, AuthUser user) {
        requireAccessById(id, user);
        return jdbc.queryForList("""
                SELECT action, from_user_id, from_user_name, to_user_id, to_user_name, comment, created_at
                FROM req_flow_log WHERE tenant_id = ? AND requirement_id = ? AND deleted = 0
                ORDER BY created_at DESC, id DESC
                """, user.tenantId(), id);
    }

    @Transactional
    public Map<String, Object> sendFlow(long id, long toUserId, String comment, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireLegacyEditable(user, row);
        Map<String, Object> project = requireProjectContext(row, user);
        String projectCode = String.valueOf(project.get("project_code"));
        String targetName = memberDirectory.requireMemberName(projectCode, toUserId, user);
        Object oldAssignee = row.get("current_flow_user_id");
        jdbc.update("UPDATE req_requirement SET current_handler_user_id = ?, current_handler_user_name = ?,"
                        + " updated_by = ? WHERE tenant_id = ? AND id = ?",
                toUserId, targetName, user.id(), user.tenantId(), id);
        long flowLogId = insertFlowLog(id, "SEND", user, toUserId, targetName, comment, user.tenantId());
        changeLog.record(KIND, id, "FLOW_SEND", "current_flow_user_id",
                String.valueOf(oldAssignee), String.valueOf(toUserId), user, "ONLINE");
        publishFlowNotification(id, row, toUserId, user, flowLogId, false);
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
        List<Map<String, Object>> sends = jdbc.queryForList(
                "SELECT from_user_id, from_user_name FROM req_flow_log WHERE tenant_id = ? AND requirement_id = ?"
                        + " AND action = 'SEND' AND deleted = 0 ORDER BY id DESC LIMIT 1",
                user.tenantId(), id);
        if (!sends.isEmpty() && sends.get(0).get("from_user_id") != null) {
            backUserId = ((Number) sends.get(0).get("from_user_id")).longValue();
            backUserName = sends.get(0).get("from_user_name") == null ? null
                    : String.valueOf(sends.get(0).get("from_user_name"));
        }
        jdbc.update("UPDATE req_requirement SET current_handler_user_id = ?, current_handler_user_name = ?,"
                        + " updated_by = ? WHERE tenant_id = ? AND id = ?",
                backUserId, backUserName, user.id(), user.tenantId(), id);
        long flowLogId = insertFlowLog(id, "RETURN", user, backUserId, backUserName, comment, user.tenantId());
        changeLog.record(KIND, id, "FLOW_RETURN", "current_flow_user_id",
                String.valueOf(current), String.valueOf(backUserId), user, "ONLINE");
        if (backUserId != null) {
            publishFlowNotification(id, row, backUserId, user, flowLogId, true);
        }
        return get(id, user);
    }

    /**
     * 提出人收回：把当前流转处理人改回提出人本人。
     * 不校验接收人是否已处理、不限制已流转几手；仅需求提出人与需求统筹管理员可调用。
     */
    @Transactional
    public Map<String, Object> withdrawFlow(long id, String comment, AuthUser user) {
        security.requireWithdrawPermission(user);
        Map<String, Object> row = row(id, user);
        Object creator = row.get("created_by");
        long creatorId = creator instanceof Number number ? number.longValue() : user.id();
        if (creatorId != user.id() && !security.isPmo(user) && !security.isAdmin(user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅需求提出人或需求统筹管理员可收回该需求");
        }
        String creatorName = lookupUserName(creatorId, user.tenantId());
        Object current = row.get("current_flow_user_id");
        jdbc.update("UPDATE req_requirement SET current_handler_user_id = ?, current_handler_user_name = ?,"
                        + " updated_by = ? WHERE tenant_id = ? AND id = ?",
                creatorId, creatorName, user.id(), user.tenantId(), id);
        insertFlowLog(id, "WITHDRAW", user, creatorId, creatorName,
                comment == null ? "提出人收回，重新流转" : comment, user.tenantId());
        changeLog.record(KIND, id, "FLOW_WITHDRAW", "current_flow_user_id",
                current == null ? null : String.valueOf(current), String.valueOf(creatorId), user, "ONLINE");
        return get(id, user);
    }

    private long insertFlowLog(long requirementId, String action, AuthUser user,
                               Long toUserId, String toUserName, String comment, long tenantId) {
        long logId = RequirementIds.next();
        jdbc.update("""
                INSERT INTO req_flow_log (id, tenant_id, requirement_kind, requirement_id, action, from_user_id, from_user_name, to_user_id, to_user_name, comment, created_by, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, logId, tenantId, KIND, requirementId, action,
                user.id(), user.displayName(), toUserId, toUserName,
                comment == null ? "" : comment, user.id());
        return logId;
    }

    /** 存量需求所属项目的编码与名称（project_id 为项目管理主键）。 */
    private Map<String, Object> requireProjectContext(Map<String, Object> row, AuthUser user) {
        Object raw = row.get("project_id");
        if (raw == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该存量需求未关联项目，无法确定项目组织架构人员");
        }
        return memberDirectory.requireProjectContext(((Number) raw).longValue(), user);
    }

    /** 流转/回传站内消息：定向通知接收人，不产生待办；失败不影响业务结果。 */
    private void publishFlowNotification(long requirementId, Map<String, Object> row,
                                         long targetUserId, AuthUser operator, long flowLogId, boolean returned) {
        try {
            Map<String, Object> project = requireProjectContext(row, operator);
            String requirementNo = row.get("requirement_no") == null ? "" : String.valueOf(row.get("requirement_no"));
            String requirementName = row.get("requirement_name") == null ? "" : String.valueOf(row.get("requirement_name"));
            String subject = requirementNo.isBlank() ? requirementName : requirementNo + " " + requirementName;
            String title = returned ? "需求已回传：" + subject : "需求流转待处理：" + subject;
            String content = returned
                    ? "「" + requirementName + "」已回传给你，请查看处理。"
                    : "「" + requirementName + "」已由 " + operator.displayName() + " 流转给你，请及时处理。";
            notifications.publish(new NotificationPublishCommand(
                    operator.tenantId(),
                    (returned ? "req-flow-return:" : "req-flow:") + flowLogId,
                    "requirement",
                    "需求管理",
                    "REQUIREMENT_LEGACY_FLOW",
                    String.valueOf(row.get("id")),
                    List.of(targetUserId),
                    title,
                    content,
                    NotificationLevel.INFO,
                    "需求管理",
                    "/requirements/legacy?legacyId=" + row.get("id"),
                    operator.id(),
                    String.valueOf(project.get("project_code")),
                    String.valueOf(project.get("project_name"))));
        } catch (RuntimeException exception) {
            log.warn("存量需求流转通知发布失败：requirementId={}, targetUserId={}", row.get("id"), targetUserId, exception);
        }
    }

    // ---------------- 版本历史 ----------------

    public List<Map<String, Object>> versions(long id, AuthUser user) {
        requireAccessById(id, user);
        return jdbc.queryForList("""
                SELECT version_no, change_summary, snapshot_json, created_by, created_at
                FROM req_requirement_version WHERE tenant_id = ? AND requirement_id = ? AND deleted = 0
                ORDER BY version_no DESC, id DESC
                """, user.tenantId(), id);
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
            RequirementSql.updateBy(jdbc, "req_legacy_detail", "requirement_id", id, user.tenantId(), values);
        }
        Map<String, Object> after = row(id, user);
        if ("是".equals(String.valueOf(after.get("change_involved")))) {
            String currentVersion = String.valueOf(after.get("version_no") == null ? "1.0" : after.get("version_no"));
            String next = nextVersion(currentVersion);
            if (!next.equals(currentVersion)) {
                jdbc.update("UPDATE req_requirement SET version_no = ?, updated_by = ? WHERE tenant_id = ? AND id = ?",
                        next, user.id(), user.tenantId(), id);
            }
            String summary = after.get("change_info") == null ? "" : String.valueOf(after.get("change_info"));
            writeVersionSnapshot(id, next, summary, after, user);
            after = row(id, user);
        }
        changeLog.recordFields(KIND, id, "CHANGE", before, after, user, "ONLINE");
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
            jdbc.update("""
                    INSERT INTO req_requirement_version (id, tenant_id, requirement_id, version_no, change_summary, snapshot_json, created_by, deleted)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                    ON DUPLICATE KEY UPDATE change_summary = VALUES(change_summary), snapshot_json = VALUES(snapshot_json)
                    """, RequirementIds.next(), user.tenantId(), id, versionNo,
                    summary == null ? "" : summary, snapshot, user.id());
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
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, created_by, current_handler_user_id AS current_flow_user_id"
                        + " FROM req_requirement WHERE tenant_id = ? AND id = ? AND deleted = 0"
                        + " AND requirement_kind = 'LEGACY'",
                user.tenantId(), id);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存量需求不存在");
        }
        security.requireLegacyVisible(user, rows.get(0));
    }

    Map<String, Object> row(long id, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + String.join(", ", SELECT_COLUMNS) + " " + FROM
                        + " WHERE r.tenant_id = ? AND r.id = ? AND r.deleted = 0 AND r.requirement_kind = 'LEGACY'",
                user.tenantId(), id);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存量需求不存在");
        }
        return rows.get(0);
    }

    private void validateCoreFields(Map<String, Object> row) {
        for (String field : RequirementEnums.LEGACY_CORE_REQUIRED_FIELDS) {
            String mapped = "requirement_name".equals(field) ? "name" : field;
            Object v = row.get(field);
            if (v == null) {
                v = row.get(mapped);
            }
            if (v == null || (v instanceof String s && s.isBlank())) {
                String label = RequirementEnums.FIELD_LABELS.getOrDefault(field, field);
                throw new BusinessException(ErrorCode.BAD_REQUEST, "核心标识字段不能为空：" + label);
            }
        }
    }

    /**
     * 需求对接进入下一阶段的前置校验：必须维护 1 个主责系统（主责唯一由保存时校验）。
     * Owner 2026-09-19 明确：只要求 1 个主责，协同系统（改造/测试）可选，不作硬拦截。
     */
    private void requireDockingSystemItems(long requirementId, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT system_role, COUNT(*) AS row_count
                FROM req_requirement_system
                WHERE tenant_id = ? AND requirement_id = ? AND deleted = 0
                GROUP BY system_role
                """, user.tenantId(), requirementId);
        long lead = 0;
        for (Map<String, Object> item : rows) {
            long count = ((Number) item.get("row_count")).longValue();
            if (RequirementEnums.SYSTEM_ROLE_LEAD.equals(String.valueOf(item.get("system_role")))) {
                lead += count;
            }
        }
        if (lead < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "需求对接进入下一阶段前必须维护 1 个主责物理子系统");
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
        for (String field : LEGACY_MAIN_FIELDS) {
            Object value = body.get(field);
            if (value != null) {
                values.put(field, value);
            }
        }
        for (String field : LEGACY_DETAIL_FIELDS) {
            Object value = body.get(field);
            if (value != null) {
                values.put(field, DATE_FIELDS.contains(field) ? RequirementValues.date(value) : value);
            }
        }
        return values;
    }

    private Map<String, Object> detailValues(long requirementId, long tenantId, Map<String, Object> values) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("requirement_id", requirementId);
        detail.put("tenant_id", tenantId);
        for (String field : LEGACY_DETAIL_FIELDS) {
            if (values.containsKey(field)) {
                detail.put(field, values.get(field));
            }
        }
        return detail;
    }
}
