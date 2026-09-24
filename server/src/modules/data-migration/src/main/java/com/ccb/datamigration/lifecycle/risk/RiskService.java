package com.ccb.datamigration.lifecycle.risk;

import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.datamigration.lifecycle.LifecyclePermissionService;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import com.ccb.datamigration.lifecycle.risk.model.RiskStrategyLibView;
import com.ccb.datamigration.lifecycle.risk.model.RiskView;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
 * 风险管理 + 风险策略库（基线第 14 章，T9）：
 * 双渠道归集；与问题域共用 reconcile 协议（归属类字段权威在上报单、防控类字段权威在台账）；
 * 等级/概率单一值约束；6 态互斥（已规避/已发生/已闭环任一时刻仅唯一）；闭环固化归档；
 * 普通用户不可作废、不可维护策略；策略库关键词匹配与一键复用（三元组语义 analog）。
 */
@Service
public class RiskService {
    private final JdbcTemplate jdbc;
    private final LifecyclePermissionService permissions;
    private final ObjectMapper objectMapper;

    public RiskService(JdbcTemplate jdbc, LifecyclePermissionService permissions, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.objectMapper = objectMapper;
    }

    /** 风险台账（角色差异化：管理员全量；普通执行人仅本人上报/防控）。 */
    public PageResult<RiskView> listRisks(AuthUser user, String riskStatus, String riskLevel, String riskCode,
                                          Long componentId, String riskSource, long page, long size) {
        StringBuilder where = new StringBuilder("r.tenant_id = ? AND r.deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        if (!permissions.isAdmin(user)) {
            where.append(" AND (r.reporter_id = ? OR r.prevent_owner_id = ?)");
            args.add(user.id());
            args.add(user.id());
        }
        if (riskStatus != null && !riskStatus.isBlank()) {
            where.append(" AND r.risk_status = ?");
            args.add(riskStatus);
        }
        if (riskLevel != null && !riskLevel.isBlank()) {
            where.append(" AND r.risk_level = ?");
            args.add(riskLevel);
        }
        if (riskSource != null && !riskSource.isBlank()) {
            where.append(" AND r.risk_source = ?");
            args.add(riskSource);
        }
        if (riskCode != null && !riskCode.isBlank()) {
            where.append(" AND r.risk_code LIKE ?");
            args.add("%" + riskCode.trim() + "%");
        }
        if (componentId != null) {
            where.append(" AND r.component_id = ?");
            args.add(componentId);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM risk r WHERE " + where, Long.class, args.toArray());
        long offset = (page - 1) * size;
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT r.* FROM risk r WHERE " + where
                + " ORDER BY r.reported_at DESC, r.id DESC LIMIT " + size + " OFFSET " + offset, args.toArray());
        return new PageResult<>(rows.stream().map(this::toView).toList(), total, page, size);
    }

    /** 主动新增风险（管理员/项目负责人）：等级与概率单一值强制约束。 */
    @Transactional
    public RiskView createRisk(AuthUser user, Map<String, Object> body) {
        permissions.requireAdmin(user);
        String title = text(body, "riskTitle");
        String desc = text(body, "riskDesc");
        String impact = text(body, "impactScope");
        if (title == null || title.isBlank() || desc == null || desc.isBlank() || impact == null || impact.isBlank()) {
            throw new BusinessException(LifecycleErrorCode.RISK_FIELD_REQUIRED, "标题/描述/影响范围为必填");
        }
        String level = requireSingleLevel(text(body, "riskLevel"));
        String probability = requireSingleProbability(text(body, "probability"));
        long id = nextId();
        String code = nextRiskCode(user.tenantId());
        jdbc.update("INSERT INTO risk (id, tenant_id, risk_code, risk_title, risk_source, project_id, "
                        + "business_group_id, component_id, order_id, process_seq, risk_level, probability, "
                        + "impact_scope, risk_desc, potential_harm, predicted_scene, attachment_ids, reporter_id, "
                        + "prevent_owner_id, risk_status) "
                        + "VALUES (?, ?, ?, ?, 'MANUAL', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'WAIT_PREVENT')",
                id, user.tenantId(), code, title, nullableLong(body.get("projectId")),
                nullableLong(body.get("businessGroupId")), nullableLong(body.get("componentId")),
                nullableLong(body.get("orderId")), nullableInt(body.get("processSeq")), level, probability,
                impact, desc, text(body, "potentialHarm"), text(body, "predictedScene"),
                toJson(longList(body.get("attachmentIds"))), user.id(), nullableLong(body.get("preventOwnerId")));
        return getRisk(user.tenantId(), id);
    }

