package com.ccb.architecture.decision.persistence;

import com.ccb.architecture.decision.model.DecisionModels.ActionItem;
import com.ccb.architecture.decision.model.DecisionModels.ActionItemInput;
import com.ccb.architecture.decision.model.DecisionModels.ActionItemStatus;
import com.ccb.architecture.decision.model.DecisionModels.Conclusion;
import com.ccb.architecture.decision.model.DecisionModels.ConclusionEffectiveStatus;
import com.ccb.architecture.decision.model.DecisionModels.DecisionMatter;
import com.ccb.architecture.decision.model.DecisionModels.FirstHandlingOutcome;
import com.ccb.architecture.decision.model.DecisionModels.MaterialRecord;
import com.ccb.architecture.decision.model.DecisionModels.MatterQuery;
import com.ccb.architecture.decision.model.DecisionModels.MatterStatus;
import com.ccb.architecture.decision.model.DecisionModels.PublicationIntent;
import com.ccb.architecture.decision.model.DecisionModels.ReviewMethod;
import com.ccb.architecture.decision.model.DecisionModels.ReviewRecord;
import com.ccb.architecture.decision.model.DecisionModels.Supersession;
import com.ccb.architecture.decision.model.DecisionModels.SupersessionKind;
import com.ccb.architecture.decision.model.DecisionModels.SupersessionTarget;
import com.ccb.architecture.decision.model.DecisionModels.WorkflowReceiptStart;
import com.ccb.architecture.decision.model.DecisionModels.WorkflowReceiptStatus;
import com.ccb.architecture.decision.model.DecisionModels.WorkflowRound;
import com.ccb.architecture.decision.model.DecisionModels.WorkflowRoundStatus;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 架构决策事项数据访问。
 *
 * <p>编号分配通过租户年份序列行锁保证并发不重号；结论发布（结论行 + 替代关系 +
 * 事项完成）必须在工作流 APPROVED 事件同一事务内执行。</p>
 */
@Repository
public class DecisionStore {

    private static final String MATTER_COLUMNS = """
            id, tenant_id, matter_no, title, problem, type_code, status, received_at,
            first_handling_deadline, first_handling_outcome, first_handling_comment,
            first_handled_at, first_handler_id, first_handler_name, review_mode,
            proposer_id, proposer_name, submitter_id, submitter_name,
            publication_prepared_at, publication_prepared_by,
            current_business_round, current_workflow_definition_id, current_workflow_version_id,
            current_workflow_instance_id, current_payload_digest, row_version,
            created_by, created_by_name, created_at, updated_at
            """;
    private static final String MATERIAL_COLUMNS = """
            id, tenant_id, matter_id, kind, content, created_by, created_by_name, created_at
            """;
    private static final String REVIEW_COLUMNS = """
            id, tenant_id, matter_id, review_no, method, reviewed_at, process_material_summary,
            key_opinion, conclusion_content, conclusion_rationale, created_by, created_by_name,
            created_at, updated_at
            """;
    private static final String ACTION_COLUMNS = """
            id, tenant_id, review_id, content, owner_user_id, owner_name, status,
            created_by, created_by_name, created_at, updated_at
            """;
    private static final String CONCLUSION_COLUMNS = """
            id, tenant_id, matter_id, review_id, content, rationale, published_at,
            published_by, published_by_name, created_at
            """;
    private static final String SUPERSESSION_COLUMNS = """
            id, tenant_id, conclusion_id, superseded_conclusion_id, kind, created_at
            """;

