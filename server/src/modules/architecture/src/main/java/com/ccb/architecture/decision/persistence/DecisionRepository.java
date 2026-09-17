package com.ccb.architecture.decision.persistence;

import com.ccb.architecture.decision.model.DecisionModels.ActionItem;
import com.ccb.architecture.decision.model.DecisionModels.ActionItemInput;
import com.ccb.architecture.decision.model.DecisionModels.ActionItemStatus;
import com.ccb.architecture.decision.model.DecisionModels.Conclusion;
import com.ccb.architecture.decision.model.DecisionModels.ConclusionEffectiveStatus;
import com.ccb.architecture.decision.model.DecisionModels.DecisionMatter;
import com.ccb.architecture.decision.model.DecisionModels.FirstHandlingOutcome;
import com.ccb.architecture.decision.model.DecisionModels.MaterialKind;
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
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Typed decision persistence boundary backed exclusively by {@link DecisionMapper}. */
@Repository
class DecisionRepository {
    private final DecisionMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    DecisionRepository(DecisionMapper mapper) {
        this.mapper = mapper;
    }

    PageResult<DecisionMatter> pageMatters(long tenantId, PageQuery page, MatterQuery query) {
        PageQuery normalizedPage = page == null ? new PageQuery(1, 20) : page;
        MatterQuery normalized = query == null ? MatterQuery.empty() : query;
        Map<String, Object> params = matterFilter(tenantId, normalized);
        Long total = mapper.countMatters(params);
        params.put("limit", normalizedPage.size());
        params.put("offset", (normalizedPage.page() - 1) * normalizedPage.size());
        return new PageResult<>(mapper.pageMatters(params).stream().map(this::matter).toList(),
                total == null ? 0 : total, normalizedPage.page(), normalizedPage.size());
    }

    Optional<DecisionMatter> findMatter(long tenantId, long id) { return Optional.ofNullable(mapper.findMatter(p("tenantId", tenantId, "id", id))).map(this::matter); }
    Optional<DecisionMatter> lockMatter(long tenantId, long id) { return Optional.ofNullable(mapper.lockMatter(p("tenantId", tenantId, "id", id))).map(this::matter); }
    Optional<DecisionMatter> lockMatter(long tenantId, long id, long rowVersion) { return Optional.ofNullable(mapper.lockMatterVersion(p("tenantId", tenantId, "id", id, "rowVersion", rowVersion))).map(this::matter); }

    int allocateMatterOrdinal(long tenantId, int year) {
        Map<String, Object> params = p("tenantId", tenantId, "year", year);
        mapper.initializeMatterSequence(params);
        Integer next = mapper.lockMatterOrdinal(params);
        if (next == null) throw new IllegalStateException("事项编号序列不可用");
        if (next > 9999) throw new IllegalStateException("事项编号年度容量已耗尽");
        mapper.advanceMatterOrdinal(p("tenantId", tenantId, "year", year, "nextOrdinal", next + 1));
        return next;
    }

    long createMatter(DecisionMatter value) { mapper.insertMatter(matterParams(value)); return value.id(); }
    void updateMatter(long tenantId, long id, long rowVersion, String title, String problem, long operatorId) { requireOne(mapper.updateMatter(p("tenantId", tenantId, "id", id, "rowVersion", rowVersion, "title", title, "problem", problem, "operatorId", operatorId)), "事项行版本冲突"); }
    void applyFirstHandling(long tenantId, long id, long rowVersion, FirstHandlingOutcome outcome, String comment, ReviewMethod reviewMode, long handlerId, String handlerName) {
        requireOne(mapper.applyFirstHandling(p("tenantId", tenantId, "id", id, "rowVersion", rowVersion,
                "status", outcome == FirstHandlingOutcome.REQUESTED_INFO ? "RETURNED_FOR_INFO" : "IN_REVIEW",
                "outcome", outcome.name(), "comment", comment, "reviewMode", reviewMode == null ? null : reviewMode.name(),
                "handlerId", handlerId, "handlerName", handlerName)), "事项首次处理状态或行版本冲突");
    }
    void resubmit(long tenantId, long id, long rowVersion, LocalDateTime receivedAt, LocalDate deadline, long submitterId, String submitterName) { requireOne(mapper.resubmit(p("tenantId", tenantId, "id", id, "rowVersion", rowVersion, "receivedAt", receivedAt, "deadline", deadline, "submitterId", submitterId, "submitterName", submitterName)), "事项重提状态或行版本冲突"); }
    void setMatterType(long tenantId, long id, long rowVersion, String typeCode, long operatorId) { requireOne(mapper.setMatterType(p("tenantId", tenantId, "id", id, "rowVersion", rowVersion, "typeCode", typeCode, "operatorId", operatorId)), "事项类型或行版本冲突"); }
    int touchPublicationPreparation(long tenantId, long id, long rowVersion, long operatorId) { return mapper.touchPublicationPreparation(p("tenantId", tenantId, "id", id, "rowVersion", rowVersion, "operatorId", operatorId)); }