    /** 台账维护（管理员/负责人）：策略字段录入与状态维护；闭环后固化归档禁止修改。 */
    @Transactional
    public RiskView updateRisk(AuthUser user, long riskId, Map<String, Object> body, boolean allowStatus) {
        permissions.requireAdmin(user);
        Map<String, Object> row = requireRisk(user.tenantId(), riskId);
        String status = String.valueOf(row.get("risk_status"));
        if ("CLOSED".equals(status)) {
            throw new BusinessException(LifecycleErrorCode.RISK_CLOSED_ARCHIVED, "已闭环风险固化归档，禁止修改");
        }
        if ("CANCELLED".equals(status)) {
            throw new BusinessException(LifecycleErrorCode.RISK_CANCEL_FORBIDDEN, "已作废风险禁止修改");
        }
        String newStatus = allowStatus ? text(body, "riskStatus") : status;
        if (newStatus != null && !isRiskStatus(newStatus)) {
            throw new BusinessException(LifecycleErrorCode.RISK_STATUS_INVALID, "风险状态非法或三态互斥冲突");
        }
        jdbc.update("UPDATE risk SET risk_title = ?, risk_desc = ?, impact_scope = ?, potential_harm = ?, "
                        + "predicted_scene = ?, pre_prevent_measure = COALESCE(?, pre_prevent_measure), "
                        + "response_strategy = COALESCE(?, response_strategy), degrade_plan = COALESCE(?, degrade_plan), "
                        + "emergency_plan = COALESCE(?, emergency_plan), prevent_priority = COALESCE(?, prevent_priority), "
                        + "risk_status = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND tenant_id = ? AND deleted = 0",
                text(body, "riskTitle"), text(body, "riskDesc"), text(body, "impactScope"),
                text(body, "potentialHarm"), text(body, "predictedScene"),
                text(body, "prePreventMeasure"), text(body, "responseStrategy"), text(body, "degradePlan"),
                text(body, "emergencyPlan"), text(body, "preventPriority"), newStatus, riskId, user.tenantId());
        return getRisk(user.tenantId(), riskId);
    }

    /** 对账式同步 reconcile（幂等）：process_risk_report → risk 归集；归属类字段权威在上报单镜像。 */
    @Transactional
    public Map<String, Object> reconcile(AuthUser user) {
        permissions.requireAdmin(user);
        List<Map<String, Object>> reports = jdbc.queryForList("SELECT * FROM process_risk_report "
                + "WHERE tenant_id = ? AND deleted = 0 AND (synced_risk_id IS NULL OR synced_risk_id = 0)",
                user.tenantId());
        int synced = 0;
        for (Map<String, Object> report : reports) {
            long reportId = longOf(report, "id");
            Map<String, Object> orderRow = orderOf(user.tenantId(), longOf(report, "order_id"));
            if (orderRow == null) {
                throw new BusinessException(LifecycleErrorCode.RISK_SYNC_CONFLICT, "上报单归属工单缺失，拒绝归集");
            }
            long id = nextId();
            String code = nextRiskCode(user.tenantId());
            jdbc.update("INSERT INTO risk (id, tenant_id, risk_code, risk_title, risk_source, project_id, "
                            + "business_group_id, component_id, order_id, process_seq, risk_level, probability, "
                            + "impact_scope, risk_desc, reporter_id, prevent_owner_id, risk_status, snapshot_version, "
                            + "synced_report_id) "
                            + "VALUES (?, ?, ?, ?, 'ORDER_SYNC', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'WAIT_PREVENT', ?, ?)",
                    id, user.tenantId(), code, stringOf(report, "risk_title"),
                    nullableLong(orderRow.get("project_id")), nullableLong(orderRow.get("business_group_id")),
                    nullableLong(orderRow.get("component_id")), longOf(report, "order_id"), intOf(report, "process_seq"),
                    stringOf(report, "risk_level"), stringOf(report, "probability"),
                    stringOf(report, "impact_scope"), stringOf(report, "risk_desc"),
                    longOf(report, "reporter_id"), longOf(report, "reporter_id"),
                    snapshotOf(report), reportId);
            jdbc.update("UPDATE process_risk_report SET synced_risk_id = ? WHERE id = ? AND tenant_id = ?",
                    id, reportId, user.tenantId());
            synced++;
        }
        return Map.of("synced", synced);
    }

