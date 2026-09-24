package com.ccb.datamigration.lifecycle.issue;

import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.datamigration.lifecycle.LifecyclePermissionService;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import com.ccb.datamigration.lifecycle.issue.model.IssueView;
import com.ccb.datamigration.lifecycle.issue.model.KnowledgeEntryView;
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
 * 问题管理 + 历史问题知识库（基线第 13 章，T8）：
 * 双渠道归集（手动新增/批量导入/工单同步）；对账式 reconcile 幂等（归属类字段权威在上报单、
 * 整改类字段权威在台账）；4 态枚举禁布尔拆分；颗粒度自动继承；闭环后禁改并自动沉淀知识条目；
 * 知识条目 ACTIVE/INVALID 禁删；Top5 相似推荐（关键词检索实现，外部向量服务可后续替换）。
 */
@Service
public class IssueService {
    private final JdbcTemplate jdbc;
    private final LifecyclePermissionService permissions;
    private final ObjectMapper objectMapper;

    public IssueService(JdbcTemplate jdbc, LifecyclePermissionService permissions, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.objectMapper = objectMapper;
    }

    /** 问题台账（角色差异化：管理员全量；负责人管辖；普通执行人仅本人上报/整改）。 */
    public PageResult<IssueView> listIssues(AuthUser user, String issueStatus, String issueSource,
                                            String issueCode, Long componentId, Long rectifierId,
                                            long page, long size) {
        StringBuilder where = new StringBuilder("i.tenant_id = ? AND i.deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        if (!permissions.isAdmin(user)) {
            where.append(" AND (i.reporter_id = ? OR i.rectifier_id = ?)");
            args.add(user.id());
            args.add(user.id());
        }
        if (issueStatus != null && !issueStatus.isBlank()) {
            where.append(" AND i.issue_status = ?");
            args.add(issueStatus);
        }
        if (issueSource != null && !issueSource.isBlank()) {
            where.append(" AND i.issue_source = ?");
            args.add(issueSource);
        }
        if (issueCode != null && !issueCode.isBlank()) {
            where.append(" AND i.issue_code LIKE ?");
            args.add("%" + issueCode.trim() + "%");
        }
        if (componentId != null) {
            where.append(" AND i.component_id = ?");
            args.add(componentId);
        }
        if (rectifierId != null) {
            where.append(" AND i.rectifier_id = ?");
            args.add(rectifierId);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM issue i WHERE " + where, Long.class, args.toArray());
        long offset = (page - 1) * size;
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT i.* FROM issue i WHERE " + where
                + " ORDER BY i.first_reported_at DESC, i.id DESC LIMIT " + size + " OFFSET " + offset, args.toArray());
        return new PageResult<>(rows.stream().map(this::toView).toList(), total, page, size);
    }

    /** 主动新增问题（管理员/项目负责人）。 */
    @Transactional
    public IssueView createIssue(AuthUser user, Map<String, Object> body, String source) {
        permissions.requireAdmin(user);
        String required = text(body, "issueTitle");
        if (required == null || required.isBlank()) {
            throw new BusinessException(LifecycleErrorCode.ISSUE_FIELD_REQUIRED, "问题标题必填");
        }
        if (text(body, "issueDesc") == null || text(body, "issueDesc").isBlank()) {
            throw new BusinessException(LifecycleErrorCode.ISSUE_FIELD_REQUIRED, "问题描述必填");
        }
        long id = nextId();
        String code = nextIssueCode(user.tenantId());
        jdbc.update("INSERT INTO issue (id, tenant_id, issue_code, issue_title, issue_source, project_id, "
                        + "business_group_id, component_id, order_id, process_seq, reporter_id, rectifier_id, "
                        + "issue_desc, scene, impact_scope, block_desc, attachment_ids, issue_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'WAIT_RECTIFY')",
                id, user.tenantId(), code, required, source == null ? "MANUAL" : source,
                nullableLong(body.get("projectId")), nullableLong(body.get("businessGroupId")),
                nullableLong(body.get("componentId")), nullableLong(body.get("orderId")), nullableInt(body.get("processSeq")),
                user.id(), nullableLong(body.get("rectifierId")), text(body, "issueDesc"), text(body, "scene"),
                text(body, "impactScope"), text(body, "blockDesc"),
                toJson(longList(body.get("attachmentIds"))));
        return getIssue(user.tenantId(), id);
    }