    long addMaterial(MaterialRecord value) { mapper.insertMaterial(p("id", value.id(), "tenantId", value.tenantId(), "matterId", value.matterId(), "kind", value.kind().name(), "content", value.content(), "createdBy", value.createdBy(), "createdByName", value.createdByName())); return value.id(); }
    List<MaterialRecord> listMaterials(long tenantId, long matterId) { return mapper.listMaterials(p("tenantId", tenantId, "matterId", matterId)).stream().map(this::material).toList(); }
    long insertReview(ReviewRecord value) { mapper.insertReview(reviewParams(value)); return value.id(); }
    int nextReviewNo(long tenantId, long matterId) { Integer value = mapper.maxReviewNo(p("tenantId", tenantId, "matterId", matterId)); return value == null ? 1 : value + 1; }
    Optional<ReviewRecord> findReview(long tenantId, long matterId, long reviewId) { return Optional.ofNullable(mapper.findReview(p("tenantId", tenantId, "matterId", matterId, "reviewId", reviewId))).map(this::review); }
    List<ReviewRecord> listReviews(long tenantId, long matterId) { return mapper.listReviews(p("tenantId", tenantId, "matterId", matterId)).stream().map(this::review).toList(); }
    void updateReview(ReviewRecord value) { mapper.updateReview(reviewParams(value)); }

    void replaceParticipants(long tenantId, long reviewId, List<Long> userIds, Map<Long, String> displayNames) {
        mapper.deleteParticipants(p("tenantId", tenantId, "reviewId", reviewId));
        long id = System.currentTimeMillis() * 1_000;
        int ordinal = 0;
        for (Long userId : userIds == null ? List.<Long>of() : userIds) {
            if (userId == null || userId <= 0) continue;
            mapper.insertParticipant(p("id", id + ordinal++, "tenantId", tenantId, "reviewId", reviewId,
                    "userId", userId, "userName", displayNames == null ? "" : displayNames.getOrDefault(userId, "")));
        }
    }
    List<Long> listParticipantIds(long tenantId, long reviewId) { return mapper.listParticipantIds(p("tenantId", tenantId, "reviewId", reviewId)); }
    List<Map<String, Object>> listParticipants(long tenantId, long reviewId) { return mapper.listParticipants(p("tenantId", tenantId, "reviewId", reviewId)); }

