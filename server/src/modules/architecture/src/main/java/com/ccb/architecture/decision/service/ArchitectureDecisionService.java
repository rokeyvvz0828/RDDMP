package com.ccb.architecture.decision.service;

import com.ccb.architecture.decision.model.DecisionModels.ActionItem;
import com.ccb.architecture.decision.model.DecisionModels.ActionItemInput;
import com.ccb.architecture.decision.model.DecisionModels.ActionItemStatus;
import com.ccb.architecture.decision.model.DecisionModels.Conclusion;
import com.ccb.architecture.decision.model.DecisionModels.ConclusionEffectiveStatus;
import com.ccb.architecture.decision.model.DecisionModels.DecisionMatter;
import com.ccb.architecture.decision.model.DecisionModels.FirstHandlingOutcome;
import com.ccb.architecture.decision.model.DecisionModels.MaterialCommand;
import com.ccb.architecture.decision.model.DecisionModels.MaterialKind;
import com.ccb.architecture.decision.model.DecisionModels.MaterialRecord;
import com.ccb.architecture.decision.model.DecisionModels.MatterCommand;
import com.ccb.architecture.decision.model.DecisionModels.MatterQuery;
import com.ccb.architecture.decision.model.DecisionModels.MatterStatus;
import com.ccb.architecture.decision.model.DecisionModels.PublicationIntent;
import com.ccb.architecture.decision.model.DecisionModels.ReviewCommand;
import com.ccb.architecture.decision.model.DecisionModels.ReviewMethod;
import com.ccb.architecture.decision.model.DecisionModels.ReviewRecord;
import com.ccb.architecture.decision.model.DecisionModels.Supersession;
import com.ccb.architecture.decision.model.DecisionModels.SupersessionKind;
import com.ccb.architecture.decision.model.DecisionModels.SupersessionTarget;
import com.ccb.architecture.decision.model.DecisionModels.WorkflowRound;
import com.ccb.architecture.decision.model.DecisionModels.WorkflowRoundStatus;
import com.ccb.architecture.decision.persistence.DecisionStore;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowBusinessGateway;
import com.ccb.workflow.integration.WorkflowStartCommand;
import com.ccb.workflow.integration.WorkflowStartResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/**
 * 架构决策事项业务服务。
 *
 * <p>发布门禁：发布准备（确定类型、结论来源评审、替代/部分修订目标）保存意图并
 * 计算摘要；启动工作流后只有 {@code architecture.decision.review} 的 APPROVED 事件
 * 在同一事务写入不可变结论与替代关系。事项编号 AD-YYYY-NNNN 租户内永久唯一。</p>
 */
@Service
public class ArchitectureDecisionService {
    public static final String MODULE_CODE = "architecture";
    public static final String MODULE_NAME = "架构管理";
    public static final String BUSINESS_TYPE = "architecture_decision_publish";
    public static final String WORKFLOW_DEFINITION_CODE = "architecture.decision.review";
    public static final String TYPE_CATEGORY_CODE = "ARCH_MATTER_TYPE";
    public static final String SUBSCRIBER_KEY = "architecture.decision.publish.lifecycle.v1";
    public static final String DETAIL_PATH_PREFIX = "/architecture/decisions/";
    public static final String MATTER_ATTACHMENT_BUSINESS_TYPE = "architecture-decision";
    private static final int FIRST_HANDLING_DEADLINE_DAYS = 7;
    private static final int MATTER_NO_MAX_ORDINAL = 9999;

