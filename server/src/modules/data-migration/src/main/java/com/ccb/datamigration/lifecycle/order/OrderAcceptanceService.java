package com.ccb.datamigration.lifecycle.order;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.datamigration.lifecycle.LifecycleAuditService;
import com.ccb.datamigration.lifecycle.LifecyclePermissionService;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import com.ccb.datamigration.lifecycle.order.model.OrderView;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 工单承接（基线 10.5 输出 → 11.2.1）：任务下发生成的每张工单自动绑定活动快照版本与工序实例，
 * 初始化流转参数；专题拆分工单初始态「待前置」且工序全部未解锁（不允许先解锁后锁回）。
 */
@Service
public class OrderAcceptanceService {
    private final JdbcTemplate jdbc;
    private final LifecyclePermissionService permissions;
    private final LifecycleAuditService audit;
    private final ObjectMapper objectMapper;

    public OrderAcceptanceService(JdbcTemplate jdbc, LifecyclePermissionService permissions, LifecycleAuditService audit,
                                  ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    /** 下发草稿：快照为已增广的工单级载荷（TOPIC=被展开普通活动 + topic_activity_ids/aggregate_edges/sub_activity_id）。 */
    public record OrderDraft(long activityId, String activityName, Long subActivityId, Long componentId, long projectId,
                             Long businessGroupId, long defaultExecutorId, long currentExecutorId,
                             List<Long> participantIds, LocalDateTime planFinishTime, String snapshotVersion,
                             Map<String, Object> snapshot, List<Long> topicActivityIds,
                             List<Map<String, Object>> aggregateEdges, boolean crossActivityBlocked) {
    }

    @Transactional
    public List<OrderView> acceptOrders(AuthUser user, long taskId, String taskCode, String taskName,
                                        List<OrderDraft> drafts) {
        permissions.requireAdmin(user);
        if (drafts == null || drafts.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.TASK_COMPONENT_REQUIRED, "拆分结果为空，无工单可承接");
        }
        List<OrderView> views = new ArrayList<>();
        for (OrderDraft draft : drafts) {
            Map<String, Object> snapshot = draft.snapshot();
            List<Map<String, Object>> definitions = snapshotDefinitions(snapshot);
            List<Map<String, Object>> edges = snapshotEdges(snapshot);
            Map<Integer, Integer> predecessorCount = predecessorCount(definitions, edges);
            long orderId = nextId();
            String orderCode = nextOrderCode(user.tenantId());
            String initialStatus = draft.crossActivityBlocked() ? "WAIT_PRE" : "EXECUTING";
            String blockReason = draft.crossActivityBlocked() ? "PRECONDITION_OPEN" : "NONE";
            jdbc.update("INSERT INTO work_order (id, tenant_id, order_code, task_id, granularity, activity_type, "
                            + "activity_id, activity_name, sub_activity_id, topic_activity_ids, aggregate_edges, "
                            + "snapshot_version, snapshot_json, project_id, business_group_id, component_id, "
                            + "default_executor_id, current_executor_id, participant_ids, flow_start_at, order_status, "
                            + "deadline_status, plan_finish_time, sla_exempt, total_process_count, closed_process_count, "
                            + "flow_progress, block_reason, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NORMAL', ?, 0, ?, 0, 0.00, ?, ?)",
                    orderId, user.tenantId(), orderCode, taskId, draft.componentId() == null ? "PROJECT" : "COMPONENT",
                    String.valueOf(draft.snapshot().get("activityType") == null ? "" : draft.snapshot().get("activityType")),
                    draft.activityId(), draft.activityName(), draft.subActivityId(),
                    toJson(draft.topicActivityIds()), toJson(draft.aggregateEdges()), draft.snapshotVersion(),
                    toJson(draft.snapshot()), draft.projectId(), draft.businessGroupId(), draft.componentId(),
                    draft.defaultExecutorId(), draft.currentExecutorId(), toJson(draft.participantIds()),
                    draft.crossActivityBlocked() ? null : LocalDateTime.now(),
                    initialStatus, draft.planFinishTime(), definitions.size(), blockReason, user.id());
            int seq = 1;
            for (Map<String, Object> definition : definitions) {
                int processSeq = ((Number) definition.get("seq")).intValue();
                String processStatus = (!draft.crossActivityBlocked() && predecessorCount.getOrDefault(processSeq, 0) == 0)
                        ? "EXECUTING" : "LOCKED";
                jdbc.update("INSERT INTO order_process_instance (id, tenant_id, order_id, process_seq, process_name, "
                                + "process_status, pre_depend_status, exit_filled, deliverable_submitted, audit_status, "
                                + "must_audit, must_submit_deliverable, reject_count, unlocked_at, closed_at, created_by) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, 'WAIT_REVIEW', ?, ?, 0, ?, NULL, ?)",
                        nextId(), user.tenantId(), orderId, processSeq, String.valueOf(definition.get("processName")),
                        processStatus,
                        predecessorCount.getOrDefault(processSeq, 0) == 0 ? "NONE" : "OPEN",
                        boolOf(definition, "mustAudit", true), boolOf(definition, "mustSubmitDeliverable", true),
                        "EXECUTING".equals(processStatus) ? LocalDateTime.now() : null, user.id());
                seq++;
            }
            writeStatusLog(user, orderId, null, initialStatus, "承接下发任务 " + taskCode);
            views.add(new OrderView(orderId, orderCode, taskId, draft.componentId() == null ? "PROJECT" : "COMPONENT",
                    String.valueOf(draft.snapshot().get("activityType")), draft.activityId(), draft.activityName(),
                    draft.subActivityId(), draft.componentId(), initialStatus, "NORMAL", definitions.size(), 0, 0.00,
                    draft.planFinishTime(), draft.crossActivityBlocked() ? null : LocalDateTime.now(), null,
                    draft.currentExecutorId(), blockReason));
        }
        audit.audit(user, audit.opCode("ACCEPT", "ORDER"), "TASK", taskId, "SUCCESS", null,
                Map.of("taskCode", taskCode, "orderCount", views.size()));
        return views;
    }

