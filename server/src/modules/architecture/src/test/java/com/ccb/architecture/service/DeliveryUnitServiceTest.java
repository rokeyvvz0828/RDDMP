package com.ccb.architecture.service;

import com.ccb.architecture.model.DeliveryUnitModels.DeliveryUnit;
import com.ccb.architecture.model.DeliveryUnitModels.DeliveryUnitCommand;
import com.ccb.architecture.model.DeliveryUnitModels.DeploymentUnitRef;
import com.ccb.architecture.persistence.DeliveryUnitNumberCapacityExceededException;
import com.ccb.architecture.persistence.DeliveryUnitStore;
import com.ccb.architecture.persistence.DeliveryUnitStore.PhysicalSubsystemProjection;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemOperationAuditCommand;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryUnitServiceTest {
    private static final long TENANT_ID = 1L;
    private static final ProjectAccess PROJECT = new ProjectAccess(70L, "PROJECT-A", "项目 A");
    private static final long PHYSICAL_ID = 501L;
    private static final long OTHER_PHYSICAL_ID = 502L;
    private static final long UNIT_ID = 1_001L;

    private final DeliveryUnitStore store = mock(DeliveryUnitStore.class);
    private final DeploymentUnitService deploymentUnitService = mock(DeploymentUnitService.class);
    private final SystemReferenceQuery referenceQuery = mock(SystemReferenceQuery.class);
    private final SystemOperationAudit operationAudit = mock(SystemOperationAudit.class);
    private final AuthUser operator = new AuthUser(88L, TENANT_ID, "tech", "-", "技术架构师", 1L, true);
    private final AtomicLong identifiers = new AtomicLong(1_000L);

    private DeliveryUnitService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryUnitService(store, deploymentUnitService, referenceQuery, operationAudit,
                new TransactionTemplate(new RecordingTransactionManager()), identifiers::incrementAndGet);
    }

    // ---------- 创建 ----------

    @Test
    void createAssignsNumberAndPersistsUnitWithRelations() {
        stubActivePhysical();
        when(store.unitNameExists(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "统一认证交付包", null)).thenReturn(false);
        when(store.findDeploymentUnitsByIds(TENANT_ID, PROJECT.id(), Set.of(31L)))
                .thenReturn(List.of(deploymentUnit(31L, PHYSICAL_ID, "ACTIVE")));
        when(store.allocateNumber(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "W0001A")).thenReturn("DUW0001A001");
        when(store.findUnit(TENANT_ID, PROJECT.id(), UNIT_ID))
                .thenReturn(Optional.of(unit(UNIT_ID, "DUW0001A001", "ACTIVE", 0)));

        var view = service.create(operator, PROJECT,
                new DeliveryUnitCommand(PHYSICAL_ID, "统一认证交付包", null, null, List.of(31L), null, null), "trace-1");

        assertThat(view.code()).isEqualTo("DUW0001A001");
        assertThat(view.name()).isEqualTo("统一认证交付包");
        assertThat(view.status()).isEqualTo("ACTIVE");
        verify(store).insertUnit(UNIT_ID, TENANT_ID, PROJECT.id(), "DUW0001A001", PHYSICAL_ID, "统一认证交付包",
                null, null, null, operator.id());
        verify(store).replaceDeploymentUnits(TENANT_ID, PROJECT.id(), PHYSICAL_ID, UNIT_ID, Set.of(31L),
                operator.id());
        verify(operationAudit).recordSuccess(any(SystemOperationAuditCommand.class));
    }

    @Test
    void createRejectsNonActivePhysicalWithoutAllocatingNumber() {
        when(store.findPhysical(TENANT_ID, PROJECT.id(), PHYSICAL_ID))
                .thenReturn(Optional.of(new PhysicalSubsystemProjection(PHYSICAL_ID, "W0001A", "渠道接入系统",
                        "INACTIVE", false)));

        assertThatThrownBy(() -> service.create(operator, PROJECT,
                new DeliveryUnitCommand(PHYSICAL_ID, "统一认证交付包", null, null, List.of(), null, null), "trace-2"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.BAD_REQUEST))
                .hasMessageContaining("物理子系统当前状态不允许");
        verify(store, never()).allocateNumber(anyLong(), anyLong(), anyLong(), anyString());
        verify(store, never()).insertUnit(anyLong(), anyLong(), anyLong(), anyString(), anyLong(), anyString(),
                any(), any(), any(), anyLong());
    }

    @Test
    void createRejectsDuplicateNameWithoutAllocatingNumber() {
        stubActivePhysical();
        when(store.unitNameExists(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "统一认证交付包", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(operator, PROJECT,
                new DeliveryUnitCommand(PHYSICAL_ID, "统一认证交付包", null, null, List.of(), null, null), "trace-3"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));
        verify(store, never()).allocateNumber(anyLong(), anyLong(), anyLong(), anyString());
        verify(operationAudit).recordFailure(any(SystemOperationAuditCommand.class));
    }

    @Test
    void createRejectsRelationOutsidePhysicalSubsystem() {
        stubActivePhysical();
        when(store.unitNameExists(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "统一认证交付包", null)).thenReturn(false);
        when(store.findDeploymentUnitsByIds(TENANT_ID, PROJECT.id(), Set.of(31L)))
                .thenReturn(List.of(deploymentUnit(31L, OTHER_PHYSICAL_ID, "ACTIVE")));

        assertThatThrownBy(() -> service.create(operator, PROJECT,
                new DeliveryUnitCommand(PHYSICAL_ID, "统一认证交付包", null, null, List.of(31L), null, null), "trace-4"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT))
                .hasMessageContaining("同一物理子系统");
        verify(store, never()).allocateNumber(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void createRejectsDuplicateCodeFromDatabase() {
        stubActivePhysical();
        when(store.unitNameExists(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "统一认证交付包", null)).thenReturn(false);
        when(store.allocateNumber(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "W0001A")).thenReturn("DUW0001A001");
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate"))
                .when(store).insertUnit(anyLong(), anyLong(), anyLong(), anyString(), anyLong(), anyString(),
                        any(), any(), any(), anyLong());

        assertThatThrownBy(() -> service.create(operator, PROJECT,
                new DeliveryUnitCommand(PHYSICAL_ID, "统一认证交付包", null, null, List.of(), null, null), "trace-5"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void createRejectsExhaustedNumberCapacityAsConflict() {
        stubActivePhysical();
        when(store.unitNameExists(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "统一认证交付包", null)).thenReturn(false);
        when(store.allocateNumber(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "W0001A"))
                .thenThrow(new DeliveryUnitNumberCapacityExceededException("编号容量已用尽"));

        assertThatThrownBy(() -> service.create(operator, PROJECT,
                new DeliveryUnitCommand(PHYSICAL_ID, "统一认证交付包", null, null, List.of(), null, null), "trace-6"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT))
                .hasMessageContaining("编号容量已用尽");
    }

    @Test
    void createRejectsMissingNameAndMissingPhysical() {
        assertThatThrownBy(() -> service.create(operator, PROJECT,
                new DeliveryUnitCommand(null, "统一认证交付包", null, null, List.of(), null, null), "trace-7"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择归属物理子系统");
        assertThatThrownBy(() -> service.create(operator, PROJECT,
                new DeliveryUnitCommand(PHYSICAL_ID, " ", null, null, List.of(), null, null), "trace-8"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("交付单元名称");
    }

    // ---------- 修改 ----------

    @Test
    void updateRejectsPhysicalSubsystemChange() {
        when(store.findUnit(TENANT_ID, PROJECT.id(), UNIT_ID))
                .thenReturn(Optional.of(unit(UNIT_ID, "DUW0001A001", "ACTIVE", 3)));

        assertThatThrownBy(() -> service.update(operator, PROJECT, UNIT_ID,
                new DeliveryUnitCommand(OTHER_PHYSICAL_ID, "统一认证交付包", null, null, List.of(), 3L, null), "trace-9"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.BAD_REQUEST))
                .hasMessageContaining("归属物理子系统不可变更");
        verify(store, never()).updateUnitContent(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), any(),
                any(), any(), anyLong());
    }

    @Test
    void updateRejectsStaleRowVersion() {
        when(store.findUnit(TENANT_ID, PROJECT.id(), UNIT_ID))
                .thenReturn(Optional.of(unit(UNIT_ID, "DUW0001A001", "ACTIVE", 3)));
        when(store.unitNameExists(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "统一认证交付包 V2", UNIT_ID))
                .thenReturn(false);
        when(store.updateUnitContent(TENANT_ID, PROJECT.id(), UNIT_ID, 3L, "统一认证交付包 V2", null, null, null,
                operator.id())).thenReturn(0);

        assertThatThrownBy(() -> service.update(operator, PROJECT, UNIT_ID,
                new DeliveryUnitCommand(null, "统一认证交付包 V2", null, null, List.of(), 3L, null), "trace-10"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT))
                .hasMessageContaining("已被其他操作修改");
    }

    @Test
    void updateRequiresRowVersion() {
        when(store.findUnit(TENANT_ID, PROJECT.id(), UNIT_ID))
                .thenReturn(Optional.of(unit(UNIT_ID, "DUW0001A001", "ACTIVE", 3)));

        assertThatThrownBy(() -> service.update(operator, PROJECT, UNIT_ID,
                new DeliveryUnitCommand(null, "统一认证交付包", null, null, List.of(), null, null), "trace-11"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据版本不能为空");
    }

    // ---------- 关联 ----------

    @Test
    void replaceDeploymentUnitsPersistsValidatedTargets() {
        when(store.findUnit(TENANT_ID, PROJECT.id(), UNIT_ID))
                .thenReturn(Optional.of(unit(UNIT_ID, "DUW0001A001", "ACTIVE", 0)));
        when(store.findDeploymentUnitsByIds(TENANT_ID, PROJECT.id(), Set.of(31L, 32L)))
                .thenReturn(List.of(deploymentUnit(31L, PHYSICAL_ID, "ACTIVE"),
                        deploymentUnit(32L, PHYSICAL_ID, "ACTIVE")));

        service.replaceDeploymentUnits(operator, PROJECT, UNIT_ID, List.of(31L, 32L, 31L), "trace-12");

        verify(store).replaceDeploymentUnits(TENANT_ID, PROJECT.id(), PHYSICAL_ID, UNIT_ID, Set.of(31L, 32L),
                operator.id());
        verify(operationAudit).recordSuccess(any(SystemOperationAuditCommand.class));
    }

    @Test
    void replaceDeploymentUnitsRejectsInactiveDeliveryUnit() {
        when(store.findUnit(TENANT_ID, PROJECT.id(), UNIT_ID))
                .thenReturn(Optional.of(unit(UNIT_ID, "DUW0001A001", "INACTIVE", 1)));

        assertThatThrownBy(() -> service.replaceDeploymentUnits(operator, PROJECT, UNIT_ID, List.of(31L), "trace-13"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已停用交付单元不能调整关联");
        verify(store, never()).replaceDeploymentUnits(anyLong(), anyLong(), anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    void replaceDeploymentUnitsRejectsUnknownTarget() {
        when(store.findUnit(TENANT_ID, PROJECT.id(), UNIT_ID))
                .thenReturn(Optional.of(unit(UNIT_ID, "DUW0001A001", "ACTIVE", 0)));
        when(store.findDeploymentUnitsByIds(TENANT_ID, PROJECT.id(), Set.of(31L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.replaceDeploymentUnits(operator, PROJECT, UNIT_ID, List.of(31L), "trace-14"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或不属于当前项目");
    }

    // ---------- 状态与删除 ----------

    @Test
    void deactivateAndReactivateGuardCurrentStatus() {
        when(store.findUnit(TENANT_ID, PROJECT.id(), UNIT_ID))
                .thenReturn(Optional.of(unit(UNIT_ID, "DUW0001A001", "INACTIVE", 1)));
        when(store.updateUnitStatus(TENANT_ID, PROJECT.id(), UNIT_ID, "ACTIVE", "INACTIVE", operator.id()))
                .thenReturn(1);

        assertThatThrownBy(() -> service.deactivate(operator, PROJECT, UNIT_ID, "trace-15"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态不允许该操作");

        verify(store, never()).updateUnitStatus(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void deleteSoftDeletesAndAudits() {
        when(store.findUnit(TENANT_ID, PROJECT.id(), UNIT_ID))
                .thenReturn(Optional.of(unit(UNIT_ID, "DUW0001A001", "ACTIVE", 0)));

        service.delete(operator, PROJECT, UNIT_ID, "trace-16");

        verify(store).softDelete(TENANT_ID, PROJECT.id(), UNIT_ID, operator.id());
        verify(operationAudit).recordSuccess(any(SystemOperationAuditCommand.class));
    }

    @Test
    void deleteRejectsMissingUnit() {
        when(store.findUnit(TENANT_ID, PROJECT.id(), UNIT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(operator, PROJECT, UNIT_ID, "trace-17"))
                .isInstanceOf(ArchitectureNotFoundException.class);
        verify(store, never()).softDelete(anyLong(), anyLong(), anyLong(), anyLong());
    }

    // ---------- 反向查询与候选 ----------

    @Test
    void relatedDeliveryUnitsMapsReverseRecordsAndRejectsUnknownDeploymentUnit() {
        when(store.findDeploymentUnitsByIds(TENANT_ID, PROJECT.id(), List.of(31L)))
                .thenReturn(List.of(deploymentUnit(31L, PHYSICAL_ID, "ACTIVE")));
        when(store.findRelatedDeliveryUnits(TENANT_ID, PROJECT.id(), 31L))
                .thenReturn(List.of(unit(1L, "DUW0001A001", "ACTIVE", 0), unit(2L, "DUW0001A002", "INACTIVE", 0)));

        var related = service.relatedDeliveryUnits(operator, PROJECT, 31L);
        assertThat(related).extracting(DeliveryUnitService.RelatedDeliveryUnitView::code)
                .containsExactly("DUW0001A001", "DUW0001A002");

        when(store.findDeploymentUnitsByIds(TENANT_ID, PROJECT.id(), List.of(99L))).thenReturn(List.of());
        assertThatThrownBy(() -> service.relatedDeliveryUnits(operator, PROJECT, 99L))
                .isInstanceOf(ArchitectureNotFoundException.class);
    }

    @Test
    void deploymentSideReplacePersistsValidatedTargets() {
        when(store.findDeploymentUnitsByIds(TENANT_ID, PROJECT.id(), List.of(31L)))
                .thenReturn(List.of(deploymentUnit(31L, PHYSICAL_ID, "ACTIVE")));
        when(store.findDeliveryUnitsByIds(TENANT_ID, PROJECT.id(), Set.of(UNIT_ID)))
                .thenReturn(List.of(unit(UNIT_ID, "DUW0001A001", "ACTIVE", 0)));
        when(store.findRelatedDeliveryUnits(TENANT_ID, PROJECT.id(), 31L))
                .thenReturn(List.of(unit(UNIT_ID, "DUW0001A001", "ACTIVE", 0)));

        var result = service.replaceDeploymentUnitDeliveryUnits(operator, PROJECT, 31L, List.of(UNIT_ID), "trace-d1");

        assertThat(result).extracting(DeliveryUnitService.RelatedDeliveryUnitView::id).containsExactly(UNIT_ID);
        verify(store).replaceDeploymentUnitsFromDeploymentSide(TENANT_ID, PROJECT.id(), PHYSICAL_ID, 31L,
                Set.of(UNIT_ID), operator.id());
        verify(operationAudit).recordSuccess(any(SystemOperationAuditCommand.class));
    }

    @Test
    void deploymentSideReplaceRejectsInactiveDeploymentUnit() {
        when(store.findDeploymentUnitsByIds(TENANT_ID, PROJECT.id(), List.of(31L)))
                .thenReturn(List.of(deploymentUnit(31L, PHYSICAL_ID, "INACTIVE")));

        assertThatThrownBy(() -> service.replaceDeploymentUnitDeliveryUnits(operator, PROJECT, 31L,
                List.of(UNIT_ID), "trace-d2"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT))
                .hasMessageContaining("已停用或已作废部署单元不能调整关联");
        verify(store, never()).replaceDeploymentUnitsFromDeploymentSide(anyLong(), anyLong(), anyLong(), anyLong(),
                any(), anyLong());
    }

    @Test
    void deploymentSideReplaceRejectsInactiveOrMissingDeliveryUnit() {
        when(store.findDeploymentUnitsByIds(TENANT_ID, PROJECT.id(), List.of(31L)))
                .thenReturn(List.of(deploymentUnit(31L, PHYSICAL_ID, "ACTIVE")));
        when(store.findDeliveryUnitsByIds(TENANT_ID, PROJECT.id(), Set.of(UNIT_ID)))
                .thenReturn(List.of(unit(UNIT_ID, "DUW0001A001", "INACTIVE", 0)));

        assertThatThrownBy(() -> service.replaceDeploymentUnitDeliveryUnits(operator, PROJECT, 31L,
                List.of(UNIT_ID), "trace-d3"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能关联启用状态的交付单元");

        when(store.findDeliveryUnitsByIds(TENANT_ID, PROJECT.id(), Set.of(UNIT_ID))).thenReturn(List.of());
        assertThatThrownBy(() -> service.replaceDeploymentUnitDeliveryUnits(operator, PROJECT, 31L,
                List.of(UNIT_ID), "trace-d4"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或已删除");
    }

    @Test
    void deploymentSideReplaceRejectsCrossPhysicalSubsystemDeliveryUnit() {
        when(store.findDeploymentUnitsByIds(TENANT_ID, PROJECT.id(), List.of(31L)))
                .thenReturn(List.of(deploymentUnit(31L, PHYSICAL_ID, "ACTIVE")));
        when(store.findDeliveryUnitsByIds(TENANT_ID, PROJECT.id(), Set.of(UNIT_ID)))
                .thenReturn(List.of(new DeliveryUnit(UNIT_ID, "DUW0002B001", OTHER_PHYSICAL_ID, "其他交付包",
                        "ACTIVE", null, null, 88L, 88L, LocalDateTime.of(2026, 9, 10, 10, 0),
                        LocalDateTime.of(2026, 9, 10, 10, 0), 0, null)));

        assertThatThrownBy(() -> service.replaceDeploymentUnitDeliveryUnits(operator, PROJECT, 31L,
                List.of(UNIT_ID), "trace-d5"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能关联同一物理子系统下的交付单元");
    }

    @Test
    void deploymentSideOptionsUseDeploymentUnitPhysicalSubsystem() {
        when(store.findDeploymentUnitsByIds(TENANT_ID, PROJECT.id(), List.of(31L)))
                .thenReturn(List.of(deploymentUnit(31L, PHYSICAL_ID, "ACTIVE")));
        when(store.searchActiveOptions(eq(TENANT_ID), eq(PROJECT.id()), eq(PHYSICAL_ID), eq("认证"), any()))
                .thenReturn(new com.ccb.common.api.PageResult<>(List.of(unit(UNIT_ID, "DUW0001A001", "ACTIVE", 0)),
                        1L, 1L, 20L));

        var result = service.deliveryUnitOptionsForDeploymentUnit(operator, PROJECT, 31L, "认证",
                new com.ccb.common.api.PageQuery(1, 20));

        assertThat(result.records()).extracting(DeliveryUnitService.RelatedDeliveryUnitView::code)
                .containsExactly("DUW0001A001");
        verify(store).searchActiveOptions(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "认证",
                new com.ccb.common.api.PageQuery(1, 20));
    }

    @Test
    void deploymentUnitOptionsDelegateToDeploymentUnitService() {
        var expected = new com.ccb.common.api.PageResult<DeploymentUnitService.RelatedDeploymentUnitView>(List.of(),
                0L, 1L, 20L);
        when(deploymentUnitService.options(eq(operator), eq(PROJECT), eq("认证"), eq(null), eq(PHYSICAL_ID),
                any())).thenReturn(expected);

        var result = service.deploymentUnitOptions(operator, PROJECT, PHYSICAL_ID, "认证", null,
                new com.ccb.common.api.PageQuery(1, 20));

        assertThat(result).isSameAs(expected);
    }

    @Test
    void listAndDetailRequireAuthenticatedActorAndProject() {
        assertThatThrownBy(() -> service.list(null, PROJECT, new com.ccb.common.api.PageQuery(1, 20), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThatThrownBy(() -> service.detail(operator, null, UNIT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择项目");
        assertThatThrownBy(() -> service.detail(operator, PROJECT, 0L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("交付单元标识无效");
    }

    // ---------- 夹具 ----------

    private void stubActivePhysical() {
        when(store.findPhysical(TENANT_ID, PROJECT.id(), PHYSICAL_ID))
                .thenReturn(Optional.of(new PhysicalSubsystemProjection(PHYSICAL_ID, "W0001A", "渠道接入系统",
                        "ACTIVE", false)));
    }

    private static DeploymentUnitRef deploymentUnit(long id, long physicalSubsystemId, String status) {
        return new DeploymentUnitRef(id, "D" + id, "UNIT" + id + "_AP", "APPLICATION", status, false,
                physicalSubsystemId);
    }

    private static DeliveryUnit unit(long id, String code, String status, long rowVersion) {
        return new DeliveryUnit(id, code, PHYSICAL_ID, "统一认证交付包", status, null, null, 88L, 88L,
                LocalDateTime.of(2026, 9, 10, 10, 0), LocalDateTime.of(2026, 9, 10, 10, 0), rowVersion, null);
    }

    @Test
    void createValidatesArtifactTypeAgainstDictionary() {
        stubActivePhysical();
        when(store.unitNameExists(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "统一认证交付包", null)).thenReturn(false);
        when(store.allocateNumber(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "W0001A")).thenReturn("DUW0001A001");
        when(store.findUnit(TENANT_ID, PROJECT.id(), UNIT_ID))
                .thenReturn(Optional.of(new DeliveryUnit(UNIT_ID, "DUW0001A001", PHYSICAL_ID, "统一认证交付包",
                        "ACTIVE", null, null, 88L, 88L, LocalDateTime.of(2026, 9, 10, 10, 0),
                        LocalDateTime.of(2026, 9, 10, 10, 0), 0L, "architecture.artifact-type.container")));
        when(referenceQuery.activeParameters(operator, "ARCH_ARTIFACT_TYPE"))
                .thenReturn(List.of(new com.ccb.system.capability.SystemParameterReference(
                        "architecture.artifact-type.container", "容器")));

        var view = service.create(operator, PROJECT, new DeliveryUnitCommand(PHYSICAL_ID, "统一认证交付包", null, null,
                List.of(), null, "architecture.artifact-type.container"), "trace-a1");

        assertThat(view.artifactTypeCode()).isEqualTo("architecture.artifact-type.container");
        verify(store).insertUnit(UNIT_ID, TENANT_ID, PROJECT.id(), "DUW0001A001", PHYSICAL_ID, "统一认证交付包",
                "architecture.artifact-type.container", null, null, operator.id());
    }

    @Test
    void createRejectsUnknownOrDisabledArtifactType() {
        stubActivePhysical();
        when(store.unitNameExists(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "统一认证交付包", null)).thenReturn(false);
        when(referenceQuery.activeParameters(operator, "ARCH_ARTIFACT_TYPE")).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(operator, PROJECT, new DeliveryUnitCommand(PHYSICAL_ID, "统一认证交付包",
                null, null, List.of(), null, "architecture.artifact-type.unknown"), "trace-a2"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.BAD_REQUEST))
                .hasMessageContaining("制品类型参数无效或已停用");
        verify(store, never()).allocateNumber(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void createAllowsNullArtifactType() {
        stubActivePhysical();
        when(store.unitNameExists(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "统一认证交付包", null)).thenReturn(false);
        when(store.allocateNumber(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "W0001A")).thenReturn("DUW0001A001");
        when(store.findUnit(TENANT_ID, PROJECT.id(), UNIT_ID))
                .thenReturn(Optional.of(unit(UNIT_ID, "DUW0001A001", "ACTIVE", 0)));

        var view = service.create(operator, PROJECT, new DeliveryUnitCommand(PHYSICAL_ID, "统一认证交付包", null, null,
                List.of(), null, null), "trace-a3");

        assertThat(view.artifactTypeCode()).isNull();
        verify(referenceQuery, never()).activeParameters(any(), any());
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
