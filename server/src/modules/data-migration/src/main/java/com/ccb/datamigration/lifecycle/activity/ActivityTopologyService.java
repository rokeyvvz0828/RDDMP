package com.ccb.datamigration.lifecycle.activity;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.datamigration.lifecycle.LifecycleAuditService;
import com.ccb.datamigration.lifecycle.LifecyclePermissionService;
import com.ccb.datamigration.lifecycle.activity.model.ProcessInput;
import com.ccb.datamigration.lifecycle.activity.model.ProcessView;
import com.ccb.datamigration.lifecycle.activity.model.TopologyInput;
import com.ccb.datamigration.lifecycle.activity.model.TopologyView;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import com.ccb.datamigration.lifecycle.snapshot.LifecycleSnapshotEnvelope;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 工序与拓扑：工序保存（草稿可存）、拓扑结构保存（派生 is_no_predecessor）、发布出口（唯一校验 + 快照冻结）。 */
@Service
public class ActivityTopologyService {
    private final JdbcTemplate jdbc;
    private final LifecyclePermissionService permissions;
    private final LifecycleAuditService audit;
    private final ObjectMapper objectMapper;

    public ActivityTopologyService(JdbcTemplate jdbc, LifecyclePermissionService permissions,
                                   LifecycleAuditService audit, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    public List<ProcessView> processes(AuthUser user, long activityId) {
        requireActivity(activityId, user.tenantId());
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, activity_id, seq, process_name, owner_role_id, is_required, entry_config, "
                        + "is_no_predecessor, exec_config, exit_content, exit_deliverable_list, qualified_rule, "
                        + "must_audit, must_submit_deliverable, deliverable_template_id, config_status "
                        + "FROM activity_process WHERE activity_id = ? AND tenant_id = ? AND deleted = 0 "
                        + "ORDER BY seq ASC", activityId, user.tenantId());
        List<Map<String, Object>> deps = fetchDeps(activityId, user.tenantId());
        Map<Long, List<Long>> predecessors = new HashMap<>();
        for (Map<String, Object> dep : deps) {
            Long source = ((Number) dep.get("source_process_id")).longValue();
            Long target = ((Number) dep.get("target_process_id")).longValue();
            predecessors.computeIfAbsent(target, k -> new ArrayList<>()).add(source);
        }
        List<ProcessView> views = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            views.add(new ProcessView(id, ((Number) row.get("activity_id")).longValue(), ((Number) row.get("seq")).intValue(),
                    (String) row.get("process_name"), row.get("owner_role_id") == null ? null : ((Number) row.get("owner_role_id")).longValue(),
                    bool(row, "is_required"), (String) row.get("entry_config"), bool(row, "is_no_predecessor"),
                    (String) row.get("exec_config"), (String) row.get("exit_content"), (String) row.get("exit_deliverable_list"),
                    (String) row.get("qualified_rule"), bool(row, "must_audit"), bool(row, "must_submit_deliverable"),
                    row.get("deliverable_template_id") == null ? null : ((Number) row.get("deliverable_template_id")).longValue(),
                    (String) row.get("config_status"), predecessors.getOrDefault(id, List.of())));
        }
        return views;
    }

    /** 拓扑只读视图（配置工作台数据结构）。 */
    public TopologyView topology(AuthUser user, long activityId) {
        return topologyView(user, activityId, "");
    }

    /** 工序保存：停用/作废活动禁新增工序；删除后序号重排 1..n；三要素缺省时置 DRAFT。 */
    @Transactional
    public List<ProcessView> saveProcesses(AuthUser user, long activityId, List<ProcessInput> inputs) {
        permissions.requireAdmin(user);
        Map<String, Object> activity = requireActivity(activityId, user.tenantId());
        rejectObsolete(activity);
        if (inputs == null || inputs.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.PROCESS_MIN_ONE, "活动至少保留 1 道工序");
        }
        if (inputs.size() > TopologyValidator.MAX_PROCESS_COUNT) {
            throw new BusinessException(LifecycleErrorCode.TOPOLOGY_LIMIT_EXCEEDED, "单活动工序数不得超过 30 节点");
        }
        if (!"ACTIVE".equals(String.valueOf(activity.get("activity_status")))) {
            // INACTIVE：不允许新增工序（已有工序可维护/重排）
            for (ProcessInput input : inputs) {
                if (input.id() == null) {
                    throw new BusinessException(LifecycleErrorCode.ACTIVITY_INACTIVE_NO_PROCESS, "停用活动不允许新增工序");
                }
            }
        }
        List<ProcessInput> normalized = new ArrayList<>(inputs);
        normalized.sort(Comparator.comparingInt(ProcessInput::seq));
        int seq = 1;
        for (ProcessInput input : normalized) {
            saveOneProcess(user, activityId, input, seq);
            seq++;
        }
        deleteProcessesNotIn(user, activityId, normalized);
        return processes(user, activityId);
    }

    private void saveOneProcess(AuthUser user, long activityId, ProcessInput input, int seq) {
        boolean triadComplete = isTriadComplete(input);
        String configStatus = triadComplete ? "READY" : "DRAFT";
        if (input.id() == null) {
            long id = nextId();
            jdbc.update("INSERT INTO activity_process (id, tenant_id, activity_id, seq, process_name, owner_role_id, "
                            + "is_required, entry_config, is_no_predecessor, exec_config, exit_content, exit_deliverable_list, "
                            + "qualified_rule, must_audit, must_submit_deliverable, deliverable_template_id, config_status, "
                            + "created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, user.tenantId(), activityId, seq, input.processName(), input.ownerRoleId(),
                    input.isRequired() == null || input.isRequired() ? 1 : 0, jsonOrNull(input.entryConfig()),
                    jsonOrNull(input.execConfig()), input.exitContent(), jsonOrNull(input.exitDeliverableList()),
                    input.qualifiedRule(), boolInt(input.mustAudit(), true), boolInt(input.mustSubmitDeliverable(), true),
                    input.deliverableTemplateId(), configStatus, user.id(), user.id());
        } else {
            jdbc.update("UPDATE activity_process SET seq = ?, process_name = ?, owner_role_id = ?, is_required = ?, "
                            + "entry_config = ?, exec_config = ?, exit_content = ?, exit_deliverable_list = ?, "
                            + "qualified_rule = ?, must_audit = ?, must_submit_deliverable = ?, deliverable_template_id = ?, "
                            + "config_status = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP(6) "
                            + "WHERE id = ? AND activity_id = ? AND tenant_id = ? AND deleted = 0",
                    seq, input.processName(), input.ownerRoleId(), input.isRequired() == null || input.isRequired() ? 1 : 0,
                    jsonOrNull(input.entryConfig()), jsonOrNull(input.execConfig()), input.exitContent(),
                    jsonOrNull(input.exitDeliverableList()), input.qualifiedRule(), boolInt(input.mustAudit(), true),
                    boolInt(input.mustSubmitDeliverable(), true), input.deliverableTemplateId(), configStatus,
                    user.id(), input.id(), activityId, user.tenantId());
        }
    }

    private void deleteProcessesNotIn(AuthUser user, long activityId, List<ProcessInput> inputs) {
        List<Long> keepIds = inputs.stream().map(ProcessInput::id).filter(java.util.Objects::nonNull).toList();
        List<Long> existing = existingProcessIds(activityId, user.tenantId());
        if (existing.isEmpty()) {
            return;
        }
        jdbc.update("UPDATE activity_process SET deleted = 1 WHERE activity_id = ? AND tenant_id = ? AND deleted = 0 "
                + (keepIds.isEmpty() ? ""
                : "AND id NOT IN (" + String.join(",", java.util.Collections.nCopies(keepIds.size(), "?")) + ")"),
                concat(List.of(activityId, user.tenantId()), keepIds));
    }

    /** 拓扑结构保存：先按连线派生 is_no_predecessor 落库，再显式校验（结构），重建依赖边。不生成版本（发布出口负责）。 */
    @Transactional
    public TopologyView saveTopology(AuthUser user, long activityId, TopologyInput input) {
        permissions.requireAdmin(user);
        Map<String, Object> activity = requireActivity(activityId, user.tenantId());
        rejectObsolete(activity);
        List<TopologyInput.EdgeInput> edges = input.edges() == null ? List.of() : input.edges();
        List<Long> processIds = existingProcessIds(activityId, user.tenantId());
        if (processIds.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.PROCESS_MIN_ONE, "请先保存工序");
        }
        // 存在性→数量→状态（结构校验）
        TopologyValidator.validateForSave(processIds, edges);
        // 派生 is_no_predecessor 先落库，再显式校验（基线 9.2.4：顺序不可颠倒）
        for (long processId : processIds) {
            boolean derived = TopologyValidator.deriveNoPredecessor(processId, edges);
            jdbc.update("UPDATE activity_process SET is_no_predecessor = ?, updated_at = CURRENT_TIMESTAMP(6) "
                    + "WHERE id = ? AND tenant_id = ? AND deleted = 0", derived ? 1 : 0, processId, user.tenantId());
        }
        // 重建依赖边
        jdbc.update("UPDATE activity_process_dep SET deleted = 1 WHERE activity_id = ? AND tenant_id = ? AND deleted = 0",
                activityId, user.tenantId());
        for (TopologyInput.EdgeInput edge : edges) {
            jdbc.update("INSERT INTO activity_process_dep (id, tenant_id, activity_id, source_process_id, target_process_id, created_by) "
                    + "VALUES (?, ?, ?, ?, ?, ?)", nextId(), user.tenantId(), activityId,
                    edge.sourceProcessId(), edge.targetProcessId(), user.id());
        }
        audit.audit(user, audit.opCode("TOPOLOGY_SAVE", "ACTIVITY"), "ACTIVITY_TOPOLOGY", activityId, "SUCCESS", null,
                Map.of("edgeCount", edges.size()));
        return topologyView(user, activityId, "保存成功（未发布），依赖：" + edges.size() + " 条");
    }

    /** 发布出口（唯一校验出口，基线 D-07）：结构 + 准出三要素 + 序号连续 → 生成拓扑版本与自描述快照（1:1）。 */
    @Transactional
    public Map<String, Object> publishVersion(AuthUser user, long activityId) {
        permissions.requireAdmin(user);
        Map<String, Object> activity = requireActivity(activityId, user.tenantId());
        rejectObsolete(activity);
        List<ProcessView> processes = processes(user, activityId);
        if (processes.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.PROCESS_MIN_ONE, "活动至少保留 1 道工序");
        }
        List<TopologyInput.EdgeInput> edges = fetchEdges(activityId, user.tenantId());
        List<Long> processIds = processes.stream().map(ProcessView::id).toList();
        TopologyValidator.validateForSave(processIds, edges);
        for (ProcessView process : processes) {
            if (!isTriadComplete(process)) {
                throw new BusinessException(LifecycleErrorCode.EXIT_TRIAD_INCOMPLETE,
                        "准出三要素（准出内容/准出交付物清单/合格判定规则）齐备方可发布：" + process.processName());
            }
            boolean derived = TopologyValidator.deriveNoPredecessor(process.id(), edges);
            if (derived != process.noPredecessor()) {
                throw new BusinessException(LifecycleErrorCode.TOPOLOGY_NOPRE_CONFLICT,
                        "工序无前置标记与依赖连线不一致：" + process.processName());
            }
        }
        // 版本与快照 1:1（铁律 #15）
        String version = nextVersion(activityId, user.tenantId());
        Map<String, Object> snapshot = buildSnapshot(activityId, activity, version, processes, edges);
        jdbc.update("UPDATE activity_process SET config_status = 'READY', updated_at = CURRENT_TIMESTAMP(6) "
                + "WHERE activity_id = ? AND tenant_id = ? AND deleted = 0", activityId, user.tenantId());
        jdbc.update("INSERT INTO activity_topology (id, tenant_id, activity_id, topology_version, topology_json, created_by) "
                + "VALUES (?, ?, ?, ?, ?, ?)", nextId(), user.tenantId(), activityId, version, toJson(edges), user.id());
        jdbc.update("INSERT INTO activity_snapshot (id, tenant_id, activity_id, topology_version, snapshot_json, created_by) "
                + "VALUES (?, ?, ?, ?, ?, ?)", nextId(), user.tenantId(), activityId, version, toJson(snapshot), user.id());
        audit.audit(user, audit.opCode("PUBLISH", "ACTIVITY"), "ACTIVITY_TOPOLOGY", activityId, "SUCCESS", null,
                Map.of("topologyVersion", version));
        return Map.of("activityId", activityId, "topologyVersion", version,
                "frozenAt", String.valueOf(snapshot.get("frozenAt")));
    }

    /** 发布状态：工序数、完善度、是否可发布（只读）。 */
    public Map<String, Object> publishStatus(AuthUser user, long activityId) {
        requireActivity(activityId, user.tenantId());
        List<ProcessView> processes = processes(user, activityId);
        long ready = processes.stream().filter(p -> "READY".equals(p.configStatus())).count();
        List<String> missingExit = processes.stream()
                .filter(p -> !isTriadComplete(p))
                .map(ProcessView::processName)
                .toList();
        boolean topologyOk = true;
        try {
            TopologyValidator.validateForSave(processes.stream().map(ProcessView::id).toList(),
                    fetchEdges(activityId, user.tenantId()));
        } catch (BusinessException ex) {
            topologyOk = false;
        }
        boolean publishable = !processes.isEmpty() && missingExit.isEmpty() && topologyOk;
        return Map.of("activityId", activityId, "processTotal", processes.size(), "processReady", ready,
                "missingExitProcesses", missingExit, "topologyOk", topologyOk, "publishable", publishable);
    }

    /** 任务发布取数契约（基线 9.5：输出 → 任务发布 snapshotForTask()）：最新发布快照的自描述载荷。 */
    public Map<String, Object> snapshotForTask(AuthUser user, long activityId) {
        requireActivity(activityId, user.tenantId());
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT snapshot_json FROM activity_snapshot WHERE activity_id = ? AND tenant_id = ? AND deleted = 0 "
                        + "ORDER BY id DESC LIMIT 1",
                activityId, user.tenantId());
        if (rows.isEmpty()) {
            return Map.of("published", false);
        }
        try {
            return objectMapper.readValue(String.valueOf(rows.get(0).get("snapshot_json")), Map.class);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "快照载荷解析失败");
        }
    }

    private TopologyView topologyView(AuthUser user, long activityId, String warning) {
        List<ProcessView> processes = processes(user, activityId);
        List<TopologyInput.EdgeInput> edges = fetchEdges(activityId, user.tenantId());
        List<TopologyView.EdgeView> edgeViews = edges.stream()
                .map(e -> new TopologyView.EdgeView(e.sourceProcessId(), e.targetProcessId()))
                .toList();
        return new TopologyView(activityId, null, processes, edgeViews, List.of(warning));
    }

    private Map<String, Object> buildSnapshot(long activityId, Map<String, Object> activity, String version,
                                              List<ProcessView> processes, List<TopologyInput.EdgeInput> edges) {
        LocalDateTime frozenAt = LocalDateTime.now();
        LifecycleSnapshotEnvelope envelope = LifecycleSnapshotEnvelope.freeze("ACTIVITY", activityId, frozenAt);
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (ProcessView process : processes) {
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("processId", process.id());
            definition.put("seq", process.seq());
            definition.put("processName", process.processName());
            definition.put("ownerRoleId", process.ownerRoleId());
            definition.put("isRequired", process.isRequired());
            definition.put("entryConfig", process.entryConfig());
            definition.put("execConfig", process.execConfig());
            definition.put("exitContent", process.exitContent());
            definition.put("exitDeliverableList", process.exitDeliverableList());
            definition.put("qualifiedRule", process.qualifiedRule());
            definition.put("mustAudit", process.mustAudit());
            definition.put("mustSubmitDeliverable", process.mustSubmitDeliverable());
            definition.put("deliverableTemplateId", process.deliverableTemplateId());
            definitions.add(definition);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", envelope.schemaVersion());
        snapshot.put("entityType", envelope.entityType());
        snapshot.put("entityId", envelope.entityId());
        snapshot.put("frozenAt", envelope.frozenAt().toString());
        snapshot.put("topologyVersion", version);
        snapshot.put("activityCode", activity.get("activity_code"));
        snapshot.put("activityType", activity.get("activity_type"));
        snapshot.put("granularity", activity.get("granularity"));
        snapshot.put("processDefinitions", definitions);
        snapshot.put("edges", edges);
        return snapshot;
    }

    private String nextVersion(long activityId, long tenantId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM activity_topology WHERE activity_id = ? AND tenant_id = ? AND deleted = 0",
                Long.class, activityId, tenantId);
        return "V" + ((count == null ? 0 : count) + 1);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "序列化失败");
        }
    }

    private String jsonOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private boolean isTriadComplete(ProcessView process) {
        return process.exitContent() != null && !process.exitContent().isBlank()
                && process.exitDeliverableList() != null && !process.exitDeliverableList().isBlank()
                && process.qualifiedRule() != null && !process.qualifiedRule().isBlank();
    }

    private boolean isTriadComplete(ProcessInput process) {
        return process.exitContent() != null && !process.exitContent().isBlank()
                && process.exitDeliverableList() != null && !process.exitDeliverableList().isBlank()
                && process.qualifiedRule() != null && !process.qualifiedRule().isBlank();
    }

    private List<Long> existingProcessIds(long activityId, long tenantId) {
        return jdbc.queryForList("SELECT id FROM activity_process WHERE activity_id = ? AND tenant_id = ? AND deleted = 0 "
                + "ORDER BY seq ASC", Long.class, activityId, tenantId);
    }

    private List<Map<String, Object>> fetchDeps(long activityId, long tenantId) {
        return jdbc.queryForList("SELECT source_process_id, target_process_id FROM activity_process_dep "
                + "WHERE activity_id = ? AND tenant_id = ? AND deleted = 0", activityId, tenantId);
    }

    private List<TopologyInput.EdgeInput> fetchEdges(long activityId, long tenantId) {
        return jdbc.queryForList("SELECT source_process_id, target_process_id FROM activity_process_dep "
                + "WHERE activity_id = ? AND tenant_id = ? AND deleted = 0", activityId, tenantId)
                .stream()
                .map(row -> new TopologyInput.EdgeInput(((Number) row.get("source_process_id")).longValue(),
                        ((Number) row.get("target_process_id")).longValue()))
                .toList();
    }

    private Map<String, Object> requireActivity(long id, long tenantId) {
        try {
            return jdbc.queryForMap("SELECT id, tenant_id, activity_type, granularity, activity_status, activity_code "
                    + "FROM activity WHERE id = ? AND tenant_id = ? AND deleted = 0", id, tenantId);
        } catch (Exception ex) {
            throw new BusinessException(LifecycleErrorCode.ACTIVITY_NOT_FOUND, "活动不存在");
        }
    }

    private void rejectObsolete(Map<String, Object> activity) {
        if ("OBSOLETE".equals(String.valueOf(activity.get("activity_status")))) {
            throw new BusinessException(LifecycleErrorCode.ACTIVITY_OBSOLETE_READONLY, "已作废活动只读，禁止任何写操作");
        }
    }

    private static boolean bool(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value != null && ((Number) value).intValue() == 1;
    }

    private static int boolInt(Boolean value, boolean defaultValue) {
        return (value == null ? defaultValue : value) ? 1 : 0;
    }

    private static Object[] concat(List<Object> heads, List<Long> tails) {
        List<Object> values = new ArrayList<>(heads);
        values.addAll(tails);
        return values.toArray();
    }

    private static long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
