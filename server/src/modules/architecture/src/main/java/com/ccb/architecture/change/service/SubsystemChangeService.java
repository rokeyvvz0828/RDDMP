package com.ccb.architecture.change.service;

import com.ccb.architecture.change.model.SubsystemChangeModels.ActionType;
import com.ccb.architecture.change.model.SubsystemChangeModels.ApplicationStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeApplication;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeHistoryEvent;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalDraft;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalPublishedState;
import com.ccb.architecture.change.model.SubsystemChangeModels.PublishedStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetKind;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetLock;
import com.ccb.architecture.change.model.SubsystemChangeModels.ValueReservation;
import com.ccb.architecture.change.persistence.SubsystemChangeStore;
import com.ccb.architecture.service.ArchitectureOptionsService;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * 架构子系统变更工单的草稿与提交准备边界。
 *
 * <p>B 方案后逻辑子系统模型已退役；新申请只允许维护物理子系统。历史 LOGICAL 工单可被读取和取消，
 * 但不能继续编辑、提交或发布。</p>
 */
@Service
public class SubsystemChangeService {
    private static final int REASON_MAX_LENGTH = 1_000;
    private static final String EVENT_CREATED = "DRAFT_CREATED";
    private static final String EVENT_UPDATED = "DRAFT_UPDATED";
    private static final String EVENT_CANCELLED = "DRAFT_CANCELLED";
    private static final String EVENT_SUBMITTED = "SUBMISSION_PREPARED";
    private static final String EVENT_RETURNED = "WORKFLOW_RETURNED";
    private static final String EVENT_REJECTED = "WORKFLOW_REJECTED";
    private static final String EVENT_CANCELLATION_REQUESTED = "WORKFLOW_CANCELLATION_REQUESTED";
    private static final String EVENT_CANCELLED_BY_WORKFLOW = "WORKFLOW_CANCELLED";
    private static final String SCOPE_PHYSICAL_CODE = "PHYSICAL_CODE";
    private static final String SCOPE_PHYSICAL_NAME = "PHYSICAL_NAME";
    private static final String SCOPE_PHYSICAL_ENGLISH_NAME = "PHYSICAL_ENGLISH_NAME";
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9_-]{2,32}");

    private final SubsystemChangeStore store;
    private final SystemReferenceQuery referenceQuery;
    private final TransactionTemplate transactions;
    private final LongSupplier idSupplier;
    private final Clock clock;

    @Autowired
    public SubsystemChangeService(SubsystemChangeStore store,
                                  SystemReferenceQuery referenceQuery,
                                  TransactionTemplate transactions) {
        this(store, referenceQuery, transactions,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000),
                Clock.systemUTC());
    }

    SubsystemChangeService(SubsystemChangeStore store,
                           SystemReferenceQuery referenceQuery,
                           TransactionTemplate transactions,
                           LongSupplier idSupplier,
                           Clock clock) {
        this.store = Objects.requireNonNull(store, "store 不能为空");
        this.referenceQuery = Objects.requireNonNull(referenceQuery, "referenceQuery 不能为空");
        this.transactions = Objects.requireNonNull(transactions, "transactions 不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /** 创建物理子系统工单草稿；一个物理工单始终只有一行物理草稿。 */
    public ApplicationDetail createPhysical(AuthUser actor, PhysicalApplicationCommand command) {
        requireActor(actor);
        PhysicalApplicationCommand normalized = normalizePhysicalCommand(actor, command);
        return inTransaction(() -> {
            ChangeApplication application = newApplication(actor, TargetKind.PHYSICAL, normalized.actionType(),
                    normalized.targetId(), normalized.reason());
            PhysicalDraft physicalDraft = newPhysicalDraft(application, normalized.physicalDraft(), null, null, 0);
            ChangeHistoryEvent history = history(application, actor.id(), EVENT_CREATED, null, ApplicationStatus.DRAFT,
                    "已创建物理子系统变更草稿");

            store.insertApplication(application);
            store.replacePhysicalDrafts(application.tenantId(), application.id(), List.of(physicalDraft));
            store.insertHistory(history);
            return new ApplicationDetail(application, List.of(physicalDraft), List.of(history));
        });
    }

    /** 非管理范围只能读取本人申请；管理范围读取当前租户全部申请。 */
    public List<ChangeApplication> list(AuthUser actor, AccessScope accessScope,
                                        ApplicationStatus status, int limit, int offset) {
        requireActor(actor);
        AccessScope scope = requireScope(accessScope);
        return List.copyOf(store.listApplications(actor.tenantId(),
                scope == AccessScope.MANAGE ? null : actor.id(), status, limit, offset));
    }

    /** 详情读取始终以认证租户过滤，再执行本人/管理范围校验。 */
    public ApplicationDetail detail(AuthUser actor, AccessScope accessScope, long applicationId) {
        requireActor(actor);
        requirePositive(applicationId, "工单编号");
        ChangeApplication application = loadAccessible(actor, requireScope(accessScope), applicationId);
        return detailFor(application);
    }

    /** 仅申请人本人可更新 DRAFT/RETURNED 物理草稿。 */
    public ApplicationDetail update(AuthUser actor, AccessScope accessScope, long applicationId,
                                    long expectedRowVersion, DraftUpdateCommand command) {
        requireActor(actor);
        requirePositive(applicationId, "工单编号");
        requireNonNegative(expectedRowVersion, "工单行版本");
        requireScope(accessScope);
        DraftUpdateCommand normalized = normalizeUpdateCommand(actor, command);

        return inTransaction(() -> {
            ChangeApplication application = lockOwned(actor, applicationId);
            requirePhysicalApplication(application, "逻辑子系统工单已退役，不能继续编辑");
            requireEditable(application);
            requireVersion(application, expectedRowVersion);
            PhysicalDraft existing = store.findPhysicalDrafts(application.tenantId(), application.id()).stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("物理工单缺少物理草稿"));
            PhysicalDraft physicalDraft = newPhysicalDraft(application, onlyPhysicalDraft(normalized),
                    existing, null, existing.draftRevision() + 1);

            if (!store.compareAndSetApplicationReason(application.tenantId(), application.id(), application.status(),
                    application.rowVersion(), normalized.reason(), actor.id())) {
                throw conflict("工单已被其他操作更新，请刷新后重试");
            }
            long updatedRowVersion = application.rowVersion() + 1;
            if (application.status() == ApplicationStatus.RETURNED) {
                // 退回后编辑可能改变编号、名称或英文名，旧保留值不能继续阻塞本工单重提。
                store.deleteValueReservations(application.tenantId(), application.id());
            }
            store.replacePhysicalDrafts(application.tenantId(), application.id(), List.of(physicalDraft));
            ChangeHistoryEvent history = history(application, actor.id(), EVENT_UPDATED,
                    application.status(), application.status(), "已更新物理子系统工单草稿");
            store.insertHistory(history);

            ChangeApplication updatedApplication = withReasonAndRowVersion(application, normalized.reason(),
                    updatedRowVersion, actor.id());
            return new ApplicationDetail(updatedApplication, List.of(physicalDraft), List.of(history));
        });
    }

    /**
     * DRAFT/RETURNED 可立即取消。IN_REVIEW 不伪造取消完成，必须由工作流终止确认事件终态化。
     */
    public ApplicationDetail cancel(AuthUser actor, AccessScope accessScope, long applicationId,
                                    long expectedRowVersion) {
        requireActor(actor);
        requirePositive(applicationId, "工单编号");
        requireNonNegative(expectedRowVersion, "工单行版本");
        requireScope(accessScope);

        return inTransaction(() -> {
            ChangeApplication application = lockOwned(actor, applicationId);
            requireVersion(application, expectedRowVersion);
            if (application.status() == ApplicationStatus.IN_REVIEW) {
                throw conflict("工单正在审批中，需工作流终止确认后才能取消");
            }
            requireEditable(application);
            if (!store.compareAndSetApplicationStatus(application.tenantId(), application.id(), application.status(),
                    application.rowVersion(), ApplicationStatus.CANCELLED, actor.id())) {
                throw conflict("工单已被其他操作更新，请刷新后重试");
            }

            List<PhysicalDraft> physicalDrafts = store.findPhysicalDrafts(application.tenantId(), application.id());
            store.deleteValueReservations(application.tenantId(), application.id());
            if (application.targetId() != null) {
                store.deleteTargetLock(application.tenantId(), application.targetKind(), application.targetId(),
                        application.id());
            }
            ChangeHistoryEvent history = history(application, actor.id(), EVENT_CANCELLED, application.status(),
                    ApplicationStatus.CANCELLED, "已取消工单草稿");
            store.insertHistory(history);
            ChangeApplication cancelled = withStatusAndRowVersion(application, ApplicationStatus.CANCELLED,
                    application.rowVersion() + 1, actor.id());
            return new ApplicationDetail(cancelled, physicalDrafts, List.of(history));
        });
    }

    /**
     * 在一个本地事务内准备提交并调用协调器。协调器抛出异常时整个事务回滚。
     */
    public SubmissionPreparation coordinateSubmission(AuthUser actor, AccessScope accessScope,
                                                       long applicationId, long expectedRowVersion,
                                                       SubmissionCoordinator coordinator) {
        requireActor(actor);
        requirePositive(applicationId, "工单编号");
        requireNonNegative(expectedRowVersion, "工单行版本");
        requireScope(accessScope);
        Objects.requireNonNull(coordinator, "coordinator 不能为空");
        return inTransaction(() -> {
            SubmissionPreparation preparation = prepareSubmissionInCurrentTransaction(
                    actor, accessScope, applicationId, expectedRowVersion);
            coordinator.start(preparation);
            return preparation;
        });
    }

    /**
     * 仅供同包协调与测试使用；绝不自行开启或提交事务，避免形成无 workflow instance 的 IN_REVIEW。
     */
    SubmissionPreparation prepareSubmissionInCurrentTransaction(AuthUser actor, AccessScope accessScope,
                                                                 long applicationId, long expectedRowVersion) {
        requireActualTransaction();
        requireActor(actor);
        requireScope(accessScope);
        ChangeApplication application = lockOwned(actor, applicationId);
        requirePhysicalApplication(application, "逻辑子系统工单已退役，不能提交");
        requireEditable(application);
        requireVersion(application, expectedRowVersion);

        PhysicalDraft physicalDraft = singlePhysicalDraft(application);
        PhysicalPublishedState target = validateAndLockSubmissionTarget(application, physicalDraft);
        acquireTargetLock(application);
        reserveValues(application, physicalDraft, target);

        String snapshot = submittedSnapshot(application, physicalDraft);
        String digest = sha256(snapshot);
        PhysicalDraft submittedPhysical = withSubmittedSnapshot(physicalDraft, snapshot);
        store.replacePhysicalDrafts(application.tenantId(), application.id(), List.of(submittedPhysical));

        if (!store.compareAndSetApplicationStatus(application.tenantId(), application.id(), application.status(),
                application.rowVersion(), ApplicationStatus.IN_REVIEW, actor.id())) {
            throw conflict("工单已被其他操作更新，请刷新后重试");
        }
        int nextRound = Math.addExact(application.currentBusinessRound(), 1);
        store.insertHistory(history(application, actor.id(), EVENT_SUBMITTED, application.status(),
                ApplicationStatus.IN_REVIEW, nextRound, "已完成提交准备", snapshot));
        return new SubmissionPreparation(application.id(), nextRound, snapshot, digest,
                List.of(physicalDraft.code()));
    }

    /**
     * 工作流事件的同事务协作入口。RETURNED 保留字段值保留；REJECTED 释放目标锁和值保留。
     */
    public void applyReviewOutcomeInCurrentTransaction(long tenantId, long applicationId,
                                                       long expectedRowVersion, long operatorId,
                                                       ReviewOutcome outcome) {
        requireActualTransaction();
        requirePositive(tenantId, "租户编号");
        requirePositive(applicationId, "工单编号");
        requireNonNegative(expectedRowVersion, "工单行版本");
        requirePositive(operatorId, "操作人编号");
        Objects.requireNonNull(outcome, "outcome 不能为空");
        ChangeApplication application = store.lockApplication(tenantId, applicationId)
                .orElseThrow(() -> notFound(applicationId));
        if (application.status() != ApplicationStatus.IN_REVIEW) {
            throw conflict("只有 IN_REVIEW 工单可以接收退回或拒绝结果");
        }
        requireVersion(application, expectedRowVersion);
        ApplicationStatus nextStatus = outcome == ReviewOutcome.RETURNED
                ? ApplicationStatus.RETURNED : ApplicationStatus.REJECTED;
        if (!store.compareAndSetApplicationStatus(tenantId, applicationId, ApplicationStatus.IN_REVIEW,
                application.rowVersion(), nextStatus, operatorId)) {
            throw conflict("工单已被其他操作更新，请刷新后重试");
        }
        if (outcome == ReviewOutcome.REJECTED) {
            store.deleteValueReservations(tenantId, applicationId);
            if (application.targetId() != null) {
                store.deleteTargetLock(tenantId, application.targetKind(), application.targetId(), applicationId);
            }
        }
        store.insertHistory(history(application, operatorId,
                outcome == ReviewOutcome.RETURNED ? EVENT_RETURNED : EVENT_REJECTED,
                ApplicationStatus.IN_REVIEW, nextStatus, application.currentBusinessRound(),
                outcome == ReviewOutcome.RETURNED ? "工作流已退回申请人" : "工作流已拒绝申请", null));
    }

    /**
     * 审批中取消必须先登记取消请求，再在同一事务回调中调用 workflow terminate。
     */
    public CancellationPreparation coordinateCancellation(AuthUser actor, long applicationId,
                                                           long expectedRowVersion,
                                                           CancellationCoordinator coordinator) {
        requireActor(actor);
        requirePositive(applicationId, "工单编号");
        requireNonNegative(expectedRowVersion, "工单行版本");
        Objects.requireNonNull(coordinator, "取消协调器不能为空");
        return inTransaction(() -> {
            ChangeApplication application = lockOwned(actor, applicationId);
            requireVersion(application, expectedRowVersion);
            if (application.status() != ApplicationStatus.IN_REVIEW) {
                throw conflict("只有 IN_REVIEW 工单需要终止审批流程");
            }
            if (application.cancellationRequested()) {
                throw conflict("工单已登记取消请求，请等待工作流终止确认");
            }
            if (application.currentBusinessRound() <= 0 || application.currentWorkflowInstanceId() == null
                    || application.currentWorkflowInstanceId() <= 0
                    || application.currentPayloadDigest() == null
                    || application.currentPayloadDigest().isBlank()) {
                throw conflict("工单缺少可终止的当前工作流上下文");
            }
            if (!store.compareAndSetCancellationRequested(application.tenantId(), application.id(),
                    application.rowVersion(), application.currentWorkflowInstanceId(), actor.id())) {
                throw conflict("工单或审批流程已变化，请刷新后重试");
            }
            store.insertHistory(history(application, actor.id(), EVENT_CANCELLATION_REQUESTED,
                    ApplicationStatus.IN_REVIEW, ApplicationStatus.IN_REVIEW,
                    application.currentBusinessRound(), "已请求终止当前审批流程", null));
            CancellationPreparation preparation = new CancellationPreparation(application.id(),
                    application.currentBusinessRound(), application.currentWorkflowInstanceId(),
                    application.currentPayloadDigest());
            coordinator.terminate(preparation);
            return preparation;
        });
    }

    /** 当前轮次 TERMINATED 事件的同事务确认入口；只有已登记取消请求才释放全部未发布资源。 */
    public void applyCancellationConfirmationInCurrentTransaction(long tenantId, long applicationId,
                                                                  long expectedRowVersion,
                                                                  long expectedWorkflowInstanceId,
                                                                  long operatorId) {
        requireActualTransaction();
        requirePositive(tenantId, "租户编号");
        requirePositive(applicationId, "工单编号");
        requireNonNegative(expectedRowVersion, "工单行版本");
        requirePositive(expectedWorkflowInstanceId, "工作流实例编号");
        requirePositive(operatorId, "操作人编号");
        ChangeApplication application = store.lockApplication(tenantId, applicationId)
                .orElseThrow(() -> notFound(applicationId));
        if (application.status() != ApplicationStatus.IN_REVIEW || !application.cancellationRequested()
                || !Objects.equals(application.currentWorkflowInstanceId(), expectedWorkflowInstanceId)) {
            throw conflict("只有已登记取消请求的当前审批流程可以确认取消");
        }
        requireVersion(application, expectedRowVersion);
        if (!store.compareAndSetApplicationStatus(tenantId, applicationId, ApplicationStatus.IN_REVIEW,
                application.rowVersion(), ApplicationStatus.CANCELLED, operatorId)) {
            throw conflict("工单已被其他操作更新，请刷新后重试");
        }
        store.deleteValueReservations(tenantId, applicationId);
        if (application.targetId() != null) {
            store.deleteTargetLock(tenantId, application.targetKind(), application.targetId(), applicationId);
        }
        store.insertHistory(history(application, operatorId, EVENT_CANCELLED_BY_WORKFLOW,
                ApplicationStatus.IN_REVIEW, ApplicationStatus.CANCELLED,
                application.currentBusinessRound(), "工作流终止已确认，工单取消", null));
    }

    @FunctionalInterface
    public interface SubmissionCoordinator {
        void start(SubmissionPreparation preparation);
    }

    @FunctionalInterface
    public interface CancellationCoordinator {
        void terminate(CancellationPreparation preparation);
    }

    public record SubmissionPreparation(long applicationId, int nextRound, String snapshot, String digest,
                                        List<String> physicalSubsystemCodes) {
        public SubmissionPreparation {
            snapshot = Objects.requireNonNull(snapshot, "snapshot 不能为空");
            digest = Objects.requireNonNull(digest, "digest 不能为空");
            physicalSubsystemCodes = List.copyOf(physicalSubsystemCodes == null ? List.of() : physicalSubsystemCodes);
        }
    }

    public record CancellationPreparation(long applicationId, int businessRound,
                                          long workflowInstanceId, String digest) {
        public CancellationPreparation {
            if (applicationId <= 0 || businessRound <= 0 || workflowInstanceId <= 0
                    || digest == null || digest.isBlank()) {
                throw new IllegalArgumentException("取消准备缺少有效的申请、轮次、实例或摘要");
            }
            digest = digest.trim();
        }
    }

    public enum ReviewOutcome {
        RETURNED,
        REJECTED
    }

    /** Controller 在 RBAC 校验后传入的强类型数据范围，不能从请求正文直接绑定。 */
    public enum AccessScope {
        OWN,
        MANAGE
    }

    public record PhysicalApplicationCommand(ActionType actionType, Long targetId, String reason,
                                             PhysicalDraftInput physicalDraft) {
    }

    public record DraftUpdateCommand(String reason, List<PhysicalDraftInput> physicalDrafts) {
        public DraftUpdateCommand {
            physicalDrafts = List.copyOf(physicalDrafts == null ? List.of() : physicalDrafts);
        }
    }

    public record PhysicalDraftInput(int lineNo, String code, String shortName,
                                     String name, String logicalSubsystemName, String businessComponentCode,
                                     String englishName, String businessGroupName,
                                     String deploymentPlatform, String disasterRecoveryMode,
                                     Long responsibleTeamOrgId, String responsibleTeamNameSnapshot,
                                     String runtimeCode, String systemLevelCode,
                                     String developmentFrameworkCode, Long ownerUserId,
                                     String description, String remark, Long sourceRowVersion) {

        public PhysicalDraftInput(int lineNo, String code, String shortName, String name,
                                  String logicalSubsystemName, String businessComponentCode,
                                  String englishName, String businessGroupName,
                                  Long responsibleTeamOrgId, String responsibleTeamNameSnapshot,
                                  String runtimeCode, String systemLevelCode,
                                  String developmentFrameworkCode, Long ownerUserId,
                                  String description, String remark, Long sourceRowVersion) {
            this(lineNo, code, shortName, name, logicalSubsystemName, businessComponentCode,
                    englishName, businessGroupName, null, null,
                    responsibleTeamOrgId, responsibleTeamNameSnapshot, runtimeCode, systemLevelCode,
                    developmentFrameworkCode, ownerUserId, description, remark, sourceRowVersion);
        }
    }

    public record ApplicationDetail(ChangeApplication application,
                                    List<PhysicalDraft> physicalDrafts,
                                    List<ChangeHistoryEvent> history) {
        public ApplicationDetail {
            application = Objects.requireNonNull(application, "application 不能为空");
            physicalDrafts = List.copyOf(physicalDrafts == null ? List.of() : physicalDrafts);
            history = List.copyOf(history == null ? List.of() : history);
        }
    }

    private PhysicalApplicationCommand normalizePhysicalCommand(AuthUser actor, PhysicalApplicationCommand command) {
        if (command == null) {
            throw badRequest("物理工单请求不能为空");
        }
        ActionType action = requireAction(command.actionType());
        validateTargetForAction(action, command.targetId());
        PhysicalDraftInput physical = normalizePhysicalInput(actor, command.physicalDraft(), action != ActionType.CREATE);
        return new PhysicalApplicationCommand(action, command.targetId(), normalizeReason(command.reason()), physical);
    }

    private DraftUpdateCommand normalizeUpdateCommand(AuthUser actor, DraftUpdateCommand command) {
        if (command == null) {
            throw badRequest("草稿更新请求不能为空");
        }
        if (command.physicalDrafts().size() != 1) {
            throw badRequest("物理工单更新必须且只能包含一行物理草稿");
        }
        return new DraftUpdateCommand(normalizeReason(command.reason()),
                List.of(normalizePhysicalInput(actor, command.physicalDrafts().get(0), false)));
    }

    private PhysicalDraftInput onlyPhysicalDraft(DraftUpdateCommand command) {
        if (command.physicalDrafts().size() != 1) {
            throw badRequest("物理工单更新必须且只能包含一行物理草稿");
        }
        return command.physicalDrafts().get(0);
    }

    private PhysicalDraftInput normalizePhysicalInput(AuthUser actor, PhysicalDraftInput input,
                                                       boolean requireSourceRowVersion) {
        if (input == null) {
            throw badRequest("物理草稿不能为空");
        }
        if (input.lineNo() <= 0) {
            throw badRequest("物理草稿行号必须为正数");
        }
        Long ownerUserId = input.ownerUserId();
        if (ownerUserId != null && ownerUserId <= 0) {
            throw badRequest("负责人编号无效");
        }
        Long sourceRowVersion = normalizeSourceRowVersion(input.sourceRowVersion(), requireSourceRowVersion);
        String code = required(input.code(), "物理子系统编号", 32).toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw badRequest("物理子系统编号只能包含字母、数字、连字符和下划线，长度为 2-32 位");
        }
        return new PhysicalDraftInput(input.lineNo(), code,
                required(input.shortName(), "物理子系统简称", 100),
                required(input.name(), "物理子系统名称", 200),
                optional(input.logicalSubsystemName(), "逻辑子系统", 200),
                validateParameter(actor, ArchitectureOptionsService.BUSINESS_COMPONENT_CATEGORY,
                        input.businessComponentCode(), "业务组件编号"),
                optional(input.englishName(), "英文名称", 200),
                optional(input.businessGroupName(), "业务群组", 100),
                optional(input.deploymentPlatform(), "部署平台", 64),
                optional(input.disasterRecoveryMode(), "灾备模式", 128),
                requiredId(input.responsibleTeamOrgId(), "负责团队"),
                required(input.responsibleTeamNameSnapshot(), "负责团队名称", 200),
                optional(input.runtimeCode(), "运行环境", 64),
                optional(input.systemLevelCode(), "系统级别", 64),
                optional(input.developmentFrameworkCode(), "开发框架", 64),
                ownerUserId,
                optional(input.description(), "描述", 2_000),
                optional(input.remark(), "备注", 1_000),
                sourceRowVersion);
    }

    private PhysicalDraft newPhysicalDraft(ChangeApplication application, PhysicalDraftInput input,
                                           PhysicalDraft existing, String submittedSnapshotJson,
                                           int draftRevision) {
        Long sourceId = application.actionType() == ActionType.CREATE ? null : application.targetId();
        Long sourceVersion = existing == null ? input.sourceRowVersion() : existing.sourceRowVersion();
        return new PhysicalDraft(application.id(), input.lineNo(), application.tenantId(), sourceId,
                input.code(), input.shortName(), input.name(), input.logicalSubsystemName(),
                input.businessComponentCode(), input.englishName(), input.businessGroupName(),
                input.deploymentPlatform(), input.disasterRecoveryMode(),
                input.responsibleTeamOrgId(), input.responsibleTeamNameSnapshot(),
                input.runtimeCode(), input.systemLevelCode(), input.developmentFrameworkCode(), input.ownerUserId(),
                input.description(), input.remark(), sourceVersion, draftRevision, submittedSnapshotJson,
                existing == null ? now() : existing.createdAt(), now());
    }

    private ChangeApplication newApplication(AuthUser actor, TargetKind targetKind, ActionType actionType,
                                             Long targetId, String reason) {
        LocalDateTime now = now();
        return new ChangeApplication(nextId(), actor.tenantId(), targetKind, actionType, targetId, actor.id(), reason,
                ApplicationStatus.DRAFT, 0, null, null, null, null, false, 0,
                actor.id(), actor.id(), now, now);
    }

    private PhysicalDraft singlePhysicalDraft(ChangeApplication application) {
        List<PhysicalDraft> physicalDrafts = sortedPhysicalDrafts(
                store.findPhysicalDrafts(application.tenantId(), application.id()));
        if (physicalDrafts.size() != 1) {
            throw new IllegalStateException("物理工单必须且只能包含一行物理草稿");
        }
        return physicalDrafts.get(0);
    }

    private PhysicalPublishedState validateAndLockSubmissionTarget(ChangeApplication application,
                                                                   PhysicalDraft physicalDraft) {
        validateTargetForAction(application.actionType(), application.targetId());
        if (application.actionType() == ActionType.CREATE) {
            requireCreateSourceEmpty(physicalDraft.sourcePhysicalSubsystemId(), physicalDraft.sourceRowVersion(),
                    "物理 CREATE 草稿");
            ensurePermanentUnique(application, physicalDraft, null);
            return null;
        }

        PhysicalPublishedState source = store.lockPhysical(application.tenantId(), application.targetId())
                .orElseThrow(() -> conflict("物理子系统目标不存在或不属于当前租户"));
        requirePhysicalSource(application, physicalDraft, source);
        validatePublishedAction(application.actionType(), source.status(), source.deleted());
        if (application.actionType() == ActionType.REPLACE) {
            ensurePermanentUnique(application, physicalDraft, null);
        } else {
            requireSameCode(physicalDraft, source);
            ensurePermanentUnique(application, physicalDraft, source.id());
        }
        return source;
    }

    private void requirePhysicalSource(ChangeApplication application, PhysicalDraft draft,
                                       PhysicalPublishedState target) {
        if (!Objects.equals(draft.sourcePhysicalSubsystemId(), application.targetId())
                || draft.sourceRowVersion() == null || draft.sourceRowVersion() != target.rowVersion()) {
            throw conflict("物理子系统来源版本已变化，请重新创建或刷新草稿");
        }
    }

    private void requireCreateSourceEmpty(Long sourceId, Long sourceRowVersion, String label) {
        if (sourceId != null || sourceRowVersion != null) {
            throw conflict(label + "不得携带已发布来源或来源行版本");
        }
    }

    private void validatePublishedAction(ActionType action, PublishedStatus status, boolean deleted) {
        if (deleted || status == PublishedStatus.VOIDED) {
            throw conflict("已作废或删除的物理子系统不能执行该动作");
        }
        if (action == ActionType.OFFLINE && status != PublishedStatus.ACTIVE) {
            throw conflict("只有 ACTIVE 物理子系统可以下线");
        }
        if (action == ActionType.REACTIVATE && status != PublishedStatus.OFFLINE) {
            throw conflict("只有 OFFLINE 物理子系统可以重新上线");
        }
        if (action == ActionType.REPLACE && status != PublishedStatus.ACTIVE) {
            throw conflict("只有 ACTIVE 物理子系统可以替换");
        }
    }

    private void requireSameCode(PhysicalDraft draft, PhysicalPublishedState target) {
        if (!Objects.equals(draft.code(), target.code())) {
            throw conflict("普通物理子系统工单不得修改系统编号；如需替换编号请使用 REPLACE");
        }
    }

    private void ensurePermanentUnique(ChangeApplication application, PhysicalDraft draft, Long excludeId) {
        if (store.physicalCodeExists(application.tenantId(), draft.code(), excludeId)) {
            throw conflict("物理子系统编号已存在，删除后的编号也不能复用");
        }
        if (store.physicalNameExists(application.tenantId(), draft.name(), excludeId)) {
            throw conflict("物理子系统名称已存在，删除后的名称也不能复用");
        }
        if (draft.englishName() != null
                && store.physicalEnglishNameExists(application.tenantId(), draft.englishName(), excludeId)) {
            throw conflict("物理子系统英文名称已存在，删除后的英文名称也不能复用");
        }
    }

    private void acquireTargetLock(ChangeApplication application) {
        if (application.actionType() == ActionType.CREATE) {
            return;
        }
        TargetLock current = store.findTargetLock(application.tenantId(), application.targetKind(),
                application.targetId()).orElse(null);
        if (current != null) {
            if (current.applicationId() != application.id()) {
                throw conflict("目标物理子系统已被其他工单锁定");
            }
            return;
        }
        try {
            store.insertTargetLock(new TargetLock(application.tenantId(), application.targetKind(),
                    application.targetId(), application.id(), now()));
        } catch (DuplicateKeyException exception) {
            throw conflict("目标物理子系统已被其他工单锁定");
        }
    }

    private void reserveValues(ChangeApplication application, PhysicalDraft physicalDraft,
                               PhysicalPublishedState target) {
        Long excludeId = target == null || application.actionType() == ActionType.REPLACE ? null : target.id();
        reserveValue(application, SCOPE_PHYSICAL_CODE, physicalDraft.code(), physicalDraft.lineNo());
        reserveValue(application, SCOPE_PHYSICAL_NAME, physicalDraft.name(), physicalDraft.lineNo());
        reserveValue(application, SCOPE_PHYSICAL_ENGLISH_NAME, physicalDraft.englishName(), physicalDraft.lineNo());
        ensurePermanentUnique(application, physicalDraft, excludeId);
    }

    private void reserveValue(ChangeApplication application, String scope, String value, int lineNo) {
        String normalized = normalizeReservationValue(value);
        if (normalized == null) {
            return;
        }
        ValueReservation current = store.findValueReservation(application.tenantId(), scope, normalized).orElse(null);
        if (current != null) {
            if (current.applicationId() != application.id() || current.lineNo() != lineNo) {
                throw conflict("字段值已被其他工单保留：" + scope);
            }
            return;
        }
        try {
            store.insertValueReservation(new ValueReservation(application.tenantId(), scope, normalized,
                    application.id(), lineNo, now()));
        } catch (DuplicateKeyException exception) {
            throw conflict("字段值已被其他工单保留：" + scope);
        }
    }

    private String normalizeReservationValue(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null
                : Normalizer.normalize(normalized, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private PhysicalDraft withSubmittedSnapshot(PhysicalDraft draft, String snapshot) {
        return new PhysicalDraft(draft.applicationId(), draft.lineNo(), draft.tenantId(),
                draft.sourcePhysicalSubsystemId(), draft.code(), draft.shortName(), draft.name(),
                draft.logicalSubsystemName(), draft.businessComponentCode(), draft.englishName(),
                draft.businessGroupName(), draft.deploymentPlatform(), draft.disasterRecoveryMode(),
                draft.responsibleTeamOrgId(),
                draft.responsibleTeamNameSnapshot(), draft.runtimeCode(), draft.systemLevelCode(),
                draft.developmentFrameworkCode(), draft.ownerUserId(), draft.description(), draft.remark(),
                draft.sourceRowVersion(), draft.draftRevision(), snapshot, draft.createdAt(), now());
    }

    private List<PhysicalDraft> sortedPhysicalDrafts(List<PhysicalDraft> drafts) {
        return drafts.stream().sorted(Comparator.comparingInt(PhysicalDraft::lineNo)).toList();
    }

    private String submittedSnapshot(ChangeApplication application, PhysicalDraft physicalDraft) {
        StringBuilder canonical = new StringBuilder();
        appendCanonical(canonical, "applicationId", application.id());
        appendCanonical(canonical, "tenantId", application.tenantId());
        appendCanonical(canonical, "targetKind", application.targetKind());
        appendCanonical(canonical, "actionType", application.actionType());
        appendCanonical(canonical, "targetId", application.targetId());
        appendCanonical(canonical, "applicantId", application.applicantId());
        appendCanonical(canonical, "reason", application.reason());
        String prefix = "physical[" + physicalDraft.lineNo() + "].";
        appendCanonical(canonical, prefix + "code", physicalDraft.code());
        appendCanonical(canonical, prefix + "shortName", physicalDraft.shortName());
        appendCanonical(canonical, prefix + "name", physicalDraft.name());
        appendCanonical(canonical, prefix + "logicalSubsystemName", physicalDraft.logicalSubsystemName());
        appendCanonical(canonical, prefix + "businessComponentCode", physicalDraft.businessComponentCode());
        appendCanonical(canonical, prefix + "englishName", physicalDraft.englishName());
        appendCanonical(canonical, prefix + "businessGroupName", physicalDraft.businessGroupName());
        appendCanonical(canonical, prefix + "deploymentPlatform", physicalDraft.deploymentPlatform());
        appendCanonical(canonical, prefix + "disasterRecoveryMode", physicalDraft.disasterRecoveryMode());
        appendCanonical(canonical, prefix + "responsibleTeamOrgId", physicalDraft.responsibleTeamOrgId());
        appendCanonical(canonical, prefix + "responsibleTeamNameSnapshot",
                physicalDraft.responsibleTeamNameSnapshot());
        appendCanonical(canonical, prefix + "runtimeCode", physicalDraft.runtimeCode());
        appendCanonical(canonical, prefix + "systemLevelCode", physicalDraft.systemLevelCode());
        appendCanonical(canonical, prefix + "developmentFrameworkCode", physicalDraft.developmentFrameworkCode());
        appendCanonical(canonical, prefix + "ownerUserId", physicalDraft.ownerUserId());
        appendCanonical(canonical, prefix + "description", physicalDraft.description());
        appendCanonical(canonical, prefix + "remark", physicalDraft.remark());
        appendCanonical(canonical, prefix + "sourceRowVersion", physicalDraft.sourceRowVersion());
        return "{\"canonical\":\"" + jsonEscape(canonical.toString()) + "\"}";
    }

    private void appendCanonical(StringBuilder target, String name, Object value) {
        String text = value == null ? "<null>" : String.valueOf(value);
        target.append(name.length()).append(':').append(name).append('=')
                .append(text.length()).append(':').append(text).append(';');
    }

    private String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
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

    private ApplicationDetail detailFor(ChangeApplication application) {
        return new ApplicationDetail(application,
                store.findPhysicalDrafts(application.tenantId(), application.id()),
                store.listHistory(application.tenantId(), application.id()));
    }

    private ChangeApplication loadAccessible(AuthUser actor, AccessScope scope, long applicationId) {
        ChangeApplication application = store.findApplication(actor.tenantId(), applicationId)
                .orElseThrow(() -> notFound(applicationId));
        requireAccess(actor, scope, application);
        return application;
    }

    /** 编辑、提交和取消始终属于申请人本人；管理权限只扩大读取和工作流审批范围。 */
    private ChangeApplication lockOwned(AuthUser actor, long applicationId) {
        ChangeApplication application = store.lockApplication(actor.tenantId(), applicationId)
                .orElseThrow(() -> notFound(applicationId));
        if (application.applicantId() != actor.id()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能维护本人发起的工单");
        }
        return application;
    }

    private void requireAccess(AuthUser actor, AccessScope scope, ChangeApplication application) {
        if (scope == AccessScope.OWN && application.applicantId() != actor.id()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能查看和维护本人发起的工单");
        }
    }

    private void requirePhysicalApplication(ChangeApplication application, String message) {
        if (application.targetKind() != TargetKind.PHYSICAL) {
            throw conflict(message);
        }
    }

    private void requireEditable(ChangeApplication application) {
        if (application.status() != ApplicationStatus.DRAFT && application.status() != ApplicationStatus.RETURNED) {
            throw conflict("只有 DRAFT 或 RETURNED 工单可以编辑或取消");
        }
    }

    private void requireVersion(ChangeApplication application, long expectedRowVersion) {
        if (application.rowVersion() != expectedRowVersion) {
            throw conflict("工单已被其他操作更新，请刷新后重试");
        }
    }

    private ChangeHistoryEvent history(ChangeApplication application, long operatorId, String eventType,
                                       ApplicationStatus fromStatus, ApplicationStatus toStatus, String summary) {
        return history(application, operatorId, eventType, fromStatus, toStatus,
                application.currentBusinessRound(), summary, null);
    }

    private ChangeHistoryEvent history(ChangeApplication application, long operatorId, String eventType,
                                       ApplicationStatus fromStatus, ApplicationStatus toStatus,
                                       int businessRound, String summary, String snapshot) {
        return new ChangeHistoryEvent(nextId(), application.tenantId(), application.id(), eventType,
                fromStatus, toStatus, businessRound, summary, snapshot, null,
                operatorId, now());
    }

    private ChangeApplication withReasonAndRowVersion(ChangeApplication application, String reason,
                                                       long rowVersion, long updatedBy) {
        return new ChangeApplication(application.id(), application.tenantId(), application.targetKind(),
                application.actionType(), application.targetId(), application.applicantId(), reason,
                application.status(), application.currentBusinessRound(), application.currentWorkflowDefinitionId(),
                application.currentWorkflowVersionId(), application.currentWorkflowInstanceId(),
                application.currentPayloadDigest(), application.cancellationRequested(), rowVersion,
                application.createdBy(), updatedBy, application.createdAt(), now());
    }

    private ChangeApplication withStatusAndRowVersion(ChangeApplication application, ApplicationStatus status,
                                                       long rowVersion, long updatedBy) {
        return new ChangeApplication(application.id(), application.tenantId(), application.targetKind(),
                application.actionType(), application.targetId(), application.applicantId(), application.reason(),
                status, application.currentBusinessRound(), application.currentWorkflowDefinitionId(),
                application.currentWorkflowVersionId(), application.currentWorkflowInstanceId(),
                application.currentPayloadDigest(), application.cancellationRequested(), rowVersion,
                application.createdBy(), updatedBy, application.createdAt(), now());
    }

    private ActionType requireAction(ActionType actionType) {
        return Objects.requireNonNull(actionType, "actionType 不能为空");
    }

    private void validateTargetForAction(ActionType actionType, Long targetId) {
        if (actionType == ActionType.CREATE) {
            if (targetId != null) {
                throw badRequest("CREATE 工单不得携带已发布目标编号");
            }
            return;
        }
        if (targetId == null || targetId <= 0) {
            throw badRequest("非 CREATE 工单必须指定已发布目标编号");
        }
    }

    private Long normalizeSourceRowVersion(Long sourceRowVersion, boolean required) {
        if (sourceRowVersion == null) {
            if (required) {
                throw badRequest("已发布目标工单必须携带来源行版本");
            }
            return null;
        }
        if (sourceRowVersion < 0) {
            throw badRequest("来源行版本不能为负数");
        }
        return sourceRowVersion;
    }

    private String validateParameter(AuthUser actor, String categoryCode, String input, String label) {
        String normalized = normalizeOptional(input);
        if (normalized == null) {
            return null;
        }
        List<SystemParameterReference> parameters = referenceQuery.activeParameters(actor, categoryCode);
        String expected = normalized;
        return (parameters == null ? List.<SystemParameterReference>of() : parameters).stream()
                .map(SystemParameterReference::code)
                .filter(code -> code != null && code.trim().equalsIgnoreCase(expected))
                .map(String::trim)
                .findFirst()
                .orElseThrow(() -> badRequest(label + "参数无效或已停用"));
    }

    private String normalizeReason(String value) {
        return optional(value, "申请原因", REASON_MAX_LENGTH);
    }

    private String required(String value, String label, int maximum) {
        String normalized = normalizeOptional(value);
        if (normalized == null || normalized.length() > maximum) {
            throw badRequest(label + "不能为空且长度不能超过 " + maximum + " 个字符");
        }
        return normalized;
    }

    private String optional(String value, String label, int maximum) {
        String normalized = normalizeOptional(value);
        if (normalized != null && normalized.length() > maximum) {
            throw badRequest(label + "长度不能超过 " + maximum + " 个字符");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private long requiredId(Long value, String label) {
        if (value == null || value <= 0) {
            throw badRequest(label + "不能为空");
        }
        return value;
    }

    private void requireActor(AuthUser actor) {
        if (actor == null || actor.id() <= 0 || actor.tenantId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要有效的认证用户和租户");
        }
    }

    private AccessScope requireScope(AccessScope scope) {
        return Objects.requireNonNull(scope, "accessScope 不能为空");
    }

    private void requirePositive(long value, String label) {
        if (value <= 0) {
            throw badRequest(label + "必须为正数");
        }
    }

    private void requireNonNegative(long value, String label) {
        if (value < 0) {
            throw badRequest(label + "不能为负数");
        }
    }

    private long nextId() {
        long id = idSupplier.getAsLong();
        if (id <= 0) {
            throw new IllegalStateException("工单标识生成失败");
        }
        return id;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        T result = transactions.execute(status -> action.get());
        if (result == null) {
            throw new IllegalStateException("工单事务未返回结果");
        }
        return result;
    }

    private void requireActualTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("提交准备或工作流终态协作必须在真实事务内执行");
        }
    }

    private ArchitectureNotFoundException notFound(long applicationId) {
        return new ArchitectureNotFoundException("工单不存在或不属于当前租户：" + applicationId);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }
}
