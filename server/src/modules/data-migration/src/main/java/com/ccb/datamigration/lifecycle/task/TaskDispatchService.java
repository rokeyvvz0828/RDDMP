package com.ccb.datamigration.lifecycle.task;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.datamigration.lifecycle.LifecycleAuditService;
import com.ccb.datamigration.lifecycle.LifecyclePermissionService;
import com.ccb.datamigration.lifecycle.activity.ActivityTopologyService;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import com.ccb.datamigration.lifecycle.order.OrderAcceptanceService;
import com.ccb.datamigration.lifecycle.order.model.OrderView;
import com.ccb.datamigration.lifecycle.task.TaskSplitEngine.SplitItem;
import com.ccb.datamigration.lifecycle.task.model.TaskView;
import com.ccb.security.model.AuthUser;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 任务下发（基线第 10 章）：白名单闸门、颗粒度联动、专题矩阵交集拆分、工单级快照固化（D-22）、权限与计划完成时间闸门。 */
@Service
public class TaskDispatchService {
    private final JdbcTemplate jdbc;
    private final LifecyclePermissionService permissions;
    private final LifecycleAuditService audit;
    private final ActivityTopologyService topologyService;
    private final OrderAcceptanceService acceptanceService;

    public TaskDispatchService(JdbcTemplate jdbc, LifecyclePermissionService permissions, LifecycleAuditService audit,
                               ActivityTopologyService topologyService, OrderAcceptanceService acceptanceService) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.audit = audit;
        this.topologyService = topologyService;
        this.acceptanceService = acceptanceService;
    }

    public PageResult<TaskView> pageTasks(AuthUser user, String taskCode, String taskName, String granularity,
                                          String activityType, String taskStatus, Long projectId, Long componentId,
                                          String createdFrom, String createdTo, PageQuery page) {
        PageQuery normalized = page == null ? new PageQuery(1, 20) : page;
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        StringBuilder where = new StringBuilder("WHERE t.tenant_id = ? AND t.deleted = 0");
        append(where, args, taskCode, "t.task_code = ?");
        append(where, args, taskName, "t.task_name LIKE ?", "%" + taskName + "%");
        append(where, args, granularity, "t.granularity = ?");
        append(where, args, activityType, "t.activity_type = ?");
        append(where, args, taskStatus, "t.task_status = ?");
        appendLong(where, args, projectId, "t.project_id = ?");
        appendLong(where, args, componentId, "t.component_id = ?");
        append(where, args, createdFrom, "t.created_at >= ?", createdFrom + " 00:00:00");
        append(where, args, createdTo, "t.created_at <= ?", createdTo + " 23:59:59");
        String select = "SELECT t.id, t.tenant_id, t.task_code, t.task_name, t.granularity, t.activity_type, "
                + "t.activity_id, t.activity_name, t.topic_activity_id, t.project_id, t.business_group_id, t.component_id, "
                + "t.default_executor_id, t.current_executor_id, t.plan_finish_time, t.task_status, t.current_process_seq, "
                + "t.finished_process_count, t.total_process_count, t.close_progress, t.snapshot_version, "
                + "t.flow_constraint_desc, t.creator_id, t.created_at, t.updated_at, "
                + "COALESCE(p.order_total, 0) AS order_total, COALESCE(p.order_closed, 0) AS order_closed "
                + "FROM task t LEFT JOIN v_task_order_progress p ON p.task_id = t.id AND p.tenant_id = t.tenant_id ";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM task t " + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(normalized.size());
        pageArgs.add((normalized.page() - 1) * normalized.size());
        List<TaskView> records = jdbc.query(select + where + " ORDER BY t.created_at DESC, t.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new TaskView(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("task_code"),
                        rs.getString("task_name"), rs.getString("granularity"), rs.getString("activity_type"),
                        rs.getLong("activity_id"), rs.getString("activity_name"), getLong(rs, "topic_activity_id"),
                        rs.getLong("project_id"), getLong(rs, "business_group_id"), getLong(rs, "component_id"),
                        rs.getLong("default_executor_id"), rs.getLong("current_executor_id"), List.of(),
                        rs.getTimestamp("plan_finish_time").toLocalDateTime(), rs.getString("task_status"),
                        getInt(rs, "current_process_seq"), rs.getInt("finished_process_count"), rs.getInt("total_process_count"),
                        rs.getDouble("close_progress"), rs.getString("snapshot_version"), rs.getString("flow_constraint_desc"),
                        rs.getLong("creator_id"), rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime(), rs.getInt("order_total"), rs.getInt("order_closed")),
                pageArgs.toArray());
        return new PageResult<>(records, total == null ? 0 : total, normalized.page(), normalized.size());
    }

    /** 任务详情 + 工单列表（台账 orders 页签）。 */
    public Map<String, Object> detail(AuthUser user, long taskId) {
        TaskView view = requireTask(user.tenantId(), taskId);
        List<OrderView> orders = jdbc.query("SELECT id, order_code, task_id, granularity, activity_type, activity_id, "
                        + "activity_name, sub_activity_id, component_id, order_status, deadline_status, "
                        + "total_process_count, closed_process_count, flow_progress, plan_finish_time, flow_start_at, "
                        + "closed_at, current_executor_id, block_reason FROM work_order "
                        + "WHERE task_id = ? AND tenant_id = ? AND deleted = 0 ORDER BY order_code",
                (rs, rowNum) -> new OrderView(rs.getLong("id"), rs.getString("order_code"), rs.getLong("task_id"),
                        rs.getString("granularity"), rs.getString("activity_type"), rs.getLong("activity_id"),
                        rs.getString("activity_name"), getLong(rs, "sub_activity_id"), getLong(rs, "component_id"),
                        rs.getString("order_status"), rs.getString("deadline_status"), rs.getInt("total_process_count"),
                        rs.getInt("closed_process_count"), rs.getDouble("flow_progress"),
                        rs.getTimestamp("plan_finish_time").toLocalDateTime(),
                        rs.getTimestamp("flow_start_at") == null ? null : rs.getTimestamp("flow_start_at").toLocalDateTime(),
                        rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toLocalDateTime(),
                        rs.getLong("current_executor_id"), rs.getString("block_reason")),
                taskId, user.tenantId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task", view);
        result.put("orders", orders);
        return result;
    }

    /** 单发/批量统一入口：按矩阵判定式拆分工单，固化任务快照与工单级快照（专题=D-22 自适应）。 */
    @Transactional
    public Map<String, Object> dispatch(AuthUser user, Map<String, Object> body) {
        permissions.requireAdmin(user);
        long activityId = longValue(body, "activityId");
        Map<String, Object> activity = requireActivity(activityId, user.tenantId());
        if (!"ACTIVE".equals(String.valueOf(activity.get("activity_status")))) {
            throw new BusinessException(LifecycleErrorCode.TASK_ACTIVITY_NOT_ACTIVE, "仅启用活动可下发任务");
        }
        String activityType = String.valueOf(activity.get("activity_type"));
        String granularity = String.valueOf(activity.get("granularity"));
        long projectId = longValue(body, "projectId");
        Long businessGroupId = nullableLong(body, "businessGroupId");
        LocalDateTime planFinishTime = parsePlanFinishTime(body);
        Long executorOverride = nullableLong(body, "executorId");
        List<Long> selectedComponents = longList(body.get("componentIds"));
        List<Long> participants = longList(body.get("participantIds"));

        Map<Long, Set<Long>> activityScope = new LinkedHashMap<>();
        Map<Long, String[]> memberMeta = new LinkedHashMap<>();
        List<Long> topicActivityIds = List.of();
        List<Map<String, Object>> aggregateEdges = List.of();
        if ("TOPIC".equals(activityType)) {
            List<Long> memberIds = jdbc.queryForList("SELECT member_activity_id FROM activity_topic_rel "
                    + "WHERE topic_activity_id = ? AND tenant_id = ? AND deleted = 0 ORDER BY id", Long.class,
                    activityId, user.tenantId());
            if (memberIds.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "专题未聚合任何普通活动，禁止下发");
            }
            topicActivityIds = memberIds;
            for (Long memberId : memberIds) {
                activityScope.put(memberId, componentScope(user.tenantId(), memberId));
                Map<String, Object> member = requireActivity(memberId, user.tenantId());
                memberMeta.put(memberId, new String[]{String.valueOf(member.get("activity_code")),
                        String.valueOf(member.get("activity_name"))});
            }
            aggregateEdges = jdbc.queryForList("SELECT source_activity_id, target_activity_id FROM topic_aggregate_edge "
                    + "WHERE topic_activity_id = ? AND tenant_id = ? AND deleted = 0 ORDER BY id",
                    activityId, user.tenantId());
        } else {
            activityScope.put(0L, componentScope(user.tenantId(), activityId));
            memberMeta.put(0L, new String[]{String.valueOf(activity.get("activity_code")),
                    String.valueOf(activity.get("activity_name"))});
            List<Long> topicParent = jdbc.queryForList("SELECT topic_activity_id FROM activity_topic_rel "
                    + "WHERE member_activity_id = ? AND tenant_id = ? AND deleted = 0", Long.class, activityId, user.tenantId());
            // 普通活动被专题聚合引用时不阻止直接下发，仅记录来源专题
            if (!topicParent.isEmpty()) {
                topicActivityIds = List.of(activityId);
            }
        }
        Set<Long> enabledComponents = new HashSet<>(jdbc.queryForList(
                "SELECT id FROM dm_component WHERE tenant_id = ? AND deleted = 0", Long.class, user.tenantId()));

        List<SplitItem> items = TaskSplitEngine.split(activityType, granularity, selectedComponents, enabledComponents,
                activityScope, memberMeta);
        String taskName = body.get("taskName") == null || String.valueOf(body.get("taskName")).isBlank()
                ? String.valueOf(activity.get("activity_name")) + "任务" : String.valueOf(body.get("taskName")).trim();
        long taskId = nextId();
        String taskCode = nextTaskCode(user.tenantId());
        // 工单快照准备：NORMAL 取自活动快照；TOPIC 取自被展开普通活动快照（D-22），挂 topic_activity_ids/aggregate_edges/sub_activity_id
        List<OrderAcceptanceService.OrderDraft> drafts = new ArrayList<>();
        Map<String, Object> taskSnapshot = new LinkedHashMap<>();
        Map<String, Object> topicPkg = new LinkedHashMap<>();
        if ("PROJECT".equals(granularity)) {
            long executorId = resolveProjectExecutor(user, executorOverride);
            Map<String, Object> snapshot = requirePublishedSnapshot(user, activityId);
            drafts.add(buildDraft(user, activityId, String.valueOf(activity.get("activity_name")), null, null,
                    projectId, businessGroupId, executorId, executorId, participants, planFinishTime,
                    String.valueOf(snapshot.get("topologyVersion")), snapshot, topicActivityIds, aggregateEdges, false));
            taskSnapshot = snapshot;
        } else {
            for (SplitItem item : items) {
                long sourceActivityId = item.subActivityId() == null ? item.activityId() : item.subActivityId();
                Map<String, Object> snapshot = requirePublishedSnapshot(user, sourceActivityId);
                boolean crossBlocked = isCrossActivityBlocked(sourceActivityId, aggregateEdges,
                        new LinkedHashSet<>(topicActivityIds));
                Map<String, Object> augmented = augmentTopicSnapshot(snapshot, sourceActivityId, topicActivityIds,
                        aggregateEdges, crossBlocked);
                Map<String, Object> component = jdbc.queryForMap("SELECT id, owner_id FROM dm_component "
                        + "WHERE id = ? AND tenant_id = ? AND deleted = 0", item.componentId(), user.tenantId());
                long executorId = executorOverride == null ? ((Number) component.get("owner_id")).longValue() : executorOverride;
                drafts.add(buildDraft(user, sourceActivityId, item.activityName(), item.subActivityId(), item.componentId(),
                        projectId, businessGroupId, executorId, executorId, participants, planFinishTime, String.valueOf(snapshot.get("topologyVersion")),
                        augmented, topicActivityIds, aggregateEdges, crossBlocked));
                taskSnapshot = snapshot;
            }
            if (drafts.isEmpty()) {
                throw new BusinessException(LifecycleErrorCode.TASK_COMPONENT_REQUIRED, "拆分结果为空，请检查组件勾选范围");
            }
        }
        // 任务快照：专题=聚合包（topic_activity_ids + aggregate_edges + 载荷摘要）；普通=活动快照同源
        if ("TOPIC".equals(activityType)) {
            topicPkg.put("schemaVersion", 1);
            topicPkg.put("entityType", "TOPIC_TASK");
            topicPkg.put("entityId", taskId);
            topicPkg.put("frozenAt", LocalDateTime.now().toString());
            topicPkg.put("topicActivityId", activityId);
            topicPkg.put("topicActivityIds", topicActivityIds);
            topicPkg.put("aggregateEdges", aggregateEdges);
            topicPkg.put("snapshotVersion", taskSnapshot.get("topologyVersion"));
        }
        jdbc.update("INSERT INTO task (id, tenant_id, task_code, task_name, granularity, activity_type, activity_id, "
                        + "activity_name, topic_activity_id, project_id, business_group_id, component_id, default_executor_id, "
                        + "current_executor_id, plan_finish_time, task_status, finished_process_count, total_process_count, "
                        + "close_progress, snapshot_version, flow_constraint_desc, creator_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'WAIT_PRE', 0, 0, 0.00, ?, ?, ?)",
                taskId, user.tenantId(), taskCode, taskName, granularity, activityType, activityId,
                String.valueOf(activity.get("activity_name")), "TOPIC".equals(activityType) ? activityId : null,
                projectId, businessGroupId, "PROJECT".equals(granularity) ? null : null, drafts.get(0).defaultExecutorId(),
                drafts.get(0).currentExecutorId(), planFinishTime, String.valueOf(taskSnapshot.get("topologyVersion")),
                "按活动拓扑快照统一流转，无独立特殊流转规则", user.id());
        jdbc.update("INSERT INTO task_snapshot (id, tenant_id, task_id, snapshot_version, sub_activity_id, "
                        + "topic_activity_ids, aggregate_edges, snapshot_json, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                nextId(), user.tenantId(), taskId, String.valueOf(taskSnapshot.get("topologyVersion")),
                "TOPIC".equals(activityType) ? null : null,
                "TOPIC".equals(activityType) ? toJson(topicActivityIds) : null,
                "TOPIC".equals(activityType) ? toJson(aggregateEdges) : null,
                "TOPIC".equals(activityType) ? toJson(topicPkg) : toJson(taskSnapshot), user.id());
        for (Long participant : new LinkedHashSet<>(participants)) {
            jdbc.update("INSERT INTO task_person_rel (id, tenant_id, task_id, member_id, person_role) VALUES (?, ?, ?, ?, 'PARTICIPANT')",
                    nextId(), user.tenantId(), taskId, participant);
        }
        List<OrderView> orders = acceptanceService.acceptOrders(user, taskId, taskCode, taskName, drafts);
        audit.audit(user, audit.opCode("DISPATCH", "TASK"), "TASK", taskId, "SUCCESS", null,
                Map.of("taskCode", taskCode, "orderCount", orders.size(), "activityType", activityType, "granularity", granularity));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task", detail(user, taskId));
        result.put("orders", orders);
        result.put("splitCount", orders.size());
        return result;
    }

    private OrderAcceptanceService.OrderDraft buildDraft(AuthUser user, long activityId, String activityName,
                                                         Long subActivityId, Long componentId, long projectId,
                                                         Long businessGroupId, long defaultExecutorId, long currentExecutorId,
                                                         List<Long> participants, LocalDateTime planFinishTime,
                                                         String snapshotVersion, Map<String, Object> snapshot,
                                                         List<Long> topicActivityIds, List<Map<String, Object>> aggregateEdges,
                                                         boolean crossActivityBlocked) {
        return new OrderAcceptanceService.OrderDraft(activityId, activityName, subActivityId, componentId, projectId,
                businessGroupId, defaultExecutorId, currentExecutorId, participants, planFinishTime, snapshotVersion,
                snapshot, topicActivityIds, aggregateEdges, crossActivityBlocked);
    }

    private Map<String, Object> requirePublishedSnapshot(AuthUser user, long activityId) {
        Map<String, Object> snapshot = topologyService.snapshotForTask(user, activityId);
        if (snapshot == null || Boolean.FALSE.equals(snapshot.get("published"))) {
            throw new BusinessException(LifecycleErrorCode.TASK_ACTIVITY_SNAPSHOT_MISSING, "活动尚未发布快照，禁止下发任务：" + activityId);
        }
        return snapshot;
    }

    private boolean isCrossActivityBlocked(long sourceActivityId, List<Map<String, Object>> edges, Set<Long> members) {
        for (Map<String, Object> edge : edges) {
            long source = ((Number) edge.get("source_activity_id")).longValue();
            long target = ((Number) edge.get("target_activity_id")).longValue();
            if (target == sourceActivityId && members.contains(source) && source != target) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> augmentTopicSnapshot(Map<String, Object> snapshot, long subActivityId,
                                                     List<Long> topicActivityIds, List<Map<String, Object>> aggregateEdges,
                                                     boolean crossActivityBlocked) {
        Map<String, Object> copy = new LinkedHashMap<>(snapshot);
        copy.put("subActivityId", subActivityId);
        copy.put("topicActivityIds", new ArrayList<>(topicActivityIds));
        copy.put("aggregateEdges", aggregateEdges);
        copy.put("crossActivityBlocked", crossActivityBlocked);
        return copy;
    }

    private long resolveProjectExecutor(AuthUser user, Long executorOverride) {
        if (executorOverride == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目级任务必须指定执行人");
        }
        requireActiveMember(user.tenantId(), executorOverride);
        return executorOverride;
    }

    private void requireActiveMember(long tenantId, long memberId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id = ? AND tenant_id = ? "
                + "AND status = 1 AND deleted = 0", Integer.class, memberId, tenantId);
        if (count == null || count == 0) {
            throw new BusinessException(LifecycleErrorCode.TASK_EXECUTOR_INACTIVE, "执行人必须为已激活成员");
        }
    }

    private LocalDateTime parsePlanFinishTime(Map<String, Object> body) {
        Object raw = body.get("planFinishTime");
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new BusinessException(LifecycleErrorCode.TASK_PLAN_FINISH_TIME_REQUIRED, "任务下发时必须填写计划完成时间");
        }
        LocalDateTime value;
        try {
            value = LocalDateTime.parse(String.valueOf(raw).replace(" ", "T"));
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "计划完成时间格式错误");
        }
        if (value.isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw new BusinessException(LifecycleErrorCode.TASK_PLAN_FINISH_TIME_IN_PAST, "计划完成时间不得早于当前时间");
        }
        return value;
    }

    private Set<Long> componentScope(long tenantId, long activityId) {
        return new LinkedHashSet<>(jdbc.queryForList("SELECT component_id FROM activity_component_rel "
                + "WHERE activity_id = ? AND tenant_id = ? AND deleted = 0", Long.class, activityId, tenantId));
    }

    private Map<String, Object> requireActivity(long id, long tenantId) {
        try {
            return jdbc.queryForMap("SELECT id, tenant_id, activity_code, activity_name, activity_type, granularity, "
                    + "activity_status, lifecycle_stage_id FROM activity WHERE id = ? AND tenant_id = ? AND deleted = 0",
                    id, tenantId);
        } catch (Exception ex) {
            throw new BusinessException(LifecycleErrorCode.ACTIVITY_NOT_FOUND, "活动不存在");
        }
    }

    private TaskView requireTask(long tenantId, long taskId) {
        try {
            Map<String, Object> row = jdbc.queryForMap("SELECT id, tenant_id, task_code, task_name, granularity, activity_type, "
                    + "activity_id, activity_name, topic_activity_id, project_id, business_group_id, component_id, "
                    + "default_executor_id, current_executor_id, plan_finish_time, task_status, current_process_seq, "
                    + "finished_process_count, total_process_count, close_progress, snapshot_version, flow_constraint_desc, "
                    + "creator_id, created_at, updated_at FROM task WHERE id = ? AND tenant_id = ? AND deleted = 0",
                    taskId, tenantId);
            return new TaskView(((Number) row.get("id")).longValue(), ((Number) row.get("tenant_id")).longValue(),
                    (String) row.get("task_code"), (String) row.get("task_name"), (String) row.get("granularity"),
                    (String) row.get("activity_type"), ((Number) row.get("activity_id")).longValue(),
                    (String) row.get("activity_name"), longOrNull(row.get("topic_activity_id")),
                    ((Number) row.get("project_id")).longValue(), longOrNull(row.get("business_group_id")),
                    longOrNull(row.get("component_id")), ((Number) row.get("default_executor_id")).longValue(),
                    ((Number) row.get("current_executor_id")).longValue(), List.of(),
                    ((java.sql.Timestamp) row.get("plan_finish_time")).toLocalDateTime(), (String) row.get("task_status"),
                    intOrNull(row.get("current_process_seq")), ((Number) row.get("finished_process_count")).intValue(),
                    ((Number) row.get("total_process_count")).intValue(), ((Number) row.get("close_progress")).doubleValue(),
                    (String) row.get("snapshot_version"), (String) row.get("flow_constraint_desc"),
                    ((Number) row.get("creator_id")).longValue(), ((java.sql.Timestamp) row.get("created_at")).toLocalDateTime(),
                    ((java.sql.Timestamp) row.get("updated_at")).toLocalDateTime(), 0, 0);
        } catch (Exception ex) {
            throw new BusinessException(LifecycleErrorCode.TASK_NOT_FOUND, "任务不存在");
        }
    }

    /** 专题跨活动依赖边管理（仅数据迁移管理员；流向 source→target：source 全部闭环后解锁 target 工单）。 */
    @Transactional
    public void saveTopicAggregateEdges(AuthUser user, long topicActivityId, List<Map<String, Object>> edges) {
        permissions.requireAdmin(user);
        requireActivity(topicActivityId, user.tenantId());
        jdbc.update("UPDATE topic_aggregate_edge SET deleted = 1 WHERE topic_activity_id = ? AND tenant_id = ? AND deleted = 0",
                topicActivityId, user.tenantId());
        for (Map<String, Object> edge : edges == null ? List.<Map<String, Object>>of() : edges) {
            long source = ((Number) edge.get("sourceActivityId")).longValue();
            long target = ((Number) edge.get("targetActivityId")).longValue();
            if (source == target) {
                throw new BusinessException(LifecycleErrorCode.TOPOLOGY_SELF_LOOP, "专题跨活动依赖禁止自连");
            }
            jdbc.update("INSERT INTO topic_aggregate_edge (id, tenant_id, topic_activity_id, source_activity_id, "
                    + "target_activity_id, created_by) VALUES (?, ?, ?, ?, ?, ?)",
                    nextId(), user.tenantId(), topicActivityId, source, target, user.id());
        }
        audit.audit(user, audit.opCode("SAVE_AGGREGATE_EDGES", "TOPIC"), "ACTIVITY_TOPIC", topicActivityId, "SUCCESS", null,
                Map.of("edgeCount", edges == null ? 0 : edges.size()));
    }

    public List<Map<String, Object>> topicAggregateEdges(AuthUser user, long topicActivityId) {
        requireActivity(topicActivityId, user.tenantId());
        return jdbc.queryForList("SELECT source_activity_id, target_activity_id FROM topic_aggregate_edge "
                + "WHERE topic_activity_id = ? AND tenant_id = ? AND deleted = 0 ORDER BY id", topicActivityId, user.tenantId());
    }

    private String nextTaskCode(long tenantId) {
        String prefix = "TK-" + java.time.LocalDate.now().toString().replace("-", "");
        Long max = jdbc.queryForObject("SELECT MAX(CAST(SUBSTRING_INDEX(task_code, '-', -1) AS UNSIGNED)) FROM task "
                + "WHERE tenant_id = ? AND task_code LIKE ? AND deleted = 0", Long.class, tenantId, prefix + "-%");
        long seq = (max == null ? 0 : max) + 1;
        return prefix + "-" + String.format("%03d", seq);
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

    private String toJson(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "序列化失败");
        }
    }

    private static Long longValue(Map<String, Object> body, String key) {
        Object raw = body.get(key);
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, key + " 不能为空");
        }
        return Long.parseLong(String.valueOf(raw));
    }

    private static Long nullableLong(Map<String, Object> body, String key) {
        Object raw = body.get(key);
        if (raw == null || String.valueOf(raw).isBlank() || "null".equals(String.valueOf(raw))) {
            return null;
        }
        return Long.parseLong(String.valueOf(raw));
    }

    private static List<Long> longList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (Object item : list) {
            try {
                ids.add(Long.valueOf(String.valueOf(item)));
            } catch (NumberFormatException ignored) {
                // 忽略非法项，后续必填/范围校验兜底
            }
        }
        return ids;
    }

    private static Long longOrNull(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Integer intOrNull(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private static Long getLong(java.sql.ResultSet rs, String column) {
        try {
            long value = rs.getLong(column);
            return rs.wasNull() ? null : value;
        } catch (Exception ex) {
            return null;
        }
    }

    private static Integer getInt(java.sql.ResultSet rs, String column) {
        try {
            int value = rs.getInt(column);
            return rs.wasNull() ? null : value;
        } catch (Exception ex) {
            return null;
        }
    }

    private static long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