    void replaceActionItems(long tenantId, long reviewId, List<ActionItemInput> inputs, long operatorId) {
        List<ActionItem> existing = listActionItems(tenantId, reviewId);
        Map<Long, ActionItem> byId = new LinkedHashMap<>();
        existing.forEach(item -> byId.put(item.id(), item));
        java.util.Set<Long> retained = new java.util.LinkedHashSet<>();
        long id = System.currentTimeMillis() * 1_000;
        int ordinal = 0;
        for (ActionItemInput input : inputs == null ? List.<ActionItemInput>of() : inputs) {
            if (input == null || input.content() == null || input.content().isBlank()) continue;
            ActionItem previous = input.id() == null ? null : byId.get(input.id());
            if (previous != null) {
                retained.add(previous.id());
                mapper.updateActionItem(p("tenantId", tenantId, "reviewId", reviewId, "id", previous.id(),
                        "content", input.content().trim(), "ownerUserId", input.ownerUserId(), "ownerName", input.ownerName()));
            } else {
                long actionId = id + ordinal++;
                retained.add(actionId);
                mapper.insertActionItem(p("id", actionId, "tenantId", tenantId, "reviewId", reviewId,
                        "content", input.content().trim(), "ownerUserId", input.ownerUserId(), "ownerName", input.ownerName(),
                        "createdBy", operatorId, "createdByName", ""));
            }
        }
        existing.stream().filter(item -> !retained.contains(item.id())).forEach(item ->
                mapper.deleteActionItem(p("tenantId", tenantId, "reviewId", reviewId, "id", item.id())));
    }
    List<ActionItem> listActionItems(long tenantId, long reviewId) { return mapper.listActionItems(p("tenantId", tenantId, "reviewId", reviewId)).stream().map(this::action).toList(); }
    Optional<ActionItem> findActionItem(long tenantId, long reviewId, long actionId) { return Optional.ofNullable(mapper.findActionItem(p("tenantId", tenantId, "reviewId", reviewId, "actionId", actionId))).map(this::action); }
    void completeActionItem(long tenantId, long reviewId, long actionId, long operatorId) { requireOne(mapper.completeActionItem(p("tenantId", tenantId, "reviewId", reviewId, "actionId", actionId, "operatorId", operatorId)), "行动项不存在或已完成"); }

    void upsertPublicationIntent(PublicationIntent value) {
        try {
            mapper.upsertPublicationIntent(p("matterId", value.matterId(), "tenantId", value.tenantId(), "reviewId", value.reviewId(),
                    "targetsJson", objectMapper.writeValueAsString(value.targets() == null ? List.of() : value.targets()),
                    "payloadDigest", value.payloadDigest(), "preparedBy", value.preparedBy(), "preparedByName", value.preparedByName(), "preparedAt", value.preparedAt()));
        } catch (JsonProcessingException exception) { throw new IllegalStateException("发布准备目标序列化失败", exception); }
    }
    Optional<PublicationIntent> findPublicationIntent(long tenantId, long matterId) {
        Map<String, Object> row = mapper.findPublicationIntent(p("tenantId", tenantId, "matterId", matterId));
        if (row == null) return Optional.empty();
        try {
            String json = string(row, "supersession_targets_json");
            List<SupersessionTarget> targets = objectMapper.readValue(json == null ? "[]" : json, new TypeReference<List<SupersessionTarget>>() { });
            return Optional.of(new PublicationIntent(longValue(row, "matter_id"), longValue(row, "tenant_id"), longValue(row, "review_id"), targets,
                    string(row, "payload_digest"), longValue(row, "prepared_by"), string(row, "prepared_by_name"), dateTime(value(row, "prepared_at"))));
        } catch (JsonProcessingException exception) { throw new IllegalStateException("发布准备目标解析失败", exception); }
    }
    Optional<Conclusion> findConclusion(long tenantId, long matterId) { return Optional.ofNullable(mapper.findConclusion(p("tenantId", tenantId, "matterId", matterId))).map(this::conclusion); }
    Optional<Conclusion> findConclusionById(long tenantId, long conclusionId) { return Optional.ofNullable(mapper.findConclusionById(p("tenantId", tenantId, "conclusionId", conclusionId))).map(this::conclusion); }
    PageResult<Conclusion> pageConclusions(long tenantId, PageQuery page, String status) {
        PageQuery normalized = page == null ? new PageQuery(1, 20) : page;
        if (status != null && !List.of("EFFECTIVE", "SUPERSEDED", "PARTIALLY_SUPERSEDED").contains(status)) throw new IllegalArgumentException("结论有效状态仅支持 EFFECTIVE、SUPERSEDED、PARTIALLY_SUPERSEDED");
        Map<String, Object> params = p("tenantId", tenantId, "effectiveStatus", status, "limit", normalized.size(), "offset", (normalized.page() - 1) * normalized.size());
        Long total = mapper.countConclusions(params);
        return new PageResult<>(mapper.pageConclusions(params).stream().map(this::conclusion).toList(), total == null ? 0 : total, normalized.page(), normalized.size());
    }
    ConclusionEffectiveStatus conclusionEffectiveStatus(long tenantId, long conclusionId) {
        List<String> kinds = mapper.supersessionKinds(p("tenantId", tenantId, "conclusionId", conclusionId));
        return kinds.contains(SupersessionKind.SUPERSEDE.name()) ? ConclusionEffectiveStatus.SUPERSEDED : (kinds.isEmpty() ? ConclusionEffectiveStatus.EFFECTIVE : ConclusionEffectiveStatus.PARTIALLY_SUPERSEDED);
    }
    List<Supersession> listSupersedes(long tenantId, long conclusionId) { return mapper.listSupersedes(p("tenantId", tenantId, "conclusionId", conclusionId)).stream().map(this::supersession).toList(); }
    List<Supersession> listSupersededBy(long tenantId, long conclusionId) { return mapper.listSupersededBy(p("tenantId", tenantId, "conclusionId", conclusionId)).stream().map(this::supersession).toList(); }
    long insertConclusion(Conclusion value) { mapper.insertConclusion(p("id", value.id(), "tenantId", value.tenantId(), "matterId", value.matterId(), "reviewId", value.reviewId(), "content", value.content(), "rationale", value.rationale(), "publishedAt", value.publishedAt(), "publishedBy", value.publishedBy(), "publishedByName", value.publishedByName())); return value.id(); }
    void insertSupersession(Supersession value) { mapper.insertSupersession(p("id", value.id(), "tenantId", value.tenantId(), "conclusionId", value.conclusionId(), "supersededConclusionId", value.supersededConclusionId(), "kind", value.kind().name())); }
    void markMatterPublished(long tenantId, long matterId, long rowVersion) { requireOne(mapper.markMatterPublished(p("tenantId", tenantId, "matterId", matterId, "rowVersion", rowVersion)), "事项状态或行版本冲突"); }

