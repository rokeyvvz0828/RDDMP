package com.ccb.architecture.change.service;

import com.ccb.architecture.change.model.SubsystemChangeModels.ActionType;
import com.ccb.architecture.change.model.SubsystemChangeModels.ApplicationStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeApplication;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeHistoryEvent;
import com.ccb.architecture.change.model.SubsystemChangeModels.LogicalDraft;
import com.ccb.architecture.change.model.SubsystemChangeModels.LogicalPublishedState;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalDraft;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalPublishedState;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalReplacement;
import com.ccb.architecture.change.model.SubsystemChangeModels.PublishedStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetLock;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetKind;
import com.ccb.architecture.change.model.SubsystemNumberKind;
import com.ccb.architecture.change.model.SubsystemNumberRequest;
import com.ccb.architecture.change.model.SubsystemNumberReservation;
import com.ccb.architecture.change.number.SubsystemNumberStrategy;
import com.ccb.architecture.change.persistence.SubsystemChangeStore;
import com.ccb.architecture.integration.ReferenceCheckRequest;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * 工作流批准事件的发布边界。
 *
 * <p>本服务不暴露 HTTP 入口，也不负责判断工作流当前处理人；只有可信的工作流生命周期消费者在完成
 * 任务、实例和摘要校验后才可调用 {@link #approve(ApprovalCommand, AuthUser)}。申请、源记录、父级、
 * 编号、引用和行版本均在批准事务内重新锁定并校验。</p>
 */
@Service
public class SubsystemPublicationService {
    private static final String PHYSICAL_SLOTS = "123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final AtomicLong GENERATED_IDENTIFIERS = new AtomicLong(System.currentTimeMillis() * 1_000L);

    private final SubsystemChangeStore store;
    private final SubsystemNumberStrategy numberStrategy;
    private final SubsystemReferenceGuard referenceGuard;
    private final TransactionTemplate transactions;
    private final LongSupplier identifierSupplier;

    @Autowired
    public SubsystemPublicationService(SubsystemChangeStore store,
                                       SubsystemNumberStrategy numberStrategy,
                                       SubsystemReferenceGuard referenceGuard,
                                       TransactionTemplate transactions) {
        this(store, numberStrategy, referenceGuard, transactions,
                SubsystemPublicationService::nextGeneratedIdentifier);
    }

    /** 仅供同包测试注入稳定标识；生产装配使用上方构造器。 */
    SubsystemPublicationService(SubsystemChangeStore store,
                                SubsystemNumberStrategy numberStrategy,
                                SubsystemReferenceGuard referenceGuard,
                                TransactionTemplate transactions,
                                LongSupplier identifierSupplier) {
        this.store = Objects.requireNonNull(store, "store 不能为空");
        this.numberStrategy = Objects.requireNonNull(numberStrategy, "numberStrategy 不能为空");
        this.referenceGuard = Objects.requireNonNull(referenceGuard, "referenceGuard 不能为空");
        this.transactions = Objects.requireNonNull(transactions, "transactions 不能为空");
        this.identifierSupplier = Objects.requireNonNull(identifierSupplier, "identifierSupplier 不能为空");
    }

    /**
     * 在同一真实事务中锁定申请、校验可信工作流上下文、标记 APPROVED，并发布主记录变更。
     * 审批命令不携带任何业务字段，所有发布值均只从已提交草稿读取。
     */
    public ApprovalResult approve(ApprovalCommand command, AuthUser workflowOperator) {
        Objects.requireNonNull(command, "command 不能为空");
        requireOperator(workflowOperator);
        try {
            ApprovalResult result = transactions.execute(status -> approveInTransaction(command, workflowOperator));
            if (result == null) {
                throw new IllegalStateException("批准事务未返回发布结果");
            }
            return result;
        } catch (DuplicateKeyException exception) {
            // 异常离开 TransactionTemplate 回调后才转换，确保事务已被标记并完成回滚。
            throw conflict("发布记录的唯一编号或名称已被占用");
        }
    }

    private ApprovalResult approveInTransaction(ApprovalCommand command, AuthUser operator) {
        ChangeApplication application = store.lockApplication(operator.tenantId(), command.applicationId())
                .orElseThrow(() -> conflict("变更申请不存在、跨租户或已被删除"));
        verifyApprovalContext(application, command);

        PreparedPublication publication = switch (application.targetKind()) {
            case LOGICAL -> prepareLogicalPublication(application, operator);
            case PHYSICAL -> preparePhysicalPublication(application, operator);
        };

        // CAS 在主记录写入前完成；其后任一步失败都会由整个事务回滚，不会留下 APPROVED 半状态。
        if (!store.compareAndSetApplicationStatus(application.tenantId(), application.id(),
                ApplicationStatus.IN_REVIEW, command.expectedApplicationRowVersion(),
                ApplicationStatus.APPROVED, operator.id())) {
            throw conflict("变更申请状态或版本已变化，请使用最新工作流事件重试");
        }

        publication.writer().run();
        for (SubsystemNumberReservation reservation : publication.reservations()) {
            numberStrategy.consume(reservation);
        }
        if (application.targetId() != null) {
            store.deleteTargetLock(application.tenantId(), application.targetKind(), application.targetId(),
                    application.id());
        }
        store.deleteValueReservations(application.tenantId(), application.id());
        store.insertHistory(new ChangeHistoryEvent(
                nextIdentifier(), application.tenantId(), application.id(), "APPROVED_PUBLISHED",
                ApplicationStatus.IN_REVIEW, ApplicationStatus.APPROVED, application.currentBusinessRound(),
                safeHistorySummary(application), null, null, operator.id(), LocalDateTime.now()));

        return new ApprovalResult(application.id(), publication.logicalSubsystemId(),
                publication.physicalSubsystemIds());
    }

    private PreparedPublication prepareLogicalPublication(ChangeApplication application, AuthUser operator) {
        return switch (application.actionType()) {
            case CREATE -> prepareLogicalCreate(application, operator);
            case UPDATE -> prepareLogicalUpdate(application, operator);
            case OFFLINE -> prepareLogicalStatusChange(application, operator, PublishedStatus.ACTIVE,
                    PublishedStatus.OFFLINE, ReferenceCheckRequest.Operation.OFFLINE);
            case REACTIVATE -> prepareLogicalStatusChange(application, operator, PublishedStatus.OFFLINE,
                    PublishedStatus.ACTIVE, null);
            case VOID -> prepareLogicalVoid(application, operator);
            case REPLACE -> throw conflict("REPLACE 仅支持物理子系统");
        };
    }

    private PreparedPublication prepareLogicalCreate(ChangeApplication application, AuthUser operator) {
        requireCreateWithoutPublishedTarget(application);
        LogicalDraft logicalDraft = store.findLogicalDraft(application.tenantId(), application.id())
                .orElseThrow(() -> conflict("逻辑新增申请缺少逻辑草稿"));
        verifyLogicalCreateDraft(application, logicalDraft);
        List<PhysicalDraft> physicalDrafts = store.findPhysicalDrafts(application.tenantId(), application.id());

        SubsystemNumberReservation logicalReservation = numberStrategy.reserve(
                SubsystemNumberRequest.logical(application.tenantId(), application.id()));
        verifyLogicalReservation(application, logicalDraft, logicalReservation);
        long logicalSubsystemId = nextIdentifier();

        List<PendingPhysicalCreate> pendingPhysical = new ArrayList<>();
        int previousLineNo = 0;
        for (PhysicalDraft draft : physicalDrafts) {
            verifyLogicalCreatePhysicalDraft(application, draft, previousLineNo);
            previousLineNo = draft.lineNo();
            SubsystemNumberReservation reservation = numberStrategy.reserve(SubsystemNumberRequest.physical(
                    application.tenantId(), application.id(), draft.lineNo(), logicalReservation.ordinal()));
            verifyPhysicalReservation(application, draft, logicalReservation.ordinal(), reservation);
            pendingPhysical.add(new PendingPhysicalCreate(nextIdentifier(), draft, reservation));
        }

        List<SubsystemNumberReservation> reservations = new ArrayList<>();
        reservations.add(logicalReservation);
        reservations.addAll(pendingPhysical.stream().map(PendingPhysicalCreate::reservation).toList());
        List<Long> physicalIds = pendingPhysical.stream().map(PendingPhysicalCreate::id).toList();
        return new PreparedPublication(logicalSubsystemId, physicalIds, reservations, () -> {
            store.insertLogicalPublished(logicalSubsystemId, application.tenantId(), logicalReservation.code(),
                    logicalReservation.ordinal(), logicalDraft, PublishedStatus.ACTIVE, 0L, operator.id());
            for (PendingPhysicalCreate physical : pendingPhysical) {
                store.insertPhysicalPublished(physical.id(), application.tenantId(), physical.reservation().code(),
                        slotFor(physical.reservation().ordinal()), logicalSubsystemId, physical.draft(),
                        PublishedStatus.ACTIVE, 0L, operator.id());
            }
        });
    }

    private PreparedPublication preparePhysicalPublication(ChangeApplication application, AuthUser operator) {
        return switch (application.actionType()) {
            case CREATE -> preparePhysicalCreate(application, operator);
            case UPDATE -> preparePhysicalUpdate(application, operator);
            case OFFLINE -> preparePhysicalStatusChange(application, operator, PublishedStatus.ACTIVE,
                    PublishedStatus.OFFLINE, ReferenceCheckRequest.Operation.OFFLINE);
            case REACTIVATE -> preparePhysicalStatusChange(application, operator, PublishedStatus.OFFLINE,
                    PublishedStatus.ACTIVE, null);
            case VOID -> preparePhysicalVoid(application, operator);
            case REPLACE -> preparePhysicalReplace(application, operator);
        };
    }

    private PreparedPublication preparePhysicalCreate(ChangeApplication application, AuthUser operator) {
        requireCreateWithoutPublishedTarget(application);
        if (store.findLogicalDraft(application.tenantId(), application.id()).isPresent()) {
            throw conflict("物理新增申请不得携带逻辑草稿");
        }
        List<PhysicalDraft> physicalDrafts = store.findPhysicalDrafts(application.tenantId(), application.id());
        if (physicalDrafts.size() != 1) {
            throw conflict("物理新增申请必须且只能包含一条物理草稿");
        }
        PhysicalDraft draft = physicalDrafts.get(0);
        verifyStandalonePhysicalCreateDraft(application, draft);
        LogicalPublishedState parent = store.lockLogical(application.tenantId(), draft.targetLogicalSubsystemId())
                .orElseThrow(() -> conflict("目标逻辑子系统不存在、跨租户或已被删除"));
        if (parent.tenantId() != application.tenantId() || parent.id() != draft.targetLogicalSubsystemId()
                || parent.deleted() || parent.status() != PublishedStatus.ACTIVE || parent.numberSequence() == null) {
            throw conflict("目标逻辑子系统必须为具有编号的 ACTIVE 发布记录");
        }

        SubsystemNumberReservation reservation = numberStrategy.reserve(SubsystemNumberRequest.physical(
                application.tenantId(), application.id(), draft.lineNo(), parent.numberSequence()));
        verifyPhysicalReservation(application, draft, parent.numberSequence(), reservation);
        long physicalSubsystemId = nextIdentifier();
        return new PreparedPublication(null, List.of(physicalSubsystemId), List.of(reservation), () ->
                store.insertPhysicalPublished(physicalSubsystemId, application.tenantId(), reservation.code(),
                        slotFor(reservation.ordinal()), parent.id(), draft, PublishedStatus.ACTIVE, 0L, operator.id()));
    }

    private PreparedPublication prepareLogicalUpdate(ChangeApplication application, AuthUser operator) {
        ExistingLogicalContext context = lockExistingLogical(application);
        requireMutableStatus(context.target().status(), "逻辑子系统");
        return new PreparedPublication(context.target().id(), List.of(), List.of(), () -> {
            if (!store.updateLogicalPublishedFields(application.tenantId(), context.target().id(), context.draft(),
                    context.target().rowVersion(), operator.id())) {
                throw conflict("逻辑子系统源行版本已变化，字段更新失败");
            }
        });
    }

    private PreparedPublication prepareLogicalStatusChange(ChangeApplication application, AuthUser operator,
                                                            PublishedStatus requiredStatus,
                                                            PublishedStatus targetStatus,
                                                            ReferenceCheckRequest.Operation guardOperation) {
        ExistingLogicalContext context = lockExistingLogical(application);
        requireExactStatus(context.target().status(), requiredStatus, application.actionType(), "逻辑子系统");
        if (guardOperation != null) {
            referenceGuard.requireClear(new ReferenceCheckRequest(application.tenantId(),
                    ReferenceCheckRequest.SubsystemKind.LOGICAL, context.target().id(), guardOperation));
        }
        return new PreparedPublication(context.target().id(), List.of(), List.of(), () -> {
            if (!store.updateLogicalPublishedStatus(application.tenantId(), context.target().id(), targetStatus,
                    context.target().rowVersion(), operator.id())) {
                throw conflict("逻辑子系统源行版本已变化，状态更新失败");
            }
        });
    }

    private PreparedPublication prepareLogicalVoid(ChangeApplication application, AuthUser operator) {
        ExistingLogicalContext context = lockExistingLogical(application);
        requireVoidableStatus(context.target().status(), "逻辑子系统");
        referenceGuard.requireClear(new ReferenceCheckRequest(application.tenantId(),
                ReferenceCheckRequest.SubsystemKind.LOGICAL, context.target().id(),
                ReferenceCheckRequest.Operation.VOID));
        return new PreparedPublication(context.target().id(), List.of(), List.of(), () -> {
            if (!store.updateLogicalPublishedStatus(application.tenantId(), context.target().id(),
                    PublishedStatus.VOIDED, context.target().rowVersion(), operator.id())) {
                throw conflict("逻辑子系统源行版本已变化，作废失败");
            }
        });
    }

    private PreparedPublication preparePhysicalUpdate(ChangeApplication application, AuthUser operator) {
        ExistingPhysicalContext context = lockExistingPhysical(application, false);
        requireMutableStatus(context.target().status(), "物理子系统");
        return new PreparedPublication(null, List.of(context.target().id()), List.of(), () -> {
            if (!store.updatePhysicalPublishedFields(application.tenantId(), context.target().id(), context.draft(),
                    context.target().rowVersion(), operator.id())) {
                throw conflict("物理子系统源行版本已变化，字段更新失败");
            }
        });
    }

    private PreparedPublication preparePhysicalStatusChange(ChangeApplication application, AuthUser operator,
                                                             PublishedStatus requiredStatus,
                                                             PublishedStatus targetStatus,
                                                             ReferenceCheckRequest.Operation guardOperation) {
        ExistingPhysicalContext context = lockExistingPhysical(application, false);
        requireExactStatus(context.target().status(), requiredStatus, application.actionType(), "物理子系统");
        if (targetStatus == PublishedStatus.ACTIVE) {
            requireActiveNumberedParent(context.targetParent(), "重新启用物理子系统");
        }
        if (guardOperation != null) {
            referenceGuard.requireClear(new ReferenceCheckRequest(application.tenantId(),
                    ReferenceCheckRequest.SubsystemKind.PHYSICAL, context.target().id(), guardOperation));
        }
        return new PreparedPublication(null, List.of(context.target().id()), List.of(), () -> {
            if (!store.updatePhysicalPublishedStatus(application.tenantId(), context.target().id(), targetStatus,
                    context.target().rowVersion(), operator.id())) {
                throw conflict("物理子系统源行版本已变化，状态更新失败");
            }
        });
    }

    private PreparedPublication preparePhysicalVoid(ChangeApplication application, AuthUser operator) {
        ExistingPhysicalContext context = lockExistingPhysical(application, false);
        requireVoidableStatus(context.target().status(), "物理子系统");
        referenceGuard.requireClear(new ReferenceCheckRequest(application.tenantId(),
                ReferenceCheckRequest.SubsystemKind.PHYSICAL, context.target().id(),
                ReferenceCheckRequest.Operation.VOID));
        return new PreparedPublication(null, List.of(context.target().id()), List.of(), () -> {
            if (!store.updatePhysicalPublishedStatus(application.tenantId(), context.target().id(),
                    PublishedStatus.VOIDED, context.target().rowVersion(), operator.id())) {
                throw conflict("物理子系统源行版本已变化，作废失败");
            }
        });
    }

    private PreparedPublication preparePhysicalReplace(ChangeApplication application, AuthUser operator) {
        ExistingPhysicalContext context = lockExistingPhysical(application, true);
        requireExactStatus(context.target().status(), PublishedStatus.ACTIVE, ActionType.REPLACE, "物理子系统");
        requireActiveNumberedParent(context.targetParent(), "替换物理子系统");
        referenceGuard.requireClear(new ReferenceCheckRequest(application.tenantId(),
                ReferenceCheckRequest.SubsystemKind.PHYSICAL, context.target().id(),
                ReferenceCheckRequest.Operation.OFFLINE));

        SubsystemNumberReservation reservation = numberStrategy.reserve(SubsystemNumberRequest.physical(
                application.tenantId(), application.id(), context.draft().lineNo(),
                context.targetParent().numberSequence()));
        verifyPhysicalReservation(application, context.draft(), context.targetParent().numberSequence(), reservation);
        long newPhysicalSubsystemId = nextIdentifier();
        long replacementId = nextIdentifier();
        return new PreparedPublication(null, List.of(newPhysicalSubsystemId), List.of(reservation), () -> {
            store.insertPhysicalPublished(newPhysicalSubsystemId, application.tenantId(), reservation.code(),
                    slotFor(reservation.ordinal()), context.targetParent().id(), context.draft(),
                    PublishedStatus.ACTIVE, 0L, operator.id());
            if (!store.updatePhysicalPublishedStatus(application.tenantId(), context.target().id(),
                    PublishedStatus.OFFLINE, context.target().rowVersion(), operator.id())) {
                throw conflict("被替换物理子系统源行版本已变化，下线失败");
            }
            store.insertPhysicalReplacement(new PhysicalReplacement(replacementId, application.tenantId(),
                    context.target().id(), newPhysicalSubsystemId, application.id(), LocalDateTime.now()));
        });
    }

    private ExistingLogicalContext lockExistingLogical(ChangeApplication application) {
        long targetId = requireExistingTarget(application);
        verifyOwnedTargetLock(application, targetId);
        LogicalDraft draft = store.findLogicalDraft(application.tenantId(), application.id())
                .orElseThrow(() -> conflict("逻辑变更申请缺少已提交逻辑草稿"));
        if (!store.findPhysicalDrafts(application.tenantId(), application.id()).isEmpty()) {
            throw conflict("非新增逻辑申请不得携带物理草稿");
        }
        LogicalPublishedState target = store.lockLogical(application.tenantId(), targetId)
                .orElseThrow(() -> conflict("逻辑子系统源记录不存在或跨租户"));
        if (target.tenantId() != application.tenantId() || target.id() != targetId || target.deleted()) {
            throw conflict("逻辑子系统源记录租户、编号或删除状态无效");
        }
        if (draft.applicationId() != application.id() || draft.tenantId() != application.tenantId()
                || !Objects.equals(draft.sourceLogicalSubsystemId(), targetId)
                || draft.sourceRowVersion() == null || draft.sourceRowVersion() != target.rowVersion()
                || (draft.reservedNumberSequence() != null
                && !Objects.equals(draft.reservedNumberSequence(), target.numberSequence()))) {
            throw conflict("逻辑草稿的申请、租户、源编号、源行版本或正式编号与发布记录不一致");
        }
        return new ExistingLogicalContext(draft, target);
    }

    private ExistingPhysicalContext lockExistingPhysical(ChangeApplication application, boolean replacement) {
        long targetId = requireExistingTarget(application);
        verifyOwnedTargetLock(application, targetId);
        if (store.findLogicalDraft(application.tenantId(), application.id()).isPresent()) {
            throw conflict("物理变更申请不得携带逻辑草稿");
        }
        List<PhysicalDraft> drafts = store.findPhysicalDrafts(application.tenantId(), application.id());
        if (drafts.size() != 1) {
            throw conflict("物理变更申请必须且只能包含一条已提交物理草稿");
        }
        PhysicalDraft draft = drafts.get(0);
        PhysicalPublishedState target = store.lockPhysical(application.tenantId(), targetId)
                .orElseThrow(() -> conflict("物理子系统源记录不存在或跨租户"));
        if (target.tenantId() != application.tenantId() || target.id() != targetId || target.deleted()) {
            throw conflict("物理子系统源记录租户、编号或删除状态无效");
        }
        if (draft.applicationId() != application.id() || draft.tenantId() != application.tenantId()
                || draft.lineNo() <= 0 || !Objects.equals(draft.sourcePhysicalSubsystemId(), targetId)
                || draft.sourceRowVersion() == null || draft.sourceRowVersion() != target.rowVersion()) {
            throw conflict("物理草稿的申请、租户、源编号或源行版本与发布记录不一致");
        }

        if (!replacement) {
            if (!Objects.equals(draft.targetLogicalSubsystemId(), target.logicalSubsystemId())
                    || (draft.reservedNumberSlot() != null
                    && !Objects.equals(draft.reservedNumberSlot(), target.numberSlot()))) {
                throw conflict("普通物理变更不得修改所属逻辑子系统或正式编号槽位");
            }
            LogicalPublishedState parent = lockParent(application, target.logicalSubsystemId());
            return new ExistingPhysicalContext(draft, target, parent, parent);
        }

        if (draft.targetLogicalSubsystemId() == null || draft.targetLogicalSubsystemId() <= 0
                || draft.targetLogicalSubsystemId() == target.logicalSubsystemId()
                || draft.reservedNumberSlot() == null || draft.reservedNumberSlot().isBlank()) {
            throw conflict("物理替换必须指定不同的新逻辑父级和已保留的新编号槽位");
        }
        long sourceParentId = target.logicalSubsystemId();
        long targetParentId = draft.targetLogicalSubsystemId();
        LogicalPublishedState first = lockParent(application, Math.min(sourceParentId, targetParentId));
        LogicalPublishedState second = lockParent(application, Math.max(sourceParentId, targetParentId));
        LogicalPublishedState sourceParent = first.id() == sourceParentId ? first : second;
        LogicalPublishedState targetParent = first.id() == targetParentId ? first : second;
        return new ExistingPhysicalContext(draft, target, sourceParent, targetParent);
    }

    private LogicalPublishedState lockParent(ChangeApplication application, long parentId) {
        LogicalPublishedState parent = store.lockLogical(application.tenantId(), parentId)
                .orElseThrow(() -> conflict("物理子系统所属逻辑父级不存在或跨租户"));
        if (parent.tenantId() != application.tenantId() || parent.id() != parentId || parent.deleted()) {
            throw conflict("物理子系统所属逻辑父级租户、编号或删除状态无效");
        }
        return parent;
    }

    private long requireExistingTarget(ChangeApplication application) {
        if (application.targetId() == null || application.targetId() <= 0) {
            throw conflict("非新增申请必须指向有效的已发布目标");
        }
        return application.targetId();
    }

    private void verifyOwnedTargetLock(ChangeApplication application, long targetId) {
        TargetLock targetLock = store.findTargetLock(application.tenantId(), application.targetKind(), targetId)
                .orElseThrow(() -> conflict("变更申请未持有目标排他锁"));
        if (targetLock.tenantId() != application.tenantId() || targetLock.targetKind() != application.targetKind()
                || targetLock.targetId() != targetId || targetLock.applicationId() != application.id()) {
            throw conflict("目标排他锁不属于当前变更申请");
        }
    }

    private void requireMutableStatus(PublishedStatus status, String label) {
        if (status == PublishedStatus.VOIDED) {
            throw conflict(label + "已作废，不允许再修改");
        }
    }

    private void requireExactStatus(PublishedStatus actual, PublishedStatus expected,
                                    ActionType actionType, String label) {
        if (actual != expected) {
            throw conflict(label + "执行 " + actionType + " 时必须处于 " + expected + " 状态");
        }
    }

    private void requireVoidableStatus(PublishedStatus status, String label) {
        if (status != PublishedStatus.ACTIVE && status != PublishedStatus.OFFLINE) {
            throw conflict(label + "只有 ACTIVE 或 OFFLINE 状态可以作废");
        }
    }

    private void requireActiveNumberedParent(LogicalPublishedState parent, String operation) {
        if (parent.status() != PublishedStatus.ACTIVE || parent.numberSequence() == null) {
            throw conflict(operation + "要求目标逻辑父级为具有正式编号的 ACTIVE 记录");
        }
    }

    private void verifyApprovalContext(ChangeApplication application, ApprovalCommand command) {
        if (application.status() != ApplicationStatus.IN_REVIEW) {
            throw conflict("只有 IN_REVIEW 申请可以批准发布");
        }
        if (application.currentBusinessRound() != command.expectedBusinessRound()
                || application.rowVersion() != command.expectedApplicationRowVersion()
                || !Objects.equals(application.currentWorkflowInstanceId(), command.expectedWorkflowInstanceId())
                || !Objects.equals(application.currentPayloadDigest(), command.expectedPayloadDigest())) {
            throw conflict("工作流轮次、实例、摘要或申请版本与当前申请不一致");
        }
    }

    private void requireCreateWithoutPublishedTarget(ChangeApplication application) {
        if (application.targetId() != null) {
            throw conflict("新增申请不得指向既有发布目标");
        }
    }

    private void verifyLogicalCreateDraft(ChangeApplication application, LogicalDraft draft) {
        if (draft.applicationId() != application.id() || draft.tenantId() != application.tenantId()
                || draft.sourceLogicalSubsystemId() != null || draft.sourceRowVersion() != null
                || draft.reservedNumberSequence() == null) {
            throw conflict("逻辑新增草稿的来源、版本或保留编号无效");
        }
    }

    private void verifyLogicalCreatePhysicalDraft(ChangeApplication application, PhysicalDraft draft,
                                                   int previousLineNo) {
        if (draft == null || draft.applicationId() != application.id() || draft.tenantId() != application.tenantId()
                || draft.lineNo() <= previousLineNo || draft.sourcePhysicalSubsystemId() != null
                || draft.sourceRowVersion() != null || draft.targetLogicalSubsystemId() != null
                || draft.reservedNumberSlot() == null || draft.reservedNumberSlot().isBlank()) {
            throw conflict("逻辑新增中的物理草稿顺序、来源、父级或保留槽位无效");
        }
    }

    private void verifyStandalonePhysicalCreateDraft(ChangeApplication application, PhysicalDraft draft) {
        if (draft.applicationId() != application.id() || draft.tenantId() != application.tenantId()
                || draft.lineNo() <= 0 || draft.sourcePhysicalSubsystemId() != null
                || draft.sourceRowVersion() != null || draft.targetLogicalSubsystemId() == null
                || draft.targetLogicalSubsystemId() <= 0 || draft.reservedNumberSlot() == null
                || draft.reservedNumberSlot().isBlank()) {
            throw conflict("物理新增草稿的来源、父级、行号或保留槽位无效");
        }
    }

    private void verifyLogicalReservation(ChangeApplication application, LogicalDraft draft,
                                          SubsystemNumberReservation reservation) {
        if (reservation == null || reservation.kind() != SubsystemNumberKind.LOGICAL
                || reservation.tenantId() != application.tenantId() || reservation.applicationId() != application.id()
                || reservation.lineNo() != 0 || reservation.logicalSequence() != null
                || reservation.ordinal() != draft.reservedNumberSequence() || isBlank(reservation.code())) {
            throw conflict("逻辑编号保留与已提交草稿不一致");
        }
    }

    private void verifyPhysicalReservation(ChangeApplication application, PhysicalDraft draft, int logicalSequence,
                                           SubsystemNumberReservation reservation) {
        if (reservation == null || reservation.kind() != SubsystemNumberKind.PHYSICAL
                || reservation.tenantId() != application.tenantId() || reservation.applicationId() != application.id()
                || reservation.lineNo() != draft.lineNo() || !Objects.equals(reservation.logicalSequence(), logicalSequence)
                || !Objects.equals(draft.reservedNumberSlot(), slotFor(reservation.ordinal()))
                || isBlank(reservation.code())) {
            throw conflict("物理编号保留与已提交草稿或父逻辑编号不一致");
        }
    }

    private String safeHistorySummary(ChangeApplication application) {
        return "工作流批准并原子发布 " + application.targetKind() + " " + application.actionType();
    }

    private long nextIdentifier() {
        long identifier = identifierSupplier.getAsLong();
        if (identifier <= 0) {
            throw new IllegalStateException("发布标识生成器返回无效值");
        }
        return identifier;
    }

    private static long nextGeneratedIdentifier() {
        long now = System.currentTimeMillis() * 1_000L;
        return GENERATED_IDENTIFIERS.updateAndGet(previous -> Math.max(previous + 1, now));
    }

    private static String slotFor(int ordinal) {
        if (ordinal < 1 || ordinal > PHYSICAL_SLOTS.length()) {
            throw conflict("物理编号槽位超出 1..9,A..Z 的容量");
        }
        return String.valueOf(PHYSICAL_SLOTS.charAt(ordinal - 1));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void requireOperator(AuthUser operator) {
        if (operator == null || operator.id() <= 0 || operator.tenantId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要有效的工作流操作人和租户");
        }
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    /** 仅含工作流一致性条件，绝不接收审批人可篡改的业务草稿字段。 */
    public record ApprovalCommand(
            long applicationId,
            int expectedBusinessRound,
            long expectedApplicationRowVersion,
            Long expectedWorkflowInstanceId,
            String expectedPayloadDigest) {
        public ApprovalCommand {
            if (applicationId <= 0 || expectedBusinessRound <= 0 || expectedApplicationRowVersion < 0
                    || expectedWorkflowInstanceId == null || expectedWorkflowInstanceId <= 0
                    || expectedPayloadDigest == null || expectedPayloadDigest.isBlank()
                    || expectedPayloadDigest.length() > 64) {
                throw new IllegalArgumentException("批准命令缺少有效的申请、轮次、版本、实例或摘要");
            }
            expectedPayloadDigest = expectedPayloadDigest.trim();
        }
    }

    public record ApprovalResult(long applicationId, Long logicalSubsystemId, List<Long> physicalSubsystemIds) {
        public ApprovalResult {
            physicalSubsystemIds = physicalSubsystemIds == null ? List.of() : List.copyOf(physicalSubsystemIds);
        }
    }

    private record PendingPhysicalCreate(long id, PhysicalDraft draft, SubsystemNumberReservation reservation) {
    }

    private record ExistingLogicalContext(LogicalDraft draft, LogicalPublishedState target) {
    }

    private record ExistingPhysicalContext(PhysicalDraft draft, PhysicalPublishedState target,
                                           LogicalPublishedState sourceParent,
                                           LogicalPublishedState targetParent) {
    }

    private record PreparedPublication(Long logicalSubsystemId, List<Long> physicalSubsystemIds,
                                       List<SubsystemNumberReservation> reservations, Runnable writer) {
        private PreparedPublication {
            physicalSubsystemIds = List.copyOf(physicalSubsystemIds);
            reservations = List.copyOf(reservations);
            writer = Objects.requireNonNull(writer, "writer 不能为空");
        }
    }
}
