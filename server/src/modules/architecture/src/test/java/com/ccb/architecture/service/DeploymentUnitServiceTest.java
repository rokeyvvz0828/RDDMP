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
        when(store.unitNameExists(TENANT_ID, PHYSICAL_ID, "电子渠道接入应用", null)).thenReturn(false);
        when(store.allocateNumber(TENANT_ID, PHYSICAL_ID, "W0001A")).thenReturn("DW0001A001");
        when(store.findUnit(TENANT_ID, 1_001L)).thenReturn(Optional.of(unit(1_001L, "DW0001A001", "ACTIVE", 1)));

        DeploymentUnitService.DeploymentUnitView view = service.create(operator, command("电子渠道接入应用", "APPLICATION"), "trace");

        assertThat(view.code()).isEqualTo("DW0001A001");
        assertThat(view.currentVersion()).isEqualTo(1);
        verify(store).insertUnit(1_001L, TENANT_ID, "DW0001A001", PHYSICAL_ID, "ECIP-AP", "电子渠道接入应用",
                null, "AP", "APPLICATION", null, null, operator.id());
        verify(store).insertVersion(1_002L, TENANT_ID, 1_001L, 1, "ECIP-AP", "电子渠道接入应用", null, "AP",
                "APPLICATION", null, null, operator.id());
        verify(operationAudit).recordSuccess(any());
    }

    @Test
    void createRejectsMissingOrNonActivePhysical() {
        when(store.findPhysical(TENANT_ID, PHYSICAL_ID))
                .thenReturn(Optional.of(new PhysicalSubsystemRef(PHYSICAL_ID, "W0001A", "渠道接入系统", "OFFLINE", false)));

        assertThatThrownBy(() -> service.create(operator, command("电子渠道接入应用", "APPLICATION"), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.BAD_REQUEST));
        verify(store, never()).insertUnit(anyLong(), anyLong(), anyString(), anyLong(), anyString(), anyString(),
                any(), anyString(), anyString(), any(), any(), anyLong());
    }

    @Test
    void createRejectsDuplicateNameAndRecordsAuditFailure() {
        when(store.findPhysical(TENANT_ID, PHYSICAL_ID))
                .thenReturn(Optional.of(new PhysicalSubsystemRef(PHYSICAL_ID, "W0001A", "渠道接入系统", "ACTIVE", false)));
        when(store.unitNameExists(TENANT_ID, PHYSICAL_ID, "电子渠道接入应用", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(operator, command("电子渠道接入应用", "APPLICATION"), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));
        verify(store, never()).allocateNumber(anyLong(), anyLong(), anyString());
        verify(operationAudit).recordFailure(any());
    }

    @Test
    void createRejectsInvalidKind() {
        assertThatThrownBy(() -> service.create(operator, command("电子渠道接入应用", "KUBERNETES"), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void createMapsDuplicateKeyRaceToConflictAndRecordsAuditFailure() {
        when(store.findPhysical(TENANT_ID, PHYSICAL_ID))
                .thenReturn(Optional.of(new PhysicalSubsystemRef(PHYSICAL_ID, "W0001A", "渠道接入系统", "ACTIVE", false)));
        when(store.unitNameExists(TENANT_ID, PHYSICAL_ID, "电子渠道接入应用", null)).thenReturn(false);
        when(store.allocateNumber(TENANT_ID, PHYSICAL_ID, "W0001A")).thenThrow(new DuplicateKeyException("race"));

        assertThatThrownBy(() -> service.create(operator, command("电子渠道接入应用", "APPLICATION"), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));
        verify(operationAudit).recordFailure(any());
    }

    // ---------- 更新即发布新版本 ----------

    @Test
    void updatePublishesNewVersionAndKeepsOldVersionImmutable() {
        when(store.lockUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unit(1_001L, "DW0001A001", "ACTIVE", 1)));
        when(store.unitNameExists(TENANT_ID, PHYSICAL_ID, "电子渠道接入应用 V2", 1_001L)).thenReturn(false);
        when(store.updateUnitContent(TENANT_ID, 1_001L, 7L, "ECIP-AP", "电子渠道接入应用 V2",
                null, "DB", "DATABASE", "迁移到数据库服务", null, operator.id())).thenReturn(1);
        when(store.findUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unitWithVersion(1_001L, "DW0001A001", "ACTIVE", 2, "电子渠道接入应用 V2")));

        DeploymentUnitService.DeploymentUnitView view = service.update(operator, 1_001L,
                new DeploymentUnitCommand(null, "ECIP-AP", "电子渠道接入应用 V2", "DATABASE",
                        "迁移到数据库服务", null, 7L), "trace");

        assertThat(view.currentVersion()).isEqualTo(2);
        verify(store).insertVersion(1_001L, TENANT_ID, 1_001L, 2, "ECIP-AP", "电子渠道接入应用 V2", null,
                "DB", "DATABASE", "迁移到数据库服务", null, operator.id());
        verify(store).updateUnitCurrentVersion(TENANT_ID, 1_001L, 2, operator.id());
        verify(operationAudit).recordSuccess(any());
    }

    @Test
    void updateRejectsInactiveAndVoidedUnits() {
        when(store.lockUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unit(1_001L, "DW0001A001", "INACTIVE", 2)));
        assertThatThrownBy(() -> service.update(operator, 1_001L,
                new DeploymentUnitCommand(null, "ECIP-AP", "新名称", "APPLICATION", null, null, 7L), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));
        verify(store, never()).insertVersion(anyLong(), anyLong(), anyLong(), anyInt(), anyString(), anyString(),
                any(), anyString(), anyString(), any(), any(), anyLong());
    }

    @Test
    void updateRejectsStaleRowVersion() {
        when(store.lockUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unit(1_001L, "DW0001A001", "ACTIVE", 1)));
        when(store.unitNameExists(TENANT_ID, PHYSICAL_ID, "新名称", 1_001L)).thenReturn(false);
        when(store.updateUnitContent(anyLong(), anyLong(), anyLong(), anyString(), anyString(), any(),
                anyString(), anyString(), any(), any(), anyLong())).thenReturn(0);

        assertThatThrownBy(() -> service.update(operator, 1_001L,
                new DeploymentUnitCommand(null, "ECIP-AP", "新名称", "APPLICATION", null, null, 7L), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));
        verify(store, never()).insertVersion(anyLong(), anyLong(), anyLong(), anyInt(), anyString(), anyString(),
                any(), anyString(), anyString(), any(), any(), anyLong());
    }

    // ---------- 生命周期 ----------

    @Test
    void deactivateTransitionsActiveToInactiveWithoutReferenceCheck() {
        when(store.lockUnit(TENANT_ID, 1_001L))
                .thenReturn(Optional.of(unit(1_001L, "DW0001A001", "ACTIVE", 1)));
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
                new DeploymentUnitVersion(1L, 1_001L, 1, "ECIP-AP", "电子渠道接入应用", "APPLICATION",
                        null, null, 88L, LocalDateTime.of(2026, 8, 23, 10, 0)),
                new DeploymentUnitVersion(2L, 1_001L, 2, "ECIP-AP", "电子渠道接入应用 V2", "DATABASE",
                        "迁移说明", null, 88L, LocalDateTime.of(2026, 8, 23, 11, 0))));
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
        return new DeploymentUnitCommand(PHYSICAL_ID, "ECIP-AP", name, kind, null, null, null);
    }

    private DeploymentUnit unit(long id, String code, String status, int currentVersion) {
        return unitWithVersion(id, code, status, currentVersion, "电子渠道接入应用");
    }

    private DeploymentUnit unitWithVersion(long id, String code, String status, int currentVersion, String name) {
        return new DeploymentUnit(id, code, PHYSICAL_ID, "ECIP-AP", name, "APPLICATION", status, currentVersion,
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
