package com.ccb.architecture.plan.service;

import com.ccb.architecture.plan.model.PlanModels.*;
import com.ccb.architecture.plan.persistence.PlanStore;
import com.ccb.architecture.service.SubsystemParticipationService;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.*;
import com.ccb.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PersonalTaskBoardTest {
    private final AuthUser actor=new AuthUser(20,7,"member","test","参与人",1,true);
    private final PlanStore store=mock(PlanStore.class);
    private final SubsystemParticipationService systems=mock(SubsystemParticipationService.class);
    private final PlanParticipationService participation=new PlanParticipationService(store,systems,
            mock(ProjectMemberReferenceQuery.class),mock(SystemReferenceQuery.class));
    private PlanQueryService query;
    @BeforeEach void setup() {
        var engine=new PlanEngine(store); engine.setParticipation(participation); query=new PlanQueryService(store,engine);
        var plan=mock(Plan.class); when(plan.id()).thenReturn(2L); when(plan.planOwnerUserId()).thenReturn(10L);
        when(plan.status()).thenReturn(PlanStatus.IN_PROGRESS); when(plan.name()).thenReturn("当前计划");
        when(store.findPlan(7,70,2)).thenReturn(Optional.of(plan));
        var stage=mock(Stage.class); when(stage.id()).thenReturn(3L); when(stage.name()).thenReturn("当前环节");
        when(stage.status()).thenReturn(PlanStatus.IN_PROGRESS);
        when(store.findStages(7,70,2)).thenReturn(List.of(stage));
        var mine=task(1,20,TaskStatus.WAITING_PRECEDING,false);
        var other=task(4,10,TaskStatus.IN_PROGRESS,false);
        var cancelled=task(5,20,TaskStatus.CANCELLED,true);
        when(store.findTasks(7,70,2L,null)).thenReturn(List.of(mine,other,cancelled));
        when(store.findTasks(7,70,2L,3L)).thenReturn(List.of(mine,other,cancelled));
        when(systems.assignmentScope(eq(actor),any(),eq(100L))).thenReturn(
                PlanParticipationAuthorizationTest.scope(10L,List.of(10L,20L)));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void 普通人只返回本人任务且默认隐藏取消任务() {
        var result=query.dashboard(actor,70,2);
        assertThat(result.stages()).hasSize(1);
        assertThat(result.stages().get(0).tasks()).extracting(PlanQueryService.DashboardTask::id).containsExactly(1L);
        assertThat(result.hasBlocked()).isTrue();
    }
    @Test void 管理者默认仍是本人范围且可以切当前计划全部() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(actor,null,"architecture:manage"));
        assertThat(query.dashboard(actor,70,2).stages().get(0).tasks()).hasSize(1);
        assertThat(query.dashboard(actor,70,2,true).stages().get(0).tasks()).hasSize(2);
    }
    @Test void 非管理者伪造全部标志被拒绝() {
        assertThatThrownBy(()->query.dashboard(actor,70,2,true)).isInstanceOf(BusinessException.class);
    }
    @Test void 管理详情中的他人任务仍标记不能执行() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(actor,null,"architecture:manage"));
        assertThat(query.detail(actor,70,2).stages().get(0).tasks().stream().filter(t->t.id()==4).findFirst().orElseThrow().canExecute()).isFalse();
    }
    @Test void 已撤销系统资格后看板和时间视图都不返回历史任务() {
        when(systems.assignmentScope(eq(actor),any(),eq(100L))).thenReturn(PlanParticipationAuthorizationTest.scope(10L,List.of(10L)));
        assertThatThrownBy(()->query.detail(actor,70,2)).isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo(com.ccb.common.exception.ErrorCode.FORBIDDEN));
        assertThatThrownBy(()->query.dashboard(actor,70,2)).isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo(com.ccb.common.exception.ErrorCode.FORBIDDEN));
        assertThatThrownBy(()->query.timeline(actor,70,2)).isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo(com.ccb.common.exception.ErrorCode.FORBIDDEN));
    }
    @Test void 看板按计划批量读取名单并在请求内复用同系统资格() {
        query.dashboard(actor,70,2);
        verify(store).findPlanParticipants(7,70,2);
        verify(store,never()).findParticipantUserIds(anyLong(),anyLong(),anyLong());
        verify(systems,times(1)).assignmentScope(eq(actor),any(),eq(100L));
    }
    @Test void 显式参与人即使不是负责人也能在批量查询中看到任务() {
        when(store.findPlanParticipants(7,70,2)).thenReturn(Map.of(4L,List.of(20L)));
        assertThat(query.dashboard(actor,70,2).stages().get(0).tasks())
                .extracting(PlanQueryService.DashboardTask::id).containsExactly(1L,4L);
    }
    private Task task(long id,long owner,TaskStatus status,boolean cancelled) {
        return new Task(id,2,3,1,"任务"+id,TargetType.PHYSICAL_SUBSYSTEM,100L,"S","系统",null,null,null,null,
                owner,null,null,null,null,status,false,cancelled,null,null,null,0);
    }
}
