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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubsystemPublicationServiceTest {
    private static final long TENANT_ID = 1L;
    private static final long APPLICATION_ID = 700L;
    private static final long WORKFLOW_INSTANCE_ID = 900L;
    private static final String DIGEST = "a".repeat(64);

    private final SubsystemChangeStore store = mock(SubsystemChangeStore.class);
    private final SubsystemNumberStrategy numberStrategy = mock(SubsystemNumberStrategy.class);
    private final SubsystemReferenceGuard referenceGuard = mock(SubsystemReferenceGuard.class);
    private final AuthUser operator = new AuthUser(88L, TENANT_ID, "workflow", "-", "工作流操作人", 1L, true);
    private final AtomicLong identifiers = new AtomicLong(1_000L);

    private RecordingTransactionManager transactionManager;
    private SubsystemPublicationService service;

    @BeforeEach
    void setUp() {
        transactionManager = new RecordingTransactionManager();
        service = new SubsystemPublicationService(store, numberStrategy, referenceGuard,
                new TransactionTemplate(transactionManager), identifiers::incrementAndGet);
    }

    @Test
    void logicalCreateWithZeroPhysicalDraftsPublishesAtomicallyAndConsumesNumber() {
        ChangeApplication application = application(TargetKind.LOGICAL, ActionType.CREATE, null);
        LogicalDraft draft = logicalDraft(7);
        SubsystemNumberReservation reservation = logicalReservation(7);
        stubApplication(application);
        when(store.findLogicalDraft(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.of(draft));
        when(store.findPhysicalDrafts(TENANT_ID, APPLICATION_ID)).thenReturn(List.of());
        when(numberStrategy.reserve(SubsystemNumberRequest.logical(TENANT_ID, APPLICATION_ID)))
                .thenReturn(reservation);
        when(store.compareAndSetApplicationStatus(TENANT_ID, APPLICATION_ID, ApplicationStatus.IN_REVIEW, 5L,
                ApplicationStatus.APPROVED, operator.id())).thenReturn(true);

        SubsystemPublicationService.ApprovalResult result = service.approve(command(), operator);

        assertThat(result.applicationId()).isEqualTo(APPLICATION_ID);
        assertThat(result.logicalSubsystemId()).isEqualTo(1_001L);
        assertThat(result.physicalSubsystemIds()).isEmpty();
        verify(store).insertLogicalPublished(1_001L, TENANT_ID, "A0007", 7, draft,
                PublishedStatus.ACTIVE, 0L, operator.id());
        verify(numberStrategy).consume(reservation);
        verify(store).deleteValueReservations(TENANT_ID, APPLICATION_ID);
        ArgumentCaptor<ChangeHistoryEvent> history = ArgumentCaptor.forClass(ChangeHistoryEvent.class);
        verify(store).insertHistory(history.capture());
        assertThat(history.getValue()).satisfies(event -> {
            assertThat(event.id()).isEqualTo(1_002L);
            assertThat(event.eventType()).isEqualTo("APPROVED_PUBLISHED");
            assertThat(event.fromStatus()).isEqualTo(ApplicationStatus.IN_REVIEW);
            assertThat(event.toStatus()).isEqualTo(ApplicationStatus.APPROVED);
            assertThat(event.summary()).doesNotContain(draft.name(), application.reason(), DIGEST);
        });
        assertThat(transactionManager.commits).isEqualTo(1);
        assertThat(transactionManager.rollbacks).isZero();
    }

    @Test
    void logicalCreateWithMultiplePhysicalDraftsUsesStableLinesAndOnlyDraftValues() {
        ChangeApplication application = application(TargetKind.LOGICAL, ActionType.CREATE, null);
        LogicalDraft logicalDraft = logicalDraft(3);
        PhysicalDraft first = physicalDraft(1, null, "1");
        PhysicalDraft second = physicalDraft(9, null, "2");
        SubsystemNumberReservation logicalReservation = logicalReservation(3);
        SubsystemNumberReservation firstReservation = physicalReservation(1, 3, 1, "W00031");
        SubsystemNumberReservation secondReservation = physicalReservation(9, 3, 2, "W00032");
        stubApplication(application);
        when(store.findLogicalDraft(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.of(logicalDraft));
        when(store.findPhysicalDrafts(TENANT_ID, APPLICATION_ID)).thenReturn(List.of(first, second));
        when(numberStrategy.reserve(SubsystemNumberRequest.logical(TENANT_ID, APPLICATION_ID)))
                .thenReturn(logicalReservation);
        when(numberStrategy.reserve(SubsystemNumberRequest.physical(TENANT_ID, APPLICATION_ID, 1, 3)))
                .thenReturn(firstReservation);
        when(numberStrategy.reserve(SubsystemNumberRequest.physical(TENANT_ID, APPLICATION_ID, 9, 3)))
                .thenReturn(secondReservation);
        when(store.compareAndSetApplicationStatus(TENANT_ID, APPLICATION_ID, ApplicationStatus.IN_REVIEW, 5L,
                ApplicationStatus.APPROVED, operator.id())).thenReturn(true);

        SubsystemPublicationService.ApprovalResult result = service.approve(command(), operator);

        assertThat(result.logicalSubsystemId()).isEqualTo(1_001L);
        assertThat(result.physicalSubsystemIds()).containsExactly(1_002L, 1_003L);
        verify(store).insertLogicalPublished(1_001L, TENANT_ID, "A0003", 3, logicalDraft,
                PublishedStatus.ACTIVE, 0L, operator.id());
        verify(store).insertPhysicalPublished(1_002L, TENANT_ID, "W00031", "1", 1_001L, first,
                PublishedStatus.ACTIVE, 0L, operator.id());
        verify(store).insertPhysicalPublished(1_003L, TENANT_ID, "W00032", "2", 1_001L, second,
                PublishedStatus.ACTIVE, 0L, operator.id());
        verify(numberStrategy).consume(logicalReservation);
        verify(numberStrategy).consume(firstReservation);
        verify(numberStrategy).consume(secondReservation);
        verify(store).deleteValueReservations(TENANT_ID, APPLICATION_ID);
        assertThat(transactionManager.commits).isEqualTo(1);
    }

    @Test
    void physicalCreateLocksActiveParentAndConsumesParentSlotNumber() {
        ChangeApplication application = application(TargetKind.PHYSICAL, ActionType.CREATE, null);
        PhysicalDraft draft = physicalDraft(4, 55L, "A");
        LogicalPublishedState parent = new LogicalPublishedState(55L, TENANT_ID, "A0012", 12,
                PublishedStatus.ACTIVE, 0, 4L, false);
        SubsystemNumberReservation reservation = physicalReservation(4, 12, 10, "W0012A");
        stubApplication(application);
        when(store.findLogicalDraft(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.empty());
        when(store.findPhysicalDrafts(TENANT_ID, APPLICATION_ID)).thenReturn(List.of(draft));
        when(store.lockLogical(TENANT_ID, 55L)).thenReturn(Optional.of(parent));
        when(numberStrategy.reserve(SubsystemNumberRequest.physical(TENANT_ID, APPLICATION_ID, 4, 12)))
                .thenReturn(reservation);
        when(store.compareAndSetApplicationStatus(TENANT_ID, APPLICATION_ID, ApplicationStatus.IN_REVIEW, 5L,
                ApplicationStatus.APPROVED, operator.id())).thenReturn(true);

        SubsystemPublicationService.ApprovalResult result = service.approve(command(), operator);

        assertThat(result.logicalSubsystemId()).isNull();
        assertThat(result.physicalSubsystemIds()).containsExactly(1_001L);
        verify(store).lockLogical(TENANT_ID, 55L);
        verify(store).insertPhysicalPublished(1_001L, TENANT_ID, "W0012A", "A", 55L, draft,
                PublishedStatus.ACTIVE, 0L, operator.id());
        verify(numberStrategy).consume(reservation);
        verify(store).deleteValueReservations(TENANT_ID, APPLICATION_ID);
        assertThat(transactionManager.commits).isEqualTo(1);
    }

    @Test
    void duplicateChildWriteEscapesCallbackAndRollsBackWholeApproval() {
        ChangeApplication application = application(TargetKind.LOGICAL, ActionType.CREATE, null);
        LogicalDraft logicalDraft = logicalDraft(1);
        PhysicalDraft child = physicalDraft(1, null, "1");
        SubsystemNumberReservation logicalReservation = logicalReservation(1);
        SubsystemNumberReservation physicalReservation = physicalReservation(1, 1, 1, "W00011");
        stubApplication(application);
        when(store.findLogicalDraft(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.of(logicalDraft));
        when(store.findPhysicalDrafts(TENANT_ID, APPLICATION_ID)).thenReturn(List.of(child));
        when(numberStrategy.reserve(SubsystemNumberRequest.logical(TENANT_ID, APPLICATION_ID)))
                .thenReturn(logicalReservation);
        when(numberStrategy.reserve(SubsystemNumberRequest.physical(TENANT_ID, APPLICATION_ID, 1, 1)))
                .thenReturn(physicalReservation);
        when(store.compareAndSetApplicationStatus(TENANT_ID, APPLICATION_ID, ApplicationStatus.IN_REVIEW, 5L,
                ApplicationStatus.APPROVED, operator.id())).thenReturn(true);
        doThrow(new DuplicateKeyException("uk_arch_physical_code"))
                .when(store).insertPhysicalPublished(anyLong(), eq(TENANT_ID), anyString(), anyString(), anyLong(),
                        any(PhysicalDraft.class), eq(PublishedStatus.ACTIVE), eq(0L), eq(operator.id()));

        assertThatThrownBy(() -> service.approve(command(), operator))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT));

        assertThat(transactionManager.commits).isZero();
        assertThat(transactionManager.rollbacks).isEqualTo(1);
        verify(numberStrategy, never()).consume(any());
        verify(store, never()).deleteValueReservations(TENANT_ID, APPLICATION_ID);
        verify(store, never()).insertHistory(any(ChangeHistoryEvent.class));
    }

    @Test
    void approvedCasFailureDoesNotWritePublishedRecordsOrCleanupReservations() {
        ChangeApplication application = application(TargetKind.LOGICAL, ActionType.CREATE, null);
        LogicalDraft draft = logicalDraft(2);
        SubsystemNumberReservation reservation = logicalReservation(2);
        stubApplication(application);
        when(store.findLogicalDraft(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.of(draft));
        when(store.findPhysicalDrafts(TENANT_ID, APPLICATION_ID)).thenReturn(List.of());
        when(numberStrategy.reserve(SubsystemNumberRequest.logical(TENANT_ID, APPLICATION_ID)))
                .thenReturn(reservation);
        when(store.compareAndSetApplicationStatus(TENANT_ID, APPLICATION_ID, ApplicationStatus.IN_REVIEW, 5L,
                ApplicationStatus.APPROVED, operator.id())).thenReturn(false);

        assertThatThrownBy(() -> service.approve(command(), operator))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT));

        verify(store, never()).insertLogicalPublished(anyLong(), anyLong(), anyString(), any(), any(), any(), anyLong(), anyLong());
        verify(numberStrategy, never()).consume(any());
        verify(store, never()).deleteValueReservations(TENANT_ID, APPLICATION_ID);
        verify(store, never()).insertHistory(any(ChangeHistoryEvent.class));
        assertThat(transactionManager.rollbacks).isEqualTo(1);
    }

    @ParameterizedTest(name = "逻辑 {0}：{1} -> allowed={2}")
    @MethodSource("logicalStateMatrix")
    void logicalApprovalStateMatrix(ActionType actionType, PublishedStatus currentStatus, boolean allowed,
                                    ReferenceCheckRequest.Operation guardOperation) {
        long targetId = 101L;
        ChangeApplication application = application(TargetKind.LOGICAL, actionType, targetId);
        LogicalDraft draft = existingLogicalDraft(targetId, 4L);
        LogicalPublishedState target = logicalState(targetId, 12, currentStatus, 4L);
        stubApplication(application);
        stubOwnedTargetLock(application);
        when(store.findLogicalDraft(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.of(draft));
        when(store.findPhysicalDrafts(TENANT_ID, APPLICATION_ID)).thenReturn(List.of());
        when(store.lockLogical(TENANT_ID, targetId)).thenReturn(Optional.of(target));
        when(store.compareAndSetApplicationStatus(TENANT_ID, APPLICATION_ID, ApplicationStatus.IN_REVIEW, 5L,
                ApplicationStatus.APPROVED, operator.id())).thenReturn(true);
        if (actionType == ActionType.UPDATE) {
            when(store.updateLogicalPublishedFields(TENANT_ID, targetId, draft, 4L, operator.id())).thenReturn(true);
        } else {
            when(store.updateLogicalPublishedStatus(eq(TENANT_ID), eq(targetId), any(PublishedStatus.class),
                    eq(4L), eq(operator.id()))).thenReturn(true);
        }

        if (!allowed) {
            assertConflict(() -> service.approve(command(), operator));
            verify(store, never()).compareAndSetApplicationStatus(anyLong(), anyLong(), any(), anyLong(), any(), anyLong());
            verify(referenceGuard, never()).requireClear(any());
            assertThat(transactionManager.rollbacks).isEqualTo(1);
            return;
        }

        SubsystemPublicationService.ApprovalResult result = service.approve(command(), operator);

        assertThat(result.logicalSubsystemId()).isEqualTo(targetId);
        assertThat(result.physicalSubsystemIds()).isEmpty();
        if (guardOperation != null) {
            verify(referenceGuard).requireClear(new ReferenceCheckRequest(TENANT_ID,
                    ReferenceCheckRequest.SubsystemKind.LOGICAL, targetId, guardOperation));
        } else {
            verify(referenceGuard, never()).requireClear(any());
        }
        verify(store).deleteTargetLock(TENANT_ID, TargetKind.LOGICAL, targetId, APPLICATION_ID);
        verify(store).deleteValueReservations(TENANT_ID, APPLICATION_ID);
        verify(store).insertHistory(any(ChangeHistoryEvent.class));
        assertThat(transactionManager.commits).isEqualTo(1);
    }

    @ParameterizedTest(name = "物理 {0}：{1} -> allowed={2}")
    @MethodSource("physicalStateMatrix")
    void physicalApprovalStateMatrix(ActionType actionType, PublishedStatus currentStatus, boolean allowed,
                                     ReferenceCheckRequest.Operation guardOperation) {
        long targetId = 201L;
        long parentId = 55L;
        ChangeApplication application = application(TargetKind.PHYSICAL, actionType, targetId);
        PhysicalDraft draft = existingPhysicalDraft(targetId, parentId, "A", 6L);
        PhysicalPublishedState target = physicalState(targetId, parentId, "A", currentStatus, 6L);
        LogicalPublishedState parent = logicalState(parentId, 12, PublishedStatus.ACTIVE, 2L);
        stubApplication(application);
        stubOwnedTargetLock(application);
        when(store.findLogicalDraft(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.empty());
        when(store.findPhysicalDrafts(TENANT_ID, APPLICATION_ID)).thenReturn(List.of(draft));
        when(store.lockPhysical(TENANT_ID, targetId)).thenReturn(Optional.of(target));
        when(store.lockLogical(TENANT_ID, parentId)).thenReturn(Optional.of(parent));
        when(store.compareAndSetApplicationStatus(TENANT_ID, APPLICATION_ID, ApplicationStatus.IN_REVIEW, 5L,
                ApplicationStatus.APPROVED, operator.id())).thenReturn(true);
        if (actionType == ActionType.UPDATE) {
            when(store.updatePhysicalPublishedFields(TENANT_ID, targetId, draft, 6L, operator.id())).thenReturn(true);
        } else {
            when(store.updatePhysicalPublishedStatus(eq(TENANT_ID), eq(targetId), any(PublishedStatus.class),
                    eq(6L), eq(operator.id()))).thenReturn(true);
        }

        if (!allowed) {
            assertConflict(() -> service.approve(command(), operator));
            verify(store, never()).compareAndSetApplicationStatus(anyLong(), anyLong(), any(), anyLong(), any(), anyLong());
            verify(referenceGuard, never()).requireClear(any());
            assertThat(transactionManager.rollbacks).isEqualTo(1);
            return;
        }

        SubsystemPublicationService.ApprovalResult result = service.approve(command(), operator);

        assertThat(result.logicalSubsystemId()).isNull();
        assertThat(result.physicalSubsystemIds()).containsExactly(targetId);
        if (guardOperation != null) {
            verify(referenceGuard).requireClear(new ReferenceCheckRequest(TENANT_ID,
                    ReferenceCheckRequest.SubsystemKind.PHYSICAL, targetId, guardOperation));
        } else {
            verify(referenceGuard, never()).requireClear(any());
        }
        verify(store).deleteTargetLock(TENANT_ID, TargetKind.PHYSICAL, targetId, APPLICATION_ID);
        verify(store).deleteValueReservations(TENANT_ID, APPLICATION_ID);
        verify(store).insertHistory(any(ChangeHistoryEvent.class));
        assertThat(transactionManager.commits).isEqualTo(1);
    }

    @Test
    void physicalUpdateRejectsParentChangeBeforeApplicationApproval() {
        long targetId = 201L;
        ChangeApplication application = application(TargetKind.PHYSICAL, ActionType.UPDATE, targetId);
        PhysicalDraft movedDraft = existingPhysicalDraft(targetId, 66L, "A", 6L);
        stubApplication(application);
        stubOwnedTargetLock(application);
        when(store.findLogicalDraft(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.empty());
        when(store.findPhysicalDrafts(TENANT_ID, APPLICATION_ID)).thenReturn(List.of(movedDraft));
        when(store.lockPhysical(TENANT_ID, targetId)).thenReturn(Optional.of(
                physicalState(targetId, 55L, "A", PublishedStatus.ACTIVE, 6L)));

        assertConflict(() -> service.approve(command(), operator));

        verify(store, never()).compareAndSetApplicationStatus(anyLong(), anyLong(), any(), anyLong(), any(), anyLong());
        verify(store, never()).updatePhysicalPublishedFields(anyLong(), anyLong(), any(), anyLong(), anyLong());
        assertThat(transactionManager.rollbacks).isEqualTo(1);
    }

    @Test
    void sourceRowCasConflictRollsBackApprovedStateAndKeepsResources() {
        long targetId = 101L;
        ChangeApplication application = application(TargetKind.LOGICAL, ActionType.UPDATE, targetId);
        LogicalDraft draft = existingLogicalDraft(targetId, 4L);
        stubApplication(application);
        stubOwnedTargetLock(application);
        when(store.findLogicalDraft(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.of(draft));
        when(store.findPhysicalDrafts(TENANT_ID, APPLICATION_ID)).thenReturn(List.of());
        when(store.lockLogical(TENANT_ID, targetId)).thenReturn(Optional.of(
                logicalState(targetId, 12, PublishedStatus.ACTIVE, 4L)));
        when(store.compareAndSetApplicationStatus(TENANT_ID, APPLICATION_ID, ApplicationStatus.IN_REVIEW, 5L,
                ApplicationStatus.APPROVED, operator.id())).thenReturn(true);
        when(store.updateLogicalPublishedFields(TENANT_ID, targetId, draft, 4L, operator.id())).thenReturn(false);

        assertConflict(() -> service.approve(command(), operator));

        verify(store, never()).deleteTargetLock(TENANT_ID, TargetKind.LOGICAL, targetId, APPLICATION_ID);
        verify(store, never()).deleteValueReservations(TENANT_ID, APPLICATION_ID);
        verify(store, never()).insertHistory(any(ChangeHistoryEvent.class));
        assertThat(transactionManager.commits).isZero();
        assertThat(transactionManager.rollbacks).isEqualTo(1);
    }

    @Test
    void nonCreateRejectsTargetLockOwnedByAnotherApplication() {
        long targetId = 101L;
        ChangeApplication application = application(TargetKind.LOGICAL, ActionType.UPDATE, targetId);
        stubApplication(application);
        when(store.findTargetLock(TENANT_ID, TargetKind.LOGICAL, targetId)).thenReturn(Optional.of(
                new TargetLock(TENANT_ID, TargetKind.LOGICAL, targetId, 999L, LocalDateTime.now())));

        assertConflict(() -> service.approve(command(), operator));

        verify(store, never()).lockLogical(TENANT_ID, targetId);
        verify(store, never()).compareAndSetApplicationStatus(anyLong(), anyLong(), any(), anyLong(), any(), anyLong());
        assertThat(transactionManager.rollbacks).isEqualTo(1);
    }

    @Test
    void logicalVoidGuardIndeterminateFailsClosedBeforeApprovedCas() {
        long targetId = 101L;
        ChangeApplication application = application(TargetKind.LOGICAL, ActionType.VOID, targetId);
        LogicalDraft draft = existingLogicalDraft(targetId, 4L);
        ReferenceCheckRequest request = new ReferenceCheckRequest(TENANT_ID,
                ReferenceCheckRequest.SubsystemKind.LOGICAL, targetId, ReferenceCheckRequest.Operation.VOID);
        stubApplication(application);
        stubOwnedTargetLock(application);
        when(store.findLogicalDraft(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.of(draft));
        when(store.findPhysicalDrafts(TENANT_ID, APPLICATION_ID)).thenReturn(List.of());
        when(store.lockLogical(TENANT_ID, targetId)).thenReturn(Optional.of(
                logicalState(targetId, 12, PublishedStatus.ACTIVE, 4L)));
        doThrow(new BusinessException(SubsystemReferenceGuard.SERVICE_UNAVAILABLE, "引用检查暂不可用"))
                .when(referenceGuard).requireClear(request);

        assertThatThrownBy(() -> service.approve(command(), operator))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(SubsystemReferenceGuard.SERVICE_UNAVAILABLE));

        verify(store, never()).compareAndSetApplicationStatus(anyLong(), anyLong(), any(), anyLong(), any(), anyLong());
        verify(store, never()).updateLogicalPublishedStatus(anyLong(), anyLong(), any(), anyLong(), anyLong());
        assertThat(transactionManager.rollbacks).isEqualTo(1);
    }

    @Test
    void physicalReplacePublishesNewRecordOfflinesOldAndCreatesImmutableRelation() {
        long oldPhysicalId = 201L;
        long oldParentId = 55L;
        long newParentId = 66L;
        ChangeApplication application = application(TargetKind.PHYSICAL, ActionType.REPLACE, oldPhysicalId);
        PhysicalDraft draft = existingPhysicalDraft(oldPhysicalId, newParentId, "1", 6L);
        SubsystemNumberReservation reservation = physicalReservation(1, 20, 1, "W00201");
        stubReplace(application, draft, oldPhysicalId, oldParentId, newParentId, reservation);
        when(store.compareAndSetApplicationStatus(TENANT_ID, APPLICATION_ID, ApplicationStatus.IN_REVIEW, 5L,
                ApplicationStatus.APPROVED, operator.id())).thenReturn(true);
        when(store.updatePhysicalPublishedStatus(TENANT_ID, oldPhysicalId, PublishedStatus.OFFLINE, 6L,
                operator.id())).thenReturn(true);

        SubsystemPublicationService.ApprovalResult result = service.approve(command(), operator);

        assertThat(result.physicalSubsystemIds()).containsExactly(1_001L);
        verify(store).insertPhysicalPublished(1_001L, TENANT_ID, "W00201", "1", newParentId, draft,
                PublishedStatus.ACTIVE, 0L, operator.id());
        verify(store).updatePhysicalPublishedStatus(TENANT_ID, oldPhysicalId, PublishedStatus.OFFLINE, 6L,
                operator.id());
        ArgumentCaptor<PhysicalReplacement> replacement = ArgumentCaptor.forClass(PhysicalReplacement.class);
        verify(store).insertPhysicalReplacement(replacement.capture());
        assertThat(replacement.getValue()).satisfies(value -> {
            assertThat(value.id()).isEqualTo(1_002L);
            assertThat(value.oldPhysicalSubsystemId()).isEqualTo(oldPhysicalId);
            assertThat(value.newPhysicalSubsystemId()).isEqualTo(1_001L);
            assertThat(value.applicationId()).isEqualTo(APPLICATION_ID);
        });
        verify(numberStrategy).consume(reservation);
        verify(store).deleteTargetLock(TENANT_ID, TargetKind.PHYSICAL, oldPhysicalId, APPLICATION_ID);
        verify(store).deleteValueReservations(TENANT_ID, APPLICATION_ID);
        verify(referenceGuard).requireClear(new ReferenceCheckRequest(TENANT_ID,
                ReferenceCheckRequest.SubsystemKind.PHYSICAL, oldPhysicalId,
                ReferenceCheckRequest.Operation.OFFLINE));
        assertThat(transactionManager.commits).isEqualTo(1);
    }

    @Test
    void physicalReplaceOldStatusCasFailureRollsBackInsertNumberAndResources() {
        long oldPhysicalId = 201L;
        long oldParentId = 55L;
        long newParentId = 66L;
        ChangeApplication application = application(TargetKind.PHYSICAL, ActionType.REPLACE, oldPhysicalId);
        PhysicalDraft draft = existingPhysicalDraft(oldPhysicalId, newParentId, "1", 6L);
        SubsystemNumberReservation reservation = physicalReservation(1, 20, 1, "W00201");
        stubReplace(application, draft, oldPhysicalId, oldParentId, newParentId, reservation);
        when(store.compareAndSetApplicationStatus(TENANT_ID, APPLICATION_ID, ApplicationStatus.IN_REVIEW, 5L,
                ApplicationStatus.APPROVED, operator.id())).thenReturn(true);
        when(store.updatePhysicalPublishedStatus(TENANT_ID, oldPhysicalId, PublishedStatus.OFFLINE, 6L,
                operator.id())).thenReturn(false);

        assertConflict(() -> service.approve(command(), operator));

        verify(store).insertPhysicalPublished(1_001L, TENANT_ID, "W00201", "1", newParentId, draft,
                PublishedStatus.ACTIVE, 0L, operator.id());
        verify(store, never()).insertPhysicalReplacement(any(PhysicalReplacement.class));
        verify(numberStrategy, never()).consume(any());
        verify(store, never()).deleteTargetLock(anyLong(), any(), anyLong(), anyLong());
        verify(store, never()).deleteValueReservations(TENANT_ID, APPLICATION_ID);
        verify(store, never()).insertHistory(any(ChangeHistoryEvent.class));
        assertThat(transactionManager.commits).isZero();
        assertThat(transactionManager.rollbacks).isEqualTo(1);
    }

    private static Stream<Arguments> logicalStateMatrix() {
        return Stream.of(
                Arguments.of(ActionType.UPDATE, PublishedStatus.ACTIVE, true, null),
                Arguments.of(ActionType.UPDATE, PublishedStatus.OFFLINE, true, null),
                Arguments.of(ActionType.UPDATE, PublishedStatus.VOIDED, false, null),
                Arguments.of(ActionType.OFFLINE, PublishedStatus.ACTIVE, true,
                        ReferenceCheckRequest.Operation.OFFLINE),
                Arguments.of(ActionType.OFFLINE, PublishedStatus.OFFLINE, false, null),
                Arguments.of(ActionType.OFFLINE, PublishedStatus.VOIDED, false, null),
                Arguments.of(ActionType.REACTIVATE, PublishedStatus.ACTIVE, false, null),
                Arguments.of(ActionType.REACTIVATE, PublishedStatus.OFFLINE, true, null),
                Arguments.of(ActionType.REACTIVATE, PublishedStatus.VOIDED, false, null),
                Arguments.of(ActionType.VOID, PublishedStatus.ACTIVE, true, ReferenceCheckRequest.Operation.VOID),
                Arguments.of(ActionType.VOID, PublishedStatus.OFFLINE, true, ReferenceCheckRequest.Operation.VOID),
                Arguments.of(ActionType.VOID, PublishedStatus.VOIDED, false, null));
    }

    private static Stream<Arguments> physicalStateMatrix() {
        return logicalStateMatrix();
    }

    private void stubApplication(ChangeApplication application) {
        when(store.lockApplication(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.of(application));
    }

    private void stubOwnedTargetLock(ChangeApplication application) {
        when(store.findTargetLock(TENANT_ID, application.targetKind(), application.targetId())).thenReturn(Optional.of(
                new TargetLock(TENANT_ID, application.targetKind(), application.targetId(), APPLICATION_ID,
                        LocalDateTime.of(2026, 8, 23, 9, 0))));
    }

    private void stubReplace(ChangeApplication application, PhysicalDraft draft, long oldPhysicalId,
                             long oldParentId, long newParentId, SubsystemNumberReservation reservation) {
        stubApplication(application);
        stubOwnedTargetLock(application);
        when(store.findLogicalDraft(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.empty());
        when(store.findPhysicalDrafts(TENANT_ID, APPLICATION_ID)).thenReturn(List.of(draft));
        when(store.lockPhysical(TENANT_ID, oldPhysicalId)).thenReturn(Optional.of(
                physicalState(oldPhysicalId, oldParentId, "A", PublishedStatus.ACTIVE, 6L)));
        when(store.lockLogical(TENANT_ID, oldParentId)).thenReturn(Optional.of(
                logicalState(oldParentId, 12, PublishedStatus.ACTIVE, 2L)));
        when(store.lockLogical(TENANT_ID, newParentId)).thenReturn(Optional.of(
                logicalState(newParentId, 20, PublishedStatus.ACTIVE, 3L)));
        when(numberStrategy.reserve(SubsystemNumberRequest.physical(
                TENANT_ID, APPLICATION_ID, draft.lineNo(), 20))).thenReturn(reservation);
    }

    private void assertConflict(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT));
    }

    private SubsystemPublicationService.ApprovalCommand command() {
        return new SubsystemPublicationService.ApprovalCommand(
                APPLICATION_ID, 2, 5L, WORKFLOW_INSTANCE_ID, DIGEST);
    }

    private ChangeApplication application(TargetKind targetKind, ActionType actionType, Long targetId) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 23, 10, 0);
        return new ChangeApplication(APPLICATION_ID, TENANT_ID, targetKind, actionType, targetId, 66L,
                "用户说明", ApplicationStatus.IN_REVIEW, 2, 100L, 101L, WORKFLOW_INSTANCE_ID, DIGEST,
                false, 5L, 66L, 66L, now, now);
    }

    private LogicalDraft logicalDraft(int reservedSequence) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 23, 10, 0);
        return new LogicalDraft(APPLICATION_ID, TENANT_ID, null, "逻辑简称", "逻辑主数据", 10L,
                "CLOUD", "CORE", "SELF", 21L, "逻辑描述", "逻辑备注", 8,
                reservedSequence, null, 1, "{\"submitted\":true}", now, now);
    }

    private LogicalDraft existingLogicalDraft(long sourceId, long sourceRowVersion) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 23, 10, 0);
        return new LogicalDraft(APPLICATION_ID, TENANT_ID, sourceId, "更新后逻辑简称", "更新后逻辑主数据", 10L,
                "CLOUD", "CORE", "SELF", 21L, "更新后逻辑描述", "更新后逻辑备注", 9,
                12, sourceRowVersion, 2, "{\"submitted\":true}", now, now);
    }

    private PhysicalDraft physicalDraft(int lineNo, Long parentId, String reservedSlot) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 23, 10, 0);
        return new PhysicalDraft(APPLICATION_ID, lineNo, TENANT_ID, null, parentId,
                "物理简称" + lineNo, "物理主数据" + lineNo, "physical-" + lineNo,
                "业务组", 31L, "负责团队", "JAVA", "L2", "SPRING", 41L,
                "物理描述", "物理备注", reservedSlot, null, 1, "{\"submitted\":true}", now, now);
    }

    private PhysicalDraft existingPhysicalDraft(long sourceId, long parentId, String reservedSlot,
                                                 long sourceRowVersion) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 23, 10, 0);
        return new PhysicalDraft(APPLICATION_ID, 1, TENANT_ID, sourceId, parentId,
                "更新后物理简称", "更新后物理主数据", "updated-physical", "更新后业务组", 31L,
                "负责团队", "JAVA", "L2", "SPRING", 41L, "更新后物理描述", "更新后物理备注",
                reservedSlot, sourceRowVersion, 2, "{\"submitted\":true}", now, now);
    }

    private LogicalPublishedState logicalState(long id, int numberSequence, PublishedStatus status,
                                                long rowVersion) {
        return new LogicalPublishedState(id, TENANT_ID, "A%04d".formatted(numberSequence), numberSequence,
                status, 0, rowVersion, false);
    }

    private PhysicalPublishedState physicalState(long id, long parentId, String numberSlot,
                                                  PublishedStatus status, long rowVersion) {
        return new PhysicalPublishedState(id, TENANT_ID, "W0012" + numberSlot, numberSlot, parentId,
                "physical-" + id, status, rowVersion, false);
    }

    private SubsystemNumberReservation logicalReservation(int ordinal) {
        return new SubsystemNumberReservation(0L, "LOGICAL", ordinal, TENANT_ID, APPLICATION_ID, 0,
                SubsystemNumberKind.LOGICAL, null, "A%04d".formatted(ordinal));
    }

    private SubsystemNumberReservation physicalReservation(int lineNo, int logicalSequence, int ordinal, String code) {
        return new SubsystemNumberReservation(0L, "PHYSICAL:" + logicalSequence, ordinal,
                TENANT_ID, APPLICATION_ID, lineNo, SubsystemNumberKind.PHYSICAL, logicalSequence, code);
    }

    private static final class RecordingTransactionManager implements PlatformTransactionManager {
        private int commits;
        private int rollbacks;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus(true);
        }

        @Override
        public void commit(TransactionStatus status) {
            commits++;
        }

        @Override
        public void rollback(TransactionStatus status) {
            rollbacks++;
        }
    }
}