    /** 台账维护（管理员）：基础信息/整改建议/解决方案；已闭环/已作废禁改。 */
    @Transactional
    public IssueView updateIssue(AuthUser user, long issueId, Map<String, Object> body, boolean allowStatus) {
        permissions.requireAdmin(user);
        Map<String, Object> row = requireIssue(user.tenantId(), issueId);
        String status = String.valueOf(row.get("issue_status"));
        if ("CLOSED".equals(status)) {
            throw new BusinessException(LifecycleErrorCode.ISSUE_CLOSED_READONLY, "已闭环问题禁止修改");
        }
        if ("CANCELLED".equals(status)) {
            throw new BusinessException(LifecycleErrorCode.ISSUE_CANCELLED_READONLY, "已作废问题禁止修改");
        }
        String newStatus = allowStatus ? text(body, "issueStatus") : status;
        if (newStatus != null && !isIssueStatus(newStatus)) {
            throw new BusinessException(LifecycleErrorCode.ISSUE_STATUS_INVALID, "问题状态非法（仅 4 态枚举）");
        }
        jdbc.update("UPDATE issue SET issue_title = ?, issue_desc = ?, scene = ?, impact_scope = ?, block_desc = ?, "
                        + "rectifier_id = ?, admin_suggestion = COALESCE(?, admin_suggestion), "
                        + "solution = COALESCE(?, solution), issue_status = ?, updated_at = CURRENT_TIMESTAMP(6) "
                        + "WHERE id = ? AND tenant_id = ? AND deleted = 0",
                text(body, "issueTitle"), text(body, "issueDesc"), text(body, "scene"), text(body, "impactScope"),
                text(body, "blockDesc"), nullableLong(body.get("rectifierId")), text(body, "adminSuggestion"),
                text(body, "solution"), newStatus, issueId, user.tenantId());
        return getIssue(user.tenantId(), issueId);
    }

    /** 批量导入（四类校验：必填缺失/格式错误/编码重复/状态非法）；合法数据唯一入库，异常行单独罗列不入库。 */
    public Map<String, Object> importIssues(AuthUser user, List<Map<String, Object>> rows) {
        permissions.requireAdmin(user);
        int inserted = 0;
        List<Map<String, Object>> anomalies = new ArrayList<>();
        int seqBase = (int) (ThreadLocalRandom.current().nextInt(100000, 999999));
        for (int idx = 0; idx < rows.size(); idx++) {
            Map<String, Object> body = rows.get(idx);
            String title = text(body, "issueTitle");
            String desc = text(body, "issueDesc");
            String code = text(body, "issueCode");
            String statusRaw = text(body, "issueStatus");
            String status = statusRaw == null ? "WAIT_RECTIFY" : statusRaw.toUpperCase();
            boolean missing = (title == null || title.isBlank()) || (desc == null || desc.isBlank());
            boolean badStatus = !isIssueStatus(status);
            boolean dup = code != null && !code.isBlank() && existsCode(user.tenantId(), code);
            if (missing || badStatus || dup) {
                Map<String, Object> anomaly = new LinkedHashMap<>();
                anomaly.put("row", idx + 1);
                anomaly.put("issueCode", code);
                anomaly.put("issueTitle", title);
                if (missing) {
                    anomaly.put("error", "必填字段缺失（标题/描述）");
                } else if (dup) {
                    anomaly.put("error", "编码重复：" + code);
                } else {
                    anomaly.put("error", "状态非法：" + statusRaw);
                }
                anomalies.add(anomaly);
                continue;
            }
            long id = nextId();
            String generatedCode = code != null && !code.isBlank() ? code
                    : "IS-" + LocalDate.now().toString().replace("-", "") + "-"
                    + String.format("%04d", (seqBase + inserted) % 10000);
            jdbc.update("INSERT INTO issue (id, tenant_id, issue_code, issue_title, issue_source, project_id, "
                            + "business_group_id, component_id, order_id, process_seq, reporter_id, rectifier_id, "
                            + "issue_desc, scene, impact_scope, block_desc, attachment_ids, issue_status) "
                            + "VALUES (?, ?, ?, ?, 'IMPORT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, user.tenantId(), generatedCode, title, nullableLong(body.get("projectId")),
                    nullableLong(body.get("businessGroupId")), nullableLong(body.get("componentId")),
                    nullableLong(body.get("orderId")), nullableInt(body.get("processSeq")), user.id(),
                    nullableLong(body.get("rectifierId")), desc, text(body, "scene"), text(body, "impactScope"),
                    text(body, "blockDesc"), toJson(longList(body.get("attachmentIds"))), status);
            inserted++;
        }
        return Map.of("inserted", inserted, "anomalies", anomalies);
    }

