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
import java.util.Map;

/**
 * 新建项目需求差异清单：一条主表记录（requirement_kind = NEW_PROJECT_DIFF）+ 差异专有字段
 * + 恰好 1 行物理子系统关联（system_role = LEAD，按物理子系统维度提出）。
 * 生命周期状态机（待评审→评审中→已评审/已退回）、数据范围与改动记录保持不变。
 */
@Service
public class RequirementDifferenceService {
    private static final String KIND = "NEW_PROJECT_DIFF";

    /** 差异专有字段（落在 req_difference_detail），口径与导入共用同一份定义。 */
    private static final List<String> DIFF_DETAIL_FIELDS = RequirementEnums.DIFFERENCE_DETAIL_FIELDS;

    private static final String SELECT_COLUMNS = """
            r.id, r.project_id, d.seq_no, d.business_conglomerate, d.business_section, r.business_group,
            r.requirement_no, d.category, r.name, s.physical_subsystem_id AS system_id, d.jinke_practice,
            d.difference_type, d.monshang_practice, d.difference_desc, d.monshang_dept, d.monshang_analyst,
            d.jinke_analyst, d.adapt_mode, d.handle_status, d.coord_group, d.solution, d.is_special,
            d.decision_level, d.decision_conclusion, d.monshang_confirm_dept, d.jinke_confirmer,
            r.review_status, d.review_comment, d.review_report_name, d.reviewed_by, d.reviewed_at,
            d.review_report_attachment_id,
            r.workflow_instance_id, d.dev_status, d.test_status, r.baseline_id, r.source, r.import_batch_id,
            r.created_by, r.current_handler_user_id, r.current_handler_user_name, r.created_at, r.updated_at,
            s.subsystem_code, s.subsystem_name
            """;

    private static final String FROM = """
            FROM req_requirement r
            JOIN req_difference_detail d ON d.requirement_id = r.id AND d.tenant_id = r.tenant_id
            LEFT JOIN req_requirement_system s ON s.requirement_id = r.id AND s.tenant_id = r.tenant_id
                 AND s.system_role = 'LEAD' AND s.deleted = 0
            """;

    private final JdbcTemplate jdbc;
    private final RequirementChangeLogService changeLog;
    private final RequirementSecurityService security;
    private final RequirementSystemService systemService;
    private final RequirementProjectMemberService memberDirectory;
    private final RequirementReviewWorkflowSupport reviewWorkflow;
    private final RequirementReviewReportService reviewReportService;
    private final WorkflowBusinessGateway workflowGateway;

    public RequirementDifferenceService(JdbcTemplate jdbc, RequirementChangeLogService changeLog,
                                        RequirementSecurityService security,
                                        RequirementSystemService systemService,
                                        RequirementProjectMemberService memberDirectory,
                                        RequirementReviewWorkflowSupport reviewWorkflow,
                                        RequirementReviewReportService reviewReportService,
                                        WorkflowBusinessGateway workflowGateway) {
        this.jdbc = jdbc;
        this.changeLog = changeLog;
        this.security = security;
        this.systemService = systemService;
        this.memberDirectory = memberDirectory;
        this.reviewWorkflow = reviewWorkflow;
        this.reviewReportService = reviewReportService;
        this.workflowGateway = workflowGateway;
    }

