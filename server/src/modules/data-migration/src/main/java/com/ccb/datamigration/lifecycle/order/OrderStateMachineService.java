package com.ccb.datamigration.lifecycle.order;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.datamigration.lifecycle.LifecycleAuditService;
import com.ccb.datamigration.lifecycle.LifecyclePermissionService;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import com.ccb.datamigration.lifecycle.order.model.OrderProcessView;
import com.ccb.datamigration.lifecycle.order.model.OrderView;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务流转引擎（基线第 11 章 + 18.2/18.3/18.4）：
 * - 流转仅以工单快照为唯一依据；工单主状态为派生值（D-13），打回取「审核打回」（D-11）；
 * - 负向闸门：禁止人工直改状态；唯一驱动因子=执行反馈达标+审核结果；
 * - 四象限闭环判定出口（先落库后判定，D-14）+ 跨活动解锁传播（D-21）；
 * - 四类异常分支（暂停/转交/超时/异常重启）各自可断言后果（D-19）。
 */
@Service
public class OrderStateMachineService {
    private final JdbcTemplate jdbc;
    private final LifecyclePermissionService permissions;
    private final LifecycleAuditService audit;
    private final ObjectMapper objectMapper;

    public OrderStateMachineService(JdbcTemplate jdbc, LifecyclePermissionService permissions, LifecycleAuditService audit,
                                    ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    public PageResult<OrderView> listOrders(AuthUser user, String orderCode, String orderStatus, String granularity,
                                            String activityType, String deadlineStatus, Long componentId, Long projectId,
                                            Long currentExecutorId, String createdFrom, String createdTo, PageQuery page) {
        refreshDeadlineAll(user.tenantId(), user);
        PageQuery normalized = page == null ? new PageQuery(1, 20) : page;
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        StringBuilder where = new StringBuilder("WHERE o.tenant_id = ? AND o.deleted = 0");
        append(where, args, orderCode, "o.order_code = ?");
        append(where, args, orderStatus, "o.order_status = ?");
        append(where, args, granularity, "o.granularity = ?");
        append(where, args, activityType, "o.activity_type = ?");
        append(where, args, deadlineStatus, "o.deadline_status = ?");
        appendLong(where, args, componentId, "o.component_id = ?");
        appendLong(where, args, projectId, "o.project_id = ?");
        appendLong(where, args, currentExecutorId, "o.current_executor_id = ?");
        append(where, args, createdFrom, "o.created_at >= ?", createdFrom + " 00:00:00");
        append(where, args, createdTo, "o.created_at <= ?", createdTo + " 23:59:59");
        appendDataScope(user, where, args);
        String select = "SELECT o.id, o.order_code, o.task_id, o.granularity, o.activity_type, o.activity_id, "
                + "o.activity_name, o.sub_activity_id, o.component_id, o.order_status, o.deadline_status, "
                + "o.total_process_count, o.closed_process_count, o.flow_progress, o.plan_finish_time, o.flow_start_at, "
                + "o.closed_at, o.current_executor_id, o.block_reason FROM work_order o ";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM work_order o " + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(normalized.size());
        pageArgs.add((normalized.page() - 1) * normalized.size());
        List<OrderView> records = jdbc.query(select + where + " ORDER BY o.updated_at DESC, o.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> mapOrder(rs), pageArgs.toArray());
        return new PageResult<>(records, total == null ? 0 : total, normalized.page(), normalized.size());
    }

    public Map<String, Object> orderDetail(AuthUser user, long orderId) {
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        refreshDeadline(user.tenantId(), orderId);
        order.put("processes", orderProcessViewList(user.tenantId(), orderId));
        order.put("snapshot", parseSnapshot(order.get("snapshot_json")));
        return order;
    }

    public List<OrderProcessView> orderProcesses(AuthUser user, long orderId) {
        requireOrder(user.tenantId(), orderId);
        return orderProcessViewList(user.tenantId(), orderId);
    }

    /** 全进程状态派生（11.2.3 D-13/D-11）：审核打回 > 审核中 > 待前置 > 待接收 > 执行中 > 已闭环。 */
    public String deriveOrderStatus(Map<String, Object> order) {
        String current = String.valueOf(order.get("order_status"));
        if (isReadonly(current)) {
            return current;
        }
        List<Map<String, Object>> processes = queryProcesses(orderIdOf(order), tenantOf(order));
        boolean anyRejected = false;
        boolean anyReviewing = false;
        boolean anyClosed = false;
        boolean anyUnlocked = false;
        for (Map<String, Object> process : processes) {
            String status = String.valueOf(process.get("process_status"));
            if ("REJECTED".equals(String.valueOf(process.get("audit_status")))) {
                anyRejected = true;
            }
            if ("REVIEWING".equals(status)) {
                anyReviewing = true;
            }
            if ("CLOSED".equals(status)) {
                anyClosed = true;
            }
            if ("EXECUTING".equals(status) || process.get("unlocked_at") != null) {
                anyUnlocked = true;
            }
        }
        if (anyRejected) {
            return "REVIEW_REJECTED";
        }
        if (anyReviewing) {
            return "REVIEWING";
        }
        if (processes.isEmpty() || (anyClosed && processes.size() == countClosed(processes))) {
            return "CLOSED";
        }
        if (anyUnlocked) {
            return "EXECUTING";
        }
        return "WAIT_PRE".equals(current) ? "WAIT_PRE" : "WAIT_ACCEPT";
    }

    /** 四象限闭环出口：反馈达标（必填齐备）→ A0 自动闭环 / A1 置 REVIEWING；先落库后判定（D-14）。 */
    @Transactional
    public Map<String, Object> applyFeedbackClosure(AuthUser user, long orderId, int processSeq,
                                                    boolean exitFilled, boolean deliverableSubmitted) {
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        rejectReadonly(order);
        Map<String, Object> process = requireProcess(user.tenantId(), orderId, processSeq);
        String processStatus = String.valueOf(process.get("process_status"));
        if ("CLOSED".equals(processStatus)) {
            throw new BusinessException(LifecycleErrorCode.FEEDBACK_CLOSED_LOCKED, "工序已闭环，禁止再流转");
        }
        if (!"EXECUTING".equals(processStatus)) {
            throw new BusinessException(LifecycleErrorCode.FEEDBACK_PROCESS_LOCKED, "工序未解锁或不在执行态");
        }
        boolean mustAudit = intOf(process, "must_audit") == 1;
        boolean mustSubmit = intOf(process, "must_submit_deliverable") == 1;
        QuadrantClosureValidator.Outcome outcome = QuadrantClosureValidator.evaluate(mustAudit, mustSubmit, exitFilled, deliverableSubmitted);
        if (outcome == QuadrantClosureValidator.Outcome.NOT_READY) {
            if (!exitFilled) {
                throw new BusinessException(LifecycleErrorCode.FEEDBACK_EXIT_REQUIRED, "准出标准内容为必填项");
            }
            throw new BusinessException(LifecycleErrorCode.FEEDBACK_DELIVERABLE_REQUIRED, "需提交交付件工序：必选交付物缺失");
        }
        boolean duplicateReview = "REVIEWING".equals(processStatus) || "WAIT_REVIEW".equals(String.valueOf(process.get("audit_status")))
                && !"REJECTED".equals(String.valueOf(process.get("audit_status")));
        if (outcome == QuadrantClosureValidator.Outcome.WAIT_AUDIT) {
            if (duplicateReview && "REVIEWING".equals(processStatus)) {
                throw new BusinessException(LifecycleErrorCode.ORDER_SUBMIT_ALREADY_REVIEWING, "工序已在审核中，重复提审被拒");
            }
            jdbc.update("UPDATE order_process_instance SET process_status = 'REVIEWING', exit_filled = ?, "
                            + "deliverable_submitted = ?, audit_status = CASE WHEN audit_status = 'PASSED' THEN 'WAIT_REVIEW' ELSE audit_status END, "
                            + "updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND tenant_id = ? AND deleted = 0",
                    boolInt(exitFilled), boolInt(deliverableSubmitted), process.get("id"), user.tenantId());
            writeStatusLog(user, orderId, processSeq, "EXECUTING", "REVIEWING", "执行反馈达标，提审进入审核");
            recomputeAndPersist(user, orderId);
            return Map.of("orderId", orderId, "processSeq", processSeq, "stage", "REVIEWING");
        }
        closeProcess(user, orderId, processSeq, exitFilled, deliverableSubmitted, "A0 免审核自动闭环");
        return Map.of("orderId", orderId, "processSeq", processSeq, "stage", "CLOSED");
    }

    /** 审核结果驱动解锁入口（{order_id, process_seq, audit_result}，T7 消费契约）。先落库后判定（D-14）。 */
    @Transactional
    public Map<String, Object> applyAuditResult(AuthUser user, long orderId, int processSeq, String auditResult) {
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        rejectReadonly(order);
        Map<String, Object> process = requireProcess(user.tenantId(), orderId, processSeq);
        if (!"REVIEWING".equals(String.valueOf(process.get("process_status")))) {
            throw new BusinessException(LifecycleErrorCode.ORDER_PROCESS_NOT_FOUND, "工序不在审核中，无法写入审核结果");
        }
        if (!"PASSED".equals(auditResult) && !"REJECTED".equals(auditResult)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审核结果必须为 PASSED/REJECTED");
        }
        // ① 先落库 audit_status（D-14 时序）
        jdbc.update("UPDATE order_process_instance SET audit_status = ?, updated_at = CURRENT_TIMESTAMP(6) "
                + "WHERE id = ? AND tenant_id = ? AND deleted = 0", auditResult, process.get("id"), user.tenantId());
        if ("PASSED".equals(auditResult)) {
            Map<String, Object> latest = queryProcess(user.tenantId(), orderId, processSeq);
            closeProcess(user, orderId, processSeq, intOf(latest, "exit_filled") == 1,
                    intOf(latest, "deliverable_submitted") == 1, "审核通过，工序闭环");
            return Map.of("orderId", orderId, "processSeq", processSeq, "stage", "CLOSED");
        }
        // ② REJECTED → 工序退回 EXECUTING，打回次数+1，审核状态 REJECTED（打回派生口径 D-11）
        int rejectCount = intOf(process, "reject_count") + 1;
        jdbc.update("UPDATE order_process_instance SET process_status = 'EXECUTING', reject_count = ?, "
                        + "updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND tenant_id = ? AND deleted = 0",
                rejectCount, process.get("id"), user.tenantId());
        writeStatusLog(user, orderId, processSeq, "REVIEWING", "REJECTED", "审核打回，工序退回执行中，锁止后置");
        recomputeAndPersist(user, orderId);
        return Map.of("orderId", orderId, "processSeq", processSeq, "stage", "REJECTED", "rejectCount", rejectCount);
    }

    /** 分支 1：暂停（18.4）。全部未闭环工序锁定，SLA 计时暂停，记录暂停前状态与工序快照。 */
    @Transactional
    public Map<String, Object> suspend(AuthUser user, long orderId, String reason) {
        permissions.requireAdmin(user);
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        String current = String.valueOf(order.get("order_status"));
        if (isTerminalOrArchived(current) || "SUSPENDED".equals(current)) {
            throw new BusinessException(LifecycleErrorCode.ORDER_NO_ACTIVE_FLOW, "仅流转中工单可执行暂停操作");
        }
        List<Map<String, Object>> processes = queryProcesses(orderId, user.tenantId());
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (Map<String, Object> process : processes) {
            String status = String.valueOf(process.get("process_status"));
            if (!"CLOSED".equals(status)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("processSeq", ((Number) process.get("process_seq")).intValue());
                item.put("processStatus", status);
                item.put("unlockedAt", process.get("unlocked_at") == null ? null : String.valueOf(process.get("unlocked_at")));
                snapshot.add(item);
                jdbc.update("UPDATE order_process_instance SET process_status = 'LOCKED', unlocked_at = NULL, "
                        + "updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND tenant_id = ? AND deleted = 0",
                        process.get("id"), user.tenantId());
            }
        }
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("UPDATE work_order SET order_status = 'SUSPENDED', deadline_status = 'NORMAL', "
                        + "suspend_before_status = ?, suspend_at = ?, "
                        + "updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND tenant_id = ? AND deleted = 0",
                current, now, orderId, user.tenantId());
        jdbc.update("INSERT INTO order_suspend_log (id, tenant_id, order_id, suspend_at, suspend_before_status, "
                        + "pre_suspend_processes, old_plan_finish_time, actor_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                nextId(), user.tenantId(), orderId, now, current, toJson(snapshot),
                order.get("plan_finish_time"), user.id());
        writeStatusLog(user, orderId, null, current, "SUSPENDED", reason == null || reason.isBlank() ? "管理员暂停" : reason);
        notify(user.tenantId(), orderId, user.id(), "ORDER_SUSPEND", "工单 " + order.get("order_code") + " 已暂停");
        return Map.of("orderId", orderId, "orderStatus", "SUSPENDED", "suspendBeforeStatus", current, "suspendAt", now.toString());
    }

    /** 分支 1：恢复。回到暂停前状态，plan_finish_time 顺延暂停时长（18.3 八·3：先顺延再重算三档）。 */
    @Transactional
    public Map<String, Object> resume(AuthUser user, long orderId) {
        permissions.requireAdmin(user);
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        if (!"SUSPENDED".equals(String.valueOf(order.get("order_status")))) {
            throw new BusinessException(LifecycleErrorCode.ORDER_NO_ACTIVE_FLOW, "仅已暂停工单可恢复");
        }
        String before = String.valueOf(order.get("suspend_before_status"));
        if (before == null || before.isBlank() || "null".equals(before)) {
            throw new BusinessException(LifecycleErrorCode.ORDER_SUSPEND_BEFORE_MISSING, "暂停态必须记录暂停前状态");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime suspendAt = order.get("suspend_at") == null ? now : ((java.sql.Timestamp) order.get("suspend_at")).toLocalDateTime();
        double hours = Duration.between(suspendAt, now).toMinutes() / 60.0;
        LocalDateTime oldPlan = ((java.sql.Timestamp) order.get("plan_finish_time")).toLocalDateTime();
        LocalDateTime newPlan = oldPlan.plusMinutes(Math.round(hours * 60));
        // 恢复前先还原未闭环工序状态（自暂停前快照）
        List<Map<String, Object>> pre = readSuspendSnapshot(order);
        for (Map<String, Object> item : pre) {
            int processSeq = ((Number) item.get("processSeq")).intValue();
            String status = String.valueOf(item.get("processStatus"));
            String unlockedAt = String.valueOf(item.get("unlockedAt"));
            if (!"null".equals(unlockedAt) && !"<null>".equals(unlockedAt) && unlockedAt != null && !unlockedAt.isEmpty()) {
                jdbc.update("UPDATE order_process_instance SET process_status = ?, unlocked_at = ?, "
                        + "updated_at = CURRENT_TIMESTAMP(6) WHERE order_id = ? AND process_seq = ? AND tenant_id = ? AND deleted = 0",
                        status, java.sql.Timestamp.valueOf(unlockedAt), orderId, processSeq, user.tenantId());
            } else if ("EXECUTING".equals(status) || "REVIEWING".equals(status)) {
                jdbc.update("UPDATE order_process_instance SET process_status = ?, unlocked_at = CURRENT_TIMESTAMP(6), "
                        + "updated_at = CURRENT_TIMESTAMP(6) WHERE order_id = ? AND process_seq = ? AND tenant_id = ? AND deleted = 0",
                        status, orderId, processSeq, user.tenantId());
            } else {
                jdbc.update("UPDATE order_process_instance SET process_status = 'LOCKED', unlocked_at = NULL, "
                        + "updated_at = CURRENT_TIMESTAMP(6) WHERE order_id = ? AND process_seq = ? AND tenant_id = ? AND deleted = 0",
                        orderId, processSeq, user.tenantId());
            }
        }
        jdbc.update("UPDATE work_order SET order_status = ?, resume_at = ?, suspend_before_status = NULL, "
                        + "suspend_hours = ?, plan_finish_time = ?, updated_at = CURRENT_TIMESTAMP(6) "
                        + "WHERE id = ? AND tenant_id = ? AND deleted = 0", before, now, hours, newPlan, orderId, user.tenantId());
        jdbc.update("UPDATE order_suspend_log SET resume_at = ?, new_plan_finish_time = ?, suspend_hours = ? "
                + "WHERE order_id = ? AND tenant_id = ? AND deleted = 0 AND resume_at IS NULL ORDER BY id DESC LIMIT 1",
                now, newPlan, hours, orderId, user.tenantId());
        writeStatusLog(user, orderId, null, "SUSPENDED", before, "恢复流转，计划完成时间顺延 " + hours + " 小时");
        recomputeAndPersist(user, orderId);
        return Map.of("orderId", orderId, "orderStatus", before, "resumeAt", now.toString(),
                "oldPlanFinishTime", oldPlan.toString(), "newPlanFinishTime", newPlan.toString(), "suspendHours", hours);
    }

    /** 分支 2：转交（主状态不变；原因必填；留痕唯一一条）。 */
    @Transactional
    public Map<String, Object> transfer(AuthUser user, long orderId, long nextExecutorId, String reason) {
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        rejectReadonly(order);
        boolean admin = permissions.isAdmin(user);
        boolean owner = order.get("component_id") != null
                && permissions.isOwnerOfComponent(user, ((Number) order.get("component_id")).longValue());
        if (!admin && !owner) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅数据迁移管理员或组件负责人可转交");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(LifecycleErrorCode.ORDER_TRANSFER_REASON_REQUIRED, "转交必须填写原因");
        }
        long current = ((Number) order.get("current_executor_id")).longValue();
        if (current == nextExecutorId) {
            throw new BusinessException(LifecycleErrorCode.ORDER_TRANSFER_SAME_MEMBER, "转交目标不能与当前执行人相同");
        }
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id = ? AND tenant_id = ? "
                + "AND status = 1 AND deleted = 0", Integer.class, nextExecutorId, user.tenantId());
        if (active == null || active == 0) {
            throw new BusinessException(LifecycleErrorCode.TASK_EXECUTOR_INACTIVE, "转交目标必须为已激活成员");
        }
        String beforeStatus = String.valueOf(order.get("order_status"));
        jdbc.update("UPDATE work_order SET current_executor_id = ?, updated_at = CURRENT_TIMESTAMP(6) "
                + "WHERE id = ? AND tenant_id = ? AND deleted = 0", nextExecutorId, orderId, user.tenantId());
        jdbc.update("INSERT INTO order_transfer_log (id, tenant_id, order_id, from_member_id, to_member_id, reason, actor_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", nextId(), user.tenantId(), orderId, current, nextExecutorId, reason, user.id());
        writeStatusLog(user, orderId, null, beforeStatus, beforeStatus, "转交执行人 " + current + " → " + nextExecutorId + "：" + reason);
        notify(user.tenantId(), orderId, nextExecutorId, "ORDER_TRANSFER", "工单 " + order.get("order_code") + " 已转交给您");
        return Map.of("orderId", orderId, "orderStatus", beforeStatus, "fromExecutorId", current, "toExecutorId", nextExecutorId);
    }

    /** 分支 4：异常重启（恢复点之后的工序重置为 LOCKED 并清空时间戳；恢复点及之前不受影响）。 */
    @Transactional
    public Map<String, Object> restart(AuthUser user, long orderId, int restartPointSeq, String reason) {
        permissions.requireAdmin(user);
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        rejectReadonly(order);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "异常重启必须填写原因");
        }
        if (restartPointSeq < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "恢复点序号无效");
        }
        long tenantId = user.tenantId();
        jdbc.update("UPDATE order_process_instance SET process_status = 'LOCKED', unlocked_at = NULL, closed_at = NULL, "
                        + "exit_filled = 0, deliverable_submitted = 0, audit_status = 'WAIT_REVIEW', updated_at = CURRENT_TIMESTAMP(6) "
                        + "WHERE order_id = ? AND tenant_id = ? AND deleted = 0 AND process_seq > ?",
                orderId, tenantId, restartPointSeq);
        jdbc.update("INSERT INTO order_restart_log (id, tenant_id, order_id, restart_point_seq, reason, actor_id) "
                + "VALUES (?, ?, ?, ?, ?, ?)", nextId(), tenantId, orderId, restartPointSeq, reason, user.id());
        writeStatusLog(user, orderId, null, String.valueOf(order.get("order_status")), null, "异常重启，恢复点=" + restartPointSeq + "：" + reason);
        propagateUnlocks(user, orderId, true);
        recomputeAndPersist(user, orderId);
        return Map.of("orderId", orderId, "restartPointSeq", restartPointSeq,
                "resetProcesses", countProcessesAfter(user.tenantId(), orderId, restartPointSeq));
    }

    /** 时效调整 / 豁免（18.3 五：必须填写原因并留痕；豁免后恒 NORMAL 不预警）。 */
    @Transactional
    public Map<String, Object> slaAdjust(AuthUser user, long orderId, LocalDateTime newPlanFinishTime, String reason) {
        permissions.requireAdmin(user);
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(LifecycleErrorCode.ORDER_SLA_REASON_REQUIRED, "时效调整必须填写原因");
        }
        if (newPlanFinishTime != null && newPlanFinishTime.isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw new BusinessException(LifecycleErrorCode.TASK_PLAN_FINISH_TIME_IN_PAST, "计划完成时间不得早于当前时间");
        }
        String from = String.valueOf(order.get("deadline_status"));
        if (newPlanFinishTime != null) {
            jdbc.update("UPDATE work_order SET plan_finish_time = ?, updated_at = CURRENT_TIMESTAMP(6) "
                    + "WHERE id = ? AND tenant_id = ? AND deleted = 0", newPlanFinishTime, orderId, user.tenantId());
        }
        jdbc.update("INSERT INTO order_sla_log (id, tenant_id, order_id, from_deadline, plan_finish_time, reason, actor_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", nextId(), user.tenantId(), orderId, from, newPlanFinishTime, reason, user.id());
        refreshDeadline(user.tenantId(), orderId);
        writeStatusLog(user, orderId, null, null, null, "时效调整：" + reason);
        return Map.of("orderId", orderId, "planFinishTime", newPlanFinishTime == null ? order.get("plan_finish_time") : newPlanFinishTime.toString());
    }

    @Transactional
    public Map<String, Object> slaExempt(AuthUser user, long orderId, boolean exempt, String reason) {
        permissions.requireAdmin(user);
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(LifecycleErrorCode.ORDER_SLA_REASON_REQUIRED, "时效豁免必须填写原因");
        }
        jdbc.update("UPDATE work_order SET sla_exempt = ?, deadline_status = ?, updated_at = CURRENT_TIMESTAMP(6) "
                + "WHERE id = ? AND tenant_id = ? AND deleted = 0", boolInt(exempt), exempt ? "NORMAL" : String.valueOf(order.get("deadline_status")),
                orderId, user.tenantId());
        jdbc.update("INSERT INTO order_sla_log (id, tenant_id, order_id, from_deadline, reason, actor_id) "
                + "VALUES (?, ?, ?, ?, ?, ?)", nextId(), user.tenantId(), orderId, String.valueOf(order.get("deadline_status")),
                (exempt ? "时效豁免：" : "取消时效豁免：") + reason, user.id());
        return Map.of("orderId", orderId, "slaExempt", exempt);
    }

    /** 归档：全部工序闭环且无打回未整改（11.4）。 */
    @Transactional
    public Map<String, Object> archive(AuthUser user, long orderId) {
        permissions.requireAdmin(user);
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        String current = String.valueOf(order.get("order_status"));
        if (!"CLOSED".equals(current) && !"ARCHIVED".equals(current)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已闭环工单可归档");
        }
        Integer blocked = jdbc.queryForObject("SELECT COUNT(*) FROM order_process_instance WHERE order_id = ? "
                + "AND tenant_id = ? AND deleted = 0 AND (process_status <> 'CLOSED' OR audit_status = 'REJECTED')",
                Integer.class, orderId, user.tenantId());
        if (blocked != null && blocked > 0) {
            throw new BusinessException(LifecycleErrorCode.ORDER_ARCHIVE_BLOCKED, "存在未闭环或打回未整改工序，禁止归档");
        }
        LocalDateTime closedAt = order.get("closed_at") == null ? LocalDateTime.now()
                : ((java.sql.Timestamp) order.get("closed_at")).toLocalDateTime();
        jdbc.update("UPDATE work_order SET order_status = 'ARCHIVED', deadline_status = 'NORMAL', "
                + "closed_at = ?, updated_at = CURRENT_TIMESTAMP(6) "
                + "WHERE id = ? AND tenant_id = ? AND deleted = 0", closedAt, orderId, user.tenantId());
        writeStatusLog(user, orderId, null, current, "ARCHIVED", "工单归档");
        updateTaskAggregate(user, orderId);
        return Map.of("orderId", orderId, "orderStatus", "ARCHIVED");
    }

    /** 任务作废联动：未终态工单同步作废并留痕（10.2.1）。 */
    @Transactional
    public void cancelOrder(AuthUser user, long orderId, String reason) {
        permissions.requireAdmin(user);
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        String current = String.valueOf(order.get("order_status"));
        if (isTerminalOrArchived(current)) {
            return;
        }
        jdbc.update("UPDATE work_order SET order_status = 'CANCELLED', deadline_status = 'NORMAL', "
                + "updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND tenant_id = ? AND deleted = 0",
                orderId, user.tenantId());
        writeStatusLog(user, orderId, null, current, "CANCELLED", reason == null || reason.isBlank() ? "任务作废联动" : reason);
        updateTaskAggregate(user, orderId);
    }

    /** SLA 刷新钩子（读写前置；只写 deadline_status 与通知，绝不触碰 order_status）。 */
    public void refreshDeadlineAll(long tenantId, AuthUser user) {
        List<Long> orderIds = jdbc.queryForList("SELECT id FROM work_order WHERE tenant_id = ? AND deleted = 0 "
                + "AND order_status NOT IN ('CLOSED', 'ARCHIVED', 'CANCELLED', 'SUSPENDED') AND sla_exempt = 0",
                Long.class, tenantId);
        for (Long orderId : orderIds) {
            refreshDeadline(tenantId, orderId);
        }
    }

    public void refreshDeadline(long tenantId, long orderId) {
        try {
            Map<String, Object> order = jdbc.queryForMap("SELECT id, order_code, order_status, deadline_status, "
                    + "plan_finish_time, sla_exempt, current_executor_id, component_id, project_id FROM work_order "
                    + "WHERE id = ? AND tenant_id = ? AND deleted = 0", orderId, tenantId);
            String status = String.valueOf(order.get("order_status"));
            boolean exempt = intOf(order, "sla_exempt") == 1;
            if (!SlaAdjudicator.participatesInWarning(status, exempt)) {
                return;
            }
            LocalDateTime plan = ((java.sql.Timestamp) order.get("plan_finish_time")).toLocalDateTime();
            String next = SlaAdjudicator.adjudicate(LocalDateTime.now(), plan);
            String current = String.valueOf(order.get("deadline_status"));
            if (!next.equals(current)) {
                jdbc.update("UPDATE work_order SET deadline_status = ?, updated_at = CURRENT_TIMESTAMP(6) "
                        + "WHERE id = ? AND tenant_id = ? AND deleted = 0", next, orderId, tenantId);
                insertSlaLog(tenantId, orderId, current, next);
                if ("NEAR_OVERDUE".equals(next)) {
                    notify(tenantId, orderId, ((Number) order.get("current_executor_id")).longValue(), "SLA_NEAR_OVERDUE",
                            "工单 " + order.get("order_code") + " 即将超时，请尽快闭环");
                } else if ("OVERDUE".equals(next)) {
                    long executor = ((Number) order.get("current_executor_id")).longValue();
                    notify(tenantId, orderId, executor, "SLA_OVERDUE", "工单 " + order.get("order_code") + " 已超时");
                    if (order.get("component_id") != null) {
                        List<Long> owners = jdbc.queryForList("SELECT owner_id FROM dm_component WHERE id = ? "
                                + "AND tenant_id = ? AND deleted = 0", Long.class, ((Number) order.get("component_id")).longValue(), tenantId);
                        if (!owners.isEmpty()) {
                            notify(tenantId, orderId, owners.get(0), "SLA_OVERDUE", "工单 " + order.get("order_code") + " 已超时（组件负责人）");
                        }
                    }
                    if (order.get("project_id") != null) {
                        List<Long> owners = jdbc.queryForList("SELECT owner_id FROM pm_project WHERE id = ? "
                                + "AND tenant_id = ? AND deleted = 0", Long.class, ((Number) order.get("project_id")).longValue(), tenantId);
                        if (!owners.isEmpty()) {
                            notify(tenantId, orderId, owners.get(0), "SLA_OVERDUE", "工单 " + order.get("order_code") + " 已超时（项目负责人）");
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 刷新钩子容错：单条失败不阻断列表
        }
    }

    /** 工序闭环 + 解锁传播（活动内 + 专题跨活动 D-21），并重算工单主状态。 */
    private void closeProcess(AuthUser user, long orderId, int processSeq, boolean exitFilled,
                              boolean deliverableSubmitted, String reason) {
        Map<String, Object> process = requireProcess(user.tenantId(), orderId, processSeq);
        LocalDateTime closedAt = LocalDateTime.now();
        jdbc.update("UPDATE order_process_instance SET process_status = 'CLOSED', closed_at = ?, exit_filled = ?, "
                        + "deliverable_submitted = ?, audit_status = CASE WHEN audit_status = 'REJECTED' THEN 'RECHECK' ELSE audit_status END, "
                        + "updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND tenant_id = ? AND deleted = 0",
                closedAt, boolInt(exitFilled), boolInt(deliverableSubmitted), process.get("id"), user.tenantId());
        writeStatusLog(user, orderId, processSeq, String.valueOf(process.get("process_status")), "CLOSED", reason);
        // 先重算工单主状态，再传播解锁（D-21 时序）
        recomputeAndPersist(user, orderId);
        propagateUnlocks(user, orderId, true);
        // 全部闭环 → CLOSED 并更新任务聚合
        Map<String, Object> latest = requireOrder(user.tenantId(), orderId);
        if ("CLOSED".equals(String.valueOf(latest.get("order_status")))) {
            updateTaskAggregate(user, orderId);
            refreshDeadline(user.tenantId(), orderId);
        }
    }

    private void propagateUnlocks(AuthUser user, long orderId, boolean topicGate) {
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        if ("ARCHIVED".equals(String.valueOf(order.get("order_status")))
                || "CANCELLED".equals(String.valueOf(order.get("order_status")))) {
            return;
        }
        Map<Integer, List<Integer>> predecessors = predecessorMap(user.tenantId(), orderId);
        List<Map<String, Object>> processes = queryProcesses(orderId, user.tenantId());
        Map<Integer, Map<String, Object>> bySeq = new LinkedHashMap<>();
        for (Map<String, Object> process : processes) {
            bySeq.put(((Number) process.get("process_seq")).intValue(), process);
        }
        boolean crossGateOpen = !topicGate || topicGateOpen(user, order);
        for (Map.Entry<Integer, Map<String, Object>> entry : bySeq.entrySet()) {
            Map<String, Object> process = entry.getValue();
            if (!"LOCKED".equals(String.valueOf(process.get("process_status")))) {
                continue;
            }
            if (!crossGateOpen) {
                continue;
            }
            List<Integer> pres = predecessors.getOrDefault(entry.getKey(), List.of());
            boolean allClosed = true;
            for (Integer pre : pres) {
                Map<String, Object> preProcess = bySeq.get(pre);
                if (preProcess == null || !"CLOSED".equals(String.valueOf(preProcess.get("process_status")))) {
                    allClosed = false;
                    break;
                }
            }
            if (allClosed) {
                unlockProcess(user, orderId, entry.getKey());
            }
        }
        if (crossGateOpen) {
            updateBlockReason(user, orderId);
        }
        // 跨活动传播：本活动全部工序闭环后解锁后续被聚合活动工单
        propagateTopicUnlocks(user, order, topicGate);
    }

    private void unlockProcess(AuthUser user, long orderId, int processSeq) {
        jdbc.update("UPDATE order_process_instance SET process_status = 'EXECUTING', unlocked_at = CURRENT_TIMESTAMP(6), "
                        + "updated_at = CURRENT_TIMESTAMP(6) WHERE order_id = ? AND process_seq = ? AND tenant_id = ? AND deleted = 0",
                orderId, processSeq, user.tenantId());
        jdbc.update("UPDATE work_order SET flow_start_at = COALESCE(flow_start_at, CURRENT_TIMESTAMP(6)), block_reason = 'NONE', "
                        + "updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND tenant_id = ? AND deleted = 0", orderId, user.tenantId());
        writeStatusLog(user, orderId, processSeq, "LOCKED", "EXECUTING", "全部前置闭环，自动解锁");
    }

    /** 跨活动闸门（D-21）：某被聚合活动可解锁 ⇔ 其跨活动前置活动全部闭环。未下发/未闭环均视为未闭环。 */
    private boolean topicGateOpen(AuthUser user, Map<String, Object> order) {
        Object raw = order.get("aggregate_edges");
        if (!(raw instanceof String json) || json.isBlank()) {
            return true;
        }
        long tenantId = ((Number) order.get("tenant_id")).longValue();
        long orderActivityId = ((Number) order.get("activity_id")).longValue();
        List<Map<String, Object>> edges = parseJsonList(json);
        if (edges.isEmpty()) {
            return true;
        }
        for (Map<String, Object> edge : edges) {
            if (((Number) edge.get("target_activity_id")).longValue() != orderActivityId) {
                continue;
            }
            long sourceActivityId = ((Number) edge.get("source_activity_id")).longValue();
            if (!isActivityFullyClosed(tenantId, sourceActivityId, order.get("task_id"))) {
                return false;
            }
        }
        return true;
    }

    private boolean isActivityFullyClosed(long tenantId, long activityId, Object taskIdObj) {
        long taskId = ((Number) taskIdObj).longValue();
        List<Long> orderIds = jdbc.queryForList("SELECT id FROM work_order WHERE task_id = ? AND tenant_id = ? "
                + "AND activity_id = ? AND deleted = 0", Long.class, taskId, tenantId, activityId);
        if (orderIds.isEmpty()) {
            return false; // 未下发生成的子活动视为未闭环
        }
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM order_process_instance WHERE order_id IN ("
                + String.join(",", java.util.Collections.nCopies(orderIds.size(), "?")) + ") "
                + "AND tenant_id = ? AND deleted = 0 AND process_status <> 'CLOSED'",
                Integer.class, concat(orderIds, tenantId));
        return open == null || open == 0;
    }

    private void propagateTopicUnlocks(AuthUser user, Map<String, Object> order, boolean topicGate) {
        Object taskRaw = order.get("task_id");
        if (taskRaw == null || !hasTopicEdges(order)) {
            return;
        }
        long tenantId = ((Number) order.get("tenant_id")).longValue();
        long taskId = ((Number) taskRaw).longValue();
        long activityId = ((Number) order.get("activity_id")).longValue();
        List<Map<String, Object>> edges = parseJsonList(String.valueOf(order.get("aggregate_edges")));
        for (Map<String, Object> edge : edges) {
            if (((Number) edge.get("source_activity_id")).longValue() != activityId) {
                continue;
            }
            long targetActivityId = ((Number) edge.get("target_activity_id")).longValue();
            if (!isActivityFullyClosed(tenantId, activityId, taskId)) {
                continue;
            }
            List<Long> downstream = jdbc.queryForList("SELECT id FROM work_order WHERE task_id = ? AND tenant_id = ? "
                    + "AND activity_id = ? AND order_status = 'WAIT_PRE' AND deleted = 0", Long.class, taskId, tenantId, targetActivityId);
            for (Long downstreamOrderId : downstream) {
                unlockProcessesOfOrder(user, downstreamOrderId);
            }
        }
    }

    private void unlockProcessesOfOrder(AuthUser user, long orderId) {
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        if (!"WAIT_PRE".equals(String.valueOf(order.get("order_status")))) {
            return;
        }
        jdbc.update("UPDATE work_order SET block_reason = 'NONE', updated_at = CURRENT_TIMESTAMP(6) "
                + "WHERE id = ? AND tenant_id = ? AND deleted = 0", orderId, user.tenantId());
        propagateUnlocks(user, orderId, false);
        recomputeAndPersist(user, orderId);
    }

    /** 主状态派生重算并持久化（工序级事件必须回写工单主状态，D-13）。 */
    private void recomputeAndPersist(AuthUser user, long orderId) {
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        String current = String.valueOf(order.get("order_status"));
        if (isReadonly(current)) {
            return;
        }
        String derived = deriveOrderStatus(order);
        if (!derived.equals(current)) {
            jdbc.update("UPDATE work_order SET order_status = ?, "
                    + (derived.equals("CLOSED") ? "deadline_status = 'NORMAL', " : "")
                    + "updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND tenant_id = ? AND deleted = 0",
                    derived, orderId, user.tenantId());
            writeStatusLog(user, orderId, null, current, derived, "工单主状态派生重算");
        }
        int closedCount = getClosedCount(user.tenantId(), orderId);
        jdbc.update("UPDATE work_order SET closed_process_count = ?, flow_progress = ?, "
                + "updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND tenant_id = ? AND deleted = 0",
                closedCount, round2(100.0 * closedCount / Math.max(1, ((Number) order.get("total_process_count")).intValue())),
                orderId, user.tenantId());
        updateTaskAggregate(user, orderId);
    }

    private void updateTaskAggregate(AuthUser user, long orderId) {
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        long taskId = ((Number) order.get("task_id")).longValue();
        List<Map<String, Object>> orders = jdbc.queryForList("SELECT order_status, total_process_count, closed_process_count "
                + "FROM work_order WHERE task_id = ? AND tenant_id = ? AND deleted = 0", taskId, user.tenantId());
        int total = 0;
        int closed = 0;
        String taskStatus = "WAIT_ACCEPT";
        boolean anyExecuting = false;
        boolean anyReviewing = false;
        boolean anyRejected = false;
        boolean anyWaitPre = false;
        boolean allTerminal = !orders.isEmpty();
        for (Map<String, Object> item : orders) {
            String status = String.valueOf(item.get("order_status"));
            total += ((Number) item.get("total_process_count")).intValue();
            closed += ((Number) item.get("closed_process_count")).intValue();
            if ("EXECUTING".equals(status)) {
                anyExecuting = true;
            }
            if ("REVIEWING".equals(status)) {
                anyReviewing = true;
            }
            if ("REVIEW_REJECTED".equals(status)) {
                anyRejected = true;
            }
            if ("WAIT_PRE".equals(status)) {
                anyWaitPre = true;
            }
            if (!"CLOSED".equals(status) && !"ARCHIVED".equals(status)) {
                allTerminal = false;
            }
        }
        if (allTerminal) {
            taskStatus = "CLOSED";
        } else if (anyRejected) {
            taskStatus = "REVIEW_REJECTED";
        } else if (anyReviewing) {
            taskStatus = "REVIEWING";
        } else if (anyWaitPre) {
            taskStatus = "WAIT_PRE";
        } else if (anyExecuting) {
            taskStatus = "EXECUTING";
        }
        jdbc.update("UPDATE task SET task_status = ?, finished_process_count = ?, total_process_count = ?, close_progress = ? "
                + "WHERE id = ? AND tenant_id = ? AND deleted = 0", taskStatus, closed, total,
                round2(100.0 * closed / Math.max(1, total)), taskId, user.tenantId());
    }

    private void updateBlockReason(AuthUser user, long orderId) {
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        Integer locked = jdbc.queryForObject("SELECT COUNT(*) FROM order_process_instance WHERE order_id = ? "
                + "AND tenant_id = ? AND deleted = 0 AND process_status = 'LOCKED'", Integer.class, orderId, user.tenantId());
        jdbc.update("UPDATE work_order SET block_reason = ? WHERE id = ? AND tenant_id = ? AND deleted = 0",
                (locked != null && locked > 0) ? "PRECONDITION_OPEN" : "NONE", orderId, user.tenantId());
    }

    private boolean hasTopicEdges(Map<String, Object> order) {
        Object raw = order.get("aggregate_edges");
        return raw != null && !String.valueOf(raw).isBlank() && !"null".equals(String.valueOf(raw));
    }

    private List<Map<String, Object>> parseJsonList(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>> type =
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    };
            for (Object item : objectMapper.readValue(json, type)) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    map.forEach((k, v) -> row.put(String.valueOf(k), v));
                    result.add(row);
                }
            }
            return result;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, Object> parseSnapshot(Object raw) {
        try {
            if (raw == null) {
                return Map.of();
            }
            return objectMapper.readValue(String.valueOf(raw), Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> readSuspendSnapshot(Map<String, Object> order) {
        List<Map<String, Object>> logs = jdbc.queryForList("SELECT pre_suspend_processes FROM order_suspend_log "
                + "WHERE order_id = ? AND tenant_id = ? AND deleted = 0 AND resume_at IS NULL ORDER BY id DESC LIMIT 1",
                ((Number) order.get("id")).longValue(), ((Number) order.get("tenant_id")).longValue());
        if (logs.isEmpty()) {
            return List.of();
        }
        Object raw = logs.get(0).get("pre_suspend_processes");
        if (raw == null) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : parseJsonList(String.valueOf(raw))) {
            result.add(new LinkedHashMap<>(item));
        }
        return result;
    }

    private List<Map<String, Object>> queryProcesses(long orderId, long tenantId) {
        return jdbc.queryForList("SELECT id, order_id, process_seq, process_name, process_status, pre_depend_status, "
                + "exit_filled, deliverable_submitted, audit_status, must_audit, must_submit_deliverable, reject_count, "
                + "unlocked_at, closed_at FROM order_process_instance WHERE order_id = ? AND tenant_id = ? AND deleted = 0 "
                + "ORDER BY process_seq", orderId, tenantId);
    }

    private Map<String, Object> queryProcess(long tenantId, long orderId, int processSeq) {
        return requireProcess(tenantId, orderId, processSeq);
    }

    private Map<Integer, List<Integer>> predecessorMap(long tenantId, long orderId) {
        Map<String, Object> order = requireOrder(tenantId, orderId);
        List<Map<String, Object>> definitions = parseDefinitions(String.valueOf(order.get("snapshot_json")));
        List<Map<String, Object>> edges = parseEdges(String.valueOf(order.get("snapshot_json")));
        Map<Long, Integer> seqByProcessId = new HashMap<>();
        for (Map<String, Object> definition : definitions) {
            seqByProcessId.put(((Number) definition.get("processId")).longValue(), ((Number) definition.get("seq")).intValue());
        }
        Map<Integer, List<Integer>> result = new HashMap<>();
        for (Map<String, Object> edge : edges) {
            Integer source = seqByProcessId.get(((Number) edge.get("sourceProcessId")).longValue());
            Integer target = seqByProcessId.get(((Number) edge.get("targetProcessId")).longValue());
            if (source != null && target != null) {
                result.computeIfAbsent(target, k -> new ArrayList<>()).add(source);
            }
        }
        return result;
    }

    private List<Map<String, Object>> parseDefinitions(String json) {
        try {
            Map<String, Object> snapshot = objectMapper.readValue(json, Map.class);
            Object raw = snapshot.get("processDefinitions");
            if (!(raw instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    map.forEach((k, v) -> row.put(String.valueOf(k), v));
                    result.add(row);
                }
            }
            return result;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Map<String, Object>> parseEdges(String json) {
        try {
            Map<String, Object> snapshot = objectMapper.readValue(json, Map.class);
            Object raw = snapshot.get("edges");
            if (!(raw instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    map.forEach((k, v) -> row.put(String.valueOf(k), v));
                    result.add(row);
                }
            }
            return result;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<OrderProcessView> orderProcessViewList(long tenantId, long orderId) {
        List<Map<String, Object>> rows = queryProcesses(orderId, tenantId);
        Map<Integer, List<Integer>> predecessors = predecessorMap(tenantId, orderId);
        List<OrderProcessView> views = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            int seq = ((Number) row.get("process_seq")).intValue();
            views.add(new OrderProcessView(((Number) row.get("id")).longValue(), orderId, seq,
                    (String) row.get("process_name"), String.valueOf(row.get("process_status")),
                    String.valueOf(row.get("pre_depend_status")), intOf(row, "exit_filled") == 1,
                    intOf(row, "deliverable_submitted") == 1, String.valueOf(row.get("audit_status")),
                    intOf(row, "must_audit") == 1, intOf(row, "must_submit_deliverable") == 1,
                    intOf(row, "reject_count"), tsOrNull(row.get("unlocked_at")), tsOrNull(row.get("closed_at")),
                    predecessors.getOrDefault(seq, List.of()), "LOCKED".equals(String.valueOf(row.get("process_status")))
                    && !predecessors.getOrDefault(seq, List.of()).isEmpty() ? "PRECONDITION_OPEN" : "NONE"));
        }
        return views;
    }

    private int getClosedCount(long tenantId, long orderId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM order_process_instance WHERE order_id = ? "
                + "AND tenant_id = ? AND deleted = 0 AND process_status = 'CLOSED'", Integer.class, orderId, tenantId);
        return count == null ? 0 : count;
    }

    private int countProcessesAfter(long tenantId, long orderId, int restartPointSeq) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM order_process_instance WHERE order_id = ? "
                + "AND tenant_id = ? AND deleted = 0 AND process_seq > ?", Integer.class, orderId, tenantId, restartPointSeq);
        return count == null ? 0 : count;
    }

    private static int countClosed(List<Map<String, Object>> processes) {
        int count = 0;
        for (Map<String, Object> process : processes) {
            if ("CLOSED".equals(String.valueOf(process.get("process_status")))) {
                count++;
            }
        }
        return count;
    }

    private Map<String, Object> requireOrder(long tenantId, long orderId) {
        try {
            Map<String, Object> row = jdbc.queryForMap("SELECT id, tenant_id, order_code, task_id, granularity, "
                    + "activity_type, activity_id, activity_name, sub_activity_id, component_id, order_status, "
                    + "deadline_status, plan_finish_time, sla_exempt, current_executor_id, total_process_count, "
                    + "closed_process_count, flow_progress, suspend_at, suspend_before_status, closed_at, block_reason, "
                    + "snapshot_json, aggregate_edges, project_id FROM work_order WHERE id = ? AND tenant_id = ? AND deleted = 0",
                    orderId, tenantId);
            return row;
        } catch (Exception ex) {
            throw new BusinessException(LifecycleErrorCode.ORDER_NOT_FOUND, "工单不存在");
        }
    }

    private Map<String, Object> requireProcess(long tenantId, long orderId, int processSeq) {
        try {
            return jdbc.queryForMap("SELECT id, order_id, process_seq, process_name, process_status, exit_filled, "
                    + "deliverable_submitted, audit_status, must_audit, must_submit_deliverable, reject_count, unlocked_at, closed_at "
                    + "FROM order_process_instance WHERE order_id = ? AND process_seq = ? AND tenant_id = ? AND deleted = 0",
                    orderId, processSeq, tenantId);
        } catch (Exception ex) {
            throw new BusinessException(LifecycleErrorCode.ORDER_PROCESS_NOT_FOUND, "工序实例不存在");
        }
    }

    private void rejectReadonly(Map<String, Object> order) {
        String status = String.valueOf(order.get("order_status"));
        if ("SUSPENDED".equals(status) || "CANCELLED".equals(status) || "ARCHIVED".equals(status)) {
            throw new BusinessException(LifecycleErrorCode.ORDER_READONLY_STATE, "三态只读：禁止任何写操作");
        }
        if ("CLOSED".equals(status)) {
            throw new BusinessException(LifecycleErrorCode.ORDER_READONLY_STATE, "已闭环工单禁止流转写操作");
        }
    }

    private void writeStatusLog(AuthUser user, long orderId, Integer processSeq, String from, String to, String reason) {
        String suffix = processSeq == null ? "" : "（工序 " + processSeq + "）";
        jdbc.update("INSERT INTO order_status_log (id, tenant_id, order_id, from_status, to_status, reason, actor_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", nextId(), user.tenantId(), orderId, from, to,
                (reason == null ? "" : reason) + suffix, user.id());
    }

    private void insertSlaLog(long tenantId, long orderId, String from, String to) {
        jdbc.update("INSERT INTO order_sla_log (id, tenant_id, order_id, from_deadline, to_deadline, reason, actor_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0)", nextId(), tenantId, orderId, from, to, "时效分档自动刷新");
    }

    private void notify(long tenantId, long orderId, long receiverId, String type, String content) {
        jdbc.update("INSERT IGNORE INTO notification (id, tenant_id, order_id, receiver_id, notify_type, content) "
                + "VALUES (?, ?, ?, ?, ?, ?)", nextId(), tenantId, orderId, receiverId, type, content);
    }

    private void appendDataScope(AuthUser user, StringBuilder where, List<Object> args) {
        if (permissions.isAdmin(user)) {
            return;
        }
        List<Long> ownedComponents = jdbc.queryForList("SELECT id FROM dm_component WHERE tenant_id = ? AND owner_id = ? "
                + "AND deleted = 0", Long.class, user.tenantId(), user.id());
        where.append(" AND (o.current_executor_id = ? OR JSON_CONTAINS(COALESCE(o.participant_ids, JSON_ARRAY()), ?, '$')");
        args.add(user.id());
        args.add(String.valueOf(user.id()));
        if (!ownedComponents.isEmpty()) {
            where.append(" OR o.component_id IN (" + String.join(",", java.util.Collections.nCopies(ownedComponents.size(), "?")) + ")");
            args.addAll(ownedComponents);
        }
        where.append(")");
    }

    private static void append(StringBuilder where, List<Object> args, String value, String clause) {
        append(where, args, value, clause, value);
    }

    private static void append(StringBuilder where, List<Object> args, String value, String clause, Object arg) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(clause);
            args.add(arg);
        }
    }

    private static void appendLong(StringBuilder where, List<Object> args, Long value, String clause) {
        if (value != null) {
            where.append(" AND ").append(clause);
            args.add(value);
        }
    }

    private static OrderView mapOrder(ResultSet rs) {
        try {
            return new OrderView(rs.getLong("id"), rs.getString("order_code"), rs.getLong("task_id"),
                    rs.getString("granularity"), rs.getString("activity_type"), rs.getLong("activity_id"),
                    rs.getString("activity_name"), longOrNull(rs, "sub_activity_id"), longOrNull(rs, "component_id"),
                    rs.getString("order_status"), rs.getString("deadline_status"), rs.getInt("total_process_count"),
                    rs.getInt("closed_process_count"), rs.getDouble("flow_progress"),
                    rs.getTimestamp("plan_finish_time").toLocalDateTime(),
                    rs.getTimestamp("flow_start_at") == null ? null : rs.getTimestamp("flow_start_at").toLocalDateTime(),
                    rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toLocalDateTime(),
                    rs.getLong("current_executor_id"), rs.getString("block_reason"));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "工单映射失败");
        }
    }

    private static boolean isReadonly(String status) {
        return "SUSPENDED".equals(status) || "CANCELLED".equals(status) || "ARCHIVED".equals(status);
    }

    private static boolean isTerminalOrArchived(String status) {
        return "CANCELLED".equals(status) || "ARCHIVED".equals(status) || "CLOSED".equals(status);
    }

    private static long orderIdOf(Map<String, Object> order) {
        return ((Number) order.get("id")).longValue();
    }

    private static long tenantOf(Map<String, Object> order) {
        return ((Number) order.get("tenant_id")).longValue();
    }

    private static long getLong(ResultSet rs, String column) {
        try {
            long value = rs.getLong(column);
            return rs.wasNull() ? 0L : value;
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static Long longOrNull(ResultSet rs, String column) {
        try {
            long value = rs.getLong(column);
            return rs.wasNull() ? null : value;
        } catch (Exception ex) {
            return null;
        }
    }

    private static LocalDateTime tsOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
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

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static Object[] concat(List<Long> heads, long tail) {
        List<Object> values = new ArrayList<>(heads);
        values.add(tail);
        return values.toArray();
    }

    private static long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
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
}
