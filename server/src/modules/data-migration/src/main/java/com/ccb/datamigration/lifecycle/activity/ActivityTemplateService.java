package com.ccb.datamigration.lifecycle.activity;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.datamigration.lifecycle.LifecycleAuditService;
import com.ccb.datamigration.lifecycle.LifecyclePermissionService;
import com.ccb.datamigration.lifecycle.activity.model.ProcessInput;
import com.ccb.datamigration.lifecycle.activity.model.TemplatePackage;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 活动模板导入 / 导出（基线 9.2.6，JSON 通路）：脱敏（不含项目/组件/人员实例数据）、四类校验、留痕。 */
@Service
public class ActivityTemplateService {
    private final JdbcTemplate jdbc;
    private final LifecyclePermissionService permissions;
    private final LifecycleAuditService audit;
    private final ActivityCodeGenerator codeGenerator;
    private final ObjectMapper objectMapper;

    public ActivityTemplateService(JdbcTemplate jdbc, LifecyclePermissionService permissions,
                                   LifecycleAuditService audit, ActivityCodeGenerator codeGenerator,
                                   ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.audit = audit;
        this.codeGenerator = codeGenerator;
        this.objectMapper = objectMapper;
    }

    /** 导出单活动模板包：仅活动基础属性 + 工序配置 + 拓扑依赖（按 seq），不携带实例数据（脱敏铁律 #14）。 */
    @Transactional
    public Map<String, Object> exportTemplate(AuthUser user, long activityId) {
        permissions.requireAdmin(user);
        Map<String, Object> activity = requireActivity(activityId, user.tenantId());
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, seq, process_name, owner_role_id, is_required, "
                + "entry_config, exec_config, exit_content, exit_deliverable_list, qualified_rule, must_audit, "
                + "must_submit_deliverable, deliverable_template_id FROM activity_process "
                + "WHERE activity_id = ? AND tenant_id = ? AND deleted = 0 ORDER BY seq ASC", activityId, user.tenantId());
        Map<Long, Integer> seqByProcessId = new HashMap<>();
        for (Map<String, Object> row : rows) {
            seqByProcessId.put(((Number) row.get("id")).longValue(), ((Number) row.get("seq")).intValue());
        }
        List<TemplatePackage.EdgeInput> edges = jdbc.queryForList("SELECT source_process_id, target_process_id "
                        + "FROM activity_process_dep WHERE activity_id = ? AND tenant_id = ? AND deleted = 0",
                activityId, user.tenantId()).stream()
                .map(row -> new TemplatePackage.EdgeInput(seqByProcessId.get(((Number) row.get("source_process_id")).longValue()),
                        seqByProcessId.get(((Number) row.get("target_process_id")).longValue())))
                .toList();
        List<ProcessInput> processes = rows.stream()
                .map(row -> new ProcessInput(((Number) row.get("id")).longValue(), ((Number) row.get("seq")).intValue(),
                        (String) row.get("process_name"), longOrNull(row.get("owner_role_id")),
                        ((Number) row.get("is_required")).intValue() == 1, (String) row.get("entry_config"),
                        (String) row.get("exec_config"), (String) row.get("exit_content"),
                        (String) row.get("exit_deliverable_list"), (String) row.get("qualified_rule"),
                        ((Number) row.get("must_audit")).intValue() == 1,
                        ((Number) row.get("must_submit_deliverable")).intValue() == 1,
                        longOrNull(row.get("deliverable_template_id"))))
                .toList();
        String templateName = String.valueOf(activity.get("activity_name")) + "（模板导出）";
        TemplatePackage pack = new TemplatePackage(templateName, String.valueOf(activity.get("activity_type")),
                String.valueOf(activity.get("granularity")), longOrNull(activity.get("lifecycle_stage_id")),
                (String) activity.get("stage_code"), (String) activity.get("scene"), (String) activity.get("goal"),
                (String) activity.get("overall_entry_cond"), (String) activity.get("overall_exit_desc"),
                (String) activity.get("overall_deliverables"), processes, edges);
        String json = toJson(pack);
        String summary = buildSummary(pack);
        jdbc.update("INSERT INTO activity_template (id, tenant_id, template_code, template_name, format, package_json, "
                        + "summary, created_by) VALUES (?, ?, ?, ?, 'JSON', ?, ?, ?)",
                nextId(), user.tenantId(), "TPL-" + String.valueOf(activity.get("activity_code")), templateName, json,
                summary, user.id());
        audit.audit(user, audit.opCode("EXPORT", "ACTIVITY_TEMPLATE"), "ACTIVITY_TEMPLATE", activityId, "SUCCESS", null,
                Map.of("templateName", templateName, "processCount", processes.size(), "edgeCount", edges.size(), "summary", summary));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("templateName", templateName);
        result.put("activityType", pack.activityType());
        result.put("granularity", pack.granularity());
        result.put("processCount", processes.size());
        result.put("edgeCount", edges.size());
        result.put("summary", summary);
        result.put("packageJson", json);
        return result;
    }

    /** 导入模板包：四类校验（JSON 格式 / 字段完整性 / 颗粒度一致性 / 依赖合法性），生成停用草稿活动，编码冲突自动重编。 */
    @Transactional
    public Map<String, Object> importTemplate(AuthUser user, String packageJson) {
        permissions.requireAdmin(user);
        TemplatePackage pack = parsePackage(packageJson);
        validateCompleteness(pack);
        validateGranularity(pack);
        List<TemplatePackage.EdgeInput> edges = validateDependencies(pack);
        String activityCode = codeGenerator.candidate(user.tenantId(), pack.activityType(), pack.granularity());
        Long stageId = resolveStage(user.tenantId(), pack);
        long activityId = nextId();
        jdbc.update("INSERT INTO activity (id, tenant_id, activity_code, activity_name, activity_type, lifecycle_stage_id, "
                        + "granularity, activity_status, scene, goal, overall_entry_cond, overall_exit_desc, "
                        + "overall_deliverables, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'INACTIVE', ?, ?, ?, ?, ?, ?, ?)",
                activityId, user.tenantId(), activityCode, pack.templateName(), pack.activityType(), stageId,
                pack.granularity(), pack.scene(), pack.goal(), pack.overallEntryCond(), pack.overallExitDesc(),
                pack.overallDeliverables(), user.id(), user.id());
        List<ProcessInput> normalized = new ArrayList<>(pack.processes());
        normalized.sort(java.util.Comparator.comparingInt(ProcessInput::seq));
        Map<Integer, Long> processIdBySeq = new HashMap<>();
        int seq = 1;
        for (ProcessInput input : normalized) {
            long processId = nextId();
            boolean derived = true;
            for (TemplatePackage.EdgeInput edge : edges) {
                if (edge.targetSeq() == input.seq()) {
                    derived = false;
                    break;
                }
            }
            boolean triad = isTriadComplete(input);
            jdbc.update("INSERT INTO activity_process (id, tenant_id, activity_id, seq, process_name, owner_role_id, "
                            + "is_required, entry_config, is_no_predecessor, exec_config, exit_content, "
                            + "exit_deliverable_list, qualified_rule, must_audit, must_submit_deliverable, "
                            + "deliverable_template_id, config_status, created_by, updated_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    processId, user.tenantId(), activityId, seq, input.processName(), input.ownerRoleId(),
                    input.isRequired() == null || input.isRequired() ? 1 : 0, input.entryConfig(), derived ? 1 : 0,
                    input.execConfig(), input.exitContent(), input.exitDeliverableList(), input.qualifiedRule(),
                    boolInt(input.mustAudit(), true), boolInt(input.mustSubmitDeliverable(), true),
                    input.deliverableTemplateId(), triad ? "READY" : "DRAFT", user.id(), user.id());
            processIdBySeq.put(input.seq(), processId);
            seq++;
        }
        for (TemplatePackage.EdgeInput edge : edges) {
            jdbc.update("INSERT INTO activity_process_dep (id, tenant_id, activity_id, source_process_id, target_process_id, created_by) "
                    + "VALUES (?, ?, ?, ?, ?, ?)", nextId(), user.tenantId(), activityId,
                    processIdBySeq.get(edge.sourceSeq()), processIdBySeq.get(edge.targetSeq()), user.id());
        }
        boolean nameDuplicate = existsNameDuplicate(user.tenantId(), pack.templateName(), activityId);
        String summary = buildSummary(pack);
        jdbc.update("INSERT INTO activity_template (id, tenant_id, template_code, template_name, format, package_json, "
                        + "summary, created_by) VALUES (?, ?, ?, ?, 'JSON', ?, ?, ?)",
                nextId(), user.tenantId(), activityCode, pack.templateName(), packageJson, summary, user.id());
        audit.audit(user, audit.opCode("IMPORT", "ACTIVITY_TEMPLATE"), "ACTIVITY_TEMPLATE", activityId, "SUCCESS", null,
                Map.of("activityCode", activityCode, "processCount", normalized.size(), "edgeCount", edges.size(),
                        "nameDuplicate", nameDuplicate, "summary", summary));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activityId", activityId);
        result.put("activityCode", activityCode);
        result.put("activityStatus", "INACTIVE");
        result.put("processCount", normalized.size());
        result.put("edgeCount", edges.size());
        result.put("nameDuplicateHint", nameDuplicate ? "存在同名活动，请人工确认是否覆盖" : "");
        return result;
    }

    private TemplatePackage parsePackage(String packageJson) {
        if (packageJson == null || packageJson.isBlank()) {
            throw new BusinessException(LifecycleErrorCode.TEMPLATE_INVALID_JSON, "模板包 JSON 不能为空");
        }
        try {
            return objectMapper.readValue(packageJson, TemplatePackage.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(LifecycleErrorCode.TEMPLATE_INVALID_JSON, "模板包 JSON 格式错误：" + ex.getOriginalMessage());
        }
    }

    private void validateCompleteness(TemplatePackage pack) {
        if (pack.templateName() == null || pack.templateName().isBlank()
                || pack.activityType() == null || pack.granularity() == null
                || pack.processes() == null || pack.processes().isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.TEMPLATE_FIELD_INCOMPLETE, "模板包缺少必填字段（模板名称/类型/颗粒度/工序）");
        }
        List<String> messages = new ArrayList<>();
        Set<Integer> seqs = new HashSet<>();
        for (ProcessInput process : pack.processes()) {
            if (process.processName() == null || process.processName().isBlank()
                    || process.exitContent() == null || process.exitContent().isBlank()
                    || process.exitDeliverableList() == null || process.exitDeliverableList().isBlank()
                    || process.qualifiedRule() == null || process.qualifiedRule().isBlank()) {
                messages.add("工序缺少准出三要素或名称");
            }
            if (!seqs.add(process.seq())) {
                messages.add("工序序号重复：" + process.seq());
            }
        }
        for (int i = 1; i <= pack.processes().size(); i++) {
            if (!seqs.contains(i)) {
                messages.add("工序序号必须连续 1..n，缺失序号：" + i);
            }
        }
        if (!messages.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.TEMPLATE_FIELD_INCOMPLETE, "模板字段完整性校验失败：" + String.join("；", messages));
        }
    }

    private void validateGranularity(TemplatePackage pack) {
        if (!"PROJECT".equals(pack.granularity()) && !"COMPONENT".equals(pack.granularity())) {
            throw new BusinessException(LifecycleErrorCode.TEMPLATE_GRANULARITY_MISMATCH, "模板颗粒度必须为 PROJECT/COMPONENT");
        }
        boolean topic = "TOPIC".equals(pack.activityType());
        if (!"NORMAL".equals(pack.activityType()) && !topic) {
            throw new BusinessException(LifecycleErrorCode.TEMPLATE_GRANULARITY_MISMATCH, "模板活动类型必须为 NORMAL/TOPIC");
        }
        if (!topic && (pack.lifecycleStageCode() == null || pack.lifecycleStageCode().isBlank())) {
            throw new BusinessException(LifecycleErrorCode.TEMPLATE_GRANULARITY_MISMATCH, "普通活动模板必须携带生命周期阶段");
        }
    }

    private List<TemplatePackage.EdgeInput> validateDependencies(TemplatePackage pack) {
        Set<Integer> seqs = new HashSet<>();
        pack.processes().forEach(p -> seqs.add(p.seq()));
        List<TemplatePackage.EdgeInput> edges = pack.edges() == null ? List.of() : pack.edges();
        Set<String> edgeKeys = new HashSet<>();
        for (TemplatePackage.EdgeInput edge : edges) {
            if (!seqs.contains(edge.sourceSeq()) || !seqs.contains(edge.targetSeq())) {
                throw new BusinessException(LifecycleErrorCode.TEMPLATE_DEPENDENCY_INVALID,
                        "模板引用缺失工序节点：" + edge.sourceSeq() + "->" + edge.targetSeq());
            }
            if (edge.sourceSeq() == edge.targetSeq()) {
                throw new BusinessException(LifecycleErrorCode.TEMPLATE_DEPENDENCY_INVALID, "模板禁止自连依赖");
            }
            if (!edgeKeys.add(edge.sourceSeq() + "->" + edge.targetSeq())) {
                throw new BusinessException(LifecycleErrorCode.TEMPLATE_DEPENDENCY_INVALID,
                        "模板禁止重复依赖：" + edge.sourceSeq() + "->" + edge.targetSeq());
            }
        }
        assertNoCycle(seqs, edgeKeys);
        return edges;
    }

    private void assertNoCycle(Set<Integer> seqs, Set<String> edgeKeys) {
        Map<Integer, List<Integer>> adjacency = new HashMap<>();
        seqs.forEach(seq -> adjacency.put(seq, new ArrayList<>()));
        for (String key : edgeKeys) {
            String[] parts = key.split("->");
            adjacency.get(Integer.parseInt(parts[0])).add(Integer.parseInt(parts[1]));
        }
        Set<Integer> visiting = new HashSet<>();
        Set<Integer> visited = new HashSet<>();
        for (int seq : seqs) {
            if (!visited.contains(seq)) {
                detectCycle(seq, adjacency, visiting, visited);
            }
        }
    }

    private void detectCycle(int seq, Map<Integer, List<Integer>> adjacency, Set<Integer> visiting, Set<Integer> visited) {
        if (visiting.contains(seq)) {
            throw new BusinessException(LifecycleErrorCode.TEMPLATE_DEPENDENCY_INVALID, "模板工序依赖形成环路");
        }
        if (visited.contains(seq)) {
            return;
        }
        visiting.add(seq);
        for (int next : adjacency.getOrDefault(seq, List.of())) {
            detectCycle(next, adjacency, visiting, visited);
        }
        visiting.remove(seq);
        visited.add(seq);
    }

    private Long resolveStage(long tenantId, TemplatePackage pack) {
        if (pack.lifecycleStageCode() == null || pack.lifecycleStageCode().isBlank()) {
            return pack.lifecycleStageId();
        }
        List<Long> ids = jdbc.queryForList("SELECT id FROM lifecycle_stage WHERE tenant_id = ? AND stage_code = ? "
                + "AND deleted = 0 AND status = 1", Long.class, tenantId, pack.lifecycleStageCode());
        if (ids.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.TEMPLATE_FIELD_INCOMPLETE, "生命周期阶段编码不存在：" + pack.lifecycleStageCode());
        }
        return ids.get(0);
    }

    private boolean existsNameDuplicate(long tenantId, String name, long excludeActivityId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM activity WHERE tenant_id = ? AND activity_name = ? "
                + "AND deleted = 0 AND id <> ?", Integer.class, tenantId, name, excludeActivityId);
        return count != null && count > 0;
    }

    private String buildSummary(TemplatePackage pack) {
        return pack.activityType() + "/" + pack.granularity()
                + " 工序 " + (pack.processes() == null ? 0 : pack.processes().size())
                + " 条、依赖 " + (pack.edges() == null ? 0 : pack.edges().size()) + " 条";
    }

    private boolean isTriadComplete(ProcessInput process) {
        return process.exitContent() != null && !process.exitContent().isBlank()
                && process.exitDeliverableList() != null && !process.exitDeliverableList().isBlank()
                && process.qualifiedRule() != null && !process.qualifiedRule().isBlank();
    }

    private Map<String, Object> requireActivity(long id, long tenantId) {
        try {
            return jdbc.queryForMap("SELECT a.id, a.tenant_id, a.activity_code, a.activity_name, a.activity_type, "
                    + "a.granularity, a.lifecycle_stage_id, a.scene, a.goal, a.overall_entry_cond, a.overall_exit_desc, "
                    + "a.overall_deliverables, s.stage_code FROM activity a "
                    + "LEFT JOIN lifecycle_stage s ON s.id = a.lifecycle_stage_id AND s.tenant_id = a.tenant_id AND s.deleted = 0 "
                    + "WHERE a.id = ? AND a.tenant_id = ? AND a.deleted = 0", id, tenantId);
        } catch (Exception ex) {
            throw new BusinessException(LifecycleErrorCode.ACTIVITY_NOT_FOUND, "活动不存在");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "序列化失败");
        }
    }

    private static Long longOrNull(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static int boolInt(Boolean value, boolean defaultValue) {
        return (value == null ? defaultValue : value) ? 1 : 0;
    }

    private static long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
