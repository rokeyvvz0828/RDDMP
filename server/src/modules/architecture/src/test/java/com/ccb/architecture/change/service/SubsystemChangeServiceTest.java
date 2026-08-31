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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubsystemChangeServiceTest {
    private static final AuthUser ACTOR = new AuthUser(9L, 7L, "architect", "hash", "架构师", 11L, true);
    private static final AuthUser OTHER = new AuthUser(10L, 7L, "other", "hash", "其他申请人", 12L, true);
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 8, 23, 10, 0);

    @Mock
    private SubsystemChangeStore store;
    @Mock
    private SubsystemNumberStrategy numberStrategy;
    @Mock
    private TransactionTemplate transactions;

    private SubsystemChangeService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            boolean previous = TransactionSynchronizationManager.isActualTransactionActive();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                return callback.doInTransaction(null);
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(previous);
            }
        });
        lenient().when(numberStrategy.reserve(any())).thenAnswer(invocation -> {
            SubsystemNumberRequest request = invocation.getArgument(0);
            int ordinal = request.kind() == SubsystemNumberKind.LOGICAL ? 12 : request.lineNo();
            String code = request.kind() == SubsystemNumberKind.LOGICAL
                    ? "A0012" : String.format("W%04d%s", request.logicalSequence(), slot(ordinal));
            return SubsystemNumberReservation.unformatted(request, ordinal).withCode(code);
        });
        AtomicLong ids = new AtomicLong(10_000L);
        service = new SubsystemChangeService(store, numberStrategy, transactions, ids::getAndIncrement,
                Clock.fixed(Instant.parse("2026-08-23T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void 创建逻辑工单支持零条物理草稿且草稿阶段不分配编号() {
        SubsystemChangeService.ApplicationDetail detail = service.createLogical(ACTOR,
                logicalCommand(ActionType.CREATE, null, List.of()));

        assertThat(detail.application().status()).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(detail.application().applicantId()).isEqualTo(ACTOR.id());
        assertThat(detail.logicalDraft().reservedNumberSequence()).isNull();
        assertThat(detail.physicalDrafts()).isEmpty();
        verifyNoInteractions(numberStrategy);
        verify(store).insertApplication(any(ChangeApplication.class));
        verify(store).replaceLogicalDraft(any(LogicalDraft.class));
        verify(store).replacePhysicalDrafts(7L, detail.application().id(), List.of());
    }

    @Test
    void 创建逻辑工单保留零到多条物理草稿的稳定行号() {
        List<SubsystemChangeService.PhysicalDraftInput> children = List.of(
                physicalInput(1, null, null), physicalInput(2, null, null));

        SubsystemChangeService.ApplicationDetail detail = service.createLogical(ACTOR,
                logicalCommand(ActionType.CREATE, null, children));

        assertThat(detail.physicalDrafts()).extracting(PhysicalDraft::lineNo).containsExactly(1, 2);
        assertThat(detail.physicalDrafts()).allSatisfy(item -> {
            assertThat(item.sourcePhysicalSubsystemId()).isNull();
            assertThat(item.reservedNumberSlot()).isNull();
        });
    }

    @Test
    void 非逻辑创建工单拒绝级联物理草稿() {
        SubsystemChangeService.LogicalApplicationCommand command = logicalCommand(ActionType.UPDATE, 101L,
                List.of(physicalInput(1, null, 3L)));

        assertThatThrownBy(() -> service.createLogical(ACTOR, command))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.BAD_REQUEST));
        verifyNoInteractions(store, transactions);
    }

    @Test
    void 创建物理工单保留所属逻辑且不分配编号() {
        SubsystemChangeService.ApplicationDetail detail = service.createPhysical(ACTOR,
                new SubsystemChangeService.PhysicalApplicationCommand(ActionType.CREATE, null, "新增物理",
                        physicalInput(1, 101L, null)));

        assertThat(detail.application().targetKind()).isEqualTo(TargetKind.PHYSICAL);
        assertThat(detail.physicalDrafts()).singleElement().satisfies(item -> {
            assertThat(item.targetLogicalSubsystemId()).isEqualTo(101L);
            assertThat(item.reservedNumberSlot()).isNull();
        });
    }

    @Test
    void 列表按本人或管理范围传入认证租户() {
        when(store.listApplications(7L, 9L, ApplicationStatus.DRAFT, 20, 0))
                .thenReturn(List.of(application(301L, ACTOR.id(), TargetKind.LOGICAL, ActionType.CREATE,
                        null, ApplicationStatus.DRAFT, 0L)));
        when(store.listApplications(7L, null, ApplicationStatus.DRAFT, 20, 0))
                .thenReturn(List.of(application(301L, ACTOR.id(), TargetKind.LOGICAL, ActionType.CREATE,
                                null, ApplicationStatus.DRAFT, 0L),
                        application(302L, OTHER.id(), TargetKind.LOGICAL, ActionType.CREATE,
                                null, ApplicationStatus.DRAFT, 0L)));

        assertThat(service.list(ACTOR, SubsystemChangeService.AccessScope.OWN,
                ApplicationStatus.DRAFT, 20, 0)).hasSize(1);
        assertThat(service.list(ACTOR, SubsystemChangeService.AccessScope.MANAGE,
                ApplicationStatus.DRAFT, 20, 0)).hasSize(2);

        verify(store).listApplications(7L, 9L, ApplicationStatus.DRAFT, 20, 0);
        verify(store).listApplications(7L, null, ApplicationStatus.DRAFT, 20, 0);
    }

    @Test
    void 详情拒绝其他申请人但管理范围可读() {
        ChangeApplication application = application(301L, OTHER.id(), TargetKind.LOGICAL, ActionType.CREATE,
                null, ApplicationStatus.DRAFT, 0L);
        when(store.findApplication(7L, 301L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.detail(ACTOR, SubsystemChangeService.AccessScope.OWN, 301L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.FORBIDDEN));

        SubsystemChangeService.ApplicationDetail detail = service.detail(ACTOR,
                SubsystemChangeService.AccessScope.MANAGE, 301L);
        assertThat(detail.application()).isSameAs(application);
    }

    @Test
    void 详情找不到当前租户工单时不泄露其他租户信息() {
        when(store.findApplication(7L, 301L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(ACTOR, SubsystemChangeService.AccessScope.MANAGE, 301L))
                .isInstanceOf(ArchitectureNotFoundException.class)
                .hasMessageContaining("当前租户");
    }

    @Test
    void 更新草稿使用原因CAS并将行版本加一后替换草稿和记录历史() {
        ChangeApplication application = application(301L, ACTOR.id(), TargetKind.LOGICAL, ActionType.UPDATE,
                101L, ApplicationStatus.DRAFT, 4L);
        LogicalDraft existing = logicalDraft(301L, 101L, 4L, null, 2);
        when(store.lockApplication(7L, 301L)).thenReturn(Optional.of(application));
        when(store.findLogicalDraft(7L, 301L)).thenReturn(Optional.of(existing));
        when(store.findPhysicalDrafts(7L, 301L)).thenReturn(List.of());
        when(store.compareAndSetApplicationReason(7L, 301L, ApplicationStatus.DRAFT, 4L,
                "更新原因", ACTOR.id())).thenReturn(true);

        SubsystemChangeService.ApplicationDetail detail = service.update(ACTOR,
                SubsystemChangeService.AccessScope.OWN, 301L, 4L,
                new SubsystemChangeService.DraftUpdateCommand("更新原因", logicalInput(5L), List.of()));

        assertThat(detail.application().reason()).isEqualTo("更新原因");
        assertThat(detail.application().rowVersion()).isEqualTo(5L);
        assertThat(detail.logicalDraft().draftRevision()).isEqualTo(3);
        assertThat(detail.logicalDraft().reservedNumberSequence()).isNull();
        verify(store).compareAndSetApplicationReason(7L, 301L, ApplicationStatus.DRAFT, 4L,
                "更新原因", ACTOR.id());
        verify(store).replaceLogicalDraft(any(LogicalDraft.class));
        ArgumentCaptor<ChangeHistoryEvent> history = ArgumentCaptor.forClass(ChangeHistoryEvent.class);
        verify(store).insertHistory(history.capture());
        assertThat(history.getValue().operatorId()).isEqualTo(ACTOR.id());
        verify(store, never()).compareAndSetApplicationStatus(any(Long.class), any(Long.class), any(), any(Long.class),
                any(), any(Long.class));
    }

    @Test
    void 退回草稿更新会清除旧值保留但保留已分配编号字段() {
        ChangeApplication application = application(301L, ACTOR.id(), TargetKind.LOGICAL, ActionType.CREATE,
                null, ApplicationStatus.RETURNED, 7L);
        LogicalDraft existing = logicalDraft(301L, null, null, 12, 1);
        when(store.lockApplication(7L, 301L)).thenReturn(Optional.of(application));
        when(store.findLogicalDraft(7L, 301L)).thenReturn(Optional.of(existing));
        when(store.findPhysicalDrafts(7L, 301L)).thenReturn(List.of());
        when(store.compareAndSetApplicationReason(7L, 301L, ApplicationStatus.RETURNED, 7L,
                "退回后修改", ACTOR.id())).thenReturn(true);

        SubsystemChangeService.ApplicationDetail detail = service.update(ACTOR,
                SubsystemChangeService.AccessScope.OWN, 301L, 7L,
                new SubsystemChangeService.DraftUpdateCommand("退回后修改", logicalInput(null), List.of()));

        assertThat(detail.application().rowVersion()).isEqualTo(8L);
        assertThat(detail.logicalDraft().reservedNumberSequence()).isEqualTo(12);
        verify(store).deleteValueReservations(7L, 301L);
    }

    @Test
    void 更新拒绝陈旧行版本且不写草稿() {
        ChangeApplication application = application(301L, ACTOR.id(), TargetKind.LOGICAL, ActionType.UPDATE,
                101L, ApplicationStatus.DRAFT, 5L);
        when(store.lockApplication(7L, 301L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.update(ACTOR, SubsystemChangeService.AccessScope.OWN, 301L, 4L,
                new SubsystemChangeService.DraftUpdateCommand("更新", logicalInput(5L), List.of())))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT));
        verify(store, never()).compareAndSetApplicationReason(any(Long.class), any(Long.class), any(), any(Long.class),
                any(), any(Long.class));
        verify(store, never()).replaceLogicalDraft(any());
    }

    @Test
    void 普通物理更新拒绝修改所属逻辑子系统() {
        ChangeApplication application = application(401L, ACTOR.id(), TargetKind.PHYSICAL, ActionType.UPDATE,
                201L, ApplicationStatus.DRAFT, 1L);
        when(store.lockApplication(7L, 401L)).thenReturn(Optional.of(application));
        when(store.findPhysicalDrafts(7L, 401L)).thenReturn(List.of(physicalDraft(401L, 1, 201L, 101L, 1)));

        assertThatThrownBy(() -> service.update(ACTOR, SubsystemChangeService.AccessScope.OWN, 401L, 1L,
                new SubsystemChangeService.DraftUpdateCommand("改父级", null,
                        List.of(physicalInput(1, 102L, null)))))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT));
        verify(store, never()).compareAndSetApplicationReason(any(Long.class), any(Long.class), any(), any(Long.class),
                any(), any(Long.class));
    }

    @Test
    void 草稿取消状态CAS后清理值保留和可能存在的目标锁并记录历史() {
        ChangeApplication application = application(301L, ACTOR.id(), TargetKind.LOGICAL, ActionType.UPDATE,
                101L, ApplicationStatus.DRAFT, 2L);
        when(store.lockApplication(7L, 301L)).thenReturn(Optional.of(application));
        when(store.compareAndSetApplicationStatus(7L, 301L, ApplicationStatus.DRAFT, 2L,
                ApplicationStatus.CANCELLED, ACTOR.id())).thenReturn(true);
        when(store.findLogicalDraft(7L, 301L)).thenReturn(Optional.of(logicalDraft(301L, 101L, 2L, null, 0)));
        when(store.findPhysicalDrafts(7L, 301L)).thenReturn(List.of());

        SubsystemChangeService.ApplicationDetail detail = service.cancel(ACTOR,
                SubsystemChangeService.AccessScope.OWN, 301L, 2L);

        assertThat(detail.application().status()).isEqualTo(ApplicationStatus.CANCELLED);
        assertThat(detail.application().rowVersion()).isEqualTo(3L);
        verify(store).deleteValueReservations(7L, 301L);
        verify(store).deleteTargetLock(7L, TargetKind.LOGICAL, 101L, 301L);
        verify(store).insertHistory(any());
    }

    @Test
    void 审批中取消明确要求T3工作流终止确认且不改变状态() {
        ChangeApplication application = application(301L, ACTOR.id(), TargetKind.LOGICAL, ActionType.UPDATE,
                101L, ApplicationStatus.IN_REVIEW, 2L);
        when(store.lockApplication(7L, 301L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.cancel(ACTOR, SubsystemChangeService.AccessScope.OWN, 301L, 2L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.getMessage()).contains("T3 工作流终止");
                });
        verify(store, never()).compareAndSetApplicationStatus(any(Long.class), any(Long.class), any(), any(Long.class),
                any(), any(Long.class));
    }

    @Test
    void 管理范围也不能编辑取消或提交他人申请() {
        ChangeApplication otherApplication = application(302L, 99L, TargetKind.LOGICAL, ActionType.UPDATE,
                101L, ApplicationStatus.DRAFT, 2L);
        when(store.lockApplication(7L, 302L)).thenReturn(Optional.of(otherApplication));

        assertForbidden(() -> service.update(ACTOR, SubsystemChangeService.AccessScope.MANAGE, 302L, 2L,
                new SubsystemChangeService.DraftUpdateCommand("越权修改", logicalInput(2L), List.of())));
        assertForbidden(() -> service.cancel(ACTOR, SubsystemChangeService.AccessScope.MANAGE, 302L, 2L));
        assertForbidden(() -> service.coordinateSubmission(ACTOR, SubsystemChangeService.AccessScope.MANAGE,
                302L, 2L, ignored -> {
                    throw new AssertionError("越权提交不应调用协调器");
                }));

        verify(store, never()).compareAndSetApplicationReason(any(Long.class), any(Long.class), any(),
                any(Long.class), any(), any(Long.class));
        verify(store, never()).compareAndSetApplicationStatus(any(Long.class), any(Long.class), any(),
                any(Long.class), any(), any(Long.class));
        verifyNoInteractions(numberStrategy);
    }

    @Test
    void 审批中取消登记当前实例并在同一事务调用终止协调器() {
        ChangeApplication application = workflowApplication(303L, ACTOR.id(), ApplicationStatus.IN_REVIEW,
                2, 90L, "a".repeat(64), false, 6L);
        when(store.lockApplication(7L, 303L)).thenReturn(Optional.of(application));
        when(store.compareAndSetCancellationRequested(7L, 303L, 6L, 90L, ACTOR.id())).thenReturn(true);
        AtomicReference<SubsystemChangeService.CancellationPreparation> callback = new AtomicReference<>();

        SubsystemChangeService.CancellationPreparation preparation = service.coordinateCancellation(
                ACTOR, 303L, 6L, callback::set);

        assertThat(preparation).isSameAs(callback.get());
        assertThat(preparation.businessRound()).isEqualTo(2);
        assertThat(preparation.workflowInstanceId()).isEqualTo(90L);
        assertThat(preparation.digest()).isEqualTo("a".repeat(64));
        verify(store).compareAndSetCancellationRequested(7L, 303L, 6L, 90L, ACTOR.id());
        verify(store).insertHistory(argThat(history -> history.eventType().equals("WORKFLOW_CANCELLATION_REQUESTED")
                && history.operatorId() == ACTOR.id()));
    }

    @Test
    void 终止确认仅处理已登记取消的当前实例并释放未发布资源() {
        ChangeApplication application = workflowApplication(304L, ACTOR.id(), ApplicationStatus.IN_REVIEW,
                2, 90L, "b".repeat(64), true, 7L);
        LogicalDraft logical = logicalDraft(304L, null, null, 12, 1);
        when(store.lockApplication(7L, 304L)).thenReturn(Optional.of(application));
        when(store.compareAndSetApplicationStatus(7L, 304L, ApplicationStatus.IN_REVIEW, 7L,
                ApplicationStatus.CANCELLED, 88L)).thenReturn(true);
        when(store.findLogicalDraft(7L, 304L)).thenReturn(Optional.of(logical));
        when(store.findPhysicalDrafts(7L, 304L)).thenReturn(List.of());

        inTestTransaction(() -> service.applyCancellationConfirmationInCurrentTransaction(
                7L, 304L, 7L, 90L, 88L));

        verify(numberStrategy).release(argThat(reservation -> reservation.code().equals("A0012")),
                eq(SubsystemNumberReleaseReason.CANCELLED));
        verify(store).deleteValueReservations(7L, 304L);
        verify(store).insertHistory(argThat(history -> history.eventType().equals("WORKFLOW_CANCELLED")
                && history.toStatus() == ApplicationStatus.CANCELLED));
    }

    @Test
    void 提交准备拒绝脱离真实事务直接调用() {
        assertThatThrownBy(() -> service.prepareSubmissionInCurrentTransaction(ACTOR,
                SubsystemChangeService.AccessScope.OWN, 501L, 0L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("真实事务");
        verifyNoInteractions(store, numberStrategy);
    }

    @Test
    void 逻辑创建首次提交支持零条物理并生成稳定快照摘要和真实操作人历史() {
        ChangeApplication application = application(501L, ACTOR.id(), TargetKind.LOGICAL, ActionType.CREATE,
                null, ApplicationStatus.DRAFT, 0L);
        LogicalDraft draft = logicalDraft(501L, null, null, null, 0);
        when(store.lockApplication(7L, 501L)).thenReturn(Optional.of(application));
        when(store.findLogicalDraft(7L, 501L)).thenReturn(Optional.of(draft));
        when(store.findPhysicalDrafts(7L, 501L)).thenReturn(List.of());
        when(store.compareAndSetApplicationStatus(7L, 501L, ApplicationStatus.DRAFT, 0L,
                ApplicationStatus.IN_REVIEW, ACTOR.id())).thenReturn(true);
        AtomicReference<SubsystemChangeService.SubmissionPreparation> callbackValue = new AtomicReference<>();

        SubsystemChangeService.SubmissionPreparation preparation = service.coordinateSubmission(ACTOR,
                SubsystemChangeService.AccessScope.OWN, 501L, 0L, callbackValue::set);

        assertThat(callbackValue.get()).isSameAs(preparation);
        assertThat(preparation.nextRound()).isEqualTo(1);
        assertThat(preparation.snapshot()).startsWith("{\"canonical\":");
        assertThat(preparation.digest()).hasSize(64);
        assertThat(preparation.reservedNumbers()).singleElement().satisfies(number -> {
            assertThat(number.kind()).isEqualTo(SubsystemNumberKind.LOGICAL);
            assertThat(number.ordinal()).isEqualTo(12);
            assertThat(number.code()).isEqualTo("A0012");
        });
        verify(numberStrategy).reserve(SubsystemNumberRequest.logical(7L, 501L));
        verify(store).insertValueReservation(argThat(reservation ->
                reservation.applicationId() == 501L
                        && reservation.reservationScope().equals("LOGICAL_NAME")
                        && reservation.normalizedValue().equals("商城系统")));
        ArgumentCaptor<ChangeHistoryEvent> history = ArgumentCaptor.forClass(ChangeHistoryEvent.class);
        verify(store).insertHistory(history.capture());
        assertThat(history.getValue().operatorId()).isEqualTo(ACTOR.id());
        assertThat(history.getValue().businessRound()).isEqualTo(1);
        assertThat(history.getValue().snapshotJson()).isEqualTo(preparation.snapshot());
    }

    @Test
    void 逻辑创建首次提交为多条物理按稳定行号保留编号并写回草稿() {
        ChangeApplication application = application(502L, ACTOR.id(), TargetKind.LOGICAL, ActionType.CREATE,
                null, ApplicationStatus.DRAFT, 0L);
        LogicalDraft logical = logicalDraft(502L, null, null, null, 0);
        List<PhysicalDraft> physicals = List.of(
                physicalDraft(502L, 2, null, null, null, null, 0),
                physicalDraft(502L, 1, null, null, null, null, 0));
        when(store.lockApplication(7L, 502L)).thenReturn(Optional.of(application));
        when(store.findLogicalDraft(7L, 502L)).thenReturn(Optional.of(logical));
        when(store.findPhysicalDrafts(7L, 502L)).thenReturn(physicals);
        when(store.compareAndSetApplicationStatus(7L, 502L, ApplicationStatus.DRAFT, 0L,
                ApplicationStatus.IN_REVIEW, ACTOR.id())).thenReturn(true);

        SubsystemChangeService.SubmissionPreparation preparation = service.coordinateSubmission(ACTOR,
                SubsystemChangeService.AccessScope.OWN, 502L, 0L, ignored -> { });

        assertThat(preparation.reservedNumbers()).extracting(SubsystemChangeService.ReservedNumber::code)
                .containsExactly("A0012", "W00121", "W00122");
        verify(store).replaceLogicalDraft(argThat(item -> item.reservedNumberSequence() == 12
                && item.submittedSnapshotJson() != null));
        verify(store).replacePhysicalDrafts(eq(7L), eq(502L), argThat(items ->
                items.size() == 2
                        && items.get(0).lineNo() == 1 && "1".equals(items.get(0).reservedNumberSlot())
                        && items.get(1).lineNo() == 2 && "2".equals(items.get(1).reservedNumberSlot())
                        && items.stream().allMatch(item -> item.submittedSnapshotJson() != null)));
    }

    @Test
    void 退回重提幂等保留原逻辑和物理编号() {
        ChangeApplication application = application(503L, ACTOR.id(), TargetKind.LOGICAL, ActionType.CREATE,
                null, ApplicationStatus.RETURNED, 3L);
        LogicalDraft logical = logicalDraft(503L, null, null, 12, 1);
        PhysicalDraft physical = physicalDraft(503L, 1, null, null, null, "1", 1);
        when(store.lockApplication(7L, 503L)).thenReturn(Optional.of(application));
        when(store.findLogicalDraft(7L, 503L)).thenReturn(Optional.of(logical));
        when(store.findPhysicalDrafts(7L, 503L)).thenReturn(List.of(physical));
        when(store.compareAndSetApplicationStatus(7L, 503L, ApplicationStatus.RETURNED, 3L,
                ApplicationStatus.IN_REVIEW, ACTOR.id())).thenReturn(true);

        SubsystemChangeService.SubmissionPreparation preparation = service.coordinateSubmission(ACTOR,
                SubsystemChangeService.AccessScope.OWN, 503L, 3L, ignored -> { });

        assertThat(preparation.reservedNumbers()).extracting(SubsystemChangeService.ReservedNumber::code)
                .containsExactly("A0012", "W00121");
        verify(numberStrategy).reserve(SubsystemNumberRequest.logical(7L, 503L));
        verify(numberStrategy).reserve(SubsystemNumberRequest.physical(7L, 503L, 1, 12));
    }

    @Test
    void 非创建提交获取目标锁校验来源版本并建立规范化值保留() {
        ChangeApplication application = application(504L, ACTOR.id(), TargetKind.LOGICAL, ActionType.UPDATE,
                101L, ApplicationStatus.DRAFT, 2L);
        LogicalDraft logical = logicalDraft(504L, 101L, 4L, null, 0);
        when(store.lockApplication(7L, 504L)).thenReturn(Optional.of(application));
        when(store.findLogicalDraft(7L, 504L)).thenReturn(Optional.of(logical));
        when(store.findPhysicalDrafts(7L, 504L)).thenReturn(List.of());
        when(store.lockLogical(7L, 101L)).thenReturn(Optional.of(
                new LogicalPublishedState(101L, 7L, "A0004", 4, PublishedStatus.ACTIVE, 3, 4L, false)));
        when(store.compareAndSetApplicationStatus(7L, 504L, ApplicationStatus.DRAFT, 2L,
                ApplicationStatus.IN_REVIEW, ACTOR.id())).thenReturn(true);

        service.coordinateSubmission(ACTOR, SubsystemChangeService.AccessScope.OWN, 504L, 2L, ignored -> { });

        verify(store).insertTargetLock(argThat(lock -> lock.tenantId() == 7L
                && lock.targetKind() == TargetKind.LOGICAL && lock.targetId() == 101L
                && lock.applicationId() == 504L));
        verify(store).insertValueReservation(argThat(reservation ->
                reservation.reservationScope().equals("LOGICAL_NAME")
                        && reservation.normalizedValue().equals("商城系统")));
        verifyNoInteractions(numberStrategy);
    }

    @Test
    void 物理替换必须锁定旧目标和新逻辑并按新逻辑分配编号() {
        ChangeApplication application = application(505L, ACTOR.id(), TargetKind.PHYSICAL, ActionType.REPLACE,
                201L, ApplicationStatus.DRAFT, 1L);
        PhysicalDraft physical = physicalDraft(505L, 1, 201L, 102L, 5L, null, 0);
        when(store.lockApplication(7L, 505L)).thenReturn(Optional.of(application));
        when(store.findPhysicalDrafts(7L, 505L)).thenReturn(List.of(physical));
        when(store.lockPhysical(7L, 201L)).thenReturn(Optional.of(
                new PhysicalPublishedState(201L, 7L, "W00011", "1", 101L, "Old",
                        PublishedStatus.ACTIVE, 5L, false)));
        when(store.lockLogical(7L, 102L)).thenReturn(Optional.of(
                new LogicalPublishedState(102L, 7L, "A0044", 44, PublishedStatus.ACTIVE, 0, 2L, false)));
        when(store.compareAndSetApplicationStatus(7L, 505L, ApplicationStatus.DRAFT, 1L,
                ApplicationStatus.IN_REVIEW, ACTOR.id())).thenReturn(true);

        SubsystemChangeService.SubmissionPreparation preparation = service.coordinateSubmission(ACTOR,
                SubsystemChangeService.AccessScope.MANAGE, 505L, 1L, ignored -> { });

        assertThat(preparation.reservedNumbers()).singleElement().satisfies(number -> {
            assertThat(number.logicalSequence()).isEqualTo(44);
            assertThat(number.code()).isEqualTo("W00441");
        });
        verify(store).insertTargetLock(any(TargetLock.class));
        verify(numberStrategy).reserve(SubsystemNumberRequest.physical(7L, 505L, 1, 44));
    }

    @Test
    void 独立物理创建按所属逻辑序号保留槽位且不获取目标工单锁() {
        ChangeApplication application = application(510L, ACTOR.id(), TargetKind.PHYSICAL, ActionType.CREATE,
                null, ApplicationStatus.DRAFT, 0L);
        PhysicalDraft physical = physicalDraft(510L, 1, null, 101L, null, null, 0);
        when(store.lockApplication(7L, 510L)).thenReturn(Optional.of(application));
        when(store.findPhysicalDrafts(7L, 510L)).thenReturn(List.of(physical));
        when(store.lockLogical(7L, 101L)).thenReturn(Optional.of(
                new LogicalPublishedState(101L, 7L, "A0004", 4, PublishedStatus.ACTIVE, 0, 2L, false)));
        when(store.compareAndSetApplicationStatus(7L, 510L, ApplicationStatus.DRAFT, 0L,
                ApplicationStatus.IN_REVIEW, ACTOR.id())).thenReturn(true);

        SubsystemChangeService.SubmissionPreparation preparation = service.coordinateSubmission(ACTOR,
                SubsystemChangeService.AccessScope.OWN, 510L, 0L, ignored -> { });

        assertThat(preparation.reservedNumbers()).singleElement().satisfies(number -> {
            assertThat(number.logicalSequence()).isEqualTo(4);
            assertThat(number.code()).isEqualTo("W00041");
        });
        verify(numberStrategy).reserve(SubsystemNumberRequest.physical(7L, 510L, 1, 4));
        verify(store, never()).insertTargetLock(any());
    }

    @Test
    void 提交协调器失败时异常向外传播以触发同事务整体回滚() {
        ChangeApplication application = application(506L, ACTOR.id(), TargetKind.LOGICAL, ActionType.CREATE,
                null, ApplicationStatus.DRAFT, 0L);
        when(store.lockApplication(7L, 506L)).thenReturn(Optional.of(application));
        when(store.findLogicalDraft(7L, 506L)).thenReturn(Optional.of(
                logicalDraft(506L, null, null, null, 0)));
        when(store.findPhysicalDrafts(7L, 506L)).thenReturn(List.of());
        when(store.compareAndSetApplicationStatus(7L, 506L, ApplicationStatus.DRAFT, 0L,
                ApplicationStatus.IN_REVIEW, ACTOR.id())).thenReturn(true);

        assertThatThrownBy(() -> service.coordinateSubmission(ACTOR,
                SubsystemChangeService.AccessScope.OWN, 506L, 0L,
                ignored -> { throw new IllegalStateException("workflow start failed"); }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("workflow start failed");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void 工作流退回保留编号目标锁和值保留() {
        ChangeApplication application = application(507L, ACTOR.id(), TargetKind.PHYSICAL, ActionType.REPLACE,
                201L, ApplicationStatus.IN_REVIEW, 4L);
        when(store.lockApplication(7L, 507L)).thenReturn(Optional.of(application));
        when(store.compareAndSetApplicationStatus(7L, 507L, ApplicationStatus.IN_REVIEW, 4L,
                ApplicationStatus.RETURNED, 77L)).thenReturn(true);

        inTestTransaction(() -> service.applyReviewOutcomeInCurrentTransaction(7L, 507L, 4L, 77L,
                SubsystemChangeService.ReviewOutcome.RETURNED));

        verify(numberStrategy, never()).release(any(), any());
        verify(store, never()).deleteValueReservations(any(Long.class), any(Long.class));
        verify(store, never()).deleteTargetLock(any(Long.class), any(), any(Long.class), any(Long.class));
        verify(store).insertHistory(argThat(history -> history.operatorId() == 77L
                && history.toStatus() == ApplicationStatus.RETURNED));
    }

    @Test
    void 工作流拒绝释放替换编号目标锁和值保留并记录事件操作人() {
        ChangeApplication application = application(508L, ACTOR.id(), TargetKind.PHYSICAL, ActionType.REPLACE,
                201L, ApplicationStatus.IN_REVIEW, 4L);
        PhysicalDraft physical = physicalDraft(508L, 1, 201L, 102L, 5L, "1", 0);
        when(store.lockApplication(7L, 508L)).thenReturn(Optional.of(application));
        when(store.findPhysicalDrafts(7L, 508L)).thenReturn(List.of(physical));
        when(store.lockLogical(7L, 102L)).thenReturn(Optional.of(
                new LogicalPublishedState(102L, 7L, "A0044", 44, PublishedStatus.OFFLINE, 0, 2L, false)));
        when(store.compareAndSetApplicationStatus(7L, 508L, ApplicationStatus.IN_REVIEW, 4L,
                ApplicationStatus.REJECTED, 77L)).thenReturn(true);

        inTestTransaction(() -> service.applyReviewOutcomeInCurrentTransaction(7L, 508L, 4L, 77L,
                SubsystemChangeService.ReviewOutcome.REJECTED));

        verify(numberStrategy).release(argThat(reservation -> reservation.kind() == SubsystemNumberKind.PHYSICAL
                        && reservation.logicalSequence() == 44 && reservation.ordinal() == 1),
                eq(SubsystemNumberReleaseReason.REJECTED));
        verify(store).deleteTargetLock(7L, TargetKind.PHYSICAL, 201L, 508L);
        verify(store).deleteValueReservations(7L, 508L);
        verify(store).insertHistory(argThat(history -> history.operatorId() == 77L
                && history.toStatus() == ApplicationStatus.REJECTED));
    }

    @Test
    void 退回工单取消释放逻辑和级联物理编号() {
        ChangeApplication application = application(509L, ACTOR.id(), TargetKind.LOGICAL, ActionType.CREATE,
                null, ApplicationStatus.RETURNED, 5L);
        when(store.lockApplication(7L, 509L)).thenReturn(Optional.of(application));
        when(store.compareAndSetApplicationStatus(7L, 509L, ApplicationStatus.RETURNED, 5L,
                ApplicationStatus.CANCELLED, ACTOR.id())).thenReturn(true);
        when(store.findLogicalDraft(7L, 509L)).thenReturn(Optional.of(
                logicalDraft(509L, null, null, 12, 1)));
        when(store.findPhysicalDrafts(7L, 509L)).thenReturn(List.of(
                physicalDraft(509L, 1, null, null, null, "1", 1)));

        service.cancel(ACTOR, SubsystemChangeService.AccessScope.OWN, 509L, 5L);

        ArgumentCaptor<SubsystemNumberReservation> released =
                ArgumentCaptor.forClass(SubsystemNumberReservation.class);
        verify(numberStrategy, org.mockito.Mockito.times(2)).release(released.capture(),
                eq(SubsystemNumberReleaseReason.CANCELLED));
        assertThat(released.getAllValues()).extracting(SubsystemNumberReservation::code)
                .containsExactly("A0012", "W00121");
        verify(store).deleteValueReservations(7L, 509L);
    }

    private SubsystemChangeService.LogicalApplicationCommand logicalCommand(ActionType actionType, Long targetId,
                                                                             List<SubsystemChangeService.PhysicalDraftInput> physicals) {
        return new SubsystemChangeService.LogicalApplicationCommand(actionType, targetId, "申请原因",
                logicalInput(actionType == ActionType.CREATE ? null : 4L), physicals);
    }

    private SubsystemChangeService.LogicalDraftInput logicalInput(Long sourceRowVersion) {
        return new SubsystemChangeService.LogicalDraftInput("商城", "商城系统", 21L,
                "P2", "APPLICATION", "CHANNEL", 31L, "描述", "备注", 3, sourceRowVersion);
    }

    private SubsystemChangeService.PhysicalDraftInput physicalInput(int lineNo, Long targetLogicalSubsystemId,
                                                                     Long sourceRowVersion) {
        return new SubsystemChangeService.PhysicalDraftInput(lineNo, targetLogicalSubsystemId,
                "商城物理" + lineNo, "商城物理系统" + lineNo, "Mall " + lineNo, "渠道", 41L,
                "平台研发团队", "RUNTIME", "A", "Spring", 51L, "描述", "备注", sourceRowVersion);
    }

    private ChangeApplication application(long id, long applicantId, TargetKind targetKind, ActionType actionType,
                                          Long targetId, ApplicationStatus status, long rowVersion) {
        return new ChangeApplication(id, 7L, targetKind, actionType, targetId, applicantId, "原始原因", status,
                0, null, null, null, null, false, rowVersion, applicantId, applicantId, TIME, TIME);
    }

    private ChangeApplication workflowApplication(long id, long applicantId, ApplicationStatus status,
                                                  int round, long instanceId, String digest,
                                                  boolean cancellationRequested, long rowVersion) {
        return new ChangeApplication(id, 7L, TargetKind.LOGICAL, ActionType.CREATE, null, applicantId,
                "原始原因", status, round, 80L, 1L, instanceId, digest, cancellationRequested,
                rowVersion, applicantId, applicantId, TIME, TIME);
    }

    private LogicalDraft logicalDraft(long applicationId, Long sourceId, Long sourceRowVersion,
                                      Integer reservedNumber, int revision) {
        return new LogicalDraft(applicationId, 7L, sourceId, "商城", "商城系统", 21L,
                "P2", "APPLICATION", "CHANNEL", 31L, "描述", "备注", 3,
                reservedNumber, sourceRowVersion, revision, null, TIME, TIME);
    }

    private PhysicalDraft physicalDraft(long applicationId, int lineNo, Long sourceId, Long targetLogicalId,
                                        int revision) {
        return physicalDraft(applicationId, lineNo, sourceId, targetLogicalId, 1L, null, revision);
    }

    private PhysicalDraft physicalDraft(long applicationId, int lineNo, Long sourceId, Long targetLogicalId,
                                        Long sourceRowVersion, String reservedSlot, int revision) {
        return new PhysicalDraft(applicationId, lineNo, 7L, sourceId, targetLogicalId,
                "商城物理", "商城物理系统", "Mall", "渠道", 41L, "平台研发团队", "RUNTIME", "A",
                "Spring", 51L, "描述", "备注", reservedSlot, sourceRowVersion, revision, null, TIME, TIME);
    }

    private void inTestTransaction(Runnable action) {
        transactions.execute(status -> {
            action.run();
            return true;
        });
    }

    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    private static String slot(int ordinal) {
        return String.valueOf("123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(ordinal - 1));
    }
}
