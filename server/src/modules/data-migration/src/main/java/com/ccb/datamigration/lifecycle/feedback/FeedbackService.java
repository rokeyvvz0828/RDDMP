package com.ccb.datamigration.lifecycle.feedback;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.datamigration.lifecycle.LifecycleAuditService;
import com.ccb.datamigration.lifecycle.LifecyclePermissionService;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import com.ccb.datamigration.lifecycle.feedback.model.FeedbackView;
import com.ccb.datamigration.lifecycle.order.OrderStateMachineService;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务进度反馈（基线第 12 章，T6）：
 * 四段门禁固定顺序（12.2.1）——① 只读态 → ② 契约 → ③ 锁态 → ④ 身份，每个非法入参唯一错误码（铁律 #12）；
 * 只读态先于一切（CANCELLED/ARCHIVED/SUSPENDED 全拒）；管理员不可代填（FEEDBACK_ADMIN_FORBIDDEN）；
 * 问题三项必填 / 风险五项必填；上报记录不可清空仅可补充；提审走四象限闭环出口（先落库后判定 D-14）。
 */
@Service
public class FeedbackService {
    private final JdbcTemplate jdbc;
    private final LifecyclePermissionService permissions;
    private final LifecycleAuditService audit;
    private final OrderStateMachineService orderMachine;
    private final ObjectMapper objectMapper;

    public FeedbackService(JdbcTemplate jdbc, LifecyclePermissionService permissions, LifecycleAuditService audit,
                           OrderStateMachineService orderMachine, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.audit = audit;
        this.orderMachine = orderMachine;
        this.objectMapper = objectMapper;
    }

