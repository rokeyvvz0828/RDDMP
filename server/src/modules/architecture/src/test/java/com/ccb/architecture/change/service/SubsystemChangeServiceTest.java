package com.ccb.architecture.change.service;

import com.ccb.architecture.change.model.SubsystemChangeModels.ActionType;
import com.ccb.architecture.change.model.SubsystemChangeModels.ApplicationStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeApplication;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeHistoryEvent;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalDraft;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetKind;
import com.ccb.architecture.change.model.SubsystemChangeModels.ValueReservation;
import com.ccb.architecture.change.persistence.SubsystemChangeStore;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
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
class SubsystemChangeServiceTest {
    private static final AuthUser ACTOR = new AuthUser(9L, 7L, "architect", "hash", "架构师", 11L, true);
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 8, 23, 10, 0);

    @Mock
    private SubsystemChangeStore store;
    @Mock
    private SystemReferenceQuery referenceQuery;
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
        AtomicLong ids = new AtomicLong(10_000L);
        service = new SubsystemChangeService(store, referenceQuery, transactions, ids::getAndIncrement,
                Clock.fixed(Instant.parse("2026-08-23T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createPhysicalNormalizesSelfFilledCodeAndBusinessComponentDictionaryValue() {
        when(referenceQuery.activeParameters(ACTOR, "ARCH_BUSINESS_COMPONENT"))
                .thenReturn(List.of(new SystemParameterReference(
                        "architecture.business-component.employee-portal", "员工门户")));

        SubsystemChangeService.ApplicationDetail detail = service.createPhysical(ACTOR,
                new SubsystemChangeService.PhysicalApplicationCommand(ActionType.CREATE, null, " 申请原因 ",
                        input(" phy_mall ", null, null)));

        assertThat(detail.application().targetKind()).isEqualTo(TargetKind.PHYSICAL);
        assertThat(detail.application().reason()).isEqualTo("申请原因");
        assertThat(detail.physicalDrafts()).singleElement().satisfies(draft -> {
            assertThat(draft.code()).isEqualTo("PHY_MALL");
            assertThat(draft.logicalSubsystemName()).isEqualTo("商城逻辑域");
            assertThat(draft.businessComponentCode()).isEqualTo("architecture.business-component.employee-portal");
            assertThat(draft.sourcePhysicalSubsystemId()).isNull();
            assertThat(draft.sourceRowVersion()).isNull();
        });
        verify(store).insertApplication(detail.application());
        verify(store).replacePhysicalDrafts(eq(ACTOR.tenantId()), eq(detail.application().id()), any());
    }

    @Test
    void createPhysicalRejectsInvalidBusinessComponentBeforePersistingDraft() {
        when(referenceQuery.activeParameters(ACTOR, "ARCH_BUSINESS_COMPONENT")).thenReturn(List.of());

        assertThatThrownBy(() -> service.createPhysical(ACTOR,
                new SubsystemChangeService.PhysicalApplicationCommand(ActionType.CREATE, null, "申请原因",
                        input("PHY_MALL", null, null))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(store, never()).insertApplication(any());
        verify(store, never()).replacePhysicalDrafts(anyLong(), anyLong(), any());
    }

    @Test
    void coordinateSubmissionStoresSnapshotReservesValuesAndReturnsPhysicalCodes() {
        ChangeApplication application = application(100L, ActionType.CREATE, null, ApplicationStatus.DRAFT, 0L);
        PhysicalDraft draft = draft(application, "PHY_MALL", null, null, null);
        when(store.lockApplication(ACTOR.tenantId(), application.id())).thenReturn(Optional.of(application));
        when(store.findPhysicalDrafts(ACTOR.tenantId(), application.id())).thenReturn(List.of(draft));
        when(store.findValueReservation(eq(ACTOR.tenantId()), any(), any())).thenReturn(Optional.empty());
        when(store.compareAndSetApplicationStatus(ACTOR.tenantId(), application.id(), ApplicationStatus.DRAFT,
                0L, ApplicationStatus.IN_REVIEW, ACTOR.id())).thenReturn(true);

        SubsystemChangeService.SubmissionPreparation preparation = service.coordinateSubmission(ACTOR,
                SubsystemChangeService.AccessScope.OWN, application.id(), 0L, ignored -> { });

        assertThat(preparation.applicationId()).isEqualTo(application.id());
        assertThat(preparation.nextRound()).isEqualTo(1);
        assertThat(preparation.snapshot()).contains("physical[1].code", "PHY_MALL",
                "physical[1].businessComponentCode", "architecture.business-component.employee-portal");
        assertThat(preparation.digest()).hasSize(64);
        assertThat(preparation.physicalSubsystemCodes()).containsExactly("PHY_MALL");

        ArgumentCaptor<List<PhysicalDraft>> submittedDrafts = ArgumentCaptor.forClass(List.class);
        verify(store).replacePhysicalDrafts(eq(ACTOR.tenantId()), eq(application.id()), submittedDrafts.capture());
        assertThat(submittedDrafts.getValue()).singleElement()
                .satisfies(item -> assertThat(item.submittedSnapshotJson()).isEqualTo(preparation.snapshot()));

        ArgumentCaptor<ValueReservation> reservations = ArgumentCaptor.forClass(ValueReservation.class);
        verify(store, org.mockito.Mockito.times(3)).insertValueReservation(reservations.capture());
        assertThat(reservations.getAllValues()).extracting(ValueReservation::reservationScope)
                .containsExactly("PHYSICAL_CODE", "PHYSICAL_NAME", "PHYSICAL_ENGLISH_NAME");
        verify(store).insertHistory(any(ChangeHistoryEvent.class));
    }

    @Test
    void coordinateSubmissionRejectsDuplicateSelfFilledCode() {
        ChangeApplication application = application(101L, ActionType.CREATE, null, ApplicationStatus.DRAFT, 0L);
        PhysicalDraft draft = draft(application, "PHY_DUP", null, null, null);
        when(store.lockApplication(ACTOR.tenantId(), application.id())).thenReturn(Optional.of(application));
        when(store.findPhysicalDrafts(ACTOR.tenantId(), application.id())).thenReturn(List.of(draft));
        when(store.physicalCodeExists(ACTOR.tenantId(), "PHY_DUP", null)).thenReturn(true);

        assertThatThrownBy(() -> service.coordinateSubmission(ACTOR,
                SubsystemChangeService.AccessScope.OWN, application.id(), 0L, ignored -> { }))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT));

        verify(store, never()).insertValueReservation(any());
        verify(store, never()).compareAndSetApplicationStatus(anyLong(), anyLong(), any(), anyLong(), any(), anyLong());
    }

    private SubsystemChangeService.PhysicalDraftInput input(String code, Long sourceVersion, Long ownerUserId) {
        return new SubsystemChangeService.PhysicalDraftInput(1, code, "商城物理", "商城物理系统",
                "商城逻辑域", "architecture.business-component.employee-portal",
                "Mall Platform", "渠道",
                "architecture.deployment-platform.p2", "architecture.disaster-recovery.active-active",
                12L, "平台研发团队", "RUNTIME", "A", "Spring",
                ownerUserId, "描述", null, sourceVersion);
    }

    private ChangeApplication application(long id, ActionType action, Long targetId,
                                          ApplicationStatus status, long rowVersion) {
        return new ChangeApplication(id, ACTOR.tenantId(), TargetKind.PHYSICAL, action, targetId,
                ACTOR.id(), "申请原因", status, 0, null, null, null, null,
                false, rowVersion, ACTOR.id(), ACTOR.id(), TIME, TIME);
    }

    private PhysicalDraft draft(ChangeApplication application, String code, Long sourceId,
                                Long sourceVersion, String submittedSnapshotJson) {
        return new PhysicalDraft(application.id(), 1, application.tenantId(), sourceId, code,
                "商城物理", "商城物理系统", "商城逻辑域",
                "architecture.business-component.employee-portal", "Mall Platform", "渠道",
                "architecture.deployment-platform.p2", "architecture.disaster-recovery.active-active",
                12L, "平台研发团队",
                "RUNTIME", "A", "Spring", 30L, "描述", null, sourceVersion,
                0, submittedSnapshotJson, TIME, TIME);
    }
}