    public PageResult<Map<String, Object>> list(long projectId, String reviewStatus, String devStatus,
                                                String testStatus, String keyword, PageQuery query, AuthUser user) {
        security.requireProjectVisible(user, projectId);
        StringBuilder where = new StringBuilder(" WHERE r.tenant_id = ? AND r.project_id = ?"
                + " AND r.deleted = 0 AND r.requirement_kind = 'NEW_PROJECT_DIFF'");
        List<Object> params = new ArrayList<>(List.of(user.tenantId(), projectId));
        appendEqual(where, params, "r.review_status", reviewStatus);
        appendEqual(where, params, "d.dev_status", devStatus);
        appendEqual(where, params, "d.test_status", testStatus);
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (r.name LIKE ? OR r.requirement_no LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        // 数据范围：统筹/管理员可见项目全量；其余人只看本人提交、当前在本人名下或曾流转经手的差异
        where.append(security.differenceScopeSql("r", user, params));
        Long total = jdbc.queryForObject("SELECT COUNT(DISTINCT r.id) " + FROM + where, Long.class, params.toArray());
        params.add(query.size());
        params.add((query.page() - 1) * query.size());
        List<Map<String, Object>> records = jdbc.queryForList(
                "SELECT " + SELECT_COLUMNS + FROM + where
                        + " ORDER BY d.seq_no, r.id LIMIT ? OFFSET ?", params.toArray());
        boolean admin = security.isAdmin(user);
        for (Map<String, Object> record : records) {
            record.put("can_edit", security.canEditDifference(user, record, admin));
        }
        return new PageResult<>(records, total == null ? 0 : total, query.page(), query.size());
    }

    public Map<String, Object> get(long id, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireProjectVisible(user, ((Number) row.get("project_id")).longValue());
        security.requireDifferenceVisible(user, row);
        row.put("can_edit", security.canEditDifference(user, row, security.isAdmin(user)));
        return row;
    }

    @Transactional
    public Map<String, Object> create(long projectId, Map<String, Object> body, AuthUser user) {
        security.requireProjectAccess(user, projectId);
        Map<String, Object> values = normalized(body);
        validate(values);
        String name = RequirementValues.text(values, "name");
        if (name == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "差异点名称不能为空");
        }
        // 新建差异按物理子系统维度提出：必须且只能选 1 个架构物理子系统
        RequirementSystemService.SystemSelection system = systemService.resolveSelection(projectId, values, user);
        if (system.subsystemId() == null && system.subsystemCode() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "请选择涉及物理子系统：新建项目差异按物理子系统维度提出，只能选 1 个");
        }
        long id = RequirementIds.next();

        Map<String, Object> main = new LinkedHashMap<>();
        main.put("id", id);
        main.put("tenant_id", user.tenantId());
        main.put("project_id", projectId);
        main.put("requirement_kind", KIND);
        main.put("requirement_no", RequirementValues.text(values, "requirement_no"));
        main.put("name", name);
        main.put("business_group", RequirementValues.text(values, "business_group"));
        main.put("summary", RequirementValues.text(values, "difference_desc"));
        main.put("review_status", "待评审");
        main.put("version_no", "1.0");
        main.put("source", "ONLINE");
        main.put("created_by", user.id());
        main.put("current_handler_user_id", user.id());
        main.put("current_handler_user_name", user.displayName());
        main.put("deleted", 0);
        RequirementSql.insert(jdbc, "req_requirement", main);

        Map<String, Object> detail = detailValues(id, user.tenantId(), values);
        // 序号由服务端按项目内最大值 +1 自动生成，不接受前端传入，保证连续且不重号
        detail.put("seq_no", nextSeq(projectId, user));
        RequirementSql.insert(jdbc, "req_difference_detail", detail);

