package com.ccb.architecture.service;

import com.ccb.architecture.persistence.SubsystemParticipationStore;
import com.ccb.architecture.persistence.SubsystemParticipationStore.SystemScope;
import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SubsystemParticipationServiceTest {
    private final AuthUser actor = new AuthUser(9, 7, "tester", "", "测试人员", 1, true);
    private final ProjectAccess project = new ProjectAccess(70, "P70", "测试项目");
    private final SubsystemParticipationStore store = mock(SubsystemParticipationStore.class);
    private final ProjectMemberReferenceQuery members = mock(ProjectMemberReferenceQuery.class);
    private final SystemReferenceQuery users = mock(SystemReferenceQuery.class);
    private final SystemOperationAudit audit = mock(SystemOperationAudit.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private SubsystemParticipationService service;

    @BeforeEach
    void setup() {
        service = new SubsystemParticipationService(store, members, users, audit, transactions);
        when(store.findSystem(7, 70, 10, false)).thenReturn(Optional.of(new SystemScope(10, 9L, 3)));
        when(members.findActiveMembers(actor, 70)).thenReturn(List.of(
                new ProjectMemberReference(109, 9, "负责人", "owner"),
                new ProjectMemberReference(120, 20, "成员", "member")));
        when(users.findUser(eq(actor), anyLong(), eq(true))).thenAnswer(i ->
                Optional.of(new SystemUserReference(i.getArgument(1), "人员", "user", null, true)));
        when(transactions.execute(any())).thenAnswer(i ->
                ((org.springframework.transaction.support.TransactionCallback<?>) i.getArgument(0)).doInTransaction(null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", " 调整分工 "})
    void 变更原因选填且名单审计始终保留(String reason) {
        when(store.findSystem(7, 70, 10, true)).thenReturn(Optional.of(new SystemScope(10, 9L, 3)));
        when(store.advanceVersion(7, 70, 10, 3, 9)).thenReturn(true);
        service.replace(actor, project, 10,
                new SubsystemParticipationService.ReplaceCommand(List.of(20L), 3L, reason), false, "trace");
        verify(store).replace(7, 70, 10, List.of(20L), 9);
        verify(store).recordChange(7, 70, 10, 9, List.of(), List.of(20L),
                reason == null ? "" : reason.trim(), "trace");
        verify(audit).recordSuccess(any());
    }

    @Test
    void 原因超过五百字符仍拒绝且不写名单() {
        when(store.findSystem(7, 70, 10, true)).thenReturn(Optional.of(new SystemScope(10, 9L, 3)));
        assertThatThrownBy(() -> service.replace(actor, project, 10,
                new SubsystemParticipationService.ReplaceCommand(List.of(20L), 3L, "因".repeat(501)), false, "trace"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("500");
        verify(store, never()).replace(anyLong(), anyLong(), anyLong(), anyList(), anyLong());
        verify(audit).recordFailure(any());
    }

    @Test
    void 负责人隐含参与且名单去重() {
        when(store.findExplicit(7, 70, 10)).thenReturn(List.of(20L, 9L));
        assertThat(service.detail(actor, project, 10, false).effectiveParticipantUserIds()).containsExactly(9L, 20L);
    }

    @Test
    void 项目成员主键不能误当用户主键() {
        when(store.findExplicit(7, 70, 10)).thenReturn(List.of(109L));
        assertThat(service.detail(actor, project, 10, false).effectiveParticipantUserIds()).containsExactly(9L);
    }

    @Test
    void 停用用户即使仍在名单也无资格() {
        when(users.findUser(actor, 9, true)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireSystemParticipant(actor, project, 10))
                .isInstanceOf(BusinessException.class).hasMessageContaining("参与");
    }

    @Test
    void 跨项目实体不可读取() {
        assertThatThrownBy(() -> service.detail(actor, new ProjectAccess(71, "P71", "其他项目"), 10, true))
                .isInstanceOf(BusinessException.class);
        verify(store, never()).findExplicit(7, 71, 10);
    }

    @Test
    void 管理身份不提供参与资格() {
        when(store.findSystem(7, 70, 10, false)).thenReturn(Optional.of(new SystemScope(10, 20L, 3)));
        assertThatThrownBy(() -> service.requireSystemParticipant(actor, project, 10)).isInstanceOf(BusinessException.class);
    }

    @Test
    void 版本冲突不能覆盖成员() {
        when(store.findSystem(7, 70, 10, true)).thenReturn(Optional.of(new SystemScope(10, 9L, 4)));
        assertThatThrownBy(() -> service.replace(actor, project, 10,
                new SubsystemParticipationService.ReplaceCommand(List.of(20L), 3L, "调整分工"), false, "trace"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("刷新");
        verify(store, never()).replace(anyLong(), anyLong(), anyLong(), anyList(), anyLong());
        verify(audit).recordFailure(any());
    }

    @Test
    void 未完成责任须先移交() {
        when(store.findSystem(7, 70, 10, true)).thenReturn(Optional.of(new SystemScope(10, 9L, 3)));
        when(store.findExplicit(7, 70, 10)).thenReturn(List.of(20L));
        when(store.hasPendingResponsibility(7, 70, 10, 20)).thenReturn(true);
        assertThatThrownBy(() -> service.replace(actor, project, 10,
                new SubsystemParticipationService.ReplaceCommand(List.of(), 3L, "退出系统"), false, "trace"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("移交");
        verify(store, never()).replace(anyLong(), anyLong(), anyLong(), anyList(), anyLong());
    }

    @Test
    void 非负责人且非管理者不得维护() {
        when(store.findSystem(7, 70, 10, true)).thenReturn(Optional.of(new SystemScope(10, 20L, 3)));
        assertThatThrownBy(() -> service.replace(actor, project, 10,
                new SubsystemParticipationService.ReplaceCommand(List.of(9L), 3L, "调整分工"), false, "trace"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("维护");
    }
    @Test
    void 负责人移交校验不能脱离事务() {
        assertThatThrownBy(() -> service.requireOwnerTransfer(7, 70, 10, 20L))
                .isInstanceOf(IllegalStateException.class);
        verify(store, never()).findSystem(anyLong(), anyLong(), anyLong(), eq(true));
    }

    @Test
    void 更换负责人会检查隐含参与退出及剩余责任() {
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            when(store.findSystem(7, 70, 10, true)).thenReturn(Optional.of(new SystemScope(10, 9L, 3)));
            when(store.hasPendingResponsibility(7, 70, 10, 9)).thenReturn(true);
            assertThatThrownBy(() -> service.requireOwnerTransfer(7, 70, 10, 20L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("移交");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void 负责人不变或仍为显式参与人不构成退出() {
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            when(store.findSystem(7, 70, 10, true)).thenReturn(Optional.of(new SystemScope(10, 9L, 3)));
            service.requireOwnerTransfer(7, 70, 10, 9L);
            when(store.findExplicit(7, 70, 10)).thenReturn(List.of(9L));
            service.requireOwnerTransfer(7, 70, 10, 20L);
            verify(store, never()).hasPendingResponsibility(anyLong(), anyLong(), anyLong(), anyLong());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void 无剩余责任时允许旧负责人退出() {
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            when(store.findSystem(7, 70, 10, true)).thenReturn(Optional.of(new SystemScope(10, 9L, 3)));
            service.requireOwnerTransfer(7, 70, 10, 20L);
            verify(store).hasPendingResponsibility(7, 70, 10, 9);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void 退出项目必须移交本项目责任但不查询其他项目() {
        when(store.hasProjectPendingResponsibility(7, 70, 9)).thenReturn(true);
        assertThatThrownBy(() -> service.requireNoPendingTasks(7, 70, 9))
                .isInstanceOf(BusinessException.class).hasMessageContaining("移交");
        service.requireNoPendingTasks(7, 71, 9);
        verify(store).hasProjectPendingResponsibility(7, 70, 9);
        verify(store).hasProjectPendingResponsibility(7, 71, 9);
    }

}
