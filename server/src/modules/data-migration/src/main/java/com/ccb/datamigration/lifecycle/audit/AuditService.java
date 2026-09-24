package com.ccb.datamigration.lifecycle.audit;

import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.datamigration.lifecycle.LifecyclePermissionService;
import com.ccb.datamigration.lifecycle.audit.model.AuditView;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import com.ccb.datamigration.lifecycle.order.OrderStateMachineService;
import com.ccb.datamigration.lifecycle.order.model.OrderProcessView;
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
 * 任务审核管理（基线第 15 章，T7）：
 * 仅审核角色可审（requireReviewAction：管理员不得代审）；审核留痕 audit_record 与工序状态迁移
 * 同一次提交（D-14 先落库后判定，委托 OrderStateMachineService.applyAuditResult）；
 * 意见通过/打回均必填、打回整改要求强制必填；批量打回三约束（统一原因/二次确认/≤50）；
 * 10 分钟撤销窗口还原工序为待审；已重新提审的工单不可撤销；审核记录固化不可删改。
 */
@Service
public class AuditService {
    private static final int BATCH_LIMIT = 50;
    private static final int REVOKE_WINDOW_MINUTES = 10;

    private final JdbcTemplate jdbc;
    private final LifecyclePermissionService permissions;
    private final OrderStateMachineService orderMachine;
    private final ObjectMapper objectMapper;

    public AuditService(JdbcTemplate jdbc, LifecyclePermissionService permissions,
                        OrderStateMachineService orderMachine, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.orderMachine = orderMachine;
        this.objectMapper = objectMapper;
    }