    private static final RowMapper<DecisionMatter> MATTER_MAPPER = (rs, rowNum) -> new DecisionMatter(
            rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("matter_no"),
            rs.getString("title"), rs.getString("problem"), rs.getString("type_code"),
            MatterStatus.valueOf(rs.getString("status")), localDateTime(rs.getTimestamp("received_at")),
            rs.getDate("first_handling_deadline").toLocalDate(),
            nullableEnum(rs, "first_handling_outcome", FirstHandlingOutcome.class),
            rs.getString("first_handling_comment"), localDateTime(rs.getTimestamp("first_handled_at")),
            nullableLong(rs, "first_handler_id"), rs.getString("first_handler_name"),
            nullableEnum(rs, "review_mode", ReviewMethod.class),
            rs.getLong("proposer_id"), rs.getString("proposer_name"),
            rs.getLong("submitter_id"), rs.getString("submitter_name"),
            localDateTime(rs.getTimestamp("publication_prepared_at")), nullableLong(rs, "publication_prepared_by"),
            rs.getInt("current_business_round"), nullableLong(rs, "current_workflow_definition_id"),
            nullableLong(rs, "current_workflow_version_id"), nullableLong(rs, "current_workflow_instance_id"),
            rs.getString("current_payload_digest"), rs.getLong("row_version"),
            rs.getLong("created_by"), rs.getString("created_by_name"),
            localDateTime(rs.getTimestamp("created_at")), localDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<MaterialRecord> MATERIAL_MAPPER = (rs, rowNum) -> new MaterialRecord(
            rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("matter_id"),
            com.ccb.architecture.decision.model.DecisionModels.MaterialKind.valueOf(rs.getString("kind")),
            rs.getString("content"), rs.getLong("created_by"), rs.getString("created_by_name"),
            localDateTime(rs.getTimestamp("created_at")));

    private static final RowMapper<ReviewRecord> REVIEW_MAPPER = (rs, rowNum) -> new ReviewRecord(
            rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("matter_id"),
            rs.getInt("review_no"), ReviewMethod.valueOf(rs.getString("method")),
            localDateTime(rs.getTimestamp("reviewed_at")), rs.getString("process_material_summary"),
            rs.getString("key_opinion"), rs.getString("conclusion_content"), rs.getString("conclusion_rationale"),
            rs.getLong("created_by"), rs.getString("created_by_name"),
            localDateTime(rs.getTimestamp("created_at")), localDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<ActionItem> ACTION_MAPPER = (rs, rowNum) -> new ActionItem(
            rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("review_id"),
            rs.getString("content"), nullableLong(rs, "owner_user_id"), rs.getString("owner_name"),
            ActionItemStatus.valueOf(rs.getString("status")),
            rs.getLong("created_by"), rs.getString("created_by_name"),
            localDateTime(rs.getTimestamp("created_at")), localDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<Conclusion> CONCLUSION_MAPPER = (rs, rowNum) -> new Conclusion(
            rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("matter_id"), rs.getLong("review_id"),
            rs.getString("content"), rs.getString("rationale"),
            localDateTime(rs.getTimestamp("published_at")), rs.getLong("published_by"),
            rs.getString("published_by_name"), localDateTime(rs.getTimestamp("created_at")));

    private static final RowMapper<Supersession> SUPERSESSION_MAPPER = (rs, rowNum) -> new Supersession(
            rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("conclusion_id"),
            rs.getLong("superseded_conclusion_id"),
            SupersessionKind.valueOf(rs.getString("kind")), localDateTime(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DecisionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- 事项 ----------

    public PageResult<DecisionMatter> pageMatters(long tenantId, PageQuery page, MatterQuery query) {
        PageQuery normalizedPage = page == null ? new PageQuery(1, 20) : page;
        MatterQuery normalized = query == null ? MatterQuery.empty() : query;
        StringBuilder filter = new StringBuilder();
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (normalized.keyword() != null && !normalized.keyword().isBlank()) {
            filter.append(" AND (matter_no LIKE ? OR title LIKE ?)");
            String like = "%" + normalized.keyword().trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (normalized.typeCode() != null) {
            filter.append(" AND type_code = ?");
            args.add(normalized.typeCode());
        }
        if (normalized.status() != null) {
            filter.append(" AND status = ?");
            args.add(normalized.status());
        }
        if (normalized.proposerId() != null) {
            filter.append(" AND proposer_id = ?");
            args.add(normalized.proposerId());
        }
        if (Boolean.TRUE.equals(normalized.firstHandlingOverdue())) {
            filter.append(" AND status IN ('SUBMITTED', 'RETURNED_FOR_INFO') AND first_handling_deadline < CURDATE()");
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM arch_decision_matter WHERE tenant_id = ? AND deleted = 0" + filter,
                Long.class, args.toArray());
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(normalizedPage.size());
        listArgs.add((normalizedPage.page() - 1) * normalizedPage.size());
        List<DecisionMatter> records = jdbc.query(
                "SELECT " + MATTER_COLUMNS + " FROM arch_decision_matter"
                        + " WHERE tenant_id = ? AND deleted = 0" + filter
                        + " ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?",
                MATTER_MAPPER, listArgs.toArray());
        return new PageResult<>(records, total == null ? 0 : total, normalizedPage.page(), normalizedPage.size());
    }

    public Optional<DecisionMatter> findMatter(long tenantId, long id) {
        return jdbc.query("SELECT " + MATTER_COLUMNS + " FROM arch_decision_matter"
                        + " WHERE tenant_id = ? AND id = ? AND deleted = 0",
                MATTER_MAPPER, tenantId, id).stream().findFirst();
    }

    public Optional<DecisionMatter> lockMatter(long tenantId, long id, long expectedRowVersion) {
        List<DecisionMatter> rows = jdbc.query(
                "SELECT " + MATTER_COLUMNS + " FROM arch_decision_matter"
                        + " WHERE tenant_id = ? AND id = ? AND deleted = 0 AND row_version = ? FOR UPDATE",
                MATTER_MAPPER, tenantId, id, expectedRowVersion);
        return rows.stream().findFirst();
    }

    public Optional<DecisionMatter> lockMatter(long tenantId, long id) {
        List<DecisionMatter> rows = jdbc.query(
                "SELECT " + MATTER_COLUMNS + " FROM arch_decision_matter"
                        + " WHERE tenant_id = ? AND id = ? AND deleted = 0 FOR UPDATE",
                MATTER_MAPPER, tenantId, id);
        return rows.stream().findFirst();
    }

    /** 分配租户内年度序号：行锁 + 唯一键保证并发不重号、不复用。 */
    public int allocateMatterOrdinal(long tenantId, int year) {
        jdbc.update("""
                INSERT INTO arch_decision_number_sequence (tenant_id, seq_year, next_ordinal)
                VALUES (?, ?, 1)
                ON DUPLICATE KEY UPDATE next_ordinal = next_ordinal
                """, tenantId, year);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT next_ordinal FROM arch_decision_number_sequence"
                        + " WHERE tenant_id = ? AND seq_year = ? FOR UPDATE",
                tenantId, year);
        if (rows.isEmpty()) {
            throw new IllegalStateException("事项编号序列不可用");
        }
        int next = ((Number) rows.get(0).get("next_ordinal")).intValue();
        if (next > 9999) {
            throw new IllegalStateException("事项编号年度容量已耗尽");
        }
        jdbc.update("UPDATE arch_decision_number_sequence SET next_ordinal = ?"
                        + " WHERE tenant_id = ? AND seq_year = ?",
                next + 1, tenantId, year);
        return next;
    }

    public long createMatter(DecisionMatter matter) {
        jdbc.update("""
                INSERT INTO arch_decision_matter
                    (id, tenant_id, matter_no, title, problem, type_code, status, received_at,
                     first_handling_deadline, proposer_id, proposer_name, submitter_id, submitter_name,
                     row_version, created_by, created_by_name, updated_by)
                VALUES (?, ?, ?, ?, ?, NULL, 'SUBMITTED', ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)
                """, matter.id(), matter.tenantId(), matter.matterNo(), matter.title(), matter.problem(),
                Timestamp.valueOf(matter.receivedAt()), java.sql.Date.valueOf(matter.firstHandlingDeadline()),
                matter.proposerId(), matter.proposerName(), matter.submitterId(), matter.submitterName(),
                matter.createdBy(), matter.createdByName(), matter.submitterId());
        return matter.id();
    }

    public void updateMatter(long tenantId, long id, long expectedRowVersion, String title, String problem,
                             long operatorId) {
        int updated = jdbc.update("""
                UPDATE arch_decision_matter
                SET title = ?, problem = ?, row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND deleted = 0 AND row_version = ?
                """, title, problem, operatorId, tenantId, id, expectedRowVersion);
        if (updated != 1) {
            throw new IllegalStateException("事项行版本冲突");
        }
    }

    /** 首次处理：受理/要求补充/确定评审方式，并记录处理人、时间与意见。 */
    public void applyFirstHandling(long tenantId, long id, long expectedRowVersion,
                                   FirstHandlingOutcome outcome, String comment, ReviewMethod reviewMode,
                                   long handlerId, String handlerName) {
        String status = outcome == FirstHandlingOutcome.REQUESTED_INFO ? "RETURNED_FOR_INFO" : "IN_REVIEW";
        String reviewModeSql = reviewMode == null ? "NULL" : "?";
        List<Object> args = new ArrayList<>();
        args.add(status);
        args.add(outcome.name());
        args.add(comment);
        if (reviewMode != null) {
            args.add(reviewMode.name());
        }
        args.add(handlerId);
        args.add(handlerName);
        args.add(handlerId);
        args.add(tenantId);
        args.add(id);
        args.add(expectedRowVersion);
        int updated = jdbc.update("""
                UPDATE arch_decision_matter
                SET status = ?, first_handling_outcome = ?, first_handling_comment = ?,
                    review_mode = %s, first_handled_at = NOW(3), first_handler_id = ?,
                    first_handler_name = ?, row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND deleted = 0 AND row_version = ?
                """.formatted(reviewModeSql), args.toArray());
        if (updated != 1) {
            throw new IllegalStateException("事项首次处理状态或行版本冲突");
        }
    }

    /** 补充后重新提交：重置受理时间、期限与首次处理字段，回到 SUBMITTED。 */
    public void resubmit(long tenantId, long id, long expectedRowVersion, LocalDateTime receivedAt,
                         LocalDate deadline, long submitterId, String submitterName) {
        int updated = jdbc.update("""
                UPDATE arch_decision_matter
                SET status = 'SUBMITTED', received_at = ?, first_handling_deadline = ?,
                    first_handling_outcome = NULL, first_handling_comment = NULL,
                    first_handled_at = NULL, first_handler_id = NULL, first_handler_name = NULL,
                    review_mode = NULL, submitter_id = ?, submitter_name = ?,
                    row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND deleted = 0 AND row_version = ?
                """, Timestamp.valueOf(receivedAt), java.sql.Date.valueOf(deadline),
                submitterId, submitterName, submitterId, tenantId, id, expectedRowVersion);
        if (updated != 1) {
            throw new IllegalStateException("事项重提状态或行版本冲突");
        }
    }

    public void setMatterType(long tenantId, long id, long expectedRowVersion, String typeCode, long operatorId) {
        int updated = jdbc.update("""
                UPDATE arch_decision_matter
                SET type_code = ?, row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND deleted = 0 AND row_version = ?
                """, typeCode, operatorId, tenantId, id, expectedRowVersion);
        if (updated != 1) {
            throw new IllegalStateException("事项类型或行版本冲突");
        }
    }

    public int touchPublicationPreparation(long tenantId, long id, long expectedRowVersion, long operatorId) {
        return jdbc.update("""
                UPDATE arch_decision_matter
                SET publication_prepared_at = NOW(3), publication_prepared_by = ?,
                    row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND deleted = 0 AND row_version = ?
                """, operatorId, operatorId, tenantId, id, expectedRowVersion);
    }

    public long addMaterial(MaterialRecord record) {
        jdbc.update("""
                INSERT INTO arch_decision_material
                    (id, tenant_id, matter_id, kind, content, created_by, created_by_name)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, record.id(), record.tenantId(), record.matterId(), record.kind().name(),
                record.content(), record.createdBy(), record.createdByName());
        return record.id();
    }

    public List<MaterialRecord> listMaterials(long tenantId, long matterId) {
        return jdbc.query("SELECT " + MATERIAL_COLUMNS + " FROM arch_decision_material"
                        + " WHERE tenant_id = ? AND matter_id = ? ORDER BY created_at ASC, id ASC",
                MATERIAL_MAPPER, tenantId, matterId);
    }

    // ---------- 评审、参与人与行动项 ----------

    public long insertReview(ReviewRecord review) {
        jdbc.update("""
                INSERT INTO arch_decision_review
                    (id, tenant_id, matter_id, review_no, method, reviewed_at, process_material_summary,
                     key_opinion, conclusion_content, conclusion_rationale, created_by, created_by_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, review.id(), review.tenantId(), review.matterId(), review.reviewNo(),
                review.method().name(), Timestamp.valueOf(review.reviewedAt()),
                review.processMaterialSummary(), review.keyOpinion(), review.conclusionContent(),
                review.conclusionRationale(), review.createdBy(), review.createdByName());
        return review.id();
    }

    public int nextReviewNo(long tenantId, long matterId) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(review_no), 0) FROM arch_decision_review"
                        + " WHERE tenant_id = ? AND matter_id = ?",
                Integer.class, tenantId, matterId);
        return max == null ? 1 : max + 1;
    }

    public Optional<ReviewRecord> findReview(long tenantId, long matterId, long reviewId) {
        return jdbc.query("SELECT " + REVIEW_COLUMNS + " FROM arch_decision_review"
                        + " WHERE tenant_id = ? AND matter_id = ? AND id = ?",
                REVIEW_MAPPER, tenantId, matterId, reviewId).stream().findFirst();
    }

    public List<ReviewRecord> listReviews(long tenantId, long matterId) {
        return jdbc.query("SELECT " + REVIEW_COLUMNS + " FROM arch_decision_review"
                        + " WHERE tenant_id = ? AND matter_id = ? ORDER BY review_no ASC, id ASC",
                REVIEW_MAPPER, tenantId, matterId);
    }

    public void updateReview(ReviewRecord review) {
        jdbc.update("""
                UPDATE arch_decision_review
                SET method = ?, reviewed_at = ?, process_material_summary = ?, key_opinion = ?,
                    conclusion_content = ?, conclusion_rationale = ?
                WHERE tenant_id = ? AND matter_id = ? AND id = ?
                """, review.method().name(), Timestamp.valueOf(review.reviewedAt()),
                review.processMaterialSummary(), review.keyOpinion(), review.conclusionContent(),
                review.conclusionRationale(), review.tenantId(), review.matterId(), review.id());
    }

    public void replaceParticipants(long tenantId, long reviewId, List<Long> userIds,
                                     java.util.Map<Long, String> displayNames) {
        jdbc.update("DELETE FROM arch_decision_review_participant WHERE tenant_id = ? AND review_id = ?",
                tenantId, reviewId);
        long id = System.currentTimeMillis() * 1_000;
        int ordinal = 0;
        for (Long userId : userIds) {
            if (userId == null || userId <= 0) {
                continue;
            }
            String name = displayNames == null ? "" : displayNames.getOrDefault(userId, "");
            jdbc.update("""
                    INSERT INTO arch_decision_review_participant
                        (id, tenant_id, review_id, user_id, user_name)
                    VALUES (?, ?, ?, ?, ?)
                    """, id + ordinal, tenantId, reviewId, userId, name);
            ordinal++;
        }
    }

    public List<Long> listParticipantIds(long tenantId, long reviewId) {
        return jdbc.queryForList(
                "SELECT user_id FROM arch_decision_review_participant"
                        + " WHERE tenant_id = ? AND review_id = ? ORDER BY id ASC",
                Long.class, tenantId, reviewId);
    }

    public List<Map<String, Object>> listParticipants(long tenantId, long reviewId) {
        return jdbc.queryForList("SELECT user_id, user_name FROM arch_decision_review_participant"
                + " WHERE tenant_id = ? AND review_id = ? ORDER BY id ASC", tenantId, reviewId);
    }

    /** 行动项全量合并：按 id 更新既有项、插入新项、删除不再出现的项。 */
    public void replaceActionItems(long tenantId, long reviewId, List<ActionItemInput> inputs, long operatorId) {
        List<ActionItem> existing = listActionItems(tenantId, reviewId);
        Map<Long, ActionItem> byId = new LinkedHashMap<>();
        for (ActionItem item : existing) {
            byId.put(item.id(), item);
        }
        java.util.Set<Long> retained = new java.util.LinkedHashSet<>();
        long id = System.currentTimeMillis() * 1_000;
        int ordinal = 0;
        for (ActionItemInput input : inputs == null ? List.<ActionItemInput>of() : inputs) {
            if (input == null || input.content() == null || input.content().isBlank()) {
                continue;
            }
            ActionItem existingItem = input.id() == null ? null : byId.get(input.id());
            if (existingItem != null) {
                retained.add(existingItem.id());
                jdbc.update("""
                        UPDATE arch_decision_action_item
                        SET content = ?, owner_user_id = ?, owner_name = ?
                        WHERE tenant_id = ? AND review_id = ? AND id = ?
                        """, input.content().trim(), input.ownerUserId(), input.ownerName(),
                        tenantId, reviewId, existingItem.id());
            } else {
                jdbc.update("""
                        INSERT INTO arch_decision_action_item
                            (id, tenant_id, review_id, content, owner_user_id, owner_name, status,
                             created_by, created_by_name)
                        VALUES (?, ?, ?, ?, ?, ?, 'OPEN', ?, ?)
                        """, id + ordinal, tenantId, reviewId, input.content().trim(),
                        input.ownerUserId(), input.ownerName(), operatorId, "");
                retained.add(id + ordinal);
                ordinal++;
            }
        }
        for (ActionItem item : existing) {
            if (!retained.contains(item.id())) {
                jdbc.update("DELETE FROM arch_decision_action_item"
                        + " WHERE tenant_id = ? AND review_id = ? AND id = ?", tenantId, reviewId, item.id());
            }
        }
    }

    public List<ActionItem> listActionItems(long tenantId, long reviewId) {
        return jdbc.query("SELECT " + ACTION_COLUMNS + " FROM arch_decision_action_item"
                        + " WHERE tenant_id = ? AND review_id = ? ORDER BY id ASC",
                ACTION_MAPPER, tenantId, reviewId);
    }

    public Optional<ActionItem> findActionItem(long tenantId, long reviewId, long actionItemId) {
        return jdbc.query("SELECT " + ACTION_COLUMNS + " FROM arch_decision_action_item"
                        + " WHERE tenant_id = ? AND review_id = ? AND id = ?",
                ACTION_MAPPER, tenantId, reviewId, actionItemId).stream().findFirst();
    }

    public void completeActionItem(long tenantId, long reviewId, long actionItemId, long operatorId) {
        int updated = jdbc.update("""
                UPDATE arch_decision_action_item
                SET status = 'DONE'
                WHERE tenant_id = ? AND review_id = ? AND id = ? AND status = 'OPEN'
                """, tenantId, reviewId, actionItemId);
        if (updated != 1) {
            throw new IllegalStateException("行动项不存在或已完成");
        }
    }

    // ---------- 发布准备与结论 ----------

    public void upsertPublicationIntent(PublicationIntent intent) {
        try {
            String targetsJson = objectMapper.writeValueAsString(intent.targets() == null
                    ? List.of() : intent.targets());
            jdbc.update("""
                    INSERT INTO arch_decision_publication_intent
                        (matter_id, tenant_id, review_id, supersession_targets_json, payload_digest,
                         prepared_by, prepared_by_name, prepared_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        review_id = VALUES(review_id),
                        supersession_targets_json = VALUES(supersession_targets_json),
                        payload_digest = VALUES(payload_digest),
                        prepared_by = VALUES(prepared_by),
                        prepared_by_name = VALUES(prepared_by_name),
                        prepared_at = VALUES(prepared_at)
                    """, intent.matterId(), intent.tenantId(), intent.reviewId(), targetsJson,
                    intent.payloadDigest(), intent.preparedBy(), intent.preparedByName(),
                    Timestamp.valueOf(intent.preparedAt()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("发布准备目标序列化失败", exception);
        }
    }

    public Optional<PublicationIntent> findPublicationIntent(long tenantId, long matterId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT matter_id, tenant_id, review_id, supersession_targets_json, payload_digest,"
                        + " prepared_by, prepared_by_name, prepared_at"
                        + " FROM arch_decision_publication_intent WHERE tenant_id = ? AND matter_id = ?",
                tenantId, matterId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        try {
            List<SupersessionTarget> targets = objectMapper.readValue(
                    String.valueOf(row.get("supersession_targets_json") == null ? "[]"
                            : row.get("supersession_targets_json")),
                    new TypeReference<List<SupersessionTarget>>() {
                    });
            return Optional.of(new PublicationIntent(
                    ((Number) row.get("matter_id")).longValue(), ((Number) row.get("tenant_id")).longValue(),
                    ((Number) row.get("review_id")).longValue(), targets,
                    String.valueOf(row.get("payload_digest")), ((Number) row.get("prepared_by")).longValue(),
                    String.valueOf(row.get("prepared_by_name")),
                    localDateTime((Timestamp) row.get("prepared_at"))));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("发布准备目标解析失败", exception);
        }
    }

    public Optional<Conclusion> findConclusion(long tenantId, long matterId) {
        return jdbc.query("SELECT " + CONCLUSION_COLUMNS + " FROM arch_decision_conclusion"
                        + " WHERE tenant_id = ? AND matter_id = ?",
                CONCLUSION_MAPPER, tenantId, matterId).stream().findFirst();
    }

    public Optional<Conclusion> findConclusionById(long tenantId, long conclusionId) {
        return jdbc.query("SELECT " + CONCLUSION_COLUMNS + " FROM arch_decision_conclusion"
                        + " WHERE tenant_id = ? AND id = ?",
                CONCLUSION_MAPPER, tenantId, conclusionId).stream().findFirst();
    }

    public PageResult<Conclusion> pageConclusions(long tenantId, PageQuery page, String effectiveStatus) {
        PageQuery normalizedPage = page == null ? new PageQuery(1, 20) : page;
        StringBuilder filter = new StringBuilder();
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (effectiveStatus != null) {
            switch (effectiveStatus) {
                case "EFFECTIVE" -> filter.append("""
                         AND NOT EXISTS (SELECT 1 FROM arch_decision_supersession s
                                          WHERE s.tenant_id = c.tenant_id AND s.superseded_conclusion_id = c.id)
                        """);
                case "SUPERSEDED" -> filter.append("""
                         AND EXISTS (SELECT 1 FROM arch_decision_supersession s
                                      WHERE s.tenant_id = c.tenant_id AND s.superseded_conclusion_id = c.id
                                        AND s.kind = 'SUPERSEDE')
                        """);
                case "PARTIALLY_SUPERSEDED" -> filter.append("""
                         AND NOT EXISTS (SELECT 1 FROM arch_decision_supersession s
                                          WHERE s.tenant_id = c.tenant_id AND s.superseded_conclusion_id = c.id
                                            AND s.kind = 'SUPERSEDE')
                         AND EXISTS (SELECT 1 FROM arch_decision_supersession s
                                      WHERE s.tenant_id = c.tenant_id AND s.superseded_conclusion_id = c.id
                                        AND s.kind = 'PARTIALLY_REVISE')
                        """);
                default -> throw new IllegalArgumentException("结论有效状态仅支持 EFFECTIVE、SUPERSEDED、PARTIALLY_SUPERSEDED");
            }
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM arch_decision_conclusion c WHERE c.tenant_id = ?" + filter,
                Long.class, args.toArray());
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(normalizedPage.size());
        listArgs.add((normalizedPage.page() - 1) * normalizedPage.size());
        List<Conclusion> records = jdbc.query(
                "SELECT " + CONCLUSION_COLUMNS
                        + " FROM arch_decision_conclusion c WHERE c.tenant_id = ?" + filter
                        + " ORDER BY c.published_at DESC, c.id DESC LIMIT ? OFFSET ?",
                CONCLUSION_MAPPER, listArgs.toArray());
        return new PageResult<>(records, total == null ? 0 : total, normalizedPage.page(), normalizedPage.size());
    }

    /** 结论有效状态推导：被替代 > 仅部分修订 > 有效。 */
    public ConclusionEffectiveStatus conclusionEffectiveStatus(long tenantId, long conclusionId) {
        List<String> superseded = jdbc.queryForList("""
                SELECT kind FROM arch_decision_supersession
                WHERE tenant_id = ? AND superseded_conclusion_id = ?
                """, String.class, tenantId, conclusionId);
        if (superseded.contains(SupersessionKind.SUPERSEDE.name())) {
            return ConclusionEffectiveStatus.SUPERSEDED;
        }
        if (!superseded.isEmpty()) {
            return ConclusionEffectiveStatus.PARTIALLY_SUPERSEDED;
        }
        return ConclusionEffectiveStatus.EFFECTIVE;
    }

    /** 该结论替代/部分修订了哪些结论（出边）。 */
    public List<Supersession> listSupersedes(long tenantId, long conclusionId) {
        return jdbc.query("SELECT " + SUPERSESSION_COLUMNS + " FROM arch_decision_supersession"
                        + " WHERE tenant_id = ? AND conclusion_id = ? ORDER BY id ASC",
                SUPERSESSION_MAPPER, tenantId, conclusionId);
    }

    /** 该结论被哪些结论替代/部分修订（入边）。 */
    public List<Supersession> listSupersededBy(long tenantId, long conclusionId) {
        return jdbc.query("SELECT " + SUPERSESSION_COLUMNS + " FROM arch_decision_supersession"
                        + " WHERE tenant_id = ? AND superseded_conclusion_id = ? ORDER BY id ASC",
                SUPERSESSION_MAPPER, tenantId, conclusionId);
    }

    public long insertConclusion(Conclusion conclusion) {
        jdbc.update("""
                INSERT INTO arch_decision_conclusion
                    (id, tenant_id, matter_id, review_id, content, rationale, published_at,
                     published_by, published_by_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, conclusion.id(), conclusion.tenantId(), conclusion.matterId(), conclusion.reviewId(),
                conclusion.content(), conclusion.rationale(), Timestamp.valueOf(conclusion.publishedAt()),
                conclusion.publishedBy(), conclusion.publishedByName());
        return conclusion.id();
    }

    public void insertSupersession(Supersession supersession) {
        jdbc.update("""
                INSERT INTO arch_decision_supersession
                    (id, tenant_id, conclusion_id, superseded_conclusion_id, kind)
                VALUES (?, ?, ?, ?, ?)
                """, supersession.id(), supersession.tenantId(), supersession.conclusionId(),
                supersession.supersededConclusionId(), supersession.kind().name());
    }

    public void markMatterPublished(long tenantId, long matterId, long expectedRowVersion) {
        int updated = jdbc.update("""
                UPDATE arch_decision_matter
                SET status = 'PUBLISHED', row_version = row_version + 1
                WHERE tenant_id = ? AND id = ? AND deleted = 0 AND row_version = ?
                """, tenantId, matterId, expectedRowVersion);
        if (updated != 1) {
            throw new IllegalStateException("事项状态或行版本冲突");
        }
    }

    // ---------- 工作流轮次与回执 ----------

    public void insertPendingWorkflowRound(WorkflowRound round) {
        jdbc.update("""
                INSERT INTO arch_decision_workflow_round
                    (id, tenant_id, matter_id, round_no, status, created_at)
                VALUES (?, ?, ?, ?, 'PENDING', NOW(3))
                """, round.id(), round.tenantId(), round.matterId(), round.roundNo());
    }

    public boolean bindWorkflowRoundStarted(long tenantId, long matterId, int roundNo,
                                            long definitionId, int definitionVersion, long instanceId,
                                            String digest, LocalDateTime startedAt) {
        return jdbc.update("""
                UPDATE arch_decision_workflow_round
                SET workflow_definition_id = ?, workflow_version_id = ?, workflow_instance_id = ?,
                    payload_digest = ?, status = 'STARTED', started_at = ?
                WHERE tenant_id = ? AND matter_id = ? AND round_no = ? AND status = 'PENDING'
                """, definitionId, definitionVersion, instanceId, digest, Timestamp.valueOf(startedAt),
                tenantId, matterId, roundNo) == 1;
    }

    public boolean compareAndSetMatterWorkflowContext(long tenantId, long matterId, int currentRound,
                                                      long currentRowVersion, int nextRound,
                                                      long definitionId, int definitionVersion,
                                                      long instanceId, String digest, long operatorId) {
        return jdbc.update("""
                UPDATE arch_decision_matter
                SET current_business_round = ?, current_workflow_definition_id = ?,
                    current_workflow_version_id = ?, current_workflow_instance_id = ?,
                    current_payload_digest = ?, row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND deleted = 0
                  AND current_business_round = ? AND row_version = ?
                """, nextRound, definitionId, definitionVersion, instanceId, digest, operatorId,
                tenantId, matterId, currentRound, currentRowVersion) == 1;
    }

    public Optional<WorkflowRound> lockWorkflowRoundByInstance(long tenantId, long instanceId) {
        List<WorkflowRound> rows = jdbc.query("""
                SELECT id, tenant_id, matter_id, round_no, workflow_definition_id, workflow_version_id,
                       workflow_instance_id, payload_digest, status, started_at, ended_at, created_at, updated_at
                FROM arch_decision_workflow_round
                WHERE tenant_id = ? AND workflow_instance_id = ? FOR UPDATE
                """, (rs, rowNum) -> new WorkflowRound(
                rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("matter_id"), rs.getInt("round_no"),
                nullableLong(rs, "workflow_definition_id"), nullableLong(rs, "workflow_version_id"),
                nullableLong(rs, "workflow_instance_id"), rs.getString("payload_digest"),
                WorkflowRoundStatus.valueOf(rs.getString("status")),
                localDateTime(rs.getTimestamp("started_at")), localDateTime(rs.getTimestamp("ended_at")),
                localDateTime(rs.getTimestamp("created_at")), localDateTime(rs.getTimestamp("updated_at"))),
                tenantId, instanceId);
        return rows.stream().findFirst();
    }

    public boolean isLatestWorkflowRound(long tenantId, long matterId, int roundNo) {
        Integer max = jdbc.queryForObject(
                "SELECT MAX(round_no) FROM arch_decision_workflow_round"
                        + " WHERE tenant_id = ? AND matter_id = ?",
                Integer.class, tenantId, matterId);
        return max != null && max == roundNo;
    }

    public boolean completeStartedWorkflowRound(long tenantId, long matterId, int roundNo,
                                                WorkflowRoundStatus nextStatus, LocalDateTime endedAt) {
        return jdbc.update("""
                UPDATE arch_decision_workflow_round
                SET status = ?, ended_at = ?
                WHERE tenant_id = ? AND matter_id = ? AND round_no = ? AND status = 'STARTED'
                """, nextStatus.name(), Timestamp.valueOf(endedAt), tenantId, matterId, roundNo) == 1;
    }

    public boolean beginReceipt(WorkflowReceiptStart receipt) {
        return jdbc.update("""
                INSERT INTO arch_decision_workflow_receipt
                    (id, tenant_id, event_id, subscriber_key, matter_id, round_no, workflow_instance_id,
                     event_type, processing_status, received_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'FAILED', NOW(3))
                ON DUPLICATE KEY UPDATE matter_id = matter_id
                """, receipt.id(), receipt.tenantId(), receipt.eventId(), receipt.subscriberKey(),
                receipt.matterId(), receipt.roundNo(), receipt.workflowInstanceId(), receipt.eventType()) == 1;
    }

    public boolean completeReceipt(long tenantId, String eventId, String subscriberKey,
                                   WorkflowReceiptStatus status, String detail) {
        return jdbc.update("""
                UPDATE arch_decision_workflow_receipt
                SET processing_status = ?, detail = ?, processed_at = NOW(3)
                WHERE tenant_id = ? AND event_id = ? AND subscriber_key = ? AND processing_status = 'FAILED'
                """, status.name(), detail, tenantId, eventId, subscriberKey) == 1;
    }

    // ---------- 工具 ----------

    private static LocalDateTime localDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static <E extends Enum<E>> E nullableEnum(java.sql.ResultSet rs, String column,
                                                      Class<E> type) throws java.sql.SQLException {
        String value = rs.getString(column);
        return value == null ? null : Enum.valueOf(type, value);
    }
}