    private final DecisionStore store;
    private final SystemReferenceQuery referenceQuery;
    private final WorkflowBusinessGateway workflowGateway;
    private final com.ccb.attachment.model.AttachmentPort attachmentPort;
    private final com.ccb.attachment.integration.AttachmentGateway attachmentGateway;
    private final LongSupplier idSupplier;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ArchitectureDecisionService(DecisionStore store, SystemReferenceQuery referenceQuery,
                                       WorkflowBusinessGateway workflowGateway,
                                       com.ccb.attachment.model.AttachmentPort attachmentPort,
                                       com.ccb.attachment.integration.AttachmentGateway attachmentGateway) {
        this(store, referenceQuery, workflowGateway, attachmentPort, attachmentGateway,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000),
                Clock.systemDefaultZone());
    }

    ArchitectureDecisionService(DecisionStore store, SystemReferenceQuery referenceQuery,
                                WorkflowBusinessGateway workflowGateway,
                                com.ccb.attachment.model.AttachmentPort attachmentPort,
                                com.ccb.attachment.integration.AttachmentGateway attachmentGateway,
                                LongSupplier idSupplier, Clock clock) {
        this.store = Objects.requireNonNull(store, "决策存储不能为空");
        this.referenceQuery = Objects.requireNonNull(referenceQuery, "参数查询不能为空");
        this.workflowGateway = Objects.requireNonNull(workflowGateway, "工作流网关不能为空");
        this.attachmentPort = Objects.requireNonNull(attachmentPort, "附件端口不能为空");
        this.attachmentGateway = Objects.requireNonNull(attachmentGateway, "附件网关不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public List<com.ccb.system.capability.SystemParameterReference> types(AuthUser actor) {
        requireActor(actor);
        return referenceQuery.activeParameters(actor, TYPE_CATEGORY_CODE);
    }

    public PageResult<DecisionMatter> list(AuthUser actor, PageQuery page, MatterQuery query) {
        requireActor(actor);
        return store.pageMatters(actor.tenantId(), page, normalizeQuery(query));
    }

    public DecisionMatter detail(AuthUser actor, long id) {
        requireActor(actor);
        return requireMatter(actor.tenantId(), id);
    }

    public List<MaterialRecord> materials(AuthUser actor, long id) {
        requireActor(actor);
        requireMatter(actor.tenantId(), id);
        return store.listMaterials(actor.tenantId(), id);
    }

    public List<ReviewRecord> reviews(AuthUser actor, long id) {
        requireActor(actor);
        requireMatter(actor.tenantId(), id);
        return store.listReviews(actor.tenantId(), id);
    }

    public List<Map<String, Object>> reviewParticipants(AuthUser actor, long id, long reviewId) {
        requireActor(actor);
        requireMatter(actor.tenantId(), id);
        store.findReview(actor.tenantId(), id, reviewId)
                .orElseThrow(() -> new ArchitectureNotFoundException("评审记录不存在"));
        return store.listParticipants(actor.tenantId(), reviewId);
    }

    public List<ActionItem> reviewActionItems(AuthUser actor, long id, long reviewId) {
        requireActor(actor);
        requireMatter(actor.tenantId(), id);
        store.findReview(actor.tenantId(), id, reviewId)
                .orElseThrow(() -> new ArchitectureNotFoundException("评审记录不存在"));
        return store.listActionItems(actor.tenantId(), reviewId);
    }

    @Transactional
    public DecisionMatter create(AuthUser actor, MatterCommand command) {
        requireActor(actor);
        MatterCommand normalized = normalizeCommand(command);
        LocalDateTime now = LocalDateTime.now(clock);
        long id = nextId();
        String matterNo = allocateMatterNo(actor.tenantId(), now);
        DecisionMatter matter = new DecisionMatter(
                id, actor.tenantId(), matterNo, normalized.title(), normalized.problem(), null,
                MatterStatus.SUBMITTED, now, now.toLocalDate().plusDays(FIRST_HANDLING_DEADLINE_DAYS),
                null, null, null, null, null, null,
                actor.id(), actor.displayName(), actor.id(), actor.displayName(),
                null, null, 0, null, null, null, null, 0,
                actor.id(), actor.displayName(), now, now);
        store.createMatter(matter);
        return requireMatter(actor.tenantId(), id);
    }

    @Transactional
    public DecisionMatter update(AuthUser actor, AccessLevel access, long id, long rowVersion,
                                 MatterCommand command) {
        requireActor(actor);
        DecisionMatter matter = requireMatter(actor.tenantId(), id);
        requireProposerOrManage(actor, access, matter, "只有事项提出人可以编辑本人事项");
        requireEditableByProposer(actor, matter, "只有待首次处理或要求补充的事项可以编辑标题与问题");
        MatterCommand normalized = normalizeCommand(command);
        store.updateMatter(actor.tenantId(), id, rowVersion, normalized.title(), normalized.problem(), actor.id());
        return requireMatter(actor.tenantId(), id);
    }

    @Transactional
    public MaterialRecord addMaterial(AuthUser actor, AccessLevel access, long id, MaterialCommand command) {
        requireActor(actor);
        DecisionMatter matter = requireMatter(actor.tenantId(), id);
        requireProposerOrReviewer(actor, access, matter, "只有提出人或架构组成员可以补充材料");
        if (matter.status() == MatterStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.CONFLICT, "事项已完成，不能再补充材料");
        }
        MaterialKind kind = command == null || command.kind() == null ? MaterialKind.OTHER : command.kind();
        String content = requireText(command == null ? null : command.content(), "材料内容", 20000);
        long materialId = nextId();
        MaterialRecord record = new MaterialRecord(materialId, actor.tenantId(), id, kind, content,
                actor.id(), actor.displayName(), LocalDateTime.now(clock));
        store.addMaterial(record);
        return record;
    }

    /** 首次处理：受理/要求补充/确定评审方式；期限由受理时间 + 7 自然日计算并持久化。 */
    @Transactional
    public DecisionMatter firstHandling(AuthUser actor, AccessLevel access, long id, long rowVersion,
                                        FirstHandlingOutcome outcome, String comment, ReviewMethod reviewMode) {
        requireActor(actor);
        requireReviewer(access, "只有架构组成员可以办理首次处理");
        DecisionMatter matter = requireMatter(actor.tenantId(), id);
        if (matter.status() != MatterStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有待首次处理的事项可以办理首次处理");
        }
        FirstHandlingOutcome normalizedOutcome = requireOutcome(outcome);
        if (normalizedOutcome != FirstHandlingOutcome.REQUESTED_INFO && reviewMode == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "确认受理或确定评审方式时必须指定异步或会议评审方式");
        }
        store.applyFirstHandling(actor.tenantId(), id, rowVersion, normalizedOutcome,
                limit(comment, 2000), reviewMode, actor.id(), actor.displayName());
        return requireMatter(actor.tenantId(), id);
    }

    /** 要求补充后由提出人补充材料并重新提交，重置受理时间与首次处理期限。 */
    @Transactional
    public DecisionMatter resubmit(AuthUser actor, AccessLevel access, long id, long rowVersion) {
        requireActor(actor);
        DecisionMatter matter = requireMatter(actor.tenantId(), id);
        requireProposerOrManage(actor, access, matter, "只有提出人可以重新提交本人事项");
        if (matter.status() != MatterStatus.RETURNED_FOR_INFO) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有要求补充信息的事项可以重新提交");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        store.resubmit(actor.tenantId(), id, rowVersion, now,
                now.toLocalDate().plusDays(FIRST_HANDLING_DEADLINE_DAYS), actor.id(), actor.displayName());
        return requireMatter(actor.tenantId(), id);
    }

    @Transactional
    public ReviewRecord recordReview(AuthUser actor, AccessLevel access, long id, ReviewCommand command) {
        requireActor(actor);
        requireReviewer(access, "只有架构组成员可以记录评审");
        DecisionMatter matter = requireMatter(actor.tenantId(), id);
        if (matter.status() != MatterStatus.IN_REVIEW) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有评审中的事项可以记录评审");
        }
        ReviewCommand normalized = normalizeReviewCommand(command, false);
        int reviewNo = store.nextReviewNo(actor.tenantId(), id);
        long reviewId = nextId();
        ReviewRecord review = new ReviewRecord(reviewId, actor.tenantId(), id, reviewNo,
                normalized.method(), normalized.reviewedAt(), normalized.processMaterialSummary(),
                normalized.keyOpinion(), normalized.conclusionContent(), normalized.conclusionRationale(),
                actor.id(), actor.displayName(), LocalDateTime.now(clock), LocalDateTime.now(clock));
        store.insertReview(review);
        store.replaceParticipants(actor.tenantId(), reviewId, normalized.participantUserIds(), participantNames(actor, normalized.participantUserIds()));
        store.replaceActionItems(actor.tenantId(), reviewId, normalized.actionItems(), actor.id());
        return store.findReview(actor.tenantId(), id, reviewId).orElseThrow();
    }

    @Transactional
    public ReviewRecord updateReview(AuthUser actor, AccessLevel access, long matterId, long reviewId,
                                     ReviewCommand command) {
        requireActor(actor);
        requireReviewer(access, "只有架构组成员可以修改评审记录");
        DecisionMatter matter = requireMatter(actor.tenantId(), matterId);
        if (matter.status() == MatterStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.CONFLICT, "事项已完成，评审记录不可再修改");
        }
        ReviewRecord existing = store.findReview(actor.tenantId(), matterId, reviewId)
                .orElseThrow(() -> new ArchitectureNotFoundException("评审记录不存在"));
        ReviewCommand normalized = normalizeReviewCommand(command, false);
        ReviewRecord updated = new ReviewRecord(existing.id(), existing.tenantId(), existing.matterId(),
                existing.reviewNo(), normalized.method(), normalized.reviewedAt(),
                normalized.processMaterialSummary(), normalized.keyOpinion(),
                normalized.conclusionContent(), normalized.conclusionRationale(),
                existing.createdBy(), existing.createdByName(), existing.createdAt(), LocalDateTime.now(clock));
        store.updateReview(updated);
        store.replaceParticipants(actor.tenantId(), reviewId, normalized.participantUserIds(), participantNames(actor, normalized.participantUserIds()));
        store.replaceActionItems(actor.tenantId(), reviewId, normalized.actionItems(), actor.id());
        return store.findReview(actor.tenantId(), matterId, reviewId).orElseThrow();
    }

    /** 行动项完成：发布后仍可跟踪，只允许状态推进。 */
    @Transactional
    public ActionItem completeActionItem(AuthUser actor, AccessLevel access, long matterId, long reviewId,
                                         long actionItemId) {
        requireActor(actor);
        requireReviewer(access, "只有架构组成员可以完成行动项");
        requireMatter(actor.tenantId(), matterId);
        store.findActionItem(actor.tenantId(), reviewId, actionItemId)
                .orElseThrow(() -> new ArchitectureNotFoundException("行动项不存在"));
        store.completeActionItem(actor.tenantId(), reviewId, actionItemId, actor.id());
        return store.findActionItem(actor.tenantId(), reviewId, actionItemId).orElseThrow();
    }

    // ---------- 发布门禁 ----------

    /** 发布准备：确定类型与结论来源，登记替代/部分修订目标，保存意图与摘要。 */
    @Transactional
    public PublicationIntent preparePublication(AuthUser actor, long id, long rowVersion,
                                                long reviewId, List<SupersessionTarget> targets) {
        requireActor(actor);
        DecisionMatter matter = requireMatter(actor.tenantId(), id);
        if (matter.status() != MatterStatus.IN_REVIEW) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有评审中的事项可以准备结论发布");
        }
        if (matter.typeCode() == null || matter.typeCode().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "正式决策发布前必须确定事项类型");
        }
        ReviewRecord review = store.findReview(actor.tenantId(), id, reviewId)
                .orElseThrow(() -> new ArchitectureNotFoundException("结论来源评审记录不存在"));
        if (review.conclusionContent() == null || review.conclusionContent().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评审记录必须先填写正式结论");
        }
        List<SupersessionTarget> normalizedTargets = normalizeTargets(actor, targets);
        String digest = sha256(canonicalIntent(id, reviewId, normalizedTargets));
        PublicationIntent intent = new PublicationIntent(id, actor.tenantId(), reviewId, normalizedTargets,
                digest, actor.id(), actor.displayName(), LocalDateTime.now(clock));
        store.upsertPublicationIntent(intent);
        touchPublicationPreparation(actor, id, rowVersion);
        return intent;
    }

    /** 启动发布工作流：新轮次实例，事件 APPROVED 才发布结论。 */
    @Transactional
    public DecisionMatter startPublication(AuthUser actor, long id, long rowVersion) {
        requireActor(actor);
        DecisionMatter matter = requireMatter(actor.tenantId(), id);
        PublicationIntent intent = store.findPublicationIntent(actor.tenantId(), id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "请先完成结论发布准备"));
        if (matter.status() != MatterStatus.IN_REVIEW) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有评审中的事项可以启动结论发布");
        }
        if (matter.typeCode() == null || matter.typeCode().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "正式决策发布前必须确定事项类型");
        }
        int nextRound = matter.currentBusinessRound() + 1;
        long roundId = nextId();
        store.insertPendingWorkflowRound(new WorkflowRound(roundId, actor.tenantId(), id, nextRound,
                null, null, null, null, WorkflowRoundStatus.PENDING, null, null, null, null));
        WorkflowBusinessContext context = new WorkflowBusinessContext(
                MODULE_CODE, MODULE_NAME, BUSINESS_TYPE, String.valueOf(id),
                "架构决策事项 " + matter.matterNo(), nextRound, null, null,
                DETAIL_PATH_PREFIX + id, intent.payloadDigest());
        WorkflowStartResult result = workflowGateway.startByCode(new WorkflowStartCommand(
                WORKFLOW_DEFINITION_CODE, context, workflowVariables(matter, intent)), actor);
        if (result == null || result.context() == null
                || !Objects.equals(result.context().dataDigest(), intent.payloadDigest())
                || !String.valueOf(id).equals(result.context().businessKey())
                || result.context().businessRound() != nextRound) {
            throw new BusinessException(ErrorCode.CONFLICT, "工作流启动结果与发布准备不一致");
        }
        LocalDateTime startedAt = LocalDateTime.now(clock);
        if (!store.bindWorkflowRoundStarted(actor.tenantId(), id, nextRound,
                result.definitionId(), result.definitionVersion(), result.instanceId(),
                intent.payloadDigest(), startedAt)) {
            throw new BusinessException(ErrorCode.CONFLICT, "发布轮次启动状态已变化");
        }
        if (!store.compareAndSetMatterWorkflowContext(actor.tenantId(), id, matter.currentBusinessRound(),
                matter.rowVersion(), nextRound, result.definitionId(), result.definitionVersion(),
                result.instanceId(), intent.payloadDigest(), actor.id())) {
            throw new BusinessException(ErrorCode.CONFLICT, "事项工作流上下文已被其他操作更新");
        }
        return requireMatter(actor.tenantId(), id);
    }

    private Map<String, Object> workflowVariables(DecisionMatter matter, PublicationIntent intent) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("matterId", matter.id());
        variables.put("matterNo", matter.matterNo());
        variables.put("typeCode", matter.typeCode());
        variables.put("reviewId", intent.reviewId());
        variables.put("supersessionTargets", intent.targets().stream()
                .map(target -> Map.of("conclusionId", target.conclusionId(), "kind", target.kind().name()))
                .toList());
        return Map.copyOf(variables);
    }

    private void touchPublicationPreparation(AuthUser actor, long id, long rowVersion) {
        int updated = store.touchPublicationPreparation(actor.tenantId(), id, rowVersion, actor.id());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "事项状态或行版本冲突");
        }
    }

    // ---------- 查询与替代链 ----------

    public List<ConclusionView> conclusions(AuthUser actor, PageQuery page, String effectiveStatus) {
        requireActor(actor);
        String normalized = normalizeOptional(effectiveStatus);
        if (normalized != null) {
            normalized = normalized.toUpperCase(Locale.ROOT);
            try {
                ConclusionEffectiveStatus.valueOf(normalized);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "结论有效状态仅支持 EFFECTIVE、SUPERSEDED 或 PARTIALLY_SUPERSEDED");
            }
        }
        PageResult<Conclusion> result = store.pageConclusions(actor.tenantId(), page, normalized);
        return result.records().stream().map(conclusion -> toConclusionView(actor, conclusion)).toList();
    }

    public ConclusionView conclusionChain(AuthUser actor, long conclusionId) {
        requireActor(actor);
        Conclusion conclusion = store.findConclusionById(actor.tenantId(), conclusionId)
                .orElseThrow(() -> new ArchitectureNotFoundException("结论不存在"));
        return toConclusionView(actor, conclusion);
    }

    private ConclusionView toConclusionView(AuthUser actor, Conclusion conclusion) {
        DecisionMatter matter = store.findMatter(actor.tenantId(), conclusion.matterId()).orElse(null);
        ConclusionEffectiveStatus effective = store.conclusionEffectiveStatus(actor.tenantId(), conclusion.id());
        List<ChainLink> supersedes = store.listSupersedes(actor.tenantId(), conclusion.id()).stream()
                .map(supersession -> toChainLink(actor, supersession))
                .toList();
        List<ChainLink> supersededBy = store.listSupersededBy(actor.tenantId(), conclusion.id()).stream()
                .map(supersession -> toChainLink(actor, supersession))
                .toList();
        return new ConclusionView(conclusion.id(), conclusion.matterId(),
                matter == null ? null : matter.matterNo(),
                matter == null ? null : matter.title(),
                matter == null ? null : matter.typeCode(),
                conclusion.content(), conclusion.rationale(),
                conclusion.publishedAt(), conclusion.publishedBy(), conclusion.publishedByName(),
                effective.name(), supersedes, supersededBy);
    }

    private ChainLink toChainLink(AuthUser actor, Supersession supersession) {
        Conclusion source = store.findConclusionById(actor.tenantId(), supersession.conclusionId()).orElse(null);
        Conclusion target = store.findConclusionById(actor.tenantId(), supersession.supersededConclusionId())
                .orElse(null);
        DecisionMatter sourceMatter = source == null ? null
                : store.findMatter(actor.tenantId(), source.matterId()).orElse(null);
        DecisionMatter targetMatter = target == null ? null
                : store.findMatter(actor.tenantId(), target.matterId()).orElse(null);
        return new ChainLink(supersession.id(), supersession.kind().name(),
                supersession.conclusionId(), sourceMatter == null ? null : sourceMatter.matterNo(),
                supersession.supersededConclusionId(), targetMatter == null ? null : targetMatter.matterNo(),
                supersession.createdAt());
    }

    public void setMatterType(AuthUser actor, AccessLevel access, long id, long rowVersion, String typeCode) {
        requireActor(actor);
        requireReviewer(access, "只有架构组成员或管理人员可以确定事项类型");
        DecisionMatter matter = requireMatter(actor.tenantId(), id);
        if (matter.status() == MatterStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.CONFLICT, "事项已完成，类型不可修改");
        }
        String normalizedType = requireText(typeCode, "事项类型", 64).toUpperCase(Locale.ROOT);
        boolean exists = referenceQuery.activeParameters(actor, TYPE_CATEGORY_CODE).stream()
                .anyMatch(option -> option.code().equalsIgnoreCase(normalizedType));
        if (!exists) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "事项类型不存在或已停用");
        }
        store.setMatterType(actor.tenantId(), id, rowVersion, normalizedType, actor.id());
    }

    // ---------- 附件（platform/attachment 公开契约） ----------

    public List<com.ccb.attachment.model.AttachmentItem> attachments(AuthUser actor, long id) {
        requireActor(actor);
        requireMatter(actor.tenantId(), id);
        return attachmentPort.list(MATTER_ATTACHMENT_BUSINESS_TYPE, id, actor.tenantId(),
                new PageQuery(1, 100), null, null).records();
    }

    @Transactional
    public void bindAttachment(AuthUser actor, long id, long attachmentId) {
        requireActor(actor);
        requireMatter(actor.tenantId(), id);
        if (attachmentId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件标识无效");
        }
        attachmentGateway.bind(new com.ccb.attachment.integration.AttachmentBindingCommand(
                attachmentId, MATTER_ATTACHMENT_BUSINESS_TYPE, String.valueOf(id), null), actor);
    }

    @Transactional
    public void deleteAttachment(AuthUser actor, long id, long attachmentId) {
        requireActor(actor);
        DecisionMatter matter = requireMatter(actor.tenantId(), id);
        if (matter.status() == MatterStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.CONFLICT, "事项已完成，附件不可删除");
        }
        if (attachmentId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件标识无效");
        }
        attachmentGateway.deleteBound(attachmentId, MATTER_ATTACHMENT_BUSINESS_TYPE, String.valueOf(id), actor);
    }

    // ---------- 工具 ----------

    /** 控制器按认证权限推导的访问级别：manage 可办理任何事项，review 可评审，propose 只能维护本人事项。 */
    public enum AccessLevel {
        PROPOSE, REVIEW, MANAGE
    }


    private java.util.Map<Long, String> participantNames(com.ccb.security.model.AuthUser actor,
                                                          List<Long> userIds) {
        java.util.Map<Long, String> names = new java.util.LinkedHashMap<>();
        for (Long userId : userIds == null ? List.<Long>of() : userIds) {
            if (userId == null || userId <= 0) {
                continue;
            }
            String name = referenceQuery.findUser(actor, userId, true)
                    .map(com.ccb.system.capability.SystemUserReference::displayName)
                    .orElse("");
            names.put(userId, name == null ? "" : name);
        }
        return names;
    }

    private String allocateMatterNo(long tenantId, LocalDateTime now) {
        int year = now.getYear();
        int ordinal = store.allocateMatterOrdinal(tenantId, year);
        if (ordinal > MATTER_NO_MAX_ORDINAL) {
            throw new BusinessException(ErrorCode.CONFLICT, "事项编号年度容量已耗尽");
        }
        return "AD-" + year + "-" + String.format(Locale.ROOT, "%04d", ordinal);
    }

    private List<SupersessionTarget> normalizeTargets(AuthUser actor, List<SupersessionTarget> targets) {
        List<SupersessionTarget> normalized = new ArrayList<>();
        Set<Long> seen = new java.util.LinkedHashSet<>();
        for (SupersessionTarget target : targets == null ? List.<SupersessionTarget>of() : targets) {
            if (target == null || target.conclusionId() <= 0) {
                continue;
            }
            if (!seen.add(target.conclusionId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "同一结论不能重复登记替代关系");
            }
            SupersessionKind kind = target.kind() == null ? SupersessionKind.SUPERSEDE : target.kind();
            Conclusion existing = store.findConclusionById(actor.tenantId(), target.conclusionId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                            "替代目标必须是已发布结论：" + target.conclusionId()));
            normalized.add(new SupersessionTarget(existing.id(), kind));
        }
        return normalized;
    }

    private ReviewCommand normalizeReviewCommand(ReviewCommand command, boolean requireConclusion) {
        if (command == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评审内容不能为空");
        }
        ReviewMethod method = command.method();
        if (method == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评审方式仅支持 ASYNC 或 MEETING");
        }
        LocalDateTime reviewedAt = command.reviewedAt() == null ? LocalDateTime.now(clock) : command.reviewedAt();
        String conclusion = normalizeOptional(command.conclusionContent());
        if (requireConclusion && conclusion == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "完成评审必须记录正式结论");
        }
        if (requireConclusion && normalizeOptional(command.conclusionRationale()) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "完成评审必须记录理由");
        }
        return new ReviewCommand(method, reviewedAt, limit(normalizeOptional(command.processMaterialSummary()), 2000),
                normalizeOptional(command.keyOpinion()), conclusion,
                normalizeOptional(command.conclusionRationale()),
                command.participantUserIds() == null ? List.of() : command.participantUserIds(),
                command.actionItems() == null ? List.of() : command.actionItems());
    }

    private String canonicalIntent(long matterId, long reviewId, List<SupersessionTarget> targets) {
        StringBuilder value = new StringBuilder("matter=").append(matterId)
                .append(";review=").append(reviewId).append(";targets=[");
        for (SupersessionTarget target : targets) {
            value.append(target.conclusionId()).append(':').append(target.kind().name()).append(',');
        }
        value.append(']');
        return value.toString();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private MatterQuery normalizeQuery(MatterQuery query) {
        MatterQuery source = query == null ? MatterQuery.empty() : query;
        String status = normalizeOptional(source.status());
        if (status != null) {
            status = status.toUpperCase(Locale.ROOT);
            try {
                MatterStatus.valueOf(status);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "事项状态仅支持 SUBMITTED、RETURNED_FOR_INFO、IN_REVIEW 或 PUBLISHED");
            }
        }
        return new MatterQuery(normalizeOptional(source.keyword()), normalizeOptional(source.typeCode()),
                status, source.firstHandlingOverdue(), source.proposerId());
    }

    private MatterCommand normalizeCommand(MatterCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "事项内容不能为空");
        }
        return new MatterCommand(requireText(command.title(), "标题", 300),
                requireText(command.problem(), "问题或困难描述", 20000));
    }

    private FirstHandlingOutcome requireOutcome(FirstHandlingOutcome outcome) {
        if (outcome == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "首次处理必须选择确认受理、要求补充信息或确定评审方式");
        }
        return outcome;
    }

    private DecisionMatter requireMatter(long tenantId, long id) {
        return store.findMatter(tenantId, id)
                .orElseThrow(() -> new ArchitectureNotFoundException("架构决策事项不存在"));
    }

    private void requireProposerOrManage(AuthUser actor, AccessLevel access, DecisionMatter matter,
                                          String message) {
        if (access != AccessLevel.MANAGE && matter.proposerId() != actor.id()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, message);
        }
    }

    private void requireProposerOrReviewer(AuthUser actor, AccessLevel access, DecisionMatter matter,
                                           String message) {
        if (access == AccessLevel.MANAGE || access == AccessLevel.REVIEW) {
            return;
        }
        if (matter.proposerId() != actor.id()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, message);
        }
    }

    private void requireReviewer(AccessLevel access, String message) {
        if (access != AccessLevel.REVIEW && access != AccessLevel.MANAGE) {
            throw new BusinessException(ErrorCode.FORBIDDEN, message);
        }
    }

    private void requireEditableByProposer(AuthUser actor, DecisionMatter matter, String message) {
        if (matter.status() != MatterStatus.SUBMITTED && matter.status() != MatterStatus.RETURNED_FOR_INFO) {
            throw new BusinessException(ErrorCode.CONFLICT, message);
        }
    }

    private void requireActor(AuthUser actor) {
        if (actor == null || actor.id() <= 0 || actor.tenantId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要有效的认证用户和租户");
        }
    }

    private String requireText(String value, String field, int maxLength) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + "不能为空");
        }
        return limit(normalized, maxLength);
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private long nextId() {
        long value = idSupplier.getAsLong();
        if (value <= 0) {
            throw new IllegalStateException("事项标识生成器返回无效值");
        }
        return value;
    }

    // ---------- 视图 ----------

    public record ConclusionView(
            long conclusionId, long matterId, String matterNo, String matterTitle, String typeCode,
            String content, String rationale, LocalDateTime publishedAt, long publishedBy,
            String publishedByName, String effectiveStatus, List<ChainLink> supersedes,
            List<ChainLink> supersededBy) {
    }

    public record ChainLink(long id, String kind, long conclusionId, String conclusionMatterNo,
                            long supersededConclusionId, String supersededMatterNo, LocalDateTime createdAt) {
    }
}
