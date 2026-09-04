package com.ccb.architecture.service;

import com.ccb.architecture.integration.DeploymentUnitReferenceCheckRequest;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnit;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitCommand;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitVersion;
import com.ccb.architecture.persistence.DeploymentUnitStore;
import com.ccb.architecture.persistence.DeploymentUnitStore.PhysicalSubsystemRef;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemReferenceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentUnitServiceTest {
    private static final long TENANT_ID = 1L;
    private static final long PHYSICAL_ID = 501L;

    private final DeploymentUnitStore store = mock(DeploymentUnitStore.class);
    private final DeploymentUnitReferenceGuard referenceGuard = mock(DeploymentUnitReferenceGuard.class);
    private final SystemReferenceQuery referenceQuery = mock(SystemReferenceQuery.class);
    private final SystemOperationAudit operationAudit = mock(SystemOperationAudit.class);
    private final AuthUser operator = new AuthUser(88L, TENANT_ID, "tech", "-", "技术架构师", 1L, true);
    private final AtomicLong identifiers = new AtomicLong(1_000L);

    private DeploymentUnitService service;

    @BeforeEach
    void setUp() {
        service = new DeploymentUnitService(store, referenceGuard, referenceQuery, operationAudit,
                new TransactionTemplate(new RecordingTransactionManager()), identifiers::incrementAndGet);
    }

    // ---------- 创建即发布版本 1 ----------

    @Test
    void createPublishesVersionOneWithAssignedNumber() {
        when(store.findPhysical(TENANT_ID, PHYSICAL_ID))
                .thenReturn(Optional.of(new PhysicalSubsystemRef(PHYSICAL_ID, "W0001A", "渠道接入系统", "ACTIVE", false)));
        when(store.unitNameExists(TENANT_ID, "ECIP_AP", null)).thenReturn(false);
        when(store.allocateNumber(TENANT_ID, PHYSICAL_ID, "W0001A")).thenReturn("DW0001A001");
        when(store.findUnit(TENANT_ID, 1_001L)).thenReturn(Optional.of(unit(1_001L, "DW0001A001", "ACTIVE", 1)));

        DeploymentUnitService.DeploymentUnitView view = service.create(operator, command("ECIP_AP", "APPLICATION"), "trace");

        assertThat(view.code()).isEqualTo("DW0001A001");
        assertThat(view.currentVersion()).isEqualTo(1);
        verify(store).insertUnit(1_001L, TENANT_ID, "DW0001A001", PHYSICAL_ID, "ECIP_AP",
                "APPLICATION", null, null, operator.id());
        verify(store).insertVersion(1_002L, TENANT_ID, 1_001L, 1, "ECIP_AP",
                "APPLICATION", null, null, operator.id());
        verify(store).replaceRelations(TENANT_ID, 1_001L, Set.of(), operator.id(), 1);
        verify(operationAudit).recordSuccess(any());
    }

    @Test
    void createRejectsMissingOrNonActivePhysical() {
        when(store.findPhysical(TENANT_ID, PHYSICAL_ID))
                .thenReturn(Optional.of(new PhysicalSubsystemRef(PHYSICAL_ID, "W0001A", "渠道接入系统", "OFFLINE", false)));

        assertThatThrownBy(() -> service.create(operator, command("ECIP_AP", "APPLICATION"), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.BAD_REQUEST));
        verify(store, never()).insertUnit(anyLong(), anyLong(), anyString(), anyLong(), anyString(), anyString(),
                any(), any(), anyLong());
    }

    @Test
    void createRejectsDuplicateNameAndRecordsAuditFailure() {
        when(store.findPhysical(TENANT_ID, PHYSICAL_ID))
                .thenReturn(Optional.of(new PhysicalSubsystemRef(PHYSICAL_ID, "W0001A", "渠道接入系统", "ACTIVE", false)));
        when(store.unitNameExists(TENANT_ID, "ECIP_AP", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(operator, command("ECIP_AP", "APPLICATION"), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));
        verify(store, never()).allocateNumber(anyLong(), anyLong(), anyString());
        verify(operationAudit).recordFailure(any());
    }

    @Test
    void createRejectsInvalidKind() {
        assertThatThrownBy(() -> service.create(operator, command("ECIP_AP", "KUBERNETES"), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void createRejectsStandardSuffixKindMismatch() {
        assertThatThrownBy(() -> service.create(operator, command("ECIP_DB", "APPLICATION"), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.BAD_REQUEST))
                .hasMessageContaining("后缀_DB");
    }

    @Test
    void createAllowsCustomSuffixWithExplicitKind() {
        when(store.findPhysical(TENANT_ID, PHYSICAL_ID))
                .thenReturn(Optional.of(new PhysicalSubsystemRef(PHYSICAL_ID, "W0001A", "渠道接入系统", "ACTIVE", false)));
        when(store.unitNameExists(TENANT_ID, "BATCH_JOB1", null)).thenReturn(false);
        when(store.allocateNumber(TENANT_ID, PHYSICAL_ID, "W0001A")).thenReturn("DW0001A001");
        when(store.findUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unitWithVersion(1_001L, "DW0001A001", "ACTIVE", 1, "BATCH_JOB1")));

        DeploymentUnitService.DeploymentUnitView view = service.create(
                operator, command("batch_job1", "APPLICATION"), "trace");

        assertThat(view.name()).isEqualTo("BATCH_JOB1");
        verify(store).insertUnit(1_001L, TENANT_ID, "DW0001A001", PHYSICAL_ID, "BATCH_JOB1",
                "APPLICATION", null, null, operator.id());
    }

    @Test
    void createRejectsUnknownCrossTenantOrInactiveRelationTarget() {
        when(store.findPhysical(TENANT_ID, PHYSICAL_ID))
                .thenReturn(Optional.of(new PhysicalSubsystemRef(PHYSICAL_ID, "W0001A", "渠道接入系统", "ACTIVE", false)));
        when(store.unitNameExists(TENANT_ID, "ECIP_AP", null)).thenReturn(false);
        when(store.lockActiveUnits(TENANT_ID, List.of(2_002L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(operator,
                new DeploymentUnitCommand(PHYSICAL_ID, "ECIP_AP", "APPLICATION", List.of(2_002L),
                        null, null, null, null), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.BAD_REQUEST))
                .hasMessageContaining("不属于当前租户或已非 ACTIVE");
        verify(store, never()).allocateNumber(anyLong(), anyLong(), anyString());
        verify(store, never()).insertUnit(anyLong(), anyLong(), anyString(), anyLong(), anyString(), anyString(),
                any(), any(), anyLong());
    }

    @Test
    void createMapsDuplicateKeyRaceToConflictAndRecordsAuditFailure() {
        when(store.findPhysical(TENANT_ID, PHYSICAL_ID))
                .thenReturn(Optional.of(new PhysicalSubsystemRef(PHYSICAL_ID, "W0001A", "渠道接入系统", "ACTIVE", false)));
        when(store.unitNameExists(TENANT_ID, "ECIP_AP", null)).thenReturn(false);
        when(store.allocateNumber(TENANT_ID, PHYSICAL_ID, "W0001A")).thenThrow(new DuplicateKeyException("race"));

        assertThatThrownBy(() -> service.create(operator, command("ECIP_AP", "APPLICATION"), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));
        verify(operationAudit).recordFailure(any());
    }

    // ---------- 更新即发布新版本 ----------

    @Test
    void updatePublishesNewVersionAndKeepsOldVersionImmutable() {
        when(store.lockUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unit(1_001L, "DW0001A001", "ACTIVE", 1)));
        when(store.unitNameExists(TENANT_ID, "ECIP_DB", 1_001L)).thenReturn(false);
        when(store.updateUnitContent(TENANT_ID, 1_001L, 7L, "ECIP_DB",
                "DATABASE", "迁移到数据库服务", null, operator.id())).thenReturn(1);
        when(store.findUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unitWithVersion(1_001L, "DW0001A001", "ACTIVE", 2, "ECIP_DB")));

        DeploymentUnitService.DeploymentUnitView view = service.update(operator, 1_001L,
                new DeploymentUnitCommand(null, "ECIP_DB", "DATABASE", List.of(), null,
                        "迁移到数据库服务", null, 7L), "trace");

        assertThat(view.currentVersion()).isEqualTo(2);
        verify(store).insertVersion(1_001L, TENANT_ID, 1_001L, 2, "ECIP_DB", "DATABASE",
                "迁移到数据库服务", null, operator.id());
        verify(store).updateUnitCurrentVersion(TENANT_ID, 1_001L, 2, operator.id());
        verify(operationAudit).recordSuccess(any());
    }

    @Test
    void updateRejectsInactiveAndVoidedUnits() {
        when(store.lockUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unit(1_001L, "DW0001A001", "INACTIVE", 2)));
        assertThatThrownBy(() -> service.update(operator, 1_001L,
                new DeploymentUnitCommand(null, "NEW_AP", "APPLICATION", List.of(), null, null, null, 7L), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));
        verify(store, never()).insertVersion(anyLong(), anyLong(), anyLong(), anyInt(), anyString(), anyString(),
                any(), any(), anyLong());
    }

    @Test
    void updateRejectsStaleRowVersion() {
        when(store.lockUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unit(1_001L, "DW0001A001", "ACTIVE", 1)));
        when(store.unitNameExists(TENANT_ID, "NEW_AP", 1_001L)).thenReturn(false);
        when(store.updateUnitContent(anyLong(), anyLong(), anyLong(), anyString(), anyString(), any(),
                any(), anyLong())).thenReturn(0);

        assertThatThrownBy(() -> service.update(operator, 1_001L,
                new DeploymentUnitCommand(null, "NEW_AP", "APPLICATION", List.of(), null, null, null, 7L), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));
        verify(store, never()).insertVersion(anyLong(), anyLong(), anyLong(), anyInt(), anyString(), anyString(),
                any(), any(), anyLong());
    }

    // ---------- 生命周期 ----------

    @Test
    void deactivateTransitionsActiveToInactiveWithoutReferenceCheck() {
        when(store.lockUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unit(1_001L, "DW0001A001", "ACTIVE", 1)));
        when(store.hasRelations(TENANT_ID, 1_001L)).thenReturn(false);
        when(store.updateUnitStatus(TENANT_ID, 1_001L, "ACTIVE", "INACTIVE", operator.id())).thenReturn(1);
        when(store.findUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unit(1_001L, "DW0001A001", "INACTIVE", 1)));

        DeploymentUnitService.DeploymentUnitView view = service.deactivate(operator, 1_001L, "trace");

        assertThat(view.status()).isEqualTo("INACTIVE");
        verify(referenceGuard, never()).requireClear(any());
        verify(operationAudit).recordSuccess(any());
    }

    @Test
    void reactivateTransitionsInactiveToActive() {
        when(store.lockUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unit(1_001L, "DW0001A001", "INACTIVE", 1)));
        when(store.updateUnitStatus(TENANT_ID, 1_001L, "INACTIVE", "ACTIVE", operator.id())).thenReturn(1);
        when(store.findUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unit(1_001L, "DW0001A001", "ACTIVE", 1)));

        service.reactivate(operator, 1_001L, "trace");

        verify(store).updateUnitStatus(TENANT_ID, 1_001L, "INACTIVE", "ACTIVE", operator.id());
    }

    @Test
    void voidRejectsReferencedUnit() {
        when(store.lockUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unit(1_001L, "DW0001A001", "ACTIVE", 1)));
        doThrow(new BusinessException(ErrorCode.CONFLICT, "环境部署实例仍引用该部署单元"))
                .when(referenceGuard).requireClear(new DeploymentUnitReferenceCheckRequest(TENANT_ID, 1_001L));

        assertThatThrownBy(() -> service.voidUnit(operator, 1_001L, "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));
        verify(store, never()).updateUnitStatus(anyLong(), anyLong(), anyString(), eq("VOIDED"), anyLong());
    }

    @Test
    void voidRejectsUnitWithDeploymentUnitRelations() {
        when(store.lockUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unit(1_001L, "DW0001A001", "ACTIVE", 1)));
        when(store.hasRelations(TENANT_ID, 1_001L)).thenReturn(true);

        assertThatThrownBy(() -> service.voidUnit(operator, 1_001L, "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT))
                .hasMessageContaining("先解除关联");
        verify(referenceGuard, never()).requireClear(any());
    }

    @Test
    void voidFailsClosedWhenReferenceCheckIsIndeterminate() {
        when(store.lockUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unit(1_001L, "DW0001A001", "ACTIVE", 1)));
        doThrow(new BusinessException(DeploymentUnitReferenceGuard.SERVICE_UNAVAILABLE, "外部引用检查暂不可用"))
                .when(referenceGuard).requireClear(new DeploymentUnitReferenceCheckRequest(TENANT_ID, 1_001L));

        assertThatThrownBy(() -> service.voidUnit(operator, 1_001L, "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code())
                        .isEqualTo(DeploymentUnitReferenceGuard.SERVICE_UNAVAILABLE));
        verify(store, never()).updateUnitStatus(anyLong(), anyLong(), anyString(), eq("VOIDED"), anyLong());
    }

    @Test
    void voidAllowsClearUnitAndKeepsNumberOccupied() {
        when(store.lockUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unit(1_001L, "DW0001A001", "ACTIVE", 1)));
        when(store.updateUnitStatus(TENANT_ID, 1_001L, "ACTIVE", "VOIDED", operator.id())).thenReturn(1);
        when(store.findUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unit(1_001L, "DW0001A001", "VOIDED", 1)));

        DeploymentUnitService.DeploymentUnitView view = service.voidUnit(operator, 1_001L, "trace");

        assertThat(view.status()).isEqualTo("VOIDED");
        assertThat(view.code()).isEqualTo("DW0001A001");
        verify(referenceGuard).requireClear(new DeploymentUnitReferenceCheckRequest(TENANT_ID, 1_001L));
    }

    // ---------- 版本历史 ----------

    @Test
    void versionsListsImmutableSnapshotsInOrder() {
        when(store.findUnit(TENANT_ID, 1_001L)).thenReturn(Optional.of(unit(1_001L, "DW0001A001", "ACTIVE", 2)));
        when(store.findVersions(TENANT_ID, 1_001L)).thenReturn(List.of(
                new DeploymentUnitVersion(1L, 1_001L, 1, "ECIP_AP", "APPLICATION",
                        null, null, null, null, 88L, LocalDateTime.of(2026, 8, 23, 10, 0)),
                new DeploymentUnitVersion(2L, 1_001L, 2, "ECIP_DB", "DATABASE",
                        null, null, "迁移说明", null, 88L, LocalDateTime.of(2026, 8, 23, 11, 0))));
        when(referenceQuery.findUser(eq(operator), eq(88L), eq(false)))
                .thenReturn(Optional.of(new com.ccb.system.capability.SystemUserReference(
                        88L, "技术架构师", "tech", null, true)));

        List<DeploymentUnitService.DeploymentUnitVersionView> versions = service.versions(operator, 1_001L);

        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).versionNo()).isEqualTo(1);
        assertThat(versions.get(1).versionNo()).isEqualTo(2);
        assertThat(versions.get(1).publishedByDisplayName()).isEqualTo("技术架构师");
    }

    // ---------- 工具 ----------

    private DeploymentUnitCommand command(String name, String kind) {
        return new DeploymentUnitCommand(PHYSICAL_ID, name, kind, List.of(), null, null, null, null);
    }

    private DeploymentUnit unit(long id, String code, String status, int currentVersion) {
        return unitWithVersion(id, code, status, currentVersion, "ECIP_AP");
    }

    private DeploymentUnit unitWithVersion(long id, String code, String status, int currentVersion, String name) {
        return new DeploymentUnit(id, code, PHYSICAL_ID, name, "APPLICATION", null, null, status, currentVersion,
                null, null, 88L, 88L, LocalDateTime.of(2026, 8, 23, 10, 0),
                LocalDateTime.of(2026, 8, 23, 10, 0), 7L);
    }

    private static final class RecordingTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus(true);
        }

        @Override
        public void commit(TransactionStatus status) {
            // no-op
        }

        @Override
        public void rollback(TransactionStatus status) {
            // no-op
        }
    }
}
