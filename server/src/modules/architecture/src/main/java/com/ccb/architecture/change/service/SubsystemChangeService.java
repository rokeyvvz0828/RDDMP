package com.ccb.architecture.change.service;

import com.ccb.architecture.change.model.SubsystemChangeModels.ActionType;
import com.ccb.architecture.change.model.SubsystemChangeModels.ApplicationStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeApplication;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeHistoryEvent;
import com.ccb.architecture.change.model.SubsystemChangeModels.LogicalDraft;
import com.ccb.architecture.change.model.SubsystemChangeModels.LogicalPublishedState;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalDraft;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalPublishedState;
import com.ccb.architecture.change.model.SubsystemChangeModels.PublishedStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetKind;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetLock;
import com.ccb.architecture.change.model.SubsystemChangeModels.ValueReservation;
import com.ccb.architecture.change.model.SubsystemNumberKind;
import com.ccb.architecture.change.model.SubsystemNumberReleaseReason;
import com.ccb.architecture.change.model.SubsystemNumberRequest;
import com.ccb.architecture.change.model.SubsystemNumberReservation;
import com.ccb.architecture.change.number.SubsystemNumberStrategy;
import com.ccb.architecture.change.persistence.SubsystemChangeStore;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/**
 * 架构子系统变更工单的草稿与提交准备边界。
 *
 * <p>本类不依赖 workflow 模块。提交准备通过同事务回调交给 T3；回调失败时，状态、编号和锁
 * 与 workflow start 一起回滚，不能留下无流程实例的 IN_REVIEW 工单。</p>
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
    private static final String SCOPE_LOGICAL_NAME = "LOGICAL_NAME";
    private static final String SCOPE_PHYSICAL_NAME = "PHYSICAL_NAME";
    private static final String SCOPE_PHYSICAL_ENGLISH_NAME = "PHYSICAL_ENGLISH_NAME";
    private static final String PHYSICAL_SLOTS = "123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final SubsystemChangeStore store;
    private final SubsystemNumberStrategy numberStrategy;
    private final TransactionTemplate transactions;
    private final LongSupplier idSupplier;
    private final Clock clock;

    @Autowired
    public SubsystemChangeService(SubsystemChangeStore store, SubsystemNumberStrategy numberStrategy,
                                  TransactionTemplate transactions) {
        this(store, numberStrategy, transactions,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000),
                Clock.systemUTC());
    }

    SubsystemChangeService(SubsystemChangeStore store, SubsystemNumberStrategy numberStrategy,
                           TransactionTemplate transactions,
                           LongSupplier idSupplier, Clock clock) {
        this.store = Objects.requireNonNull(store, "store 不能为空");
        this.numberStrategy = Objects.requireNonNull(numberStrategy, "numberStrategy 不能为空");
        this.transactions = Objects.requireNonNull(transactions, "transactions 不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /** 创建逻辑子系统工单草稿；仅逻辑 CREATE 可以携带 0..N 个物理草稿。 */
    public ApplicationDetail createLogical(AuthUser actor, LogicalApplicationCommand command) {
        requireActor(actor);
        LogicalApplicationCommand normalized = normalizeLogicalCommand(command);
        return inTransaction(() -> {
            ChangeApplication application = newApplication(actor, TargetKind.LOGICAL, normalized.actionType(),
                    normalized.targetId(), normalized.reason());
            LogicalDraft logicalDraft = newLogicalDraft(application, normalized.logicalDraft(), null, null, null, 0);
            List<PhysicalDraft> physicalDrafts = newLogicalPhysicalDrafts(application, normalized.physicalDrafts(),
                    Map.of(), false);
            ChangeHistoryEvent history = history(application, actor.id(), EVENT_CREATED, null, ApplicationStatus.DRAFT,
                    "已创建逻辑子系统变更草稿");

            store.insertApplication(application);
            store.replaceLogicalDraft(logicalDraft);
            store.replacePhysicalDrafts(application.tenantId(), application.id(), physicalDrafts);
            store.insertHistory(history);
            return new ApplicationDetail(application, logicalDraft, physicalDrafts, List.of(history));
        });
    }

    /** 创建物理子系统工单草稿；一个物理工单始终只有一行物理草稿。 */
    public ApplicationDetail createPhysical(AuthUser actor, PhysicalApplicationCommand command) {
        requireActor(actor);
        PhysicalApplicationCommand normalized = normalizePhysicalCommand(command);
        return inTransaction(() -> {
            ChangeApplication application = newApplication(actor, TargetKind.PHYSICAL, normalized.actionType(),
                    normalized.targetId(), normalized.reason());
            PhysicalDraft physicalDraft = newPhysicalDraft(application, normalized.physicalDraft(), null, null, null, 0);
            ChangeHistoryEvent history = history(application, actor.id(), EVENT_CREATED, null, ApplicationStatus.DRAFT,
                    "已创建物理子系统变更草稿");

            store.insertApplication(application);
            store.replacePhysicalDrafts(application.tenantId(), application.id(), List.of(physicalDraft));
            store.insertHistory(history);
            return new ApplicationDetail(application, null, List.of(physicalDraft), List.of(history));
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

    /**
     * 仅申请人本人或管理范围可更新 DRAFT/RETURNED 草稿。
     *
     * <p>申请级原因使用 Store 的状态/版本 CAS；该 CAS 成功后版本已加一，后续草稿与历史写入
     * 只在同一事务内继续，不再使用旧版本做第二次 CAS。</p>
     */
    public ApplicationDetail update(AuthUser actor, AccessScope accessScope, long applicationId,
                                    long expectedRowVersion, DraftUpdateCommand command) {
        requireActor(actor);
        requirePositive(applicationId, "工单编号");
        requireNonNegative(expectedRowVersion, "工单行版本");
        requireScope(accessScope);
        DraftUpdateCommand normalized = normalizeUpdateCommand(command);

        return inTransaction(() -> {
            ChangeApplication application = lockOwned(actor, applicationId);
            requireEditable(application);
            requireVersion(application, expectedRowVersion);
            DraftState updatedDrafts = updateDraftState(application, normalized);

            if (!store.compareAndSetApplicationReason(application.tenantId(), application.id(), application.status(),
                    application.rowVersion(), normalized.reason(), actor.id())) {
                throw conflict("工单已被其他操作更新，请刷新后重试");
            }
            long updatedRowVersion = application.rowVersion() + 1;
            if (application.status() == ApplicationStatus.RETURNED) {
                // 退回后编辑可能改变名称/英文名，旧值保留不能继续阻塞后续提交。
                store.deleteValueReservations(application.tenantId(), application.id());
            }
            if (updatedDrafts.logicalDraft() != null) {
                store.replaceLogicalDraft(updatedDrafts.logicalDraft());
            }
            store.replacePhysicalDrafts(application.tenantId(), application.id(), updatedDrafts.physicalDrafts());
            ChangeHistoryEvent history = history(application, actor.id(), EVENT_UPDATED, application.status(), application.status(),
                    "已更新工单草稿");
            store.insertHistory(history);

            ChangeApplication updatedApplication = withReasonAndRowVersion(application, normalized.reason(),
                    updatedRowVersion, actor.id());
            return new ApplicationDetail(updatedApplication, updatedDrafts.logicalDraft(),
                    updatedDrafts.physicalDrafts(), List.of(history));
        });
    }

    /**
     * DRAFT/RETURNED 可立即取消。IN_REVIEW 不伪造取消完成，必须由 T3 调用工作流终止并等待确认事件。
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
                throw conflict("工单正在审批中，需 T3 工作流终止确认后才能取消");
            }
            requireEditable(application);
            if (!store.compareAndSetApplicationStatus(application.tenantId(), application.id(), application.status(),
                    application.rowVersion(), ApplicationStatus.CANCELLED, actor.id())) {
                throw conflict("工单已被其他操作更新，请刷新后重试");
            }

            LogicalDraft logicalDraft = store.findLogicalDraft(application.tenantId(), application.id()).orElse(null);
            List<PhysicalDraft> physicalDrafts = store.findPhysicalDrafts(application.tenantId(), application.id());
            releaseReservedNumbers(application, logicalDraft, physicalDrafts, SubsystemNumberReleaseReason.CANCELLED);
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
            return new ApplicationDetail(cancelled, logicalDraft, physicalDrafts, List.of(history));
        });
    }

    /**
     * 在一个本地事务内准备提交并调用 T3 协调器。协调器抛出异常时整个事务回滚。
     * 协调器负责调用 WorkflowBusinessGateway，但本服务不直接依赖 workflow 模块。
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
        requireEditable(application);
        requireVersion(application, expectedRowVersion);

        LogicalDraft logicalDraft = store.findLogicalDraft(application.tenantId(), application.id()).orElse(null);
        List<PhysicalDraft> physicalDrafts = sortedPhysicalDrafts(
                store.findPhysicalDrafts(application.tenantId(), application.id()));
        SubmissionTarget target = validateAndLockSubmissionTarget(application, logicalDraft, physicalDrafts);
        acquireTargetLock(application);
        reserveValues(application, logicalDraft, physicalDrafts);

        NumberedDrafts numbered = reserveNumbers(application, target, logicalDraft, physicalDrafts);
        String snapshot = submittedSnapshot(application, numbered.logicalDraft(), numbered.physicalDrafts());
        String digest = sha256(snapshot);
        LogicalDraft submittedLogical = withSubmittedSnapshot(numbered.logicalDraft(), snapshot);
        List<PhysicalDraft> submittedPhysicals = numbered.physicalDrafts().stream()
                .map(draft -> withSubmittedSnapshot(draft, snapshot))
                .toList();
        if (submittedLogical != null) {
            store.replaceLogicalDraft(submittedLogical);
        }
        store.replacePhysicalDrafts(application.tenantId(), application.id(), submittedPhysicals);

        if (!store.compareAndSetApplicationStatus(application.tenantId(), application.id(), application.status(),
                application.rowVersion(), ApplicationStatus.IN_REVIEW, actor.id())) {
            throw conflict("工单已被其他操作更新，请刷新后重试");
        }
        int nextRound = Math.addExact(application.currentBusinessRound(), 1);
        store.insertHistory(history(application, actor.id(), EVENT_SUBMITTED, application.status(),
                ApplicationStatus.IN_REVIEW, nextRound, "已完成提交准备", snapshot));
        return new SubmissionPreparation(application.id(), nextRound, snapshot, digest, numbered.reservedNumbers());
    }

    /**
     * T3 工作流事件的同事务协作入口。RETURNED 保留全部资源；REJECTED 释放编号、目标锁和值保留。
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
            LogicalDraft logicalDraft = store.findLogicalDraft(tenantId, applicationId).orElse(null);
            List<PhysicalDraft> physicalDrafts = store.findPhysicalDrafts(tenantId, applicationId);
            releaseReservedNumbers(application, logicalDraft, physicalDrafts, SubsystemNumberReleaseReason.REJECTED);
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
     * 回调失败时取消标记和历史一起回滚，不能伪造已经取消。
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
        LogicalDraft logicalDraft = store.findLogicalDraft(tenantId, applicationId).orElse(null);
        List<PhysicalDraft> physicalDrafts = store.findPhysicalDrafts(tenantId, applicationId);
        releaseReservedNumbers(application, logicalDraft, physicalDrafts, SubsystemNumberReleaseReason.CANCELLED);
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
                                        List<ReservedNumber> reservedNumbers) {
        public SubmissionPreparation {
            snapshot = Objects.requireNonNull(snapshot, "snapshot 不能为空");
            digest = Objects.requireNonNull(digest, "digest 不能为空");
            reservedNumbers = List.copyOf(reservedNumbers == null ? List.of() : reservedNumbers);
        }
    }

    public record ReservedNumber(SubsystemNumberKind kind, int lineNo, Integer logicalSequence,
                                 int ordinal, String code) {
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

    public record LogicalApplicationCommand(ActionType actionType, Long targetId, String reason,
                                            LogicalDraftInput logicalDraft,
                                            List<PhysicalDraftInput> physicalDrafts) {
        public LogicalApplicationCommand {
            physicalDrafts = List.copyOf(physicalDrafts == null ? List.of() : physicalDrafts);
        }
    }

    public record PhysicalApplicationCommand(ActionType actionType, Long targetId, String reason,
                                             PhysicalDraftInput physicalDraft) {
    }

    public record DraftUpdateCommand(String reason, LogicalDraftInput logicalDraft,
                                     List<PhysicalDraftInput> physicalDrafts) {
        public DraftUpdateCommand {
            physicalDrafts = List.copyOf(physicalDrafts == null ? List.of() : physicalDrafts);
        }
    }

    public record LogicalDraftInput(String shortName, String name, Long businessOrgId,
                                    String deploymentPlatformCode, String systemTypeCode,
                                    String systemOwnershipCode, Long contactUserId,
                                    String description, String remark, Integer sortNo,
                                    Long sourceRowVersion) {
    }

    public record PhysicalDraftInput(int lineNo, Long targetLogicalSubsystemId, String shortName,
                                     String name, String englishName, String businessGroupName,
                                     Long responsibleTeamOrgId, String responsibleTeamNameSnapshot,
                                     String runtimeCode, String systemLevelCode,
                                     String developmentFrameworkCode, Long ownerUserId,
                                     String description, String remark, Long sourceRowVersion) {
    }

    public record ApplicationDetail(ChangeApplication application, LogicalDraft logicalDraft,
                                    List<PhysicalDraft> physicalDrafts,
                                    List<ChangeHistoryEvent> history) {
        public ApplicationDetail {
            application = Objects.requireNonNull(application, "application 不能为空");
            physicalDrafts = List.copyOf(physicalDrafts == null ? List.of() : physicalDrafts);
            history = List.copyOf(history == null ? List.of() : history);
        }
    }

    private LogicalApplicationCommand normalizeLogicalCommand(LogicalApplicationCommand command) {
        if (command == null) {
            throw badRequest("逻辑工单请求不能为空");
        }
        ActionType action = requireAction(command.actionType());
        if (action == ActionType.REPLACE) {
            throw badRequest("逻辑子系统不支持 REPLACE 工单");
        }
        validateTargetForAction(action, command.targetId());
        LogicalDraftInput logical = normalizeLogicalInput(command.logicalDraft(), action != ActionType.CREATE);
        List<PhysicalDraftInput> physicals = normalizePhysicalInputs(command.physicalDrafts());
        if (action != ActionType.CREATE && !physicals.isEmpty()) {
            throw badRequest("只有逻辑 CREATE 工单可以级联物理子系统草稿");
        }
        for (PhysicalDraftInput physical : physicals) {
            if (physical.targetLogicalSubsystemId() != null) {
                throw badRequest("逻辑 CREATE 的级联物理草稿不得指定已发布逻辑子系统");
            }
        }
        return new LogicalApplicationCommand(action, command.targetId(), normalizeReason(command.reason()), logical, physicals);
    }

    private PhysicalApplicationCommand normalizePhysicalCommand(PhysicalApplicationCommand command) {
        if (command == null) {
            throw badRequest("物理工单请求不能为空");
        }
        ActionType action = requireAction(command.actionType());
        validateTargetForAction(action, command.targetId());
        PhysicalDraftInput physical = normalizePhysicalInput(command.physicalDraft(), action != ActionType.CREATE);
        if (physical.targetLogicalSubsystemId() == null || physical.targetLogicalSubsystemId() <= 0) {
            throw badRequest("物理工单必须指定所属或替换目标逻辑子系统");
        }
        return new PhysicalApplicationCommand(action, command.targetId(), normalizeReason(command.reason()), physical);
    }

    private DraftUpdateCommand normalizeUpdateCommand(DraftUpdateCommand command) {
        if (command == null) {
            throw badRequest("草稿更新请求不能为空");
        }
        LogicalDraftInput logical = command.logicalDraft() == null ? null : normalizeLogicalInput(command.logicalDraft(), false);
        return new DraftUpdateCommand(normalizeReason(command.reason()), logical,
                normalizePhysicalInputs(command.physicalDrafts()));
    }

    private LogicalDraftInput normalizeLogicalInput(LogicalDraftInput input, boolean requireSourceRowVersion) {
        if (input == null) {
            throw badRequest("逻辑草稿不能为空");
        }
        Long sourceRowVersion = normalizeSourceRowVersion(input.sourceRowVersion(), requireSourceRowVersion);
        return new LogicalDraftInput(required(input.shortName(), "逻辑子系统简称", 100),
                required(input.name(), "逻辑子系统名称", 200), requiredId(input.businessOrgId(), "事业群"),
                optional(input.deploymentPlatformCode(), "部署平台", 64),
                optional(input.systemTypeCode(), "系统类型", 64),
                optional(input.systemOwnershipCode(), "系统归属", 64), requiredId(input.contactUserId(), "联系人"),
                optional(input.description(), "描述", 2_000), optional(input.remark(), "备注", 1_000),
                input.sortNo() == null ? 0 : input.sortNo(), sourceRowVersion);
    }

    private List<PhysicalDraftInput> normalizePhysicalInputs(List<PhysicalDraftInput> inputs) {
        List<PhysicalDraftInput> normalized = new ArrayList<>();
        Set<Integer> lineNumbers = new HashSet<>();
        for (PhysicalDraftInput input : inputs == null ? List.<PhysicalDraftInput>of() : inputs) {
            PhysicalDraftInput result = normalizePhysicalInput(input, false);
            if (!lineNumbers.add(result.lineNo())) {
                throw badRequest("物理草稿行号不能重复");
            }
            normalized.add(result);
        }
        return List.copyOf(normalized);
    }

    private PhysicalDraftInput normalizePhysicalInput(PhysicalDraftInput input, boolean requireSourceRowVersion) {
        if (input == null) {
            throw badRequest("物理草稿不能为空");
        }
        if (input.lineNo() <= 0) {
            throw badRequest("物理草稿行号必须为正数");
        }
        Long targetLogicalSubsystemId = input.targetLogicalSubsystemId();
        if (targetLogicalSubsystemId != null && targetLogicalSubsystemId <= 0) {
            throw badRequest("所属逻辑子系统编号无效");
        }
        Long ownerUserId = input.ownerUserId();
        if (ownerUserId != null && ownerUserId <= 0) {
            throw badRequest("负责人编号无效");
        }
        Long sourceRowVersion = normalizeSourceRowVersion(input.sourceRowVersion(), requireSourceRowVersion);
        return new PhysicalDraftInput(input.lineNo(), targetLogicalSubsystemId,
                required(input.shortName(), "物理子系统简称", 100), required(input.name(), "物理子系统名称", 200),
                optional(input.englishName(), "英文名称", 200), optional(input.businessGroupName(), "业务群组", 100),
                requiredId(input.responsibleTeamOrgId(), "负责团队"),
                required(input.responsibleTeamNameSnapshot(), "负责团队名称", 200),
                optional(input.runtimeCode(), "运行环境", 64), optional(input.systemLevelCode(), "系统级别", 64),
                optional(input.developmentFrameworkCode(), "开发框架", 64), ownerUserId,
                optional(input.description(), "描述", 2_000), optional(input.remark(), "备注", 1_000), sourceRowVersion);
    }

    private DraftState updateDraftState(ChangeApplication application, DraftUpdateCommand update) {
        if (application.targetKind() == TargetKind.LOGICAL) {
            if (update.logicalDraft() == null) {
                throw badRequest("逻辑工单更新必须包含逻辑草稿");
            }
            if (application.actionType() != ActionType.CREATE && !update.physicalDrafts().isEmpty()) {
                throw badRequest("非 CREATE 逻辑工单不能级联物理草稿");
            }
            LogicalDraft existing = store.findLogicalDraft(application.tenantId(), application.id())
                    .orElseThrow(() -> new IllegalStateException("逻辑工单缺少逻辑草稿"));
            List<PhysicalDraft> currentPhysicals = store.findPhysicalDrafts(application.tenantId(), application.id());
            LogicalDraft logical = newLogicalDraft(application, update.logicalDraft(), existing,
                    existing.reservedNumberSequence(), null, existing.draftRevision() + 1);
            List<PhysicalDraft> physicals = newLogicalPhysicalDrafts(application, update.physicalDrafts(),
                    indexByLine(currentPhysicals), application.status() == ApplicationStatus.RETURNED);
            return new DraftState(logical, physicals);
        }

        if (update.logicalDraft() != null || update.physicalDrafts().size() != 1) {
            throw badRequest("物理工单更新必须且只能包含一行物理草稿");
        }
        PhysicalDraft existing = store.findPhysicalDrafts(application.tenantId(), application.id()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("物理工单缺少物理草稿"));
        PhysicalDraftInput input = update.physicalDrafts().get(0);
        if (application.actionType() != ActionType.REPLACE
                && !Objects.equals(existing.targetLogicalSubsystemId(), input.targetLogicalSubsystemId())) {
            throw conflict("普通物理工单不得修改所属逻辑子系统，请使用 REPLACE");
        }
        PhysicalDraft physical = newPhysicalDraft(application, input, existing, existing.reservedNumberSlot(), null,
                existing.draftRevision() + 1);
        return new DraftState(null, List.of(physical));
    }

    private List<PhysicalDraft> newLogicalPhysicalDrafts(ChangeApplication application,
                                                          List<PhysicalDraftInput> inputs,
                                                          Map<Integer, PhysicalDraft> existingByLine,
                                                          boolean rejectReservedRemoval) {
        Set<Integer> requestedLines = new HashSet<>();
        List<PhysicalDraft> result = new ArrayList<>();
        for (PhysicalDraftInput input : inputs) {
            requestedLines.add(input.lineNo());
            PhysicalDraft existing = existingByLine.get(input.lineNo());
            result.add(newPhysicalDraft(application, input, existing,
                    existing == null ? null : existing.reservedNumberSlot(), null,
                    existing == null ? 0 : existing.draftRevision() + 1));
        }
        if (rejectReservedRemoval) {
            existingByLine.values().stream()
                    .filter(existing -> !requestedLines.contains(existing.lineNo()) && existing.reservedNumberSlot() != null)
                    .findFirst()
                    .ifPresent(existing -> {
                        throw conflict("退回后已保留编号的物理草稿不能删除，请取消后重新创建工单");
                    });
        }
        return List.copyOf(result);
    }

    private LogicalDraft newLogicalDraft(ChangeApplication application, LogicalDraftInput input,
                                         LogicalDraft existing, Integer reservedNumberSequence,
                                         String submittedSnapshotJson, int draftRevision) {
        Long sourceId = application.actionType() == ActionType.CREATE ? null : application.targetId();
        Long sourceVersion = existing == null ? input.sourceRowVersion() : existing.sourceRowVersion();
        return new LogicalDraft(application.id(), application.tenantId(), sourceId,
                input.shortName(), input.name(), input.businessOrgId(), input.deploymentPlatformCode(),
                input.systemTypeCode(), input.systemOwnershipCode(), input.contactUserId(), input.description(),
                input.remark(), input.sortNo(), reservedNumberSequence, sourceVersion, draftRevision,
                submittedSnapshotJson, existing == null ? now() : existing.createdAt(), now());
    }

    private PhysicalDraft newPhysicalDraft(ChangeApplication application, PhysicalDraftInput input,
                                           PhysicalDraft existing, String reservedNumberSlot,
                                           String submittedSnapshotJson, int draftRevision) {
        Long sourceId = application.targetKind() == TargetKind.PHYSICAL && application.actionType() != ActionType.CREATE
                ? application.targetId() : null;
        Long sourceVersion = existing == null ? input.sourceRowVersion() : existing.sourceRowVersion();
        return new PhysicalDraft(application.id(), input.lineNo(), application.tenantId(), sourceId,
                input.targetLogicalSubsystemId(), input.shortName(), input.name(), input.englishName(),
                input.businessGroupName(), input.responsibleTeamOrgId(), input.responsibleTeamNameSnapshot(),
                input.runtimeCode(), input.systemLevelCode(), input.developmentFrameworkCode(), input.ownerUserId(),
                input.description(), input.remark(), reservedNumberSlot, sourceVersion, draftRevision,
                submittedSnapshotJson, existing == null ? now() : existing.createdAt(), now());
    }

    private ChangeApplication newApplication(AuthUser actor, TargetKind targetKind, ActionType actionType,
                                             Long targetId, String reason) {
        LocalDateTime now = now();
        return new ChangeApplication(nextId(), actor.tenantId(), targetKind, actionType, targetId, actor.id(), reason,
                ApplicationStatus.DRAFT, 0, null, null, null, null, false, 0,
                actor.id(), actor.id(), now, now);
    }

    private SubmissionTarget validateAndLockSubmissionTarget(ChangeApplication application,
                                                             LogicalDraft logicalDraft,
                                                             List<PhysicalDraft> physicalDrafts) {
        validateTargetForAction(application.actionType(), application.targetId());
        if (application.targetKind() == TargetKind.LOGICAL) {
            if (logicalDraft == null) {
                throw new IllegalStateException("逻辑工单缺少逻辑草稿");
            }
            if (application.actionType() == ActionType.REPLACE) {
                throw badRequest("逻辑子系统不支持 REPLACE 工单");
            }
            if (application.actionType() == ActionType.CREATE) {
                requireCreateSourceEmpty(logicalDraft.sourceLogicalSubsystemId(), logicalDraft.sourceRowVersion(),
                        "逻辑 CREATE 草稿");
                for (PhysicalDraft physicalDraft : physicalDrafts) {
                    requireCreateSourceEmpty(physicalDraft.sourcePhysicalSubsystemId(), physicalDraft.sourceRowVersion(),
                            "级联物理 CREATE 草稿");
                    if (physicalDraft.targetLogicalSubsystemId() != null) {
                        throw conflict("级联物理 CREATE 草稿不得指向已发布逻辑子系统");
                    }
                }
                return new SubmissionTarget(null);
            }
            if (!physicalDrafts.isEmpty()) {
                throw conflict("非 CREATE 逻辑工单不能级联物理草稿");
            }
            LogicalPublishedState target = store.lockLogical(application.tenantId(), application.targetId())
                    .orElseThrow(() -> conflict("逻辑子系统目标不存在或不属于当前租户"));
            requireLogicalSource(application, logicalDraft, target);
            validatePublishedAction(application.actionType(), target.status(), target.deleted());
            return new SubmissionTarget(null);
        }

        if (logicalDraft != null || physicalDrafts.size() != 1) {
            throw new IllegalStateException("物理工单必须且只能包含一行物理草稿");
        }
        PhysicalDraft physicalDraft = physicalDrafts.get(0);
        if (application.actionType() == ActionType.CREATE) {
            requireCreateSourceEmpty(physicalDraft.sourcePhysicalSubsystemId(), physicalDraft.sourceRowVersion(),
                    "物理 CREATE 草稿");
            LogicalPublishedState parent = lockActiveNumberedLogical(application.tenantId(),
                    physicalDraft.targetLogicalSubsystemId(), "物理 CREATE 的所属逻辑子系统");
            return new SubmissionTarget(parent.numberSequence());
        }

        PhysicalPublishedState source = store.lockPhysical(application.tenantId(), application.targetId())
                .orElseThrow(() -> conflict("物理子系统目标不存在或不属于当前租户"));
        requirePhysicalSource(application, physicalDraft, source);
        validatePublishedAction(application.actionType(), source.status(), source.deleted());
        if (application.actionType() == ActionType.REPLACE) {
            if (Objects.equals(physicalDraft.targetLogicalSubsystemId(), source.logicalSubsystemId())) {
                throw conflict("REPLACE 必须指定不同的新目标逻辑子系统");
            }
            LogicalPublishedState replacementParent = lockActiveNumberedLogical(application.tenantId(),
                    physicalDraft.targetLogicalSubsystemId(), "REPLACE 的新目标逻辑子系统");
            return new SubmissionTarget(replacementParent.numberSequence());
        }
        if (!Objects.equals(physicalDraft.targetLogicalSubsystemId(), source.logicalSubsystemId())) {
            throw conflict("普通物理工单不得修改所属逻辑子系统，请使用 REPLACE");
        }
        return new SubmissionTarget(null);
    }

    private void requireLogicalSource(ChangeApplication application, LogicalDraft draft,
                                      LogicalPublishedState target) {
        if (!Objects.equals(draft.sourceLogicalSubsystemId(), application.targetId())
                || draft.sourceRowVersion() == null || draft.sourceRowVersion() != target.rowVersion()) {
            throw conflict("逻辑子系统来源版本已变化，请重新创建或刷新草稿");
        }
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
            throw conflict("已作废或删除的子系统不能执行该动作");
        }
        if (action == ActionType.OFFLINE && status != PublishedStatus.ACTIVE) {
            throw conflict("只有 ACTIVE 子系统可以下线");
        }
        if (action == ActionType.REACTIVATE && status != PublishedStatus.OFFLINE) {
            throw conflict("只有 OFFLINE 子系统可以重新上线");
        }
    }

    private LogicalPublishedState lockActiveNumberedLogical(long tenantId, Long logicalId, String label) {
        if (logicalId == null || logicalId <= 0) {
            throw conflict(label + "不能为空");
        }
        LogicalPublishedState logical = store.lockLogical(tenantId, logicalId)
                .orElseThrow(() -> conflict(label + "不存在或不属于当前租户"));
        if (logical.deleted() || logical.status() != PublishedStatus.ACTIVE || logical.numberSequence() == null) {
            throw conflict(label + "必须是已编号的 ACTIVE 逻辑子系统");
        }
        return logical;
    }

    private LogicalPublishedState lockNumberedLogicalForRelease(long tenantId, Long logicalId) {
        if (logicalId == null || logicalId <= 0) {
            throw new IllegalStateException("编号所属逻辑子系统不能为空");
        }
        LogicalPublishedState logical = store.lockLogical(tenantId, logicalId)
                .orElseThrow(() -> new IllegalStateException("编号所属逻辑子系统不存在，无法释放物理编号"));
        if (logical.numberSequence() == null) {
            throw new IllegalStateException("编号所属逻辑子系统缺少编号序号，无法释放物理编号");
        }
        return logical;
    }

    private void acquireTargetLock(ChangeApplication application) {
        if (application.actionType() == ActionType.CREATE) {
            return;
        }
        TargetLock current = store.findTargetLock(application.tenantId(), application.targetKind(),
                application.targetId()).orElse(null);
        if (current != null) {
            if (current.applicationId() != application.id()) {
                throw conflict("目标子系统已被其他工单锁定");
            }
            return;
        }
        try {
            store.insertTargetLock(new TargetLock(application.tenantId(), application.targetKind(),
                    application.targetId(), application.id(), now()));
        } catch (DuplicateKeyException exception) {
            throw conflict("目标子系统已被其他工单锁定");
        }
    }

    private void reserveValues(ChangeApplication application, LogicalDraft logicalDraft,
                               List<PhysicalDraft> physicalDrafts) {
        if (logicalDraft != null) {
            reserveValue(application, SCOPE_LOGICAL_NAME, logicalDraft.name(), 0);
        }
        for (PhysicalDraft physicalDraft : physicalDrafts) {
            reserveValue(application, SCOPE_PHYSICAL_NAME, physicalDraft.name(), physicalDraft.lineNo());
            reserveValue(application, SCOPE_PHYSICAL_ENGLISH_NAME, physicalDraft.englishName(),
                    physicalDraft.lineNo());
        }
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

    private NumberedDrafts reserveNumbers(ChangeApplication application, SubmissionTarget target,
                                          LogicalDraft logicalDraft, List<PhysicalDraft> physicalDrafts) {
        List<ReservedNumber> reservations = new ArrayList<>();
        LogicalDraft numberedLogical = logicalDraft;
        List<PhysicalDraft> numberedPhysicals = new ArrayList<>(physicalDrafts);
        if (application.targetKind() == TargetKind.LOGICAL && application.actionType() == ActionType.CREATE) {
            SubsystemNumberReservation logicalReservation = reserveNumber(
                    SubsystemNumberRequest.logical(application.tenantId(), application.id()),
                    logicalDraft.reservedNumberSequence());
            numberedLogical = withReservedNumber(logicalDraft, logicalReservation.ordinal());
            reservations.add(project(logicalReservation));
            numberedPhysicals.clear();
            for (PhysicalDraft physicalDraft : physicalDrafts) {
                SubsystemNumberReservation physicalReservation = reserveNumber(
                        SubsystemNumberRequest.physical(application.tenantId(), application.id(),
                                physicalDraft.lineNo(), logicalReservation.ordinal()),
                        physicalDraft.reservedNumberSlot() == null
                                ? null : slotOrdinal(physicalDraft.reservedNumberSlot()));
                numberedPhysicals.add(withReservedNumber(physicalDraft,
                        slotForOrdinal(physicalReservation.ordinal())));
                reservations.add(project(physicalReservation));
            }
        } else if (application.targetKind() == TargetKind.PHYSICAL
                && (application.actionType() == ActionType.CREATE || application.actionType() == ActionType.REPLACE)) {
            PhysicalDraft physicalDraft = physicalDrafts.get(0);
            SubsystemNumberReservation reservation = reserveNumber(
                    SubsystemNumberRequest.physical(application.tenantId(), application.id(), physicalDraft.lineNo(),
                            Objects.requireNonNull(target.numberingLogicalSequence())),
                    physicalDraft.reservedNumberSlot() == null
                            ? null : slotOrdinal(physicalDraft.reservedNumberSlot()));
            numberedPhysicals = List.of(withReservedNumber(physicalDraft, slotForOrdinal(reservation.ordinal())));
            reservations.add(project(reservation));
        }
        return new NumberedDrafts(numberedLogical, sortedPhysicalDrafts(numberedPhysicals), reservations);
    }

    private SubsystemNumberReservation reserveNumber(SubsystemNumberRequest request, Integer retainedOrdinal) {
        SubsystemNumberReservation reservation = numberStrategy.reserve(request);
        if (reservation.tenantId() != request.tenantId()
                || reservation.applicationId() != request.applicationId()
                || reservation.lineNo() != request.lineNo()
                || reservation.kind() != request.kind()
                || !Objects.equals(reservation.logicalSequence(), request.logicalSequence())
                || reservation.code() == null || reservation.code().isBlank()) {
            throw new IllegalStateException("编号策略返回了不匹配的保留记录");
        }
        if (retainedOrdinal != null && retainedOrdinal != reservation.ordinal()) {
            throw conflict("退回工单的保留编号发生变化，禁止重提");
        }
        return reservation;
    }

    private ReservedNumber project(SubsystemNumberReservation reservation) {
        return new ReservedNumber(reservation.kind(), reservation.lineNo(), reservation.logicalSequence(),
                reservation.ordinal(), reservation.code());
    }

    private LogicalDraft withReservedNumber(LogicalDraft draft, int sequence) {
        return new LogicalDraft(draft.applicationId(), draft.tenantId(), draft.sourceLogicalSubsystemId(),
                draft.shortName(), draft.name(), draft.businessOrgId(), draft.deploymentPlatformCode(),
                draft.systemTypeCode(), draft.systemOwnershipCode(), draft.contactUserId(), draft.description(),
                draft.remark(), draft.sortNo(), sequence, draft.sourceRowVersion(), draft.draftRevision(),
                draft.submittedSnapshotJson(), draft.createdAt(), now());
    }

    private PhysicalDraft withReservedNumber(PhysicalDraft draft, String slot) {
        return new PhysicalDraft(draft.applicationId(), draft.lineNo(), draft.tenantId(),
                draft.sourcePhysicalSubsystemId(), draft.targetLogicalSubsystemId(), draft.shortName(), draft.name(),
                draft.englishName(), draft.businessGroupName(), draft.responsibleTeamOrgId(),
                draft.responsibleTeamNameSnapshot(), draft.runtimeCode(), draft.systemLevelCode(),
                draft.developmentFrameworkCode(), draft.ownerUserId(), draft.description(), draft.remark(), slot,
                draft.sourceRowVersion(), draft.draftRevision(), draft.submittedSnapshotJson(), draft.createdAt(), now());
    }

    private LogicalDraft withSubmittedSnapshot(LogicalDraft draft, String snapshot) {
        if (draft == null) {
            return null;
        }
        return new LogicalDraft(draft.applicationId(), draft.tenantId(), draft.sourceLogicalSubsystemId(),
                draft.shortName(), draft.name(), draft.businessOrgId(), draft.deploymentPlatformCode(),
                draft.systemTypeCode(), draft.systemOwnershipCode(), draft.contactUserId(), draft.description(),
                draft.remark(), draft.sortNo(), draft.reservedNumberSequence(), draft.sourceRowVersion(),
                draft.draftRevision(), snapshot, draft.createdAt(), now());
    }

    private PhysicalDraft withSubmittedSnapshot(PhysicalDraft draft, String snapshot) {
        return new PhysicalDraft(draft.applicationId(), draft.lineNo(), draft.tenantId(),
                draft.sourcePhysicalSubsystemId(), draft.targetLogicalSubsystemId(), draft.shortName(), draft.name(),
                draft.englishName(), draft.businessGroupName(), draft.responsibleTeamOrgId(),
                draft.responsibleTeamNameSnapshot(), draft.runtimeCode(), draft.systemLevelCode(),
                draft.developmentFrameworkCode(), draft.ownerUserId(), draft.description(), draft.remark(),
                draft.reservedNumberSlot(), draft.sourceRowVersion(), draft.draftRevision(), snapshot,
                draft.createdAt(), now());
    }

    private void releaseReservedNumbers(ChangeApplication application, LogicalDraft logicalDraft,
                                        List<PhysicalDraft> physicalDrafts, SubsystemNumberReleaseReason reason) {
        if (application.targetKind() == TargetKind.LOGICAL && application.actionType() == ActionType.CREATE) {
            Integer logicalSequence = logicalDraft == null ? null : logicalDraft.reservedNumberSequence();
            if (logicalSequence != null) {
                releaseNumber(SubsystemNumberRequest.logical(application.tenantId(), application.id()),
                        logicalSequence, reason);
            }
            for (PhysicalDraft physicalDraft : physicalDrafts) {
                if (physicalDraft.reservedNumberSlot() == null) {
                    continue;
                }
                if (logicalSequence == null) {
                    throw new IllegalStateException("级联物理编号缺少父逻辑保留序号");
                }
                releaseNumber(SubsystemNumberRequest.physical(application.tenantId(), application.id(),
                                physicalDraft.lineNo(), logicalSequence),
                        slotOrdinal(physicalDraft.reservedNumberSlot()), reason);
            }
            return;
        }
        if (application.targetKind() == TargetKind.PHYSICAL
                && (application.actionType() == ActionType.CREATE || application.actionType() == ActionType.REPLACE)
                && !physicalDrafts.isEmpty() && physicalDrafts.get(0).reservedNumberSlot() != null) {
            PhysicalDraft physicalDraft = physicalDrafts.get(0);
            LogicalPublishedState parent = lockNumberedLogicalForRelease(application.tenantId(),
                    physicalDraft.targetLogicalSubsystemId());
            releaseNumber(SubsystemNumberRequest.physical(application.tenantId(), application.id(),
                            physicalDraft.lineNo(), parent.numberSequence()),
                    slotOrdinal(physicalDraft.reservedNumberSlot()), reason);
        }
    }

    private void releaseNumber(SubsystemNumberRequest request, int ordinal,
                               SubsystemNumberReleaseReason reason) {
        SubsystemNumberReservation reservation = SubsystemNumberReservation.unformatted(request, ordinal)
                .withCode(formatCode(request.kind(), request.logicalSequence(), ordinal));
        numberStrategy.release(reservation, reason);
    }

    private String formatCode(SubsystemNumberKind kind, Integer logicalSequence, int ordinal) {
        if (kind == SubsystemNumberKind.LOGICAL) {
            return String.format(Locale.ROOT, "A%04d", ordinal);
        }
        return String.format(Locale.ROOT, "W%04d%s", Objects.requireNonNull(logicalSequence),
                slotForOrdinal(ordinal));
    }

    private String slotForOrdinal(int ordinal) {
        if (ordinal < 1 || ordinal > PHYSICAL_SLOTS.length()) {
            throw new IllegalArgumentException("物理编号槽位必须在 1..35 范围内");
        }
        return String.valueOf(PHYSICAL_SLOTS.charAt(ordinal - 1));
    }

    private int slotOrdinal(String slot) {
        if (slot == null || slot.length() != 1) {
            throw new IllegalStateException("物理保留编号槽位无效");
        }
        int index = PHYSICAL_SLOTS.indexOf(slot.toUpperCase(Locale.ROOT));
        if (index < 0) {
            throw new IllegalStateException("物理保留编号槽位无效");
        }
        return index + 1;
    }

    private List<PhysicalDraft> sortedPhysicalDrafts(List<PhysicalDraft> drafts) {
        return drafts.stream().sorted(Comparator.comparingInt(PhysicalDraft::lineNo)).toList();
    }

    private String submittedSnapshot(ChangeApplication application, LogicalDraft logicalDraft,
                                     List<PhysicalDraft> physicalDrafts) {
        StringBuilder canonical = new StringBuilder();
        appendCanonical(canonical, "applicationId", application.id());
        appendCanonical(canonical, "tenantId", application.tenantId());
        appendCanonical(canonical, "targetKind", application.targetKind());
        appendCanonical(canonical, "actionType", application.actionType());
        appendCanonical(canonical, "targetId", application.targetId());
        appendCanonical(canonical, "applicantId", application.applicantId());
        appendCanonical(canonical, "reason", application.reason());
        if (logicalDraft != null) {
            appendCanonical(canonical, "logical.shortName", logicalDraft.shortName());
            appendCanonical(canonical, "logical.name", logicalDraft.name());
            appendCanonical(canonical, "logical.businessOrgId", logicalDraft.businessOrgId());
            appendCanonical(canonical, "logical.deploymentPlatformCode", logicalDraft.deploymentPlatformCode());
            appendCanonical(canonical, "logical.systemTypeCode", logicalDraft.systemTypeCode());
            appendCanonical(canonical, "logical.systemOwnershipCode", logicalDraft.systemOwnershipCode());
            appendCanonical(canonical, "logical.contactUserId", logicalDraft.contactUserId());
            appendCanonical(canonical, "logical.description", logicalDraft.description());
            appendCanonical(canonical, "logical.remark", logicalDraft.remark());
            appendCanonical(canonical, "logical.sortNo", logicalDraft.sortNo());
            appendCanonical(canonical, "logical.reservedNumberSequence", logicalDraft.reservedNumberSequence());
            appendCanonical(canonical, "logical.sourceRowVersion", logicalDraft.sourceRowVersion());
        }
        for (PhysicalDraft physicalDraft : sortedPhysicalDrafts(physicalDrafts)) {
            String prefix = "physical[" + physicalDraft.lineNo() + "].";
            appendCanonical(canonical, prefix + "targetLogicalSubsystemId", physicalDraft.targetLogicalSubsystemId());
            appendCanonical(canonical, prefix + "shortName", physicalDraft.shortName());
            appendCanonical(canonical, prefix + "name", physicalDraft.name());
            appendCanonical(canonical, prefix + "englishName", physicalDraft.englishName());
            appendCanonical(canonical, prefix + "businessGroupName", physicalDraft.businessGroupName());
            appendCanonical(canonical, prefix + "responsibleTeamOrgId", physicalDraft.responsibleTeamOrgId());
            appendCanonical(canonical, prefix + "responsibleTeamNameSnapshot",
                    physicalDraft.responsibleTeamNameSnapshot());
            appendCanonical(canonical, prefix + "runtimeCode", physicalDraft.runtimeCode());
            appendCanonical(canonical, prefix + "systemLevelCode", physicalDraft.systemLevelCode());
            appendCanonical(canonical, prefix + "developmentFrameworkCode", physicalDraft.developmentFrameworkCode());
            appendCanonical(canonical, prefix + "ownerUserId", physicalDraft.ownerUserId());
            appendCanonical(canonical, prefix + "description", physicalDraft.description());
            appendCanonical(canonical, prefix + "remark", physicalDraft.remark());
            appendCanonical(canonical, prefix + "reservedNumberSlot", physicalDraft.reservedNumberSlot());
            appendCanonical(canonical, prefix + "sourceRowVersion", physicalDraft.sourceRowVersion());
        }
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
                store.findLogicalDraft(application.tenantId(), application.id()).orElse(null),
                store.findPhysicalDrafts(application.tenantId(), application.id()),
                store.listHistory(application.tenantId(), application.id()));
    }

    private ChangeApplication loadAccessible(AuthUser actor, AccessScope scope, long applicationId) {
        ChangeApplication application = store.findApplication(actor.tenantId(), applicationId)
                .orElseThrow(() -> notFound(applicationId));
        requireAccess(actor, scope, application);
        return application;
    }

    private ChangeApplication lockAccessible(AuthUser actor, AccessScope scope, long applicationId) {
        ChangeApplication application = store.lockApplication(actor.tenantId(), applicationId)
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

    private Map<Integer, PhysicalDraft> indexByLine(List<PhysicalDraft> drafts) {
        Map<Integer, PhysicalDraft> indexed = new HashMap<>();
        for (PhysicalDraft draft : drafts) {
            indexed.put(draft.lineNo(), draft);
        }
        return indexed;
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

    private record DraftState(LogicalDraft logicalDraft, List<PhysicalDraft> physicalDrafts) {
        private DraftState {
            physicalDrafts = List.copyOf(physicalDrafts);
        }
    }

    private record SubmissionTarget(Integer numberingLogicalSequence) {
    }

    private record NumberedDrafts(LogicalDraft logicalDraft, List<PhysicalDraft> physicalDrafts,
                                  List<ReservedNumber> reservedNumbers) {
        private NumberedDrafts {
            physicalDrafts = List.copyOf(physicalDrafts);
            reservedNumbers = List.copyOf(reservedNumbers);
        }
    }
}