    /** 审核台账（角色差异化）：管理员全量；审核人=待审+本人已审/打回；执行人=本人工单的待审/打回。
     *  覆盖无审核记录的待审工单（以工序实例为主表，LEFT JOIN 最新一条审核记录）。 */
    public PageResult<AuditView> listAudit(AuthUser user, String orderCode, String auditResult, String auditStatus,
                                           long page, long size) {
        StringBuilder where = new StringBuilder("p.tenant_id = ? AND p.deleted = 0 "
                + "AND (p.process_status = 'REVIEWING' OR (p.audit_status = 'REJECTED' AND p.process_status = 'EXECUTING'))");
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        boolean admin = permissions.isAdmin(user);
        if (!admin) {
            if (permissions.canAudit(user)) {
                // 审核人：本人已审/打回 + 全部待审（待审靠快照审核角色，此处放开待审可见）
                where.append(" AND (p.process_status = 'REVIEWING' OR a.auditor_id = ?)");
                args.add(user.id());
            } else {
                // 普通执行人：仅本人工单
                where.append(" AND w.current_executor_id = ?");
                args.add(user.id());
            }
        }
        if (orderCode != null && !orderCode.isBlank()) {
            where.append(" AND w.order_code LIKE ?");
            args.add("%" + orderCode.trim() + "%");
        }
        if (auditResult != null && !auditResult.isBlank()) {
            where.append(" AND a.audit_result = ?");
            args.add(auditResult);
        }
        if (auditStatus != null && !auditStatus.isBlank()) {
            where.append(" AND a.audit_status = ?");
            args.add(auditStatus);
        }
        String latest = "SELECT x.* FROM audit_record x JOIN (SELECT order_id, process_seq, MAX(id) mid FROM audit_record "
                + "WHERE tenant_id = ? AND deleted = 0 GROUP BY order_id, process_seq) m ON m.mid = x.id";
        List<Object> countArgs = new ArrayList<>(args);
        countArgs.add(user.tenantId());
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM order_process_instance p "
                + "JOIN work_order w ON w.id = p.order_id AND w.tenant_id = p.tenant_id "
                + "LEFT JOIN (" + latest + ") a ON a.order_id = p.order_id AND a.process_seq = p.process_seq "
                + "WHERE " + where, Long.class, countArgs.toArray());
        long offset = (page - 1) * size;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(user.tenantId());
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT p.order_id, p.process_seq, a.*, "
                + "w.order_code, w.granularity, w.activity_type, w.activity_name, w.component_id, w.current_executor_id, "
                + "p.process_name, p.process_status, p.reject_count, p.audit_status AS pa_audit_status "
                + "FROM order_process_instance p "
                + "JOIN work_order w ON w.id = p.order_id AND w.tenant_id = p.tenant_id "
                + "LEFT JOIN (" + latest + ") a ON a.order_id = p.order_id AND a.process_seq = p.process_seq "
                + "WHERE " + where + " ORDER BY p.order_id DESC, p.process_seq LIMIT " + size + " OFFSET " + offset,
                pageArgs.toArray());
        List<AuditView> records = rows.stream().map(this::toView).toList();
        return new PageResult<>(records, total, page, size);
    }

    /** 单条审核通过：先固化审核记录，再委托状态机闭环（同一次提交）。 */
    @Transactional
    public Map<String, Object> auditPass(AuthUser user, long orderId, int processSeq,
                                         String opinion, String remark, List<Long> attachmentIds) {
        permissions.requireReviewAction(user);
        requireOpinion(opinion);
        Map<String, Object> process = requireReviewing(user, orderId, processSeq);
        int round = intOf(process, "reject_count") + 1;
        long recordId = insertRecord(user, orderId, processSeq, round, "PASSED", opinion, null,
                attachmentIds, remark, null);
        Map<String, Object> result = new LinkedHashMap<>(orderMachine.applyAuditResult(user, orderId, processSeq, "PASSED"));
        result.put("recordId", recordId);
        result.put("auditRound", round);
        return result;
    }

    /** 单条审核打回：打回整改要求强制必填；工序回执行中，复审轮次+1。 */
    @Transactional
    public Map<String, Object> auditReject(AuthUser user, long orderId, int processSeq,
                                           String opinion, String rectifyRequirement, String remark,
                                           List<Long> attachmentIds, Long batchId) {
        permissions.requireReviewAction(user);
        requireOpinion(opinion);
        if (rectifyRequirement == null || rectifyRequirement.isBlank()) {
            throw new BusinessException(LifecycleErrorCode.AUDIT_RECTIFY_REQUIRED, "打回整改要求必填（整改内容/标准/复审条件）");
        }
        Map<String, Object> process = requireReviewing(user, orderId, processSeq);
        int round = intOf(process, "reject_count") + 1;
        long recordId = insertRecord(user, orderId, processSeq, round, "REJECTED", opinion,
                rectifyRequirement, attachmentIds, remark, batchId);
        Map<String, Object> result = new LinkedHashMap<>(orderMachine.applyAuditResult(user, orderId, processSeq, "REJECTED"));
        result.put("recordId", recordId);
        result.put("auditRound", round);
        return result;
    }

    /** 批量通过：单批 ≤50；每条独立固化审核记录，任一条失败整体回滚。 */
    @Transactional
    public Map<String, Object> batchPass(AuthUser user, List<Long> orderIds, String opinion, String remark) {
        permissions.requireReviewAction(user);
        requireBatchLimit(orderIds);
        requireOpinion(opinion);
        int passed = 0;
        int closed = 0;
        for (Long orderId : orderIds) {
            for (Integer seq : reviewingProcesses(user.tenantId(), orderId)) {
                Map<String, Object> result = auditPass(user, orderId, seq, opinion, remark, List.of());
                if ("REJECTED".equals(result.get("stage"))) {
                    throw new BusinessException(LifecycleErrorCode.AUDIT_PROCESS_NOT_REVIEWING, "批量通过遇到非审核中工序，整体回滚");
                }
                passed++;
                if ("CLOSED".equals(result.get("stage"))) {
                    closed++;
                }
            }
        }
        return Map.of("passed", passed, "closed", closed, "batch", "PASS");
    }

    /** 批量打回（三约束）：统一原因/二次确认/单批≤50；创建撤销窗口批次，逐条打回并关联 batch_id。 */
    @Transactional
    public Map<String, Object> batchReject(AuthUser user, List<Long> orderIds, String unifiedReason,
                                           String rectifyRequirement, boolean confirmed) {
        permissions.requireReviewAction(user);
        requireBatchLimit(orderIds);
        if (unifiedReason == null || unifiedReason.isBlank() || rectifyRequirement == null || rectifyRequirement.isBlank()) {
            throw new BusinessException(LifecycleErrorCode.AUDIT_BATCH_REASON_REQUIRED, "批量打回必须填写统一原因与整改要求");
        }
        if (!confirmed) {
            throw new BusinessException(LifecycleErrorCode.AUDIT_BATCH_CONFIRM_REQUIRED, "批量打回必须二次确认提交");
        }
        long batchId = nextId();
        LocalDateTime deadline = LocalDateTime.now().plusMinutes(REVOKE_WINDOW_MINUTES);
        jdbc.update("INSERT INTO audit_batch_revoke (id, tenant_id, order_ids, unified_reason, confirmed_at, confirmed_by, "
                        + "revoke_deadline) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6), ?, ?)",
                batchId, user.tenantId(), toJson(orderIds), unifiedReason, user.id(), deadline);
        int rejected = 0;
        for (Long orderId : orderIds) {
            for (Integer seq : reviewingProcesses(user.tenantId(), orderId)) {
                auditReject(user, orderId, seq, unifiedReason, rectifyRequirement, null, List.of(), batchId);
                rejected++;
            }
        }
        return Map.of("batchId", batchId, "rejected", rejected, "revokeDeadline", deadline.toString());
    }

    /** 整批撤销（10 分钟窗口）：还原工序为待审（REVIEWING/WAIT_REVIEW），复审次数回退；
     *  已重新提审的工单不可撤销（跳过并返回清单）。 */
    @Transactional
    public Map<String, Object> revokeBatch(AuthUser user, long batchId) {
        permissions.requireReviewAction(user);
        if (!permissions.canAuditBatch(user)) {
            throw new BusinessException(LifecycleErrorCode.AUDIT_PERMISSION_DENIED, "无批量打回撤销权限");
        }
        Map<String, Object> batch = requireBatch(user.tenantId(), batchId);
        if (batch.get("revoked_at") != null) {
            throw new BusinessException(LifecycleErrorCode.AUDIT_RECORD_NOT_FOUND, "该批次已撤销，禁止重复撤销");
        }
        LocalDateTime deadline = toLocalDateTime(batch.get("revoke_deadline"));
        if (LocalDateTime.now().isAfter(deadline)) {
            throw new BusinessException(LifecycleErrorCode.AUDIT_REVOKE_WINDOW_EXPIRED, "撤销窗口（10 分钟）已过");
        }
        List<Long> orderIds = parseLongList(String.valueOf(batch.get("order_ids")));
        int revoked = 0;
        List<Long> skipped = new ArrayList<>();
        for (Long orderId : orderIds) {
            int reverted = jdbc.update("UPDATE order_process_instance p SET p.process_status = 'REVIEWING', "
                            + "p.audit_status = 'WAIT_REVIEW', p.reject_count = GREATEST(p.reject_count - 1, 0), "
                            + "p.updated_at = CURRENT_TIMESTAMP(6) WHERE p.order_id = ? AND p.tenant_id = ? AND p.deleted = 0 "
                            + "AND p.process_status = 'EXECUTING' AND p.audit_status = 'REJECTED'",
                    orderId, user.tenantId());
            if (reverted > 0) {
                jdbc.update("UPDATE audit_record SET revoked_at = CURRENT_TIMESTAMP(6), updated_at = CURRENT_TIMESTAMP(6) "
                                + "WHERE order_id = ? AND tenant_id = ? AND deleted = 0 AND batch_id = ? AND revoked_at IS NULL",
                        orderId, user.tenantId(), batchId);
                jdbc.update("INSERT INTO order_status_log (id, tenant_id, order_id, from_status, to_status, reason, actor_id) "
                                + "VALUES (?, ?, ?, 'REJECTED', 'REVIEWING', '批量打回撤销，工序还原待审', ?)",
                        nextId(), user.tenantId(), orderId, user.id());
                revoked++;
            } else {
                skipped.add(orderId);
            }
        }
        jdbc.update("UPDATE audit_batch_revoke SET revoked_at = CURRENT_TIMESTAMP(6), revoked_by = ?, "
                + "updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND tenant_id = ? AND deleted = 0",
                user.id(), batchId, user.tenantId());
        return Map.of("batchId", batchId, "revoked", revoked, "skipped", skipped,
                "skippedCount", skipped.size(), "message", skipped.isEmpty() ? "整批撤销完成" : "部分工单已重新提审，不可撤销");
    }

    /** 审核记录溯源（固化展示；含整批撤销留痕的 revoked_at）。 */
    public List<AuditView> auditRecords(AuthUser user, long orderId, int processSeq) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT a.*, w.order_code, w.granularity, w.activity_type, "
                        + "w.activity_name, w.component_id, p.process_name, p.process_status, p.reject_count "
                        + "FROM audit_record a JOIN work_order w ON w.id = a.order_id AND w.tenant_id = a.tenant_id "
                        + "JOIN order_process_instance p ON p.order_id = a.order_id AND p.process_seq = a.process_seq "
                        + "AND p.tenant_id = a.tenant_id WHERE a.order_id = ? AND a.process_seq = ? AND a.tenant_id = ? "
                        + "AND a.deleted = 0 ORDER BY a.audit_round DESC, a.id DESC",
                orderId, processSeq, user.tenantId());
        return rows.stream().map(this::toView).toList();
    }

    /** 审核详情数据包：工单 + 工序面板 + 反馈（进度/日志/问题卡点/交付物/备注）+ 审核记录（基线 15.2.3）。 */
    public Map<String, Object> auditPackage(AuthUser user, long orderId) {
        Map<String, Object> order = orderMachine.orderDetail(user, orderId);
        List<OrderProcessView> processes = orderMachine.orderProcesses(user, orderId);
        List<AuditView> records = auditRecords(user, orderId,
                processes.isEmpty() ? 0 : processes.get(0).processSeq());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", order);
        result.put("processes", processes);
        result.put("auditRecords", records);
        return result;
    }

    // ===== 内部辅助 =====

    private void requireOpinion(String opinion) {
        if (opinion == null || opinion.isBlank()) {
            throw new BusinessException(LifecycleErrorCode.AUDIT_OPINION_REQUIRED, "审核意见必填（通过=合规验收结论；打回=问题定位与不合规点）");
        }
    }

    private Map<String, Object> requireReviewing(AuthUser user, long orderId, int processSeq) {
        Map<String, Object> process = requireProcess(user.tenantId(), orderId, processSeq);
        if (!"REVIEWING".equals(String.valueOf(process.get("process_status")))) {
            throw new BusinessException(LifecycleErrorCode.AUDIT_PROCESS_NOT_REVIEWING, "工序不在审核中，无法写入审核结果");
        }
        return process;
    }

    private List<Integer> reviewingProcesses(long tenantId, long orderId) {
        return jdbc.queryForList("SELECT process_seq FROM order_process_instance WHERE order_id = ? AND tenant_id = ? "
                        + "AND deleted = 0 AND process_status = 'REVIEWING' ORDER BY process_seq",
                Integer.class, orderId, tenantId);
    }

    private long insertRecord(AuthUser user, long orderId, int processSeq, int round, String result,
                              String opinion, String rectifyRequirement, List<Long> attachmentIds,
                              String remark, Long batchId) {
        Map<String, Object> order = requireOrder(user.tenantId(), orderId);
        Map<String, Object> process = requireProcess(user.tenantId(), orderId, processSeq);
        String snapshotVersion = String.valueOf(process.get("snapshot_version") == null ? "" : process.get("snapshot_version"));
        long recordId = nextId();
        jdbc.update("INSERT INTO audit_record (id, tenant_id, order_id, process_seq, audit_round, audit_result, "
                        + "audit_opinion, rectify_requirement, audit_attachment_ids, special_remark, auditor_id, "
                        + "audit_status, rectify_done, post_unlock_status, snapshot_version, batch_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NONE', 'LOCKED', ?, ?)",
                recordId, user.tenantId(), orderId, processSeq, round, result, opinion,
                rectifyRequirement, toJson(attachmentIds == null ? List.of() : attachmentIds),
                remark, user.id(), "PASSED".equals(result) ? "PASSED" : "REJECTED", snapshotVersion, batchId);
        if (order != null && "PASSED".equals(result)) {
            jdbc.update("UPDATE audit_record SET post_unlock_status = 'UNLOCKED' WHERE id = ?", recordId);
        }
        return recordId;
    }

    private Map<String, Object> requireBatch(long tenantId, long batchId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM audit_batch_revoke WHERE id = ? "
                + "AND tenant_id = ? AND deleted = 0", batchId, tenantId);
        if (rows.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.AUDIT_RECORD_NOT_FOUND, "批量打回批次不存在");
        }
        return rows.get(0);
    }

    private void requireBatchLimit(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择待审核工单");
        }
        if (orderIds.size() > BATCH_LIMIT) {
            throw new BusinessException(LifecycleErrorCode.AUDIT_BATCH_LIMIT, "批量审核单批上限 50 条，超出需分批执行");
        }
    }

    private Map<String, Object> requireOrder(long tenantId, long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM work_order WHERE id = ? AND tenant_id = ? AND deleted = 0",
                orderId, tenantId);
        if (rows.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.AUDIT_ORDER_NOT_FOUND, "待审工单不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> requireProcess(long tenantId, long orderId, int processSeq) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM order_process_instance WHERE order_id = ? "
                + "AND process_seq = ? AND tenant_id = ? AND deleted = 0", orderId, processSeq, tenantId);
        if (rows.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.ORDER_PROCESS_NOT_FOUND, "工序实例不存在");
        }
        return rows.get(0);
    }

    private AuditView toView(Map<String, Object> row) {
        Object auditId = row.get("id");
        long id = auditId instanceof Number n ? n.longValue()
                : (longOf(row, "order_id") * 1000 + intOf(row, "process_seq"));
        return new AuditView(id, stringOf(row, "order_code"), longOf(row, "order_id"),
                stringOf(row, "granularity"), stringOf(row, "activity_type"), stringOf(row, "activity_name"),
                nullableLong(row, "component_id"), null, null, intOf(row, "process_seq"),
                stringOf(row, "process_name"), stringOf(row, "process_status"),
                auditId == null ? stringOf(row, "pa_audit_status") : stringOf(row, "audit_status"),
                intOf(row, "reject_count"), auditId == null ? 0 : intOf(row, "audit_round"),
                stringOrNull(row, "audit_result"), stringOrNull(row, "audit_opinion"),
                stringOrNull(row, "rectify_requirement"), nullableLong(row, "auditor_id"),
                toLocalDateTimeNullable(row.get("audited_at")), stringOrNull(row, "rectify_done"),
                stringOrNull(row, "post_unlock_status"), stringOrNull(row, "snapshot_version"),
                nullableLong(row, "batch_id"), toLocalDateTimeNullable(row.get("revoked_at")));
    }

    private static String stringOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static int intOfMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number n ? n.intValue() : (value == null ? 0 : Integer.parseInt(String.valueOf(value)));
    }

    private static int intOf(Map<String, Object> map, String key) {
        return intOfMap(map, key);
    }

    private static long longOf(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number n ? n.longValue() : (value == null ? 0L : Long.parseLong(String.valueOf(value)));
    }

    private static Long nullableLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static String stringOf(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof java.sql.Timestamp stamp) {
            return stamp.toLocalDateTime();
        }
        return value == null ? null : LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }

    private static LocalDateTime toLocalDateTimeNullable(Object value) {
        return value == null ? null : toLocalDateTime(value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "序列化失败：" + ex.getMessage());
        }
    }

    private List<Long> parseLongList(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<Long>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
