package com.ccb.architecture.plan.service;

import com.ccb.architecture.plan.model.PlanModels.*;
import com.ccb.architecture.plan.persistence.PlanStore;
import com.ccb.architecture.service.SubsystemParticipationService;
import com.ccb.architecture.service.SubsystemParticipationService.*;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PlanParticipationAuthorizationTest {
    private final AuthUser actor = new AuthUser(20,7,"member","test","参与人",1,true);
    private final PlanStore store = mock(PlanStore.class);
    private final SubsystemParticipationService systems = mock(SubsystemParticipationService.class);
    private final ProjectMemberReferenceQuery members = mock(ProjectMemberReferenceQuery.class);
    private final SystemReferenceQuery users = mock(SystemReferenceQuery.class);
    private final PlanParticipationService service = new PlanParticipationService(store,systems,members,users);
    private Task task;
    @BeforeEach void setUp() {
        task = task(10,100L,0);
        when(store.findParticipantUserIds(7,70,1)).thenReturn(List.of(20L));
        when(store.lockTask(7,70,1)).thenReturn(Optional.of(task));
        when(systems.assignmentScope(eq(actor),any(),eq(100L))).thenReturn(scope(10L,List.of(10L,20L)));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void 公共任务继承合格计划负责人且用用户姓名而非成员占位编号() {
        when(members.findActiveMembers(actor,70)).thenReturn(List.of(new ProjectMemberReference(1,1007,"1007","owner")));
        when(users.findUser(actor,1007,true)).thenReturn(Optional.of(new SystemUserReference(1007,"演示负责人","owner",null,true)));
        var result = service.defaults(actor,70,null,null,1007L);
        assertThat(result.ownerUserId()).isEqualTo(1007L);
        assertThat(result.participantUserIds()).containsExactly(1007L);
        assertThat(result.candidates()).extracting(Candidate::displayName).containsExactly("演示负责人");
    }
    @Test void 公共任务不自动分派给非项目成员也不扩大管理员资格() {
        var result = service.defaults(actor,70,null,null,1007L);
        assertThat(result.ownerUserId()).isNull();
        assertThat(result.participantUserIds()).isEmpty();
        assertThatThrownBy(() -> service.validate(actor,70,null,null,1007L,List.of()))
                .isInstanceOf(BusinessException.class);
    }
    @Test void 公共任务过滤失效用户并允许改派有效成员() {
        when(members.findActiveMembers(actor,70)).thenReturn(List.of(
                new ProjectMemberReference(1,1007,"旧姓名","owner"),new ProjectMemberReference(2,20,"旧姓名","member")));
        when(users.findUser(actor,20,true)).thenReturn(Optional.of(new SystemUserReference(20,"参与人","member",null,true)));
        var result = service.defaults(actor,70,null,null,1007L);
        assertThat(result.ownerUserId()).isNull();
        assertThat(result.candidates()).extracting(Candidate::userId).containsExactly(20L);
        assertThat(service.validate(actor,70,null,null,20L,List.of()).participantUserIds()).containsExactly(20L);
    }
    @Test void 系统任务带出负责人和有效参与名单() {
        var value=service.defaults(actor,70,TargetType.PHYSICAL_SUBSYSTEM,100L,99L);
        assertThat(value.ownerUserId()).isEqualTo(10L);
        assertThat(value.participantUserIds()).containsExactly(10L,20L);
    }
    @Test void 部署单元继承所属系统而非单元自身人员() {
        when(store.unitSystemId(7,70,300)).thenReturn(Optional.of(100L));
        assertThat(service.defaults(actor,70,TargetType.DEPLOYMENT_UNIT,300L,99L).ownerUserId()).isEqualTo(10L);
    }
    @Test void 新负责人自动加入且已显式保留的旧负责人不丢失() {
        var value=service.validate(actor,70,TargetType.PHYSICAL_SUBSYSTEM,100L,20L,List.of(10L));
        assertThat(value.participantUserIds()).containsExactly(10L,20L);
    }
    @Test void 系统以外人员不能被分派() {
        assertThatThrownBy(()->service.validate(actor,70,TargetType.PHYSICAL_SUBSYSTEM,100L,99L,List.of()))
                .isInstanceOf(BusinessException.class);
    }
    @Test void 当前参与人可以执行并使用父系统锁和任务锁() {
        service.requireExecutor(actor,70,task);
        var order=inOrder(systems,store);
        order.verify(systems).lockAndRequireSystemParticipant(eq(actor),any(),eq(100L));
        order.verify(store).lockTask(7,70,1);
    }
    @Test void 管理者可以看但不能代执行() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(actor,null,"architecture:manage"));
        when(store.findParticipantUserIds(7,70,1)).thenReturn(List.of(10L));
        assertThat(service.visible(actor,70,task)).isTrue();
        assertThatThrownBy(()->service.requireExecutor(actor,70,task)).isInstanceOf(BusinessException.class);
    }
    @Test void 仅系统成员没有任务快照也不能执行() {
        when(store.findParticipantUserIds(7,70,1)).thenReturn(List.of());
        assertThat(service.related(actor,70,task)).isFalse();
    }
    @Test void 历史名单不能恢复已撤销资格() {
        when(systems.assignmentScope(eq(actor),any(),eq(100L))).thenReturn(scope(10L,List.of(10L)));
        assertThat(service.related(actor,70,task)).isFalse();
        assertThat(service.recipients(actor,70,task)).containsExactly(10L);
    }
    @Test void 系统删除时管理查询不崩溃但执行资格失效() {
        when(systems.assignmentScope(eq(actor),any(),eq(100L))).thenThrow(new BusinessException(ErrorCode.FORBIDDEN,"系统不存在"));
        assertThat(service.eligible(actor,70,task)).isFalse();
        assertThat(service.recipients(actor,70,task)).isEmpty();
    }
    @Test void 同任务并发修改后拒绝陈旧执行请求() {
        when(store.lockTask(7,70,1)).thenReturn(Optional.of(task(10,100L,1)));
        assertThatThrownBy(()->service.requireExecutor(actor,70,task)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务已更新");
    }
    @Test void 伪造其他项目单元不能继承当前系统() {
        assertThatThrownBy(()->service.systemId(actor,71,TargetType.DEPLOYMENT_UNIT,300L)).isInstanceOf(BusinessException.class);
        verify(store).unitSystemId(7,71,300);
    }
    @Test void 公共任务只默认负责人不自动分派全项目() {
        when(members.findActiveMembers(actor,70)).thenReturn(List.of(new ProjectMemberReference(1,20,"参与人","member")));
        when(users.findUser(actor,20,true)).thenReturn(Optional.of(new SystemUserReference(20,"member","参与人",null,true)));
        var value=service.defaults(actor,70,null,null,20L);
        assertThat(value.participantUserIds()).containsExactly(20L);
        assertThatThrownBy(()->service.validate(actor,70,null,null,99L,List.of())).isInstanceOf(BusinessException.class);
    }
    @Test void 非参与人不能通过任务发起工单且创建器不运行() {
        when(store.findTask(7,70,1)).thenReturn(Optional.of(task));
        when(store.findParticipantUserIds(7,70,1)).thenReturn(List.of());
        var engine=new PlanEngine(store); engine.setParticipation(service);
        var workOrders=new PlanWorkOrderService(store,engine,mock(PlanNotificationService.class));
        java.util.function.Supplier<Long> creator=mock(java.util.function.Supplier.class);
        assertThatThrownBy(()->workOrders.createFromTask(actor,70,1L,WorkOrderType.NETWORK_DNS,creator,Long::longValue))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(creator);
    }
    @Test void 管理标志不能绕过解除关联执行资格() {
        when(store.findTask(7,70,1)).thenReturn(Optional.of(task));
        when(store.findParticipantUserIds(7,70,1)).thenReturn(List.of());
        when(store.findWorkOrder(7,70,9)).thenReturn(Optional.of(new TaskWorkOrder(9,1,2,
                WorkOrderType.NETWORK_DNS,88,WorkOrderSource.CREATED_FROM_TASK,false)));
        var engine=new PlanEngine(store); engine.setParticipation(service);
        var orders=new PlanWorkOrderService(store,engine,mock(PlanNotificationService.class));
        assertThatThrownBy(()->orders.remove(actor,70,9,"解除",true)).isInstanceOf(BusinessException.class);
        verify(store,never()).removeWorkOrder(anyLong(),anyLong(),anyLong(),anyString(),anyLong());
    }
    @Test void 旧资源申请人撤销系统资格后关联明细不可见() {
        when(store.workOrderApplicant(7,70,WorkOrderType.RESOURCE_REQUEST,88)).thenReturn(Optional.of(20L));
        when(store.resourceRequestRefs(7,70,List.of(88L))).thenReturn(List.of(new long[]{88,90,100}));
        doThrow(new BusinessException(ErrorCode.FORBIDDEN,"资格失效")).when(systems)
                .requireSystemParticipant(eq(actor),any(),eq(100L));
        assertThat(service.workOrderVisible(actor,70,WorkOrderType.RESOURCE_REQUEST,88)).isFalse();
    }
    @Test void 未认证上下文不能获得管理权限() {
        var authentication=new TestingAuthenticationToken(actor,null,"architecture:manage");
        authentication.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        assertThat(service.administrator(actor)).isFalse();
    }
    @Test void 空阻塞命令必须返回业务错误且不得访问任务() {
        var blocks = new PlanBlockService(store, new PlanEngine(store), mock(PlanNotificationService.class),
                new com.fasterxml.jackson.databind.ObjectMapper());
        assertThatThrownBy(() -> blocks.addBlock(actor,70,1,null,false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("阻塞信息不能为空");
        assertThatThrownBy(() -> blocks.updateBlock(actor,70,1,null,true))
                .isInstanceOf(BusinessException.class).hasMessageContaining("阻塞信息不能为空");
        verify(store,never()).findBlock(anyLong(),anyLong(),anyLong());
    }
    @Test void 已退出项目的计划负责人不再收到逾期摘要() {
        var publisher = mock(com.ccb.system.notification.SystemNotificationPublisher.class);
        when(store.planIdsNeedingAlert()).thenReturn(List.of(new PlanStore.AlertPlan(7,70,2,"PLAN",20)));
        when(members.findActiveMembers(any(),eq(70L))).thenReturn(List.of());
        new PlanNotificationService(publisher,store,members).scanOverdueAlerts();
        verifyNoInteractions(publisher);
        verify(store,never()).countOverdueTasks(anyLong(),anyLong(),anyLong(),any());
    }
    static ParticipationView scope(Long owner,List<Long> ids) {
        return new ParticipationView(owner,ids,ids,0,false,ids.stream().map(id->new Candidate(id,"测试成员"+id)).toList());
    }
    static Task task(long owner,Long target,long version) {
        return new Task(1,2,3,1,"测试任务",TargetType.PHYSICAL_SUBSYSTEM,target,"SYS","测试系统",null,null,null,null,
                owner,null,null,null,null,TaskStatus.NOT_STARTED,false,false,null,null,null,version);
    }
}