    /** 防控推进（防控责任人）：更新防控进度与落地执行记录；闭环后拒绝。 */
    @Transactional
    public RiskView prevent(AuthUser user, long riskId, String progress, String record) {
        Map<String, Object> row = requireRisk(user.tenantId(), riskId);
        String status = String.valueOf(row.get("risk_status"));
        if ("CLOSED".equals(status)) {
            throw new BusinessException(LifecycleErrorCode.RISK_CLOSED_ARCHIVED, "已闭环风险固化归档，禁止修改");
        }
        boolean actor = user.id() == longOf(row, "prevent_owner_id") || user.id() == longOf(row, "reporter_id");
        if (!actor && !permissions.isAdmin(user)) {
            throw new BusinessException(LifecycleErrorCode.RISK_PREVENT_PERMISSION_DENIED, "仅防控责任人/上报人可推进防控");
        }
        jdbc.update("INSERT INTO risk_prevent_record (id, tenant_id, risk_id, record, operator_id) VALUES (?, ?, ?, ?, ?)",
                nextId(), user.tenantId(), riskId, record == null ? "" : record, user.id());
        jdbc.update("UPDATE risk SET prevent_progress = ?, prevent_records = ?, risk_status = 'PREVENTING', "
                        + "updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND tenant_id = ? AND deleted = 0",
                progress == null ? "" : progress,
                appendJson(String.valueOf(row.get("prevent_records")),
                        Map.of("record", record == null ? "" : record, "operatorId", user.id(),
                                "at", LocalDateTime.now().toString())),
                riskId, user.tenantId());
        return getRisk(user.tenantId(), riskId);
    }

    /** 闭环固化（管理员核验）：固定状态互斥（已规避/已发生/已闭环任一时刻仅唯一）；策略固化归档。 */
    @Transactional
    public Map<String, Object> close(AuthUser user, long riskId, String finalStatus) {
        permissions.requireAdmin(user);
        Map<String, Object> row = requireRisk(user.tenantId(), riskId);
        if ("CLOSED".equals(String.valueOf(row.get("risk_status")))) {
            throw new BusinessException(LifecycleErrorCode.RISK_CLOSED_ARCHIVED, "风险已闭环");
        }
        String target = finalStatus == null ? "CLOSED" : finalStatus.toUpperCase();
        if (!"CLOSED".equals(target) && !"AVOIDED".equals(target) && !"OCCURRED".equals(target)) {
            throw new BusinessException(LifecycleErrorCode.RISK_STATUS_INVALID, "闭环终点仅可为已规避/已发生/已闭环（三态互斥）");
        }
        jdbc.update("UPDATE risk SET risk_status = ?, prevented_at = CURRENT_TIMESTAMP(6), "
                        + "updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND tenant_id = ? AND deleted = 0",
                target, riskId, user.tenantId());
        // 策略固化归档：随风险快照沉淀一条 risk_strategy 实例（闭环后禁止删改）
        String strategyCode = "RS-" + LocalDate.now().toString().replace("-", "") + "-"
                + String.format("%03d", (int) (nextId() % 1000));
        jdbc.update("INSERT INTO risk_strategy (id, tenant_id, risk_id, strategy_code, pre_prevent_measure, "
                        + "response_strategy, degrade_plan, emergency_plan, create_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                nextId(), user.tenantId(), riskId, strategyCode,
                stringOrNull(row, "pre_prevent_measure"), stringOrNull(row, "response_strategy"),
                stringOrNull(row, "degrade_plan"), stringOrNull(row, "emergency_plan"), user.id());
        return Map.of("riskId", riskId, "riskStatus", target, "strategyCode", strategyCode);
    }