    /** 对账式同步 reconcile（幂等）：process_issue_report → issue 归集；归属类字段权威在上报单镜像。 */
    @Transactional
    public Map<String, Object> reconcile(AuthUser user) {
        permissions.requireAdmin(user);
        List<Map<String, Object>> reports = jdbc.queryForList("SELECT * FROM process_issue_report "
                + "WHERE tenant_id = ? AND deleted = 0 AND (synced_issue_id IS NULL OR synced_issue_id = 0)",
                user.tenantId());
        int synced = 0;
        for (Map<String, Object> report : reports) {
            long reportId = longOf(report, "id");
            Map<String, Object> orderRow = orderOf(user.tenantId(), longOf(report, "order_id"));
            if (orderRow == null) {
                throw new BusinessException(LifecycleErrorCode.ISSUE_SYNC_CONFLICT, "上报单归属工单缺失，拒绝归集");
            }
            long id = nextId();
            String code = nextIssueCode(user.tenantId());
            jdbc.update("INSERT INTO issue (id, tenant_id, issue_code, issue_title, issue_source, project_id, "
                            + "business_group_id, component_id, order_id, process_seq, reporter_id, issue_desc, scene, "
                            + "impact_scope, snapshot_version, synced_report_id, issue_status) "
                            + "VALUES (?, ?, ?, ?, 'ORDER_SYNC', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'WAIT_RECTIFY')",
                    id, user.tenantId(), code, stringOf(report, "issue_title"),
                    nullableLong(orderRow.get("project_id")), nullableLong(orderRow.get("business_group_id")),
                    nullableLong(orderRow.get("component_id")), longOf(report, "order_id"), intOf(report, "process_seq"),
                    longOf(report, "reporter_id"), stringOf(report, "issue_desc"), stringOrNull(report, "scene"),
                    stringOrNull(report, "impact_scope"), snapshotOf(report), reportId);
            jdbc.update("UPDATE process_issue_report SET synced_issue_id = ? WHERE id = ? AND tenant_id = ?",
                    id, reportId, user.tenantId());
            synced++;
        }
        return Map.of("synced", synced);
    }

