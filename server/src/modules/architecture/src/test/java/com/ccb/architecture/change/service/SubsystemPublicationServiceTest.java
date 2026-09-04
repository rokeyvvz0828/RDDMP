package com.ccb.architecture.change.service;

import com.ccb.architecture.change.model.SubsystemChangeModels.ActionType;
import com.ccb.architecture.change.model.SubsystemChangeModels.ApplicationStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeApplication;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeHistoryEvent;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalDraft;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalPublishedState;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalReplacement;
import com.ccb.architecture.change.model.SubsystemChangeModels.PublishedStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetKind;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetLock;
import com.ccb.architecture.change.persistence.SubsystemChangeStore;
import com.ccb.architecture.integration.ReferenceCheckRequest;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubsystemPublicationServiceTest {
    private static final long TENANT_ID = 7L;
    private static final long APPLICATION_ID = 100L;
    private static final AuthUser OPERATOR = new AuthUser(9L, TENANT_ID, "reviewer", "hash", "审批人", 11L, true);
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 8, 24, 10, 0);

    @Mock
    private SubsystemChangeStore store;
    @Mock
    private SubsystemReferenceGuard referenceGuard;
    @Mock
    private TransactionTemplate transactions;

    private SubsystemPublicationService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        AtomicLong ids = new AtomicLong(2_000L);
        service = new SubsystemPublicationService(store, referenceGuard, transactions, ids::getAndIncrement);
    }

    @Test
    void approveCreatePublishesSelfFilledPhysicalCodeFromSubmittedSnapshot() {
        ChangeApplication application = application(ActionType.CREATE, null, TargetKind.PHYSICAL);
        PhysicalDraft draft = draft(application, "PHY_MALL", null, null, "submitted");
        when(store.lockApplication(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.of(application));
        when(store.findPhysicalDrafts(TENANT_ID, APPLICATION_ID)).thenReturn(List.of(draft));
        when(store.compareAndSetApplicationStatus(TENANT_ID, APPLICATION_ID, ApplicationStatus.IN_REVIEW,
                4L, ApplicationStatus.APPROVED, OPERATOR.id())).thenReturn(true);

        SubsystemPublicationService.ApprovalResult result = service.approve(command(), OPERATOR);

        assertThat(result.applicationId()).isEqualTo(APPLICATION_ID);
        assertThat(result.physicalSubsystemIds()).containsExactly(2_000L);
        verify(store).insertPhysicalPublished(2_000L, TENANT_ID, draft, PublishedStatus.ACTIVE, 0L, OPERATOR.id());
        verify(store).deleteValueReservations(TENANT_ID, APPLICATION_ID);
        verify(store).insertHistory(any(ChangeHistoryEvent.class));
    }

    @Test
    void approveUpdateKeepsExistingPhysicalCodeAndUpdatesSamePublishedRecord() {
        long targetId = 501L;
        ChangeApplication application = application(ActionType.UPDATE, targetId, TargetKind.PHYSICAL);
        PhysicalDraft draft = draft(application, "PHY_MALL", targetId, 6L, "submitted");
        PhysicalPublishedState target = physicalState(targetId, "PHY_MALL", PublishedStatus.ACTIVE, 6L);
        when(store.lockApplication(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.of(application));
        when(store.findTargetLock(TENANT_ID, TargetKind.PHYSICAL, targetId)).thenReturn(Optional.of(targetLock(targetId)));
        when(store.findPhysicalDrafts(TENANT_ID, APPLICATION_ID)).thenReturn(List.of(draft));
        when(store.lockPhysical(TENANT_ID, targetId)).thenReturn(Optional.of(target));
        when(store.compareAndSetApplicationStatus(TENANT_ID, APPLICATION_ID, ApplicationStatus.IN_REVIEW,
                4L, ApplicationStatus.APPROVED, OPERATOR.id())).thenReturn(true);
        when(store.updatePhysicalPublishedFields(TENANT_ID, targetId, draft, 6L, OPERATOR.id())).thenReturn(true);

        SubsystemPublicationService.ApprovalResult result = service.approve(command(), OPERATOR);

        assertThat(result.physicalSubsystemIds()).containsExactly(targetId);
        verify(store).updatePhysicalPublishedFields(TENANT_ID, targetId, draft, 6L, OPERATOR.id());
        verify(store).deleteTargetLock(TENANT_ID, TargetKind.PHYSICAL, targetId, APPLICATION_ID);
        verify(store, never()).insertPhysicalPublished(eq(targetId), anyLong(), any(), any(), anyLong(), anyLong());
    }

    @Test
    void approveReplaceCreatesNewPhysicalAndOfflinesOldRecordAfterReferenceGuardClear() {
        long targetId = 501L;
        ChangeApplication application = application(ActionType.REPLACE, targetId, TargetKind.PHYSICAL);
        PhysicalDraft draft = draft(application, "PHY_MALL_V2", targetId, 6L, "submitted");
        PhysicalPublishedState target = physicalState(targetId, "PHY_MALL", PublishedStatus.ACTIVE, 6L);
        when(store.lockApplication(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.of(application));
        when(store.findTargetLock(TENANT_ID, TargetKind.PHYSICAL, targetId)).thenReturn(Optional.of(targetLock(targetId)));
        when(store.findPhysicalDrafts(TENANT_ID, APPLICATION_ID)).thenReturn(List.of(draft));
        when(store.lockPhysical(TENANT_ID, targetId)).thenReturn(Optional.of(target));
        when(store.compareAndSetApplicationStatus(TENANT_ID, APPLICATION_ID, ApplicationStatus.IN_REVIEW,
                4L, ApplicationStatus.APPROVED, OPERATOR.id())).thenReturn(true);
        when(store.updatePhysicalPublishedStatus(TENANT_ID, targetId, PublishedStatus.OFFLINE, 6L, OPERATOR.id()))
                .thenReturn(true);

        SubsystemPublicationService.ApprovalResult result = service.approve(command(), OPERATOR);

        assertThat(result.physicalSubsystemIds()).containsExactly(2_000L);
        verify(referenceGuard).requireClear(new ReferenceCheckRequest(TENANT_ID,
                ReferenceCheckRequest.SubsystemKind.PHYSICAL, targetId, ReferenceCheckRequest.Operation.OFFLINE));
        verify(store).insertPhysicalPublished(2_000L, TENANT_ID, draft, PublishedStatus.ACTIVE, 0L, OPERATOR.id());
        verify(store).updatePhysicalPublishedStatus(TENANT_ID, targetId, PublishedStatus.OFFLINE, 6L, OPERATOR.id());
        ArgumentCaptor<PhysicalReplacement> replacement = ArgumentCaptor.forClass(PhysicalReplacement.class);
        verify(store).insertPhysicalReplacement(replacement.capture());
        assertThat(replacement.getValue().oldPhysicalSubsystemId()).isEqualTo(targetId);
        assertThat(replacement.getValue().newPhysicalSubsystemId()).isEqualTo(2_000L);
    }

    @Test
    void logicalApplicationCannotBeApprovedAfterLogicalSubsystemRetirement() {
        ChangeApplication application = application(ActionType.CREATE, null, TargetKind.LOGICAL);
        when(store.lockApplication(TENANT_ID, APPLICATION_ID)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.approve(command(), OPERATOR))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.getMessage()).contains("逻辑子系统工单已退役");
                });

        verify(store, never()).findPhysicalDrafts(anyLong(), anyLong());
        verify(store, never()).compareAndSetApplicationStatus(anyLong(), anyLong(), any(), anyLong(), any(), anyLong());
    }

    private SubsystemPublicationService.ApprovalCommand command() {
        return new SubsystemPublicationService.ApprovalCommand(APPLICATION_ID, 2, 4L, 77L, "digest");
    }

    private ChangeApplication application(ActionType action, Long targetId, TargetKind targetKind) {
        return new ChangeApplication(APPLICATION_ID, TENANT_ID, targetKind, action, targetId,
                OPERATOR.id(), "申请原因", ApplicationStatus.IN_REVIEW, 2,
                31L, 32L, 77L, "digest", false, 4L, OPERATOR.id(), OPERATOR.id(), TIME, TIME);
    }

    private PhysicalDraft draft(ChangeApplication application, String code, Long sourceId,
                                Long sourceRowVersion, String submittedSnapshot) {
        return new PhysicalDraft(application.id(), 1, application.tenantId(), sourceId, code,
                "商城物理", "商城物理系统", "商城逻辑域",
                "architecture.business-component.employee-portal", "Mall Platform", "渠道",
                "architecture.deployment-platform.p2", "architecture.disaster-recovery.active-active",
                12L, "平台研发团队",
                "RUNTIME", "A", "Spring", 30L, "描述", null, sourceRowVersion,
                0, submittedSnapshot, TIME, TIME);
    }

    private PhysicalPublishedState physicalState(long id, String code, PublishedStatus status, long rowVersion) {
        return new PhysicalPublishedState(id, TENANT_ID, code, "商城逻辑域",
                "architecture.business-component.employee-portal", "Mall Platform",
                status, rowVersion, false);
    }

    private TargetLock targetLock(long targetId) {
        return new TargetLock(TENANT_ID, TargetKind.PHYSICAL, targetId, APPLICATION_ID, TIME);
    }
}