    /** 作废（仅管理员；普通用户不可作废，基线 14.4）。 */
    @Transactional
    public void cancel(AuthUser user, long riskId, String reason) {
        permissions.requireAdmin(user);
        Map<String, Object> row = requireRisk(user.tenantId(), riskId);
        if ("CLOSED".equals(String.valueOf(row.get("risk_status")))) {
            throw new BusinessException(LifecycleErrorCode.RISK_CLOSED_ARCHIVED, "已闭环风险禁止作废");
        }
        jdbc.update("UPDATE risk SET risk_status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP(6) "
                + "WHERE id = ? AND tenant_id = ? AND deleted = 0", riskId, user.tenantId());
    }

    // ===== 策略库 =====

    /** 策略库清单（全员可读；维护仅管理员）。 */
    public List<RiskStrategyLibView> listStrategies(AuthUser user, String keyword, String status) {
        StringBuilder where = new StringBuilder("s.tenant_id = ? AND s.deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (s.strategy_title LIKE ? OR s.risk_title_pattern LIKE ?)");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND s.status = ?");
            args.add(status);
        }
        return jdbc.queryForList("SELECT s.* FROM risk_strategy_lib s WHERE " + where
                + " ORDER BY s.use_count DESC, s.id DESC", args.toArray()).stream().map(this::toStrategyView).toList();
    }

    /** 策略新增（管理员）。 */
    @Transactional
    public RiskStrategyLibView createStrategy(AuthUser user, Map<String, Object> body) {
        permissions.requireAdmin(user);
        String title = text(body, "strategyTitle");
        if (title == null || title.isBlank()) {
            throw new BusinessException(LifecycleErrorCode.RISK_STRATEGY_PERMISSION_DENIED, "策略标题必填");
        }
        String level = body.get("riskLevel") == null ? null : requireSingleLevel(text(body, "riskLevel"));
        String probability = body.get("probability") == null ? null : requireSingleProbability(text(body, "probability"));
        long id = nextId();
        String code = "SL-" + LocalDate.now().toString().replace("-", "") + "-"
                + String.format("%03d", (int) (nextId() % 1000));
        jdbc.update("INSERT INTO risk_strategy_lib (id, tenant_id, strategy_code, strategy_title, risk_title_pattern, "
                        + "match_keywords, risk_level, probability, pre_prevent_measure, response_strategy, "
                        + "degrade_plan, emergency_plan, use_count, status, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'ACTIVE', ?)",
                id, user.tenantId(), code, title, text(body, "riskTitlePattern"),
                toJson(body.get("matchKeywords") == null ? List.of() : body.get("matchKeywords")),
                level, probability, text(body, "prePreventMeasure"), text(body, "responseStrategy"),
                text(body, "degradePlan"), text(body, "emergencyPlan"), user.id());
        return toStrategyView(jdbc.queryForMap("SELECT * FROM risk_strategy_lib WHERE id = ?", id));
    }

    /** 策略智能匹配（关键词检索；协同过滤/向量服务后续替换）：返回同类策略候选（等级/概率命中优先）。 */
    public List<RiskStrategyLibView> matchStrategies(AuthUser user, String riskTitle, String level, String probability) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM risk_strategy_lib "
                + "WHERE tenant_id = ? AND deleted = 0 AND status = 'ACTIVE' ORDER BY use_count DESC LIMIT 100",
                user.tenantId());
        List<RiskStrategyLibView> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            int score = 0;
            if (level != null && level.equalsIgnoreCase(stringOf(row, "risk_level"))) {
                score += 3;
            }
            if (probability != null && probability.equalsIgnoreCase(stringOf(row, "probability"))) {
                score += 3;
            }
            score += keywordScore((riskTitle == null ? "" : riskTitle),
                    stringOrNull(row, "risk_title_pattern") + " " + String.join(" ",
                            parseStringList(String.valueOf(row.get("match_keywords")))));
            if (score > 0) {
                result.add(toStrategyView(row));
            }
            if (result.size() >= 5) {
                break;
            }
        }
        return result;
    }

    /** 一键复用（管理员/负责人）：策略字段回填目标风险并留痕；复用次数回写。 */
    @Transactional
    public Map<String, Object> reuseStrategy(AuthUser user, long strategyId, long riskId) {
        permissions.requireAdmin(user);
        Map<String, Object> strategy = requireStrategy(user.tenantId(), strategyId);
        Map<String, Object> risk = requireRisk(user.tenantId(), riskId);
        if ("CLOSED".equals(String.valueOf(risk.get("risk_status")))) {
            throw new BusinessException(LifecycleErrorCode.RISK_CLOSED_ARCHIVED, "已闭环风险禁止复用策略");
        }
        jdbc.update("UPDATE risk SET pre_prevent_measure = ?, response_strategy = ?, degrade_plan = ?, "
                        + "emergency_plan = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND tenant_id = ? AND deleted = 0",
                stringOrNull(strategy, "pre_prevent_measure"), stringOrNull(strategy, "response_strategy"),
                stringOrNull(strategy, "degrade_plan"), stringOrNull(strategy, "emergency_plan"),
                riskId, user.tenantId());
        jdbc.update("UPDATE risk_strategy_lib SET use_count = use_count + 1, updated_at = CURRENT_TIMESTAMP(6) "
                + "WHERE id = ? AND tenant_id = ? AND deleted = 0", strategyId, user.tenantId());
        return Map.of("strategyId", strategyId, "riskId", riskId, "applied", true);
    }

    /** 策略下线（管理员；禁止物理删除）。 */
    @Transactional
    public RiskStrategyLibView offStrategy(AuthUser user, long strategyId) {
        permissions.requireAdmin(user);
        requireStrategy(user.tenantId(), strategyId);
        jdbc.update("UPDATE risk_strategy_lib SET status = 'INVALID', updated_at = CURRENT_TIMESTAMP(6) "
                + "WHERE id = ? AND tenant_id = ? AND deleted = 0", strategyId, user.tenantId());
        return toStrategyView(jdbc.queryForMap("SELECT * FROM risk_strategy_lib WHERE id = ?", strategyId));
    }

    // ===== 内部辅助 =====

    private RiskView getRisk(long tenantId, long riskId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM risk WHERE id = ? AND tenant_id = ? AND deleted = 0",
                riskId, tenantId);
        return rows.isEmpty() ? null : toView(rows.get(0));
    }

    private Map<String, Object> requireRisk(long tenantId, long riskId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM risk WHERE id = ? AND tenant_id = ? AND deleted = 0",
                riskId, tenantId);
        if (rows.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.RISK_NOT_FOUND, "风险不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> requireStrategy(long tenantId, long strategyId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM risk_strategy_lib WHERE id = ? AND tenant_id = ? AND deleted = 0",
                strategyId, tenantId);
        if (rows.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.RISK_STRATEGY_NOT_FOUND, "策略库条目不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> orderOf(long tenantId, long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM work_order WHERE id = ? AND tenant_id = ? AND deleted = 0",
                orderId, tenantId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String nextRiskCode(long tenantId) {
        String prefix = "RK-" + LocalDate.now().toString().replace("-", "");
        Long max = jdbc.queryForObject("SELECT MAX(CAST(SUBSTRING_INDEX(risk_code, '-', -1) AS UNSIGNED)) FROM risk "
                + "WHERE tenant_id = ? AND risk_code LIKE ? AND deleted = 0", Long.class, tenantId, prefix + "-%");
        long seq = (max == null ? 0 : max) + 1;
        return prefix + "-" + String.format("%03d", seq);
    }

    private static String requireSingleLevel(String level) {
        if (level == null || !List.of("HIGH", "MEDIUM", "LOW").contains(level.toUpperCase())) {
            throw new BusinessException(LifecycleErrorCode.RISK_LEVEL_SINGLE, "风险等级仅可配置单一值（HIGH/MEDIUM/LOW）");
        }
        return level.toUpperCase();
    }

    private static String requireSingleProbability(String probability) {
        if (probability == null || !List.of("HIGH", "MEDIUM", "LOW").contains(probability.toUpperCase())) {
            throw new BusinessException(LifecycleErrorCode.RISK_PROBABILITY_SINGLE, "发生概率仅可配置单一值（HIGH/MEDIUM/LOW）");
        }
        return probability.toUpperCase();
    }

    private static boolean isRiskStatus(String status) {
        return "WAIT_PREVENT".equals(status) || "PREVENTING".equals(status) || "AVOIDED".equals(status)
                || "OCCURRED".equals(status) || "CLOSED".equals(status) || "CANCELLED".equals(status);
    }

    private int keywordScore(String query, String text) {
        if (text == null) {
            return 0;
        }
        int score = 0;
        for (String token : query.split("[\\s，。,.、；;：:]+")) {
            if (token.length() >= 2 && text.contains(token)) {
                score++;
            }
        }
        return score;
    }

    private String appendJson(String existing, Object addition) {
        try {
            List<Object> list = existing == null || existing.isBlank() ? new ArrayList<>()
                    : objectMapper.readValue(existing, new TypeReference<List<Object>>() {
                    });
            list.add(addition);
            return objectMapper.writeValueAsString(list);
        } catch (Exception ex) {
            return toJson(List.of(addition));
        }
    }

    private RiskView toView(Map<String, Object> row) {
        return new RiskView(longOf(row, "id"), stringOf(row, "risk_code"), stringOf(row, "risk_title"),
                stringOf(row, "risk_source"), nullableLong(row.get("project_id")), nullableLong(row.get("business_group_id")),
                nullableLong(row.get("component_id")), nullableLong(row.get("order_id")),
                row.get("process_seq") == null ? null : intOf(row, "process_seq"),
                stringOf(row, "risk_level"), stringOf(row, "probability"), stringOrNull(row, "impact_scope"),
                stringOf(row, "risk_desc"), stringOrNull(row, "potential_harm"), stringOrNull(row, "predicted_scene"),
                parseLongList(String.valueOf(row.get("attachment_ids"))), longOf(row, "reporter_id"),
                nullableLong(row.get("prevent_owner_id")), toDateTime(row.get("reported_at")),
                toDateTime(row.get("prevented_at")), toDateTime(row.get("updated_at")),
                stringOrNull(row, "pre_prevent_measure"), stringOrNull(row, "response_strategy"),
                stringOrNull(row, "degrade_plan"), stringOrNull(row, "emergency_plan"),
                stringOrNull(row, "prevent_priority"), row.get("dispose_deadline") == null ? null : toDateTime(row.get("dispose_deadline")),
                stringOrNull(row, "prevent_progress"), parseMapList(String.valueOf(row.get("prevent_records"))),
                parseLongList(String.valueOf(row.get("prevent_attachment_ids"))), stringOf(row, "risk_status"),
                stringOrNull(row, "snapshot_version"), nullableLong(row.get("synced_report_id")));
    }

    private RiskStrategyLibView toStrategyView(Map<String, Object> row) {
        return new RiskStrategyLibView(longOf(row, "id"), stringOf(row, "strategy_code"),
                stringOf(row, "strategy_title"), stringOrNull(row, "risk_title_pattern"),
                parseStringList(String.valueOf(row.get("match_keywords"))),
                stringOrNull(row, "risk_level"), stringOrNull(row, "probability"),
                stringOf(row, "pre_prevent_measure"), stringOrNull(row, "response_strategy"),
                stringOrNull(row, "degrade_plan"), stringOrNull(row, "emergency_plan"),
                intOf(row, "use_count"), stringOf(row, "status"),
                toDateTime(row.get("created_at")), toDateTime(row.get("updated_at")));
    }

    private List<Long> parseLongList(String json) {
        try {
            if (json == null || json.isBlank() || "null".equals(json)) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> parseStringList(String json) {
        try {
            if (json == null || json.isBlank() || "null".equals(json)) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Map<String, Object>> parseMapList(String json) {
        try {
            if (json == null || json.isBlank() || "null".equals(json)) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String snapshotOf(Map<String, Object> report) {
        Object value = report.get("snapshot_version");
        return value == null ? "" : String.valueOf(value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "序列化失败：" + ex.getMessage());
        }
    }

    private static String stringOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String stringOf(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static int intOf(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number n ? n.intValue() : (value == null ? 0 : Integer.parseInt(String.valueOf(value)));
    }

    private static long longOf(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number n ? n.longValue() : (value == null ? 0L : Long.parseLong(String.valueOf(value)));
    }

    private static Long nullableLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static Integer nullableInt(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static LocalDateTime toDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof java.sql.Timestamp stamp) {
            return stamp.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }

    private static List<Long> longList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(v -> v instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(v))).toList();
    }

    private static long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