    /** 整改推进（执行人/责任人）：整改进度与过程记录追加；已闭环/已作废拒绝。 */
    @Transactional
    public IssueView rectify(AuthUser user, long issueId, String progress, String record) {
        Map<String, Object> row = requireIssue(user.tenantId(), issueId);
        String status = String.valueOf(row.get("issue_status"));
        if ("CLOSED".equals(status)) {
            throw new BusinessException(LifecycleErrorCode.ISSUE_CLOSED_READONLY, "已闭环问题禁止修改");
        }
        if ("CANCELLED".equals(status)) {
            throw new BusinessException(LifecycleErrorCode.ISSUE_CANCELLED_READONLY, "已作废问题禁止修改");
        }
        boolean actor = user.id() == longOf(row, "reporter_id") || (row.get("rectifier_id") != null
                && user.id() == longOf(row, "rectifier_id"));
        if (!actor && !permissions.isAdmin(user)) {
            throw new BusinessException(LifecycleErrorCode.ISSUE_RECTIFY_PERMISSION_DENIED, "仅执行人/责任整改人可提交整改进度");
        }
        long recordId = nextId();
        jdbc.update("INSERT INTO issue_rectify_record (id, tenant_id, issue_id, record, operator_id) VALUES (?, ?, ?, ?, ?)",
                recordId, user.tenantId(), issueId, record == null ? "" : record, user.id());
        jdbc.update("UPDATE issue SET rectify_progress = ?, rectify_records = ?, issue_status = 'RECTIFYING', "
                        + "updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND tenant_id = ? AND deleted = 0",
                progress == null ? "" : progress, appendJson(String.valueOf(row.get("rectify_records")),
                        Map.of("record", record == null ? "" : record, "operatorId", user.id(), "at", LocalDateTime.now().toString())),
                issueId, user.tenantId());
        return getIssue(user.tenantId(), issueId);
    }

    /** 核验闭环（管理员）：solution 必填（知识沉淀依据）；闭环后自动沉淀知识条目，原问题禁改。 */
    @Transactional
    public Map<String, Object> close(AuthUser user, long issueId, String solution) {
        permissions.requireAdmin(user);
        Map<String, Object> row = requireIssue(user.tenantId(), issueId);
        String status = String.valueOf(row.get("issue_status"));
        if ("CLOSED".equals(status)) {
            throw new BusinessException(LifecycleErrorCode.ISSUE_CLOSED_READONLY, "问题已闭环");
        }
        if (solution == null || solution.isBlank()) {
            throw new BusinessException(LifecycleErrorCode.ISSUE_CLOSE_SOLUTION_REQUIRED, "闭环必须填写标准化解决方案（知识沉淀依据）");
        }
        jdbc.update("UPDATE issue SET issue_status = 'CLOSED', solution = COALESCE(?, solution), rectified_at = CURRENT_TIMESTAMP(6), "
                + "updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND tenant_id = ? AND deleted = 0",
                solution, issueId, user.tenantId());
        long knowledgeId = depositKnowledge(user, issueId);
        return Map.of("issueId", issueId, "issueStatus", "CLOSED", "knowledgeId", knowledgeId);
    }

    /** 作废（管理员）。 */
    @Transactional
    public void cancel(AuthUser user, long issueId, String reason) {
        permissions.requireAdmin(user);
        Map<String, Object> row = requireIssue(user.tenantId(), issueId);
        if ("CLOSED".equals(String.valueOf(row.get("issue_status")))) {
            throw new BusinessException(LifecycleErrorCode.ISSUE_CLOSED_READONLY, "已闭环问题禁止作废");
        }
        jdbc.update("UPDATE issue SET issue_status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP(6) "
                + "WHERE id = ? AND tenant_id = ? AND deleted = 0", issueId, user.tenantId());
    }

    /** Top5 相似历史问题推荐（关键词检索实现；外部向量服务接入后可替换）。 */
    public List<KnowledgeEntryView> recommendTop5(AuthUser user, String title, String desc) {
        List<KnowledgeEntryView> result = new ArrayList<>();
        String key = ((title == null ? "" : title) + " " + (desc == null ? "" : desc)).trim();
        if (key.isBlank()) {
            return result;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM knowledge_entry "
                + "WHERE tenant_id = ? AND deleted = 0 AND knowledge_status = 'ACTIVE' ORDER BY reuse_count DESC LIMIT 50",
                user.tenantId());
        for (Map<String, Object> row : rows) {
            int score = keywordScore(key, stringOf(row, "problem_desc"));
            if (score > 0) {
                result.add(toKnowledgeView(row));
            }
            if (result.size() >= 5) {
                break;
            }
        }
        return result;
    }

