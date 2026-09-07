package com.ccb.architecture.service;

import com.ccb.architecture.persistence.SubsystemParticipationStore;
import com.ccb.architecture.persistence.SubsystemParticipationStore.SystemScope;
import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
}
