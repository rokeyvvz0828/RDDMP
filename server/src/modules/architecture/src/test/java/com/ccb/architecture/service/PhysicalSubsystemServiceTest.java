package com.ccb.architecture.service;

import com.ccb.architecture.model.LogicalSubsystem;
import com.ccb.architecture.model.LogicalSubsystemLock;
import com.ccb.architecture.model.PhysicalSubsystem;
import com.ccb.architecture.model.PhysicalSubsystemCommand;
import com.ccb.architecture.model.PhysicalSubsystemQuery;
import com.ccb.architecture.repository.ArchitectureSubsystemRepository;
import com.ccb.architecture.service.PhysicalSubsystemService.PhysicalSubsystemView;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemOperationAuditCommand;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.capability.SystemUserReference;
import com.ccb.system.org.OrgTreeNode;
import com.ccb.system.org.OrganizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhysicalSubsystemServiceTest {
    private static final AuthUser ACTOR = new AuthUser(9, 7, "architect", "hash", "架构管理员", 11, true);

    @Mock
    private ArchitectureSubsystemRepository repository;
    @Mock
    private OrganizationService organizationService;
    @Mock
    private SystemReferenceQuery referenceQuery;
    @Mock
    private SystemOperationAudit operationAudit;
    @Mock
    private TransactionTemplate transactions;

    private PhysicalSubsystemService service;

    @BeforeEach
    void setUp() {
        service = new PhysicalSubsystemService(repository, organizationService, referenceQuery, operationAudit, transactions);
    }

    @Test
    void listProjectsCurrentTeamLogicalLabelsAndCreatorName() {
        PhysicalSubsystem raw = physical(201, "团队旧名称", 30L);
        when(repository.pagePhysical(eq(7L), any(PageQuery.class), any(PhysicalSubsystemQuery.class)))
                .thenReturn(new PageResult<>(List.of(raw), 1, 1, 20));
        when(organizationService.tree(ACTOR)).thenReturn(List.of(organization(12, "平台研发团队", 1)));
        when(repository.findLogical(7, 101)).thenReturn(Optional.of(logical()));
        when(referenceQuery.findUser(ACTOR, 30, false))
                .thenReturn(Optional.of(new SystemUserReference(30, "系统负责人", "owner", null, true)));
        when(referenceQuery.findUser(ACTOR, 9, false))
                .thenReturn(Optional.of(new SystemUserReference(9, "架构管理员", "architect", null, true)));

        PageResult<PhysicalSubsystemView> result = service.list(ACTOR, new PageQuery(1, 20),
                new PhysicalSubsystemQuery(" WP ", null, null, " 渠道 ", 12L, 101L));

        assertThat(result.records()).singleElement().satisfies(view -> {
            assertThat(view.responsibleTeamDisplayName()).isEqualTo("平台研发团队");
            assertThat(view.responsibleTeamValid()).isTrue();
            assertThat(view.logicalSubsystemCode()).isEqualTo("AP_201");
            assertThat(view.ownerDisplayName()).isEqualTo("系统负责人");
            assertThat(view.createdByDisplayName()).isEqualTo("架构管理员");
        });
        ArgumentCaptor<PhysicalSubsystemQuery> query = ArgumentCaptor.forClass(PhysicalSubsystemQuery.class);
        verify(repository).pagePhysical(eq(7L), any(PageQuery.class), query.capture());
        assertThat(query.getValue().code()).isEqualTo("WP");
        assertThat(query.getValue().businessGroupName()).isEqualTo("渠道");
    }

    @Test
    void detailUsesSnapshotWhenResponsibleTeamIsInactive() {
        when(repository.findPhysical(7, 201)).thenReturn(Optional.of(physical(201, "原平台团队", null)));
        when(organizationService.tree(ACTOR)).thenReturn(List.of(organization(12, "已改名团队", 0)));
        when(repository.findLogical(7, 101)).thenReturn(Optional.of(logical()));

        PhysicalSubsystemView view = service.detail(ACTOR, 201);

        assertThat(view.responsibleTeamValid()).isFalse();
        assertThat(view.responsibleTeamDisplayName()).isEqualTo("原平台团队");
    }

    @Test
    void createNormalizesBlankBusinessGroupTakesServerTeamSnapshotAndAuditsInsideTransaction() {
        AtomicBoolean inTransaction = new AtomicBoolean();
        executeTransactions(inTransaction);
        validCreateReferences();
        when(repository.lockLogical(7, 101)).thenReturn(Optional.of(new LogicalSubsystemLock(101, false)));
        AtomicLong insertedId = new AtomicLong();
        doAnswer(invocation -> {
            insertedId.set(invocation.getArgument(0));
            return null;
        }).when(repository).insertPhysical(anyLong(), eq(7L), any(PhysicalSubsystemCommand.class),
                eq("平台研发团队"), eq(9L));
        when(repository.findPhysical(eq(7L), anyLong()))
                .thenAnswer(invocation -> Optional.of(physical(invocation.getArgument(1),
                        "平台研发团队", null)));
        doAnswer(invocation -> {
            assertThat(inTransaction.get()).isTrue();
            return null;
        }).when(operationAudit).recordSuccess(any());

        PhysicalSubsystemView result = service.create(ACTOR, validCommand(), "trace-create");

        assertThat(result.id()).isEqualTo(insertedId.get());
        ArgumentCaptor<PhysicalSubsystemCommand> normalized = ArgumentCaptor.forClass(PhysicalSubsystemCommand.class);
        verify(repository).insertPhysical(eq(insertedId.get()), eq(7L), normalized.capture(),
                eq("平台研发团队"), eq(9L));
        assertThat(normalized.getValue().code()).isEqualTo("WP_201");
        assertThat(normalized.getValue().businessGroupName()).isNull();
        assertThat(normalized.getValue().runtimeCode()).isEqualTo("architecture.runtime.7x24");
        assertThat(normalized.getValue().ownerUserId()).isNull();
        assertThat(result.responsibleTeamDisplayName()).isEqualTo("平台研发团队");
        verify(repository).lockLogical(7, 101);
        verify(operationAudit).recordSuccess(any());
    }

    @Test
    void ordinaryInvalidLogicalReferenceReturns400BeforeParentLock() {
        when(repository.findLogical(7, 101)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(ACTOR, validCommand(), "trace-invalid-parent"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(transactions, never()).execute(any());
        verify(repository, never()).lockLogical(anyLong(), anyLong());
        verify(operationAudit).recordFailure(any());
    }

    @Test
    void parentDeletedAfterInitialCheckReturns409() {
        executeTransactions(new AtomicBoolean());
        validCreateReferences();
        when(repository.lockLogical(7, 101)).thenReturn(Optional.of(new LogicalSubsystemLock(101, true)));

        assertThatThrownBy(() -> service.create(ACTOR, validCommand(), "trace-race"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT));

        verify(repository, never()).insertPhysical(anyLong(), anyLong(), any(), any(), anyLong());
        verify(operationAudit).recordFailure(any());
    }

    @Test
    void updateRequiresInactiveResponsibleTeamToBeReselected() {
        when(repository.findPhysical(7, 201)).thenReturn(Optional.of(physical(201, "原团队", null)));
        when(repository.findLogical(7, 101)).thenReturn(Optional.of(logical()));
        when(organizationService.tree(ACTOR)).thenReturn(List.of(organization(12, "原团队", 0)));

        assertThatThrownBy(() -> service.update(ACTOR, 201, validCommand(), "trace-team"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(exception.getMessage()).contains("重新选择");
                });

        verify(transactions, never()).execute(any());
        verify(repository, never()).updatePhysical(anyLong(), anyLong(), any(), any(), anyLong());
    }

    @Test
    void updateExistingResourceRefreshesTeamSnapshotAndUsesExcludeId() {
        executeTransactions(new AtomicBoolean());
        when(repository.findPhysical(7, 201)).thenReturn(Optional.of(physical(201, "原团队", null)));
        validCreateReferences();
        when(repository.lockLogical(7, 101)).thenReturn(Optional.of(new LogicalSubsystemLock(101, false)));
        when(repository.updatePhysical(eq(7L), eq(201L), any(PhysicalSubsystemCommand.class),
                eq("平台研发团队"), eq(9L))).thenReturn(1);

        PhysicalSubsystemView result = service.update(ACTOR, 201, validCommand(), "trace-update-success");

        assertThat(result.id()).isEqualTo(201);
        verify(repository).physicalCodeExists(7, "WP_201", 201L);
        verify(repository).physicalNameExists(7, "员工渠道物理平台", 201L);
        verify(repository).updatePhysical(eq(7L), eq(201L), any(PhysicalSubsystemCommand.class),
                eq("平台研发团队"), eq(9L));
        verify(operationAudit).recordSuccess(any());
    }

    @Test
    void optionalOwnerMustBeActiveAndTenantScopedWhenProvided() {
        when(repository.findLogical(7, 101)).thenReturn(Optional.of(logical()));
        when(organizationService.tree(ACTOR)).thenReturn(List.of(organization(12, "平台研发团队", 1)));
        when(referenceQuery.findUser(ACTOR, 30, true)).thenReturn(Optional.empty());
        PhysicalSubsystemCommand command = new PhysicalSubsystemCommand("WP_201", "员工渠道物理", "员工渠道物理平台",
                101L, null, 12L, null, null, null, 30L, null, null);

        assertThatThrownBy(() -> service.create(ACTOR, command, "trace-owner"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void databaseDuplicateIsMappedTo409WithoutLeakingSqlToAudit() {
        executeTransactions(new AtomicBoolean());
        validCreateReferences();
        when(repository.lockLogical(7, 101)).thenReturn(Optional.of(new LogicalSubsystemLock(101, false)));
        doThrow(new DuplicateKeyException("secret physical SQL"))
                .when(repository).insertPhysical(anyLong(), eq(7L), any(), any(), eq(9L));

        assertThatThrownBy(() -> service.create(ACTOR, validCommand(), "trace-duplicate"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT));

        ArgumentCaptor<SystemOperationAuditCommand> audit = ArgumentCaptor.forClass(SystemOperationAuditCommand.class);
        verify(operationAudit).recordFailure(audit.capture());
        assertThat(audit.getValue().errorMessage()).doesNotContain("secret physical SQL");
    }

    @Test
    void deleteSoftDeletesWithoutRevalidatingHistoricTeam() {
        executeTransactions(new AtomicBoolean());
        when(repository.findPhysical(7, 201)).thenReturn(Optional.of(physical(201, "已删除团队", null)));
        when(repository.softDeletePhysical(7, 201, 9)).thenReturn(1);

        service.delete(ACTOR, 201, "trace-delete");

        verify(repository).softDeletePhysical(7, 201, 9);
        verify(organizationService, never()).tree(any());
        verify(operationAudit).recordSuccess(any());
    }

    @Test
    void missingAuthenticatedTenantReturns401AndIsNotAudited() {
        AuthUser missingTenant = new AuthUser(9, 0, "architect", "hash", "架构管理员", 11, true);

        assertThatThrownBy(() -> service.create(missingTenant, validCommand(), "trace-auth"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(operationAudit, never()).recordFailure(any());
    }

    private void executeTransactions(AtomicBoolean inTransaction) {
        when(transactions.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            inTransaction.set(true);
            try {
                return callback.doInTransaction(mock(TransactionStatus.class));
            } finally {
                inTransaction.set(false);
            }
        });
    }

    private void validCreateReferences() {
        when(repository.findLogical(7, 101)).thenReturn(Optional.of(logical()));
        when(organizationService.tree(ACTOR)).thenReturn(List.of(organization(12, "平台研发团队", 1)));
        when(referenceQuery.activeParameters(ACTOR, PhysicalSubsystemService.RUNTIME_CATEGORY))
                .thenReturn(List.of(new SystemParameterReference("architecture.runtime.7x24", "7*24")));
    }

    private PhysicalSubsystemCommand validCommand() {
        return new PhysicalSubsystemCommand(" wp_201 ", " 员工渠道物理 ", " 员工渠道物理平台 ",
                101L, "   ", 12L, " ARCHITECTURE.RUNTIME.7X24 ", null, null, null, " 描述 ", "  ");
    }

    private OrgTreeNode organization(long id, String name, int status) {
        return new OrgTreeNode(id, 0, "TEAM", name, 1, status, new ArrayList<>(), new ArrayList<>());
    }

    private LogicalSubsystem logical() {
        return new LogicalSubsystem(101, "AP_201", "员工渠道", "员工渠道整合平台", 11,
                null, null, null, 21, null, null, 9, 9,
                LocalDateTime.of(2026, 8, 15, 10, 0), LocalDateTime.of(2026, 8, 15, 10, 0));
    }

    private PhysicalSubsystem physical(long id, String snapshot, Long ownerId) {
        return new PhysicalSubsystem(id, "WP_201", "员工渠道物理", "员工渠道物理平台", 101,
                null, 12, snapshot, "architecture.runtime.7x24", null, null, ownerId, "描述", null,
                9, 9, LocalDateTime.of(2026, 8, 15, 10, 0), LocalDateTime.of(2026, 8, 15, 10, 0));
    }
}