    private List<Map<String, Object>> snapshotDefinitions(Map<String, Object> snapshot) {
        Object raw = snapshot.get("processDefinitions");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.TASK_ACTIVITY_SNAPSHOT_MISSING, "活动快照缺少工序定义，禁止下发");
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
    }

    private List<Map<String, Object>> snapshotEdges(Map<String, Object> snapshot) {
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
    }

    /** 按 seq 归集入边数量（依赖边以 processId 表达，映射到 seq）。 */
    private Map<Integer, Integer> predecessorCount(List<Map<String, Object>> definitions, List<Map<String, Object>> edges) {
        Map<Long, Integer> seqByProcessId = new HashMap<>();
        for (Map<String, Object> definition : definitions) {
            seqByProcessId.put(((Number) definition.get("processId")).longValue(), ((Number) definition.get("seq")).intValue());
        }
        Map<Integer, Integer> count = new HashMap<>();
        for (Map<String, Object> edge : edges) {
            long source = ((Number) edge.get("sourceProcessId")).longValue();
            long target = ((Number) edge.get("targetProcessId")).longValue();
            Integer targetSeq = seqByProcessId.get(target);
            if (targetSeq != null) {
                count.merge(targetSeq, 1, Integer::sum);
            }
        }
        return count;
    }

    private String nextOrderCode(long tenantId) {
        Long max = jdbc.queryForObject("SELECT MAX(CAST(SUBSTRING_INDEX(order_code, '-', -1) AS UNSIGNED)) FROM work_order "
                + "WHERE tenant_id = ? AND deleted = 0", Long.class, tenantId);
        long seq = (max == null ? 0 : max) + 1;
        return "WO-" + String.format("%06d", seq);
    }

    private void writeStatusLog(AuthUser user, long orderId, String from, String to, String reason) {
        jdbc.update("INSERT INTO order_status_log (id, tenant_id, order_id, from_status, to_status, reason, actor_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", nextId(), user.tenantId(), orderId, from, to, reason, user.id());
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

    private static boolean boolOf(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private static long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
