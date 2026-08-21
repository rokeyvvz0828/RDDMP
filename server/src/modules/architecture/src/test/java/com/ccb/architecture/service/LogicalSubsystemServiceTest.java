package com.ccb.architecture.service;

import com.ccb.architecture.model.LogicalSubsystem;
import com.ccb.architecture.model.LogicalSubsystemCommand;
import com.ccb.architecture.model.LogicalSubsystemLock;
import com.ccb.architecture.model.LogicalSubsystemQuery;
import com.ccb.architecture.repository.ArchitectureSubsystemRepository;
import com.ccb.architecture.web.ArchitectureNotFoundException;
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
class LogicalSubsystemServiceTest {
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

    private LogicalSubsystemService service;

    @BeforeEach
    void setUp() {
        service = new LogicalSubsystemService(repository, organizationService, referenceQuery, operationAudit, transactions);
    }

    @Test
    void listUsesAuthenticatedTenantAndNormalizesFilters() {
        PageResult<LogicalSubsystem> expected = new PageResult<>(List.of(logical(101)), 1, 1, 20);
        when(repository.pageLogical(eq(7L), any(PageQuery.class), any(LogicalSubsystemQuery.class)))
                .thenReturn(expected);

        PageResult<LogicalSubsystem> actual = service.list(ACTOR, new PageQuery(1, 20),
                new LogicalSubsystemQuery(" AP ", " 渠道 ", " 员工 ", 11L));

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<LogicalSubsystemQuery> query = ArgumentCaptor.forClass(LogicalSubsystemQuery.class);
        verify(repository).pageLogical(eq(7L), any(PageQuery.class), query.capture());
        assertThat(query.getValue()).isEqualTo(new LogicalSubsystemQuery("AP", "渠道", "员工", 11L));
    }

    @Test
    void createNormalizesFieldsValidatesReferencesAndAuditsInsideTransaction() {
        AtomicBoolean inTransaction = new AtomicBoolean();
        executeTransactions(inTransaction);
        validReferences();
        AtomicLong insertedId = new AtomicLong();
        doAnswer(invocation -> {
            insertedId.set(invocation.getArgument(0));
            return null;
        }).when(repository).insertLogical(anyLong(), eq(7L), any(LogicalSubsystemCommand.class), eq(9L));
        when(repository.findLogical(eq(7L), anyLong()))
                .thenAnswer(invocation -> Optional.of(logical(invocation.getArgument(1))));
        doAnswer(invocation -> {
            assertThat(inTransaction.get()).isTrue();
            return null;
        }).when(operationAudit).recordSuccess(any());

        LogicalSubsystem actual = service.create(ACTOR, validCommand(), "trace-create");

        assertThat(actual.id()).isEqualTo(insertedId.get());
        ArgumentCaptor<LogicalSubsystemCommand> normalized = ArgumentCaptor.forClass(LogicalSubsystemCommand.class);
        verify(repository).insertLogical(eq(insertedId.get()), eq(7L), normalized.capture(), eq(9L));
        assertThat(normalized.getValue()).isEqualTo(new LogicalSubsystemCommand(
                "AP_201", "员工渠道", "员工渠道整合平台", 11L,
                "P2", "APPLICATION", "CHANNEL", 21L, "系统描述", null));
        ArgumentCaptor<SystemOperationAuditCommand> audit = ArgumentCaptor.forClass(SystemOperationAuditCommand.class);
        verify(operationAudit).recordSuccess(audit.capture());
        assertThat(audit.getValue().actor()).isSameAs(ACTOR);
        assertThat(audit.getValue().operationCode()).isEqualTo("ARCHITECTURE_LOGICAL_CREATE");
        assertThat(audit.getValue().requestPath()).isEqualTo("/api/architecture/logical-subsystems");
        assertThat(audit.getValue().traceId()).isEqualTo("trace-create");
    }