        saveLeadSystem(id, projectId, system, user);
        changeLog.recordCreate(KIND, id, main, user, "ONLINE");
        return get(id, user);
    }

    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> before = row(id, user);
        requireEditable(before);
        security.requireDifferenceEditable(user, before);
        Map<String, Object> values = normalized(body);
        validate(values);
        String existingReqNo = RequirementValues.text(before, "requirement_no");
        String nextReqNo = RequirementValues.text(values, "requirement_no");
        if (nextReqNo != null && !nextReqNo.equals(existingReqNo)) {
            throw new BusinessException(ErrorCode.CONFLICT, "需求编号创建后不可修改");
        }
        String existingSeqNo = before.get("seq_no") == null ? null : String.valueOf(before.get("seq_no")).trim();
        String nextSeqNo = values.get("seq_no") == null ? null : String.valueOf(values.get("seq_no")).trim();
        if (nextSeqNo != null && !nextSeqNo.equals(existingSeqNo)) {
            throw new BusinessException(ErrorCode.CONFLICT, "序号创建后不可修改");
        }
        Map<String, Object> main = new LinkedHashMap<>();
        if (values.containsKey("name")) {
            main.put("name", values.get("name"));
        }
        if (values.containsKey("business_group")) {
            main.put("business_group", values.get("business_group"));
        }
        if (values.containsKey("difference_desc")) {
            main.put("summary", values.get("difference_desc"));
        }
        if (!main.isEmpty()) {
            main.put("updated_by", user.id());
            RequirementSql.update(jdbc, "req_requirement", id, user.tenantId(), main);
        }
        Map<String, Object> detail = detailValues(id, user.tenantId(), values);
        detail.remove("requirement_id");
        detail.remove("tenant_id");
        if (!detail.isEmpty()) {
            RequirementSql.updateBy(jdbc, "req_difference_detail", "requirement_id", id, user.tenantId(), detail);
        }
        // 涉及物理子系统：新建差异只有 1 行，变更时整体替换
        if (values.containsKey("system_id") || values.containsKey("physical_subsystem_id")) {
            RequirementSystemService.SystemSelection system = systemService.resolveSelection(
                    ((Number) before.get("project_id")).longValue(), values, user);
            jdbc.update("UPDATE req_requirement_system SET deleted = 1, updated_by = ?"
                    + " WHERE tenant_id = ? AND requirement_id = ? AND deleted = 0", user.id(), user.tenantId(), id);
            saveLeadSystem(id, ((Number) before.get("project_id")).longValue(), system, user);
        }
        Map<String, Object> after = row(id, user);
        changeLog.recordFields(KIND, id, "UPDATE", before, after, user, "ONLINE");
        return after;
    }

    @Transactional
    public void delete(long id, AuthUser user) {
        Map<String, Object> row = row(id, user);
        requireEditable(row);
        security.requireDifferenceEditable(user, row);
        jdbc.update("UPDATE req_requirement SET deleted = 1, updated_by = ? WHERE tenant_id = ? AND id = ?",
                user.id(), user.tenantId(), id);
        jdbc.update("UPDATE req_requirement_system SET deleted = 1 WHERE tenant_id = ? AND requirement_id = ?",
                user.tenantId(), id);
        changeLog.record(KIND, id, "DELETE", "deleted", "0", "1", user, "ONLINE");
    }

    /**
     * 提交评审：待评审/已退回 → 评审中，并启动审批流（requirement.diff.review）。
     * <p>发起前会自动终结本差异遗留的同名 business_key（req-diff:{id}）RUNNING 实例，避免重提产生双实例脏数据。
     * 业务字段先改为"评审中"并锁定；审批人在工作流中心 APPROVE/REJECT 后，
     * 由 RequirementWorkflowListener 消费持久化生命周期事件，幂等回写"已评审/已退回"。
     */
    @Transactional
    public Map<String, Object> submitReview(long id, List<Long> approverIds, Long reportAttachmentId, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireDifferenceEditable(user, row);
        String status = String.valueOf(row.get("review_status"));
        if (!"待评审".equals(status) && !"已退回".equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可提交评审：" + status);
        }
        if (approverIds == null || approverIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择审批人");
        }
        Map<String, Object> project = memberDirectory.requireRequirementProjectContext(
                ((Number) row.get("project_id")).longValue(), user);
        String projectCode = String.valueOf(project.get("project_code"));
        String projectName = String.valueOf(project.get("project_name"));
        // 审批人只能来自当前项目组织架构的有效成员（项目管理维护）
        memberDirectory.requireActiveMembers(projectCode, approverIds, user);
        // 评审必须上传文件：先校验并绑定评审报告附件，缺失直接在写库前拒绝
        RequirementReviewReportService.BoundReport report =
                reviewReportService.requireBound(user, id, projectCode, reportAttachmentId);
        // 终结同 business_key 的 RUNNING 旧实例（若存在），防止"退回后再提交"造成双实例 & 我的代办脏数据
        String businessKey = "req-diff:" + id;
        terminateResidualDiffInstances(user.tenantId(), businessKey, id);
        // 启动审批流，businessKey 编码业务单号；variables 携带 approverIds 供 VARIABLE 审批节点解析
        long definitionId = reviewWorkflow.requirePublishedDefinitionId(user.tenantId(), "requirement.diff.review");
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("differenceId", id);
        variables.put("submitterId", user.id());
        variables.put("submitterName", user.displayName());
        variables.put("approverIds", approverIds);
        variables.put("fromStatus", status);
        String title = "差异评审 - " + (row.get("name") == null ? "" : String.valueOf(row.get("name")))
                + "（" + (row.get("requirement_no") == null ? ("#" + id) : String.valueOf(row.get("requirement_no"))) + "）";
        String ref = (projectCode == null || projectCode.isBlank()) ? null : projectCode;
        WorkflowBusinessContext context = new WorkflowBusinessContext(
                "requirement", "需求管理", "requirement_diff_review", businessKey, title, 1,
                ref, projectName, "/requirements/new-project",
                reviewWorkflow.digest(businessKey + "|" + status + "|" + title));
        WorkflowStartResult instance = workflowGateway.startByDefinitionId(
                new WorkflowStartDefinitionCommand(definitionId, context, variables), user);
        long instanceId = instance.instanceId();
        jdbc.update("UPDATE req_requirement SET review_status = '评审中', workflow_instance_id = ?, updated_by = ?"
                        + " WHERE tenant_id = ? AND id = ?",
                instanceId, user.id(), user.tenantId(), id);
        jdbc.update("UPDATE req_difference_detail SET review_comment = NULL, review_report_name = ?"
                        + ", review_report_attachment_id = ? WHERE tenant_id = ? AND requirement_id = ?",
                truncate(report.fileName(), 200),
                report.attachmentId(), user.tenantId(), id);
        changeLog.record(KIND, id, "SUBMIT_REVIEW", "review_status", status, "评审中", user, "ONLINE");
        return get(id, user);
    }

    /**
     * 撤销评审：审批中状态下，创建人或项目成员可主动撤回当前在审的流程实例，
     * review_status 从"评审中"回退为"待评审"，wf_instance 变 TERMINATED，PENDING 审批任务批量标记 DONE/CANCELLED。
     * 退回后 review_status 已经是"已退回"的情况也允许再次"撤销"（直接回待评审，给编辑机会）。
     */
    @Transactional
    public Map<String, Object> cancelReview(long id, String reason, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireDifferenceEditable(user, row);
        String status = String.valueOf(row.get("review_status"));
        if (!"评审中".equals(status) && !"已退回".equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可撤销评审：" + status);
        }
        String businessKey = "req-diff:" + id;
        terminateResidualDiffInstances(user.tenantId(), businessKey, id);
        jdbc.update("UPDATE req_requirement SET review_status = '待评审', workflow_instance_id = NULL, updated_by = ?"
                        + " WHERE tenant_id = ? AND id = ?",
                user.id(), user.tenantId(), id);
        jdbc.update("UPDATE req_difference_detail SET review_comment = ? WHERE tenant_id = ? AND requirement_id = ?",
                reason == null || reason.isBlank() ? null : reason.substring(0, Math.min(500, reason.length())),
                user.tenantId(), id);
        changeLog.record(KIND, id, "CANCEL_REVIEW", "review_status", status, "待评审", user, "ONLINE");
        return get(id, user);
    }

    /**
     * 差异流转：管理员/创建人/当前处理人将差异流转给下一个人，接收人成为新的当前处理人并可编辑。
     * 流转记录写入统一流转日志 req_flow_log，并留改动记录。
     */
    @Transactional
    public Map<String, Object> transfer(long id, long toUserId, String comment, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireDifferenceEditable(user, row);
        requireEditable(row);
        if (toUserId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择流转目标用户");
        }
        // 接收人只能是当前项目组织架构的有效成员（项目管理维护）
        String projectCode = memberDirectory.projectCodeOfRequirementProject(
                ((Number) row.get("project_id")).longValue(), user);
        String targetName = memberDirectory.requireMemberName(projectCode, toUserId, user);
        Object oldHandler = row.get("current_handler_user_id");
        jdbc.update("UPDATE req_requirement SET current_handler_user_id = ?, current_handler_user_name = ?,"
                        + " updated_by = ? WHERE tenant_id = ? AND id = ?",
                toUserId, targetName, user.id(), user.tenantId(), id);
        insertFlowLog(id, KIND, "SEND", user, toUserId, targetName, comment, user.tenantId());
        changeLog.record(KIND, id, "FLOW_SEND", "current_handler_user_id",
                oldHandler == null ? null : String.valueOf(oldHandler), String.valueOf(toUserId), user, "ONLINE");
        return get(id, user);
    }

    /**
     * 提出人收回：把当前处理人改回提出人本人。
     * 不校验接收人是否已改动、不限制已流转几手；评审中/已评审不允许收回（评审中走撤销评审）。
     * 仅需求提出人与需求统筹管理员可调用。
     */
    @Transactional
    public Map<String, Object> withdraw(long id, String comment, AuthUser user) {
        security.requireWithdrawPermission(user);
        Map<String, Object> row = row(id, user);
        Object creator = row.get("created_by");
        long creatorId = creator instanceof Number number ? number.longValue() : user.id();
        if (creatorId != user.id() && !security.isPmo(user) && !security.isAdmin(user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅需求提出人或需求统筹管理员可收回该需求");
        }
        String status = String.valueOf(row.get("review_status"));
        if ("评审中".equals(status) || "已评审".equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "评审中或已评审的差异不能收回，请先撤销评审");
        }
        String creatorName = lookupUserName(user.tenantId(), creatorId);
        Object oldHandler = row.get("current_handler_user_id");
        jdbc.update("UPDATE req_requirement SET current_handler_user_id = ?, current_handler_user_name = ?,"
                        + " updated_by = ? WHERE tenant_id = ? AND id = ?",
                creatorId, creatorName, user.id(), user.tenantId(), id);
        insertFlowLog(id, KIND, "WITHDRAW", user, creatorId, creatorName, comment, user.tenantId());
        changeLog.record(KIND, id, "FLOW_WITHDRAW", "current_handler_user_id",
                oldHandler == null ? null : String.valueOf(oldHandler), String.valueOf(creatorId), user, "ONLINE");
        return get(id, user);
    }

    /** 终结指定差异的同 business_key 残留 RUNNING 审批实例 & PENDING 审批任务，为重提/撤销释放锁。 */
    private void terminateResidualDiffInstances(long tenantId, String businessKey, long diffId) {
        reviewWorkflow.terminateResidualInstances(tenantId, businessKey);
        // 若当前差异仍挂旧 workflow_instance_id（在审撤回场景），解挂
        jdbc.update("UPDATE req_requirement SET workflow_instance_id = NULL WHERE tenant_id = ? AND id = ?",
                tenantId, diffId);
    }

    /**
     * 流转接收人与审批人选项：唯一来源为当前项目组织架构的有效成员。
     * 候选范围由项目管理维护，需求模块不再提供"全租户用户"选项。
     */
    public List<Map<String, Object>> projectMembers(String projectRef, String keyword, AuthUser user) {
        return memberDirectory.activeMembers(projectRef, keyword, user);
    }

    public List<Map<String, Object>> changes(long id, AuthUser user) {
        get(id, user);
        return changeLog.list(KIND, id, user);
    }

    /**
     * 审批记录：按差异关联的 workflow_instance_id 查 wf_task_action，
     * 返回审批人、审批动作、意见、目标用户（加签场景）、任务类型、时间。
     * workflow_instance_id 为空或非数字（stub）时返回空列表，不进入工作流中心。
     */
    public List<Map<String, Object>> approvalLogs(long id, AuthUser user) {
        Map<String, Object> row = row(id, user);
        security.requireProjectVisible(user, ((Number) row.get("project_id")).longValue());
        Object raw = row.get("workflow_instance_id");
        if (raw == null) return List.of();
        long instanceId;
        try {
            instanceId = Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return List.of();
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
                "SELECT " + SELECT_COLUMNS + FROM
                        + " WHERE r.tenant_id = ? AND r.id = ? AND r.deleted = 0"
                        + " AND r.requirement_kind = 'NEW_PROJECT_DIFF'",
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

    private void validate(Map<String, Object> values) {
        RequirementValues.requireOption("categories", RequirementValues.text(values, "category"));
        RequirementValues.requireOption("differenceTypes", RequirementValues.text(values, "difference_type"));
        RequirementValues.requireOption("adaptModes", RequirementValues.text(values, "adapt_mode"));
        RequirementValues.requireOption("handleStatuses", RequirementValues.text(values, "handle_status"));
        RequirementValues.requireOption("decisionLevels", RequirementValues.text(values, "decision_level"));
        RequirementValues.requireOption("yesNo", RequirementValues.text(values, "is_special"));
        RequirementValues.requireOption("devStatuses", RequirementValues.text(values, "dev_status"));
        RequirementValues.requireOption("testStatuses", RequirementValues.text(values, "test_status"));
    }

    private Map<String, Object> normalized(Map<String, Object> body) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : DIFF_DETAIL_FIELDS) {
            Object value = body.get(field);
            if (value != null) {
                values.put(field, value);
            }
        }
        for (String field : List.of("name", "business_group", "requirement_no", "seq_no",
                "system_id", "physical_subsystem_id", "subsystem_code", "subsystem_name")) {
            Object value = body.get(field);
            if (value != null) {
                values.put(field, value);
            }
        }
        return values;
    }

    private Map<String, Object> detailValues(long requirementId, long tenantId, Map<String, Object> values) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("requirement_id", requirementId);
        detail.put("tenant_id", tenantId);
        for (String field : DIFF_DETAIL_FIELDS) {
            if (values.containsKey(field)) {
                detail.put(field, values.get(field));
            }
        }
        detail.putIfAbsent("dev_status", "未开始");
        detail.putIfAbsent("test_status", "未开始");
        return detail;
    }

    /** 新建差异的物理子系统关联行：恰好 1 行 LEAD。 */
    private void saveLeadSystem(long requirementId, long projectId,
                                RequirementSystemService.SystemSelection system, AuthUser user) {
        long systemRowId = RequirementIds.next();
        jdbc.update("""
                INSERT INTO req_requirement_system
                (id, tenant_id, requirement_id, physical_subsystem_id, subsystem_code, subsystem_name,
                 system_role, owner_user_id, owner_user_name, status, description, remark, created_by, deleted)
                VALUES (?, ?, ?, ?, ?, ?, 'LEAD', ?, ?, '未开始', NULL, NULL, ?, 0)
                """, systemRowId, user.tenantId(), requirementId, system.subsystemId(),
                system.subsystemCode(), system.subsystemName(), system.ownerUserId(),
                system.ownerUserName(), user.id());
    }

    private long insertFlowLog(long requirementId, String kind, String action, AuthUser user,
                               Long toUserId, String toUserName, String comment, long tenantId) {
        long logId = RequirementIds.next();
        jdbc.update("""
                INSERT INTO req_flow_log
                (id, tenant_id, requirement_kind, requirement_id, action, from_user_id, from_user_name,
                 to_user_id, to_user_name, comment, created_by, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, logId, tenantId, kind, requirementId, action,
                user.id(), user.displayName(), toUserId, toUserName,
                comment == null ? "" : comment, user.id());
        return logId;
    }

    private String lookupUserName(long tenantId, long userId) {
        List<String> names = jdbc.queryForList(
                "SELECT display_name FROM sys_user WHERE tenant_id = ? AND id = ? AND deleted = 0",
                String.class, tenantId, userId);
        return names.isEmpty() ? null : names.get(0);
    }

    /** 名称列截断（未上传评审报告时为 null）。 */
    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private int nextSeq(long projectId, AuthUser user) {
        Long max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(d.seq_no), 0) FROM req_requirement r"
                        + " JOIN req_difference_detail d ON d.requirement_id = r.id"
                        + " WHERE r.tenant_id = ? AND r.project_id = ? AND r.deleted = 0"
                        + " AND r.requirement_kind = 'NEW_PROJECT_DIFF'",
                Long.class, user.tenantId(), projectId);
        return max == null ? 1 : max.intValue() + 1;
    }

    private void appendEqual(StringBuilder where, List<Object> params, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append(" = ?");
            params.add(value);
        }
    }
}