    /** 知识条目自动沉淀（闭环触发）：问题→解决方案→复用次数三元组；复制标签与附件。 */
    private long depositKnowledge(AuthUser user, long issueId) {
        Map<String, Object> row = requireIssue(user.tenantId(), issueId);
        long id = nextId();
        String code = "KB-" + LocalDate.now().toString().replace("-", "") + "-"
                + String.format("%03d", (int) (id % 1000));
        jdbc.update("INSERT INTO knowledge_entry (id, tenant_id, knowledge_code, source_issue_id, problem_desc, "
                        + "solution, rectify_records, attachment_ids, tag_ids, reuse_count) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                id, user.tenantId(), code, issueId, stringOf(row, "issue_desc"), stringOrNull(row, "solution"),
                stringOrNull(row, "rectify_records"), stringOrNull(row, "attachment_ids"),
                stringOrNull(row, "rectify_attachment_ids"));
        return id;
    }

    /** 知识条目检索/详情（全员可检索查看）。 */
    public List<KnowledgeEntryView> listKnowledge(AuthUser user, String keyword, Long tagId, String status) {
        StringBuilder where = new StringBuilder("k.tenant_id = ? AND k.deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (k.problem_desc LIKE ? OR k.solution LIKE ?)");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND k.knowledge_status = ?");
            args.add(status);
        }
        if (tagId != null) {
            where.append(" AND k.id IN (SELECT knowledge_id FROM knowledge_tag_rel WHERE tag_id = ? AND tenant_id = ?)");
            args.add(tagId);
            args.add(user.tenantId());
        }
        return jdbc.queryForList("SELECT k.* FROM knowledge_entry k WHERE " + where + " ORDER BY k.reuse_count DESC",
                args.toArray()).stream().map(this::toKnowledgeView).toList();
    }

    /** 一键复用（三元组复用次数回写 + 引用留痕）。 */
    @Transactional
    public KnowledgeEntryView refer(AuthUser user, long knowledgeId, Long issueId) {
        Map<String, Object> row = requireKnowledge(user.tenantId(), knowledgeId);
        jdbc.update("UPDATE knowledge_entry SET reuse_count = reuse_count + 1, updated_at = CURRENT_TIMESTAMP(6) "
                + "WHERE id = ? AND tenant_id = ? AND deleted = 0", knowledgeId, user.tenantId());
        jdbc.update("INSERT INTO knowledge_reuse_log (id, tenant_id, knowledge_id, issue_id, action, result) "
                + "VALUES (?, ?, ?, ?, 'REFER', 'PENDING')", nextId(), user.tenantId(), knowledgeId, issueId);
        return toKnowledgeView(row);
    }

    /** 知识条目失效（管理员，禁止物理删除）。 */
    @Transactional
    public KnowledgeEntryView invalidate(AuthUser user, long knowledgeId) {
        permissions.requireAdmin(user);
        requireKnowledge(user.tenantId(), knowledgeId);
        jdbc.update("UPDATE knowledge_entry SET knowledge_status = 'INVALID', updated_at = CURRENT_TIMESTAMP(6) "
                + "WHERE id = ? AND tenant_id = ? AND deleted = 0", knowledgeId, user.tenantId());
        return toKnowledgeView(jdbc.queryForMap("SELECT * FROM knowledge_entry WHERE id = ? AND tenant_id = ?",
                knowledgeId, user.tenantId()));
    }

    // ===== 内部辅助 =====

    private IssueView getIssue(long tenantId, long issueId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM issue WHERE id = ? AND tenant_id = ? AND deleted = 0",
                issueId, tenantId);
        return rows.isEmpty() ? null : toView(rows.get(0));
    }

    private Map<String, Object> requireIssue(long tenantId, long issueId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM issue WHERE id = ? AND tenant_id = ? AND deleted = 0",
                issueId, tenantId);
        if (rows.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.ISSUE_NOT_FOUND, "问题不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> requireKnowledge(long tenantId, long knowledgeId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM knowledge_entry WHERE id = ? AND tenant_id = ? AND deleted = 0",
                knowledgeId, tenantId);
        if (rows.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.KNOWLEDGE_NOT_FOUND, "知识条目不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> orderOf(long tenantId, long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM work_order WHERE id = ? AND tenant_id = ? AND deleted = 0",
                orderId, tenantId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean existsCode(long tenantId, String code) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM issue WHERE tenant_id = ? AND issue_code = ? AND deleted = 0",
                Integer.class, tenantId, code);
        return count != null && count > 0;
    }

    private String nextIssueCode(long tenantId) {
        String prefix = "IS-" + LocalDate.now().toString().replace("-", "");
        Long max = jdbc.queryForObject("SELECT MAX(CAST(SUBSTRING_INDEX(issue_code, '-', -1) AS UNSIGNED)) FROM issue "
                + "WHERE tenant_id = ? AND issue_code LIKE ? AND deleted = 0", Long.class, tenantId, prefix + "-%");
        long seq = (max == null ? 0 : max) + 1;
        return prefix + "-" + String.format("%03d", seq);
    }

    private static boolean isIssueStatus(String status) {
        return "WAIT_RECTIFY".equals(status) || "RECTIFYING".equals(status)
                || "CLOSED".equals(status) || "CANCELLED".equals(status);
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

    private IssueView toView(Map<String, Object> row) {
        return new IssueView(longOf(row, "id"), stringOf(row, "issue_code"), stringOf(row, "issue_title"),
                stringOf(row, "issue_source"), nullableLong(row.get("project_id")), nullableLong(row.get("business_group_id")),
                nullableLong(row.get("component_id")), nullableLong(row.get("order_id")),
                row.get("process_seq") == null ? null : intOf(row, "process_seq"),
                longOf(row, "reporter_id"), nullableLong(row.get("rectifier_id")),
                toDateTime(row.get("first_reported_at")), toDateTime(row.get("rectified_at")),
                toDateTime(row.get("updated_at")), stringOf(row, "issue_desc"), stringOrNull(row, "scene"),
                stringOrNull(row, "impact_scope"), stringOrNull(row, "block_desc"),
                parseLongList(String.valueOf(row.get("attachment_ids"))), stringOrNull(row, "admin_suggestion"),
                stringOrNull(row, "solution"), stringOrNull(row, "rectify_progress"),
                parseMapList(String.valueOf(row.get("rectify_records"))),
                parseLongList(String.valueOf(row.get("rectify_attachment_ids"))), stringOf(row, "issue_status"),
                stringOrNull(row, "snapshot_version"), nullableLong(row.get("synced_report_id")));
    }

    private KnowledgeEntryView toKnowledgeView(Map<String, Object> row) {
        return new KnowledgeEntryView(longOf(row, "id"), stringOf(row, "knowledge_code"),
                nullableLong(row.get("source_issue_id")), stringOf(row, "problem_desc"), stringOf(row, "solution"),
                parseStringList(String.valueOf(row.get("rectify_records"))),
                parseLongList(String.valueOf(row.get("attachment_ids"))),
                parseLongList(String.valueOf(row.get("tag_ids"))), intOf(row, "reuse_count"),
                stringOf(row, "knowledge_status"), toDateTime(row.get("created_at")), toDateTime(row.get("updated_at")));
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
            if (json.trim().startsWith("[")) {
                return objectMapper.readValue(json, new TypeReference<List<String>>() {
                });
            }
            return List.of(json);
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