    @Test
    void invalidCrossTenantContactReturnsBadRequestAndAuditsAfterRollback() {
        AtomicBoolean inTransaction = new AtomicBoolean();
        executeTransactions(inTransaction);
        when(organizationService.tree(ACTOR)).thenReturn(List.of(activeOrganization()));
        when(referenceQuery.findUser(ACTOR, 21, true)).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            assertThat(inTransaction.get()).isFalse();
            return null;
        }).when(operationAudit).recordFailure(any());

        assertThatThrownBy(() -> service.create(ACTOR, validCommand(), "trace-invalid"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(repository, never()).insertLogical(anyLong(), anyLong(), any(), anyLong());
        ArgumentCaptor<SystemOperationAuditCommand> audit = ArgumentCaptor.forClass(SystemOperationAuditCommand.class);
        verify(operationAudit).recordFailure(audit.capture());
        assertThat(audit.getValue().errorMessage()).contains("联系人");
    }

    @Test
    void permanentCodeConflictReturns409() {
        executeTransactions(new AtomicBoolean());
        validReferences();
        when(repository.logicalCodeExists(7, "AP_201", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(ACTOR, validCommand(), "trace-conflict"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT));

        verify(operationAudit).recordFailure(any());
        verify(repository, never()).insertLogical(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    void databaseDuplicateIsMappedTo409AndKeepsSafeAuditMessage() {
        executeTransactions(new AtomicBoolean());
        validReferences();
        doThrow(new DuplicateKeyException("secret SQL and submitted values"))
                .when(repository).insertLogical(anyLong(), eq(7L), any(), eq(9L));

        assertThatThrownBy(() -> service.create(ACTOR, validCommand(), "trace-duplicate"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT));

        ArgumentCaptor<SystemOperationAuditCommand> audit = ArgumentCaptor.forClass(SystemOperationAuditCommand.class);
        verify(operationAudit).recordFailure(audit.capture());
        assertThat(audit.getValue().errorMessage()).doesNotContain("secret SQL");
    }

    @Test
    void updateMissingResourceReturnsModuleNotFoundAndAuditsFailure() {
        executeTransactions(new AtomicBoolean());
        when(repository.findLogical(7, 404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(ACTOR, 404, validCommand(), "trace-update"))
                .isInstanceOf(ArchitectureNotFoundException.class);

        ArgumentCaptor<SystemOperationAuditCommand> audit = ArgumentCaptor.forClass(SystemOperationAuditCommand.class);
        verify(operationAudit).recordFailure(audit.capture());
        assertThat(audit.getValue().requestPath()).endsWith("/404");
    }

    @Test
    void updateExistingResourceUsesTenantAndExcludeIdThenAuditsSuccess() {
        executeTransactions(new AtomicBoolean());
        validReferences();
        when(repository.findLogical(7, 101)).thenReturn(Optional.of(logical(101)));
        when(repository.updateLogical(eq(7L), eq(101L), any(LogicalSubsystemCommand.class), eq(9L)))
                .thenReturn(1);

        LogicalSubsystem actual = service.update(ACTOR, 101, validCommand(), "trace-update-success");

        assertThat(actual.id()).isEqualTo(101);
        verify(repository).logicalCodeExists(7, "AP_201", 101L);
        verify(repository).logicalNameExists(7, "员工渠道整合平台", 101L);
        verify(repository).updateLogical(eq(7L), eq(101L), any(LogicalSubsystemCommand.class), eq(9L));
        verify(operationAudit).recordSuccess(any());
    }

    @Test
    void deleteReferencedLogicalSubsystemReturns409WithoutDeleting() {
        executeTransactions(new AtomicBoolean());
        when(repository.lockLogical(7, 101)).thenReturn(Optional.of(new LogicalSubsystemLock(101, false)));
        when(repository.countActivePhysicalByLogical(7, 101)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(ACTOR, 101, "trace-delete"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.CONFLICT));

        verify(repository, never()).softDeleteLogical(anyLong(), anyLong(), anyLong());
        verify(operationAudit).recordFailure(any());
    }

    @Test
    void deleteSoftDeletesLockedTenantResourceAndAuditsSuccess() {
        executeTransactions(new AtomicBoolean());
        when(repository.lockLogical(7, 101)).thenReturn(Optional.of(new LogicalSubsystemLock(101, false)));
        when(repository.countActivePhysicalByLogical(7, 101)).thenReturn(0L);
        when(repository.softDeleteLogical(7, 101, 9)).thenReturn(1);

        service.delete(ACTOR, 101, "trace-delete-success");

        verify(repository).softDeleteLogical(7, 101, 9);
        ArgumentCaptor<SystemOperationAuditCommand> audit = ArgumentCaptor.forClass(SystemOperationAuditCommand.class);
        verify(operationAudit).recordSuccess(audit.capture());
        assertThat(audit.getValue().operationCode()).isEqualTo("ARCHITECTURE_LOGICAL_DELETE");
        assertThat(audit.getValue().requestPath()).endsWith("/101");
    }

    @Test
    void failureAuditCannotMaskOriginalBusinessError() {
        executeTransactions(new AtomicBoolean());
        when(repository.findLogical(7, 404)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("audit unavailable")).when(operationAudit).recordFailure(any());

        assertThatThrownBy(() -> service.update(ACTOR, 404, validCommand(), "trace-original"))
                .isInstanceOf(ArchitectureNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    void missingAuthenticatedTenantReturns401AndIsNotAudited() {
        AuthUser missingTenant = new AuthUser(9, 0, "architect", "hash", "架构管理员", 11, true);

        assertThatThrownBy(() -> service.create(missingTenant, validCommand(), "trace-auth"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(operationAudit, never()).recordFailure(any());
        verify(transactions, never()).execute(any());
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

    private void validReferences() {
        when(organizationService.tree(ACTOR)).thenReturn(List.of(activeOrganization()));
        when(referenceQuery.findUser(ACTOR, 21, true))
                .thenReturn(Optional.of(new SystemUserReference(21, "联系人", "contact", null, true)));
        when(referenceQuery.activeParameters(ACTOR, LogicalSubsystemService.DEPLOYMENT_PLATFORM_CATEGORY))
                .thenReturn(List.of(new SystemParameterReference("P2", "员工渠道平台（P2）")));
        when(referenceQuery.activeParameters(ACTOR, LogicalSubsystemService.SYSTEM_TYPE_CATEGORY))
                .thenReturn(List.of(new SystemParameterReference("APPLICATION", "应用平台类")));
        when(referenceQuery.activeParameters(ACTOR, LogicalSubsystemService.SYSTEM_OWNERSHIP_CATEGORY))
                .thenReturn(List.of(new SystemParameterReference("CHANNEL", "渠道整合层")));
    }

    private OrgTreeNode activeOrganization() {
        return new OrgTreeNode(11, 0, "BU", "数字事业群", 1, 1, new ArrayList<>(), new ArrayList<>());
    }

    private LogicalSubsystemCommand validCommand() {
        return new LogicalSubsystemCommand(" ap_201 ", " 员工渠道 ", " 员工渠道整合平台 ", 11L,
                " p2 ", " application ", " channel ", 21L, " 系统描述 ", "   ");
    }

    private LogicalSubsystem logical(long id) {
        return new LogicalSubsystem(id, "AP_201", "员工渠道", "员工渠道整合平台", 11,
                "P2", "APPLICATION", "CHANNEL", 21, "系统描述", null,
                9, 9, LocalDateTime.of(2026, 8, 15, 10, 0), LocalDateTime.of(2026, 8, 15, 10, 0));
    }
}