    void insertPendingWorkflowRound(WorkflowRound value) { mapper.insertPendingWorkflowRound(p("id", value.id(), "tenantId", value.tenantId(), "matterId", value.matterId(), "roundNo", value.roundNo())); }
    boolean bindWorkflowRoundStarted(long tenantId, long matterId, int roundNo, long definitionId, int definitionVersion, long instanceId, String digest, LocalDateTime startedAt) { return mapper.bindWorkflowRoundStarted(p("tenantId", tenantId, "matterId", matterId, "roundNo", roundNo, "definitionId", definitionId, "definitionVersion", definitionVersion, "instanceId", instanceId, "digest", digest, "startedAt", startedAt)) == 1; }
    boolean compareAndSetMatterWorkflowContext(long tenantId, long matterId, int currentRound, long rowVersion, int nextRound, long definitionId, int definitionVersion, long instanceId, String digest, long operatorId) { return mapper.compareAndSetMatterWorkflowContext(p("tenantId", tenantId, "matterId", matterId, "currentRound", currentRound, "rowVersion", rowVersion, "nextRound", nextRound, "definitionId", definitionId, "definitionVersion", definitionVersion, "instanceId", instanceId, "digest", digest, "operatorId", operatorId)) == 1; }
    Optional<WorkflowRound> lockWorkflowRoundByInstance(long tenantId, long instanceId) { return Optional.ofNullable(mapper.lockWorkflowRoundByInstance(p("tenantId", tenantId, "instanceId", instanceId))).map(this::round); }
    boolean isLatestWorkflowRound(long tenantId, long matterId, int roundNo) { Integer max = mapper.maxWorkflowRoundNo(p("tenantId", tenantId, "matterId", matterId)); return max != null && max == roundNo; }
    boolean completeStartedWorkflowRound(long tenantId, long matterId, int roundNo, WorkflowRoundStatus status, LocalDateTime endedAt) { return mapper.completeStartedWorkflowRound(p("tenantId", tenantId, "matterId", matterId, "roundNo", roundNo, "status", status.name(), "endedAt", endedAt)) == 1; }
    boolean beginReceipt(WorkflowReceiptStart value) { return mapper.beginReceipt(p("id", value.id(), "tenantId", value.tenantId(), "eventId", value.eventId(), "subscriberKey", value.subscriberKey(), "matterId", value.matterId(), "roundNo", value.roundNo(), "instanceId", value.workflowInstanceId(), "eventType", value.eventType())) == 1; }
    boolean completeReceipt(long tenantId, String eventId, String subscriberKey, WorkflowReceiptStatus status, String detail) { return mapper.completeReceipt(p("tenantId", tenantId, "eventId", eventId, "subscriberKey", subscriberKey, "status", status.name(), "detail", detail)) == 1; }