    /** 保存/迭代执行反馈（增量保存不触发闭环；A0 免审核且齐备时自动闭环）。 */
    @Transactional
    public FeedbackView saveFeedback(AuthUser user, long orderId, int processSeq, Map<String, Object> body) {
        Map<String, Object> process = requireProcess(user, orderId, processSeq);
        int lockStage = lockGate(user, orderId, processSeq, process);
        identityGate(user, orderId, process);
        String progress = text(body, "progressDesc");
        String exitContent = text(body, "exitContentFilled");
        if (progress == null || progress.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.FEEDBACK_PROGRESS_REQUIRED, "工序执行进度为必填项");
        }
        List<Long> deliverableIds = longList(body.get("deliverableIds"));
        List<Long> attachmentIds = longList(body.get("attachmentIds"));
        String extraRemark = text(body, "extraRemark");
        boolean mustSubmit = intOf(process, "must_submit_deliverable") == 1;
        boolean exitFilled = exitContent != null && !exitContent.isEmpty();
        boolean deliverableSubmitted = !mustSubmit || !deliverableIds.isEmpty();
        if (mustSubmit && deliverableIds.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.FEEDBACK_DELIVERABLE_REQUIRED, "需提交交付件工序：必选交付物缺失");
        }
        String snapshotVersion = String.valueOf(process.get("snapshot_version") == null
                ? "" : process.get("snapshot_version"));
        long now = nextId();
        Map<String, Object> existing = findFeedback(user.tenantId(), orderId, processSeq);
        if (existing == null) {
            jdbc.update("INSERT INTO process_feedback (id, tenant_id, order_id, process_seq, progress_desc, "
                            + "exit_content_filled, deliverable_ids, attachment_ids, extra_remark, filled_by, snapshot_version) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    now, user.tenantId(), orderId, processSeq, progress, exitContent == null ? "" : exitContent,
                    toJson(deliverableIds), toJson(attachmentIds), extraRemark, user.id(), snapshotVersion);
        } else {
            jdbc.update("UPDATE process_feedback SET progress_desc = ?, exit_content_filled = ?, deliverable_ids = ?, "
                            + "attachment_ids = ?, extra_remark = ?, filled_by = ?, filled_at = CURRENT_TIMESTAMP(6), "
                            + "updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND tenant_id = ? AND deleted = 0",
                    progress, exitContent == null ? "" : exitContent, toJson(deliverableIds), toJson(attachmentIds),
                    extraRemark, user.id(), existing.get("id"), user.tenantId());
        }
        // 回写工序实例 exit_filled / deliverable_submitted（闭环判定事实）
        jdbc.update("UPDATE order_process_instance SET exit_filled = ?, deliverable_submitted = ?, "
                        + "updated_at = CURRENT_TIMESTAMP(6) WHERE order_id = ? AND process_seq = ? AND tenant_id = ? AND deleted = 0",
                boolInt(exitFilled), boolInt(deliverableSubmitted), orderId, processSeq, user.tenantId());
        audit.audit(user, audit.opCode("SAVE_FEEDBACK", "FEEDBACK"), "ORDER", orderId, "SUCCESS", null,
                Map.of("processSeq", processSeq, "saved", true));
        // A0 免审核自动闭环：must_audit=false 且准出齐备（18.2 A0∩B0/A0∩B1）
        boolean mustAudit = intOf(process, "must_audit") == 1;
        if (lockStage == 0 && !mustAudit && exitFilled && deliverableSubmitted) {
            orderMachine.applyFeedbackClosure(user, orderId, processSeq, exitFilled, deliverableSubmitted);
        }
        return feedbackView(user, orderId, processSeq);
    }

    /** 提审（仅需审核工序）：四段门禁后进入四象限闭环出口，置 REVIEWING。 */
    @Transactional
    public Map<String, Object> submitAudit(AuthUser user, long orderId, int processSeq) {
        Map<String, Object> process = requireProcess(user, orderId, processSeq);
        boolean mustAudit = intOf(process, "must_audit") == 1;
        if (!mustAudit) {
            throw new BusinessException(LifecycleErrorCode.FEEDBACK_NO_AUDIT_STAGE, "本工序配置为无需审核，无提审环节");
        }
        lockGate(user, orderId, processSeq, process);
        identityGate(user, orderId, process);
        if ("REVIEWING".equals(String.valueOf(process.get("process_status")))) {
            throw new BusinessException(LifecycleErrorCode.FEEDBACK_DUPLICATE_SUBMIT, "工序已提审，重复提审被拒");
        }
        Map<String, Object> feedback = findFeedback(user.tenantId(), orderId, processSeq);
        boolean exitFilled = feedback != null && text(feedback, "exit_content_filled") != null
                && !String.valueOf(feedback.get("exit_content_filled")).isEmpty();
        boolean mustSubmit = intOf(process, "must_submit_deliverable") == 1;
        boolean deliverableSubmitted = !mustSubmit || hasDeliverables(feedback);
        if (!exitFilled) {
            throw new BusinessException(LifecycleErrorCode.FEEDBACK_EXIT_REQUIRED, "准出标准内容为必填项");
        }
        if (mustSubmit && !deliverableSubmitted) {
            throw new BusinessException(LifecycleErrorCode.FEEDBACK_DELIVERABLE_REQUIRED, "需提交交付件工序：必选交付物缺失");
        }
        return orderMachine.applyFeedbackClosure(user, orderId, processSeq, exitFilled, deliverableSubmitted);
    }

    /** 问题上报（独立台账，三项必填：标题/描述/发生场景；不可清空仅可补充）。 */
    @Transactional
    public Map<String, Object> reportIssue(AuthUser user, long orderId, int processSeq, Map<String, Object> body) {
        Map<String, Object> process = requireProcess(user, orderId, processSeq);
        identityGate(user, orderId, process);
        String title = text(body, "title");
        String desc = text(body, "desc");
        String scene = text(body, "scene");
        if (title == null || title.isEmpty() || desc == null || desc.isEmpty() || scene == null || scene.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.FEEDBACK_ISSUE_FIELDS_REQUIRED, "问题上报必填三项：标题/描述/发生场景");
        }
        jdbc.update("INSERT INTO process_issue_report (id, tenant_id, order_id, process_seq, issue_title, issue_desc, "
                        + "scene, impact_scope, attachment_ids, reporter_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                nextId(), user.tenantId(), orderId, processSeq, title, desc, scene, text(body, "impactScope"),
                toJson(longList(body.get("attachmentIds"))), user.id());
        audit.audit(user, audit.opCode("REPORT_ISSUE", "FEEDBACK"), "ORDER", orderId, "SUCCESS", null,
                Map.of("processSeq", processSeq, "title", title));
        return Map.of("orderId", orderId, "processSeq", processSeq, "reported", true);
    }

    /** 风险上报（独立台账，五项必填：标题/等级/描述/概率/影响范围；单工序多条）。 */
    @Transactional
    public Map<String, Object> reportRisk(AuthUser user, long orderId, int processSeq, Map<String, Object> body) {
        Map<String, Object> process = requireProcess(user, orderId, processSeq);
        identityGate(user, orderId, process);
        String title = text(body, "title");
        String level = text(body, "level");
        String desc = text(body, "desc");
        String probability = text(body, "probability");
        String impact = text(body, "impactScope");
        if (title == null || title.isEmpty() || level == null || level.isEmpty() || desc == null || desc.isEmpty()
                || probability == null || probability.isEmpty() || impact == null || impact.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.FEEDBACK_RISK_FIELDS_REQUIRED, "风险上报必填五项：标题/等级/描述/概率/影响范围");
        }
        jdbc.update("INSERT INTO process_risk_report (id, tenant_id, order_id, process_seq, risk_title, risk_level, "
                        + "risk_desc, probability, impact_scope, prepared_measure, attachment_ids, reporter_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                nextId(), user.tenantId(), orderId, processSeq, title, level.toUpperCase(), desc, probability.toUpperCase(),
                impact, text(body, "preparedMeasure"), toJson(longList(body.get("attachmentIds"))), user.id());
        audit.audit(user, audit.opCode("REPORT_RISK", "FEEDBACK"), "ORDER", orderId, "SUCCESS", null,
                Map.of("processSeq", processSeq, "level", level));
        return Map.of("orderId", orderId, "processSeq", processSeq, "reported", true);
    }

    /** 作业日志（追加式留痕，不可清空；同时补写反馈 JSON 日志条目）。 */
    @Transactional
    public Map<String, Object> addWorkLog(AuthUser user, long orderId, int processSeq, String content) {
        Map<String, Object> process = requireProcess(user, orderId, processSeq);
        identityGate(user, orderId, process);
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "作业日志内容必填");
        }
        long logId = nextId();
        jdbc.update("INSERT INTO work_log (id, tenant_id, order_id, process_seq, content, operator_id) "
                + "VALUES (?, ?, ?, ?, ?, ?)", logId, user.tenantId(), orderId, processSeq, content.trim(), user.id());
        Map<String, Object> feedback = findFeedback(user.tenantId(), orderId, processSeq);
        if (feedback != null) {
            Object raw = feedback.get("work_log");
            List<Map<String, Object>> logs = parseJsonList(raw == null ? null : String.valueOf(raw));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", logId);
            entry.put("content", content.trim());
            entry.put("operatorId", user.id());
            entry.put("createdAt", LocalDateTime.now().toString());
            logs.add(entry);
            jdbc.update("UPDATE process_feedback SET work_log = ?, updated_at = CURRENT_TIMESTAMP(6) "
                    + "WHERE id = ? AND tenant_id = ? AND deleted = 0", toJson(logs), feedback.get("id"), user.tenantId());
        }
        return Map.of("orderId", orderId, "processSeq", processSeq, "logId", logId);
    }

    /** 反馈详情（读：管理员/审核人/负责人/执行人/参与人可读，写权限四段门禁才有）。 */
    public FeedbackView feedbackView(AuthUser user, long orderId, int processSeq) {
        Map<String, Object> process = requireProcess(user, orderId, processSeq);
        requireReadScope(user, orderId, process);
        return toView(user.tenantId(), orderId, processSeq);
    }

    /** ④ 身份闸门：管理员不可代填；仅工单执行人/参与人可写（FEEDBACK_ADMIN_FORBIDDEN / FEEDBACK_EXECUTOR_ONLY）。 */
    private void identityGate(AuthUser user, long orderId, Map<String, Object> process) {
        if (permissions.isAdmin(user)) {
            throw new BusinessException(LifecycleErrorCode.FEEDBACK_ADMIN_FORBIDDEN, "管理员不得代填执行反馈");
        }
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        long executor = ((Number) order.get("current_executor_id")).longValue();
        boolean participant = isParticipant(user, order);
        if (executor == user.id() || participant) {
            return;
        }
        throw new BusinessException(LifecycleErrorCode.FEEDBACK_EXECUTOR_ONLY, "仅工单执行人/参与人可编辑反馈内容");
    }

    /** ③ 锁态闸门（返回 0 表示可写；LOCKED→45404，CLOSED→45405，只读指令由 ① 先行承接）。 */
    private int lockGate(AuthUser user, long orderId, int processSeq, Map<String, Object> process) {
        // ① 只读态先于一切：CANCELLED/ARCHIVED/SUSPENDED 全拒
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        String orderStatus = String.valueOf(order.get("order_status"));
        if ("SUSPENDED".equals(orderStatus) || "CANCELLED".equals(orderStatus) || "ARCHIVED".equals(orderStatus)) {
            throw new BusinessException(LifecycleErrorCode.FEEDBACK_READONLY_STATE, "工单处于只读态，禁止反馈写操作");
        }
        String status = String.valueOf(process.get("process_status"));
        if ("LOCKED".equals(status)) {
            throw new BusinessException(LifecycleErrorCode.FEEDBACK_PROCESS_LOCKED, "工序未解锁或不在执行态，禁止填写反馈");
        }
        if ("REVIEWING".equals(status)) {
            throw new BusinessException(LifecycleErrorCode.FEEDBACK_PROCESS_LOCKED, "工序审核中，内容锁定禁止再编辑");
        }
        if ("CLOSED".equals(status)) {
            throw new BusinessException(LifecycleErrorCode.FEEDBACK_CLOSED_LOCKED, "工序已闭环，执行内容锁定禁止再编辑");
        }
        return 0;
    }

    private void requireReadScope(AuthUser user, long orderId, Map<String, Object> process) {
        if (permissions.isAdmin(user)) {
            return;
        }
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        long executor = ((Number) order.get("current_executor_id")).longValue();
        boolean owner = order.get("component_id") != null
                && permissions.isOwnerOfComponent(user, ((Number) order.get("component_id")).longValue());
        boolean projectOwner = order.get("project_id") != null
                && permissions.isOwnerOfProject(user, ((Number) order.get("project_id")).longValue());
        if (executor == user.id() || isParticipant(user, order) || owner || projectOwner) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无该数据范围的反馈查看权限");
    }

    private boolean isParticipant(AuthUser user, Map<String, Object> order) {
        Object raw = order.get("participant_ids");
        if (raw == null) {
            return false;
        }
        for (Long id : parseLongList(String.valueOf(raw))) {
            if (id == user.id()) {
                return true;
            }
        }
        return false;
    }

    private List<Long> parseLongList(String json) {
        List<Long> ids = new ArrayList<>();
        try {
            for (Object item : objectMapper.readValue(json, List.class)) {
                ids.add(Long.valueOf(String.valueOf(item)));
            }
        } catch (Exception ignored) {
            // 空/非法 JSON 视为无参与人
        }
        return ids;
    }

    private Map<String, Object> requireProcess(AuthUser user, long orderId, int processSeq) {
        try {
            return jdbc.queryForMap("SELECT p.id, p.tenant_id, p.order_id, p.process_seq, p.process_name, "
                    + "p.process_status, p.exit_filled, p.deliverable_submitted, p.audit_status, p.must_audit, "
                    + "p.must_submit_deliverable, p.reject_count, w.snapshot_version AS snapshot_version "
                    + "FROM order_process_instance p JOIN work_order w ON w.id = p.order_id AND w.tenant_id = p.tenant_id AND w.deleted = 0 "
                    + "WHERE p.order_id = ? AND p.process_seq = ? AND p.tenant_id = ? AND p.deleted = 0",
                    orderId, processSeq, user.tenantId());
        } catch (Exception ex) {
            throw new BusinessException(LifecycleErrorCode.ORDER_PROCESS_NOT_FOUND, "工单工序实例不存在");
        }
    }

    private Map<String, Object> requireOrder(long tenantId, long orderId) {
        try {
            return jdbc.queryForMap("SELECT id, order_code, task_id, tenant_id, order_status, "
                    + "current_executor_id, component_id, project_id, participant_ids FROM work_order "
                    + "WHERE id = ? AND tenant_id = ? AND deleted = 0", orderId, tenantId);
        } catch (Exception ex) {
            throw new BusinessException(LifecycleErrorCode.ORDER_NOT_FOUND, "工单不存在");
        }
    }

    private Map<String, Object> findFeedback(long tenantId, long orderId, int processSeq) {
        try {
            return jdbc.queryForMap("SELECT id, tenant_id, order_id, process_seq, progress_desc, exit_content_filled, "
                    + "deliverable_ids, work_log, attachment_ids, extra_remark, filled_by, filled_at, is_locked, "
                    + "snapshot_version FROM process_feedback WHERE order_id = ? AND process_seq = ? "
                    + "AND tenant_id = ? AND deleted = 0", orderId, processSeq, tenantId);
        } catch (Exception ex) {
            return null;
        }
    }

    private FeedbackView toView(long tenantId, long orderId, int processSeq) {
        Map<String, Object> feedback = findFeedback(tenantId, orderId, processSeq);
        List<Map<String, Object>> issues = jdbc.queryForList("SELECT id, issue_title, issue_desc, scene, impact_scope, "
                + "attachment_ids, reporter_id, reported_at, admin_advice FROM process_issue_report "
                + "WHERE order_id = ? AND process_seq = ? AND tenant_id = ? AND deleted = 0 ORDER BY id",
                orderId, processSeq, tenantId);
        List<Map<String, Object>> risks = jdbc.queryForList("SELECT id, risk_title, risk_level, risk_desc, probability, "
                + "impact_scope, prepared_measure, strategy_text, attachment_ids, reporter_id, reported_at "
                + "FROM process_risk_report WHERE order_id = ? AND process_seq = ? AND tenant_id = ? AND deleted = 0 ORDER BY id",
                orderId, processSeq, tenantId);
        List<Map<String, Object>> logs = jdbc.queryForList("SELECT id, content, operator_id, created_at FROM work_log "
                + "WHERE order_id = ? AND process_seq = ? AND tenant_id = ? AND deleted = 0 ORDER BY id",
                orderId, processSeq, tenantId);
        if (feedback == null) {
            return new FeedbackView(0, orderId, processSeq, null, null, List.of(), logs, List.of(), null, 0, null,
                    false, "", issues, risks, 0);
        }
        return new FeedbackView(((Number) feedback.get("id")).longValue(), orderId, processSeq,
                stringOrNull(feedback.get("progress_desc")), stringOrNull(feedback.get("exit_content_filled")),
                parseLongList(String.valueOf(feedback.get("deliverable_ids") == null ? "[]" : feedback.get("deliverable_ids"))),
                logs, parseLongList(String.valueOf(feedback.get("attachment_ids") == null ? "[]" : feedback.get("attachment_ids"))),
                stringOrNull(feedback.get("extra_remark")), getLong(feedback, "filled_by"),
                feedback.get("filled_at") == null ? null : ((java.sql.Timestamp) feedback.get("filled_at")).toLocalDateTime(),
                intOf(feedback, "is_locked") == 1, stringOrNull(feedback.get("snapshot_version")), issues, risks, 0);
    }

    private static boolean hasDeliverables(Map<String, Object> feedback) {
        if (feedback == null) {
            return false;
        }
        Object raw = feedback.get("deliverable_ids");
        return raw != null && !String.valueOf(raw).isBlank() && !"null".equals(String.valueOf(raw))
                && !"[]".equals(String.valueOf(raw));
    }

    private List<Map<String, Object>> parseJsonList(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value);
        return s.isBlank() || "null".equals(s) ? null : s.trim();
    }

    private static List<Long> longList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (Object item : list) {
            try {
                Long id = Long.valueOf(String.valueOf(item));
                if (id != null && id > 0) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
                // 忽略非法项
            }
        }
        return ids;
    }

    private static int intOf(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static int boolInt(boolean value) {
        return value ? 1 : 0;
    }

    private static long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String toJson(Object value) {
        try {
            if (value == null) {
                return null;
            }
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "序列化失败");
        }
    }

    private static long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