    private Map<String, Object> matterFilter(long tenantId, MatterQuery query) { return p("tenantId", tenantId, "keyword", query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim(), "typeCode", query.typeCode(), "status", query.status(), "proposerId", query.proposerId(), "firstHandlingOverdue", Boolean.TRUE.equals(query.firstHandlingOverdue())); }
    private Map<String, Object> matterParams(DecisionMatter value) { return p("id", value.id(), "tenantId", value.tenantId(), "matterNo", value.matterNo(), "title", value.title(), "problem", value.problem(), "receivedAt", value.receivedAt(), "deadline", value.firstHandlingDeadline(), "proposerId", value.proposerId(), "proposerName", value.proposerName(), "submitterId", value.submitterId(), "submitterName", value.submitterName(), "createdBy", value.createdBy(), "createdByName", value.createdByName(), "updatedBy", value.submitterId()); }
    private Map<String, Object> reviewParams(ReviewRecord value) { return p("id", value.id(), "tenantId", value.tenantId(), "matterId", value.matterId(), "reviewNo", value.reviewNo(), "method", value.method().name(), "reviewedAt", value.reviewedAt(), "processMaterialSummary", value.processMaterialSummary(), "keyOpinion", value.keyOpinion(), "conclusionContent", value.conclusionContent(), "conclusionRationale", value.conclusionRationale(), "createdBy", value.createdBy(), "createdByName", value.createdByName(), "reviewId", value.id()); }
    private DecisionMatter matter(Map<String, Object> row) { return new DecisionMatter(longValue(row,"id"), longValue(row,"tenant_id"), string(row,"matter_no"), string(row,"title"), string(row,"problem"), string(row,"type_code"), MatterStatus.valueOf(string(row,"status")), dateTime(value(row,"received_at")), date(value(row,"first_handling_deadline")), nullableEnum(row,"first_handling_outcome", FirstHandlingOutcome.class), string(row,"first_handling_comment"), dateTime(value(row,"first_handled_at")), nullableLong(row,"first_handler_id"), string(row,"first_handler_name"), nullableEnum(row,"review_mode", ReviewMethod.class), longValue(row,"proposer_id"), string(row,"proposer_name"), longValue(row,"submitter_id"), string(row,"submitter_name"), dateTime(value(row,"publication_prepared_at")), nullableLong(row,"publication_prepared_by"), integer(row,"current_business_round"), nullableLong(row,"current_workflow_definition_id"), nullableLong(row,"current_workflow_version_id"), nullableLong(row,"current_workflow_instance_id"), string(row,"current_payload_digest"), longValue(row,"row_version"), longValue(row,"created_by"), string(row,"created_by_name"), dateTime(value(row,"created_at")), dateTime(value(row,"updated_at"))); }
    private MaterialRecord material(Map<String, Object> row) { return new MaterialRecord(longValue(row,"id"), longValue(row,"tenant_id"), longValue(row,"matter_id"), MaterialKind.valueOf(string(row,"kind")), string(row,"content"), longValue(row,"created_by"), string(row,"created_by_name"), dateTime(value(row,"created_at"))); }
    private ReviewRecord review(Map<String, Object> row) { return new ReviewRecord(longValue(row,"id"), longValue(row,"tenant_id"), longValue(row,"matter_id"), integer(row,"review_no"), ReviewMethod.valueOf(string(row,"method")), dateTime(value(row,"reviewed_at")), string(row,"process_material_summary"), string(row,"key_opinion"), string(row,"conclusion_content"), string(row,"conclusion_rationale"), longValue(row,"created_by"), string(row,"created_by_name"), dateTime(value(row,"created_at")), dateTime(value(row,"updated_at"))); }
    private ActionItem action(Map<String, Object> row) { return new ActionItem(longValue(row,"id"), longValue(row,"tenant_id"), longValue(row,"review_id"), string(row,"content"), nullableLong(row,"owner_user_id"), string(row,"owner_name"), ActionItemStatus.valueOf(string(row,"status")), longValue(row,"created_by"), string(row,"created_by_name"), dateTime(value(row,"created_at")), dateTime(value(row,"updated_at"))); }
    private Conclusion conclusion(Map<String, Object> row) { return new Conclusion(longValue(row,"id"), longValue(row,"tenant_id"), longValue(row,"matter_id"), longValue(row,"review_id"), string(row,"content"), string(row,"rationale"), dateTime(value(row,"published_at")), longValue(row,"published_by"), string(row,"published_by_name"), dateTime(value(row,"created_at"))); }
    private Supersession supersession(Map<String, Object> row) { return new Supersession(longValue(row,"id"), longValue(row,"tenant_id"), longValue(row,"conclusion_id"), longValue(row,"superseded_conclusion_id"), SupersessionKind.valueOf(string(row,"kind")), dateTime(value(row,"created_at"))); }
    private WorkflowRound round(Map<String, Object> row) { return new WorkflowRound(longValue(row,"id"), longValue(row,"tenant_id"), longValue(row,"matter_id"), integer(row,"round_no"), nullableLong(row,"workflow_definition_id"), nullableLong(row,"workflow_version_id"), nullableLong(row,"workflow_instance_id"), string(row,"payload_digest"), WorkflowRoundStatus.valueOf(string(row,"status")), dateTime(value(row,"started_at")), dateTime(value(row,"ended_at")), dateTime(value(row,"created_at")), dateTime(value(row,"updated_at"))); }
    private static void requireOne(int updated, String message) { if (updated != 1) throw new IllegalStateException(message); }
    private static Map<String, Object> p(Object... values) { Map<String, Object> result = new HashMap<>(); for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]); return result; }
    private static Object value(Map<String, Object> row, String name) { Object value = row.get(name); return value == null ? row.get(toCamel(name)) : value; }
    private static String string(Map<String, Object> row, String name) { Object value = value(row, name); return value == null ? null : String.valueOf(value); }
    private static long longValue(Map<String, Object> row, String name) { return ((Number) value(row, name)).longValue(); }
    private static int integer(Map<String, Object> row, String name) { return ((Number) value(row, name)).intValue(); }
    private static Long nullableLong(Map<String, Object> row, String name) { Object value = value(row, name); return value == null ? null : ((Number) value).longValue(); }
    private static LocalDateTime dateTime(Object value) { if (value == null) return null; if (value instanceof LocalDateTime local) return local; if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime(); throw new IllegalStateException("日期时间列格式错误"); }
    private static LocalDate date(Object value) { if (value instanceof LocalDate local) return local; if (value instanceof Date sqlDate) return sqlDate.toLocalDate(); throw new IllegalStateException("日期列格式错误"); }
    private static <E extends Enum<E>> E nullableEnum(Map<String, Object> row, String name, Class<E> type) { String value = string(row, name); return value == null ? null : Enum.valueOf(type, value); }
    private static String toCamel(String value) { StringBuilder result = new StringBuilder(); boolean upper = false; for (char character : value.toCharArray()) { if (character == '_') upper = true; else { result.append(upper ? Character.toUpperCase(character) : character); upper = false; } } return result.toString(); }
}
