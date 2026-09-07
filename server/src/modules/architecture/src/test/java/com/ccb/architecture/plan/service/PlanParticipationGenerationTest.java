package com.ccb.architecture.plan.service;

import com.ccb.architecture.plan.model.PlanModels.*;
import com.ccb.architecture.plan.persistence.PlanStore;
import com.ccb.architecture.service.SubsystemParticipationService;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.*;
import com.ccb.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PlanParticipationGenerationTest {
    private final AuthUser actor=new AuthUser(10,7,"owner","test","管理者",1,true);
    private final PlanStore store=mock(PlanStore.class);
    private final SubsystemParticipationService systems=mock(SubsystemParticipationService.class);
    private final PlanTemplateService templates=mock(PlanTemplateService.class);
    private PlanGenerationService generation;
    private Task task;
    @BeforeEach void setup() {
        var engine=new PlanEngine(store);
        engine.setParticipation(new PlanParticipationService(store,systems,mock(ProjectMemberReferenceQuery.class),mock(SystemReferenceQuery.class)));
        generation=new PlanGenerationService(store,templates,engine,mock(PlanNotificationService.class),
                mock(SystemReferenceQuery.class),new ObjectMapper());
        task=PlanParticipationAuthorizationTest.task(10,100L,0);
        when(store.findTask(7,70,1)).thenReturn(Optional.of(task));
        when(store.lockTask(7,70,1)).thenReturn(Optional.of(task));
        var plan=mock(Plan.class); when(plan.planOwnerUserId()).thenReturn(10L);
        when(store.findPlan(7,70,2)).thenReturn(Optional.of(plan));
        when(systems.assignmentScope(eq(actor),any(),eq(100L))).thenReturn(PlanParticipationAuthorizationTest.scope(10L,List.of(10L,20L)));
        when(store.findParticipantUserIds(7,70,1)).thenReturn(List.of(10L));
    }
    @Test void 分派更新负责人名单与原因审计() {
        when(store.updateTaskAssignment(7,70,1,20,0,10)).thenReturn(true);
        generation.assign(actor,70,1,new AssignmentCommand(20L,List.of(10L),0L,"交接"),false);
        verify(store).deleteParticipants(7,70,1);
        verify(store).insertParticipant(eq(7L),eq(70L),anyLong(),eq(1L),eq(20L),eq(10L));
        verify(store).insertParticipant(eq(7L),eq(70L),anyLong(),eq(1L),eq(10L),eq(10L));
        verify(store).insertActivity(eq(7L),eq(70L),anyLong(),eq("PLAN"),eq(2L),eq("TASK"),eq(1L),eq("TASK_ASSIGNED"),eq(10L),eq("交接"),anyString(),anyString());
    }
    @Test void 版本冲突不能改写参与名单() {
        assertThatThrownBy(()->generation.assign(actor,70,1,new AssignmentCommand(20L,List.of(),0L,"交接"),false)).isInstanceOf(BusinessException.class);
        verify(store,never()).deleteParticipants(anyLong(),anyLong(),anyLong());
    }
    @Test void 缺少原因拒绝分派() {
        assertThatThrownBy(()->generation.assign(actor,70,1,new AssignmentCommand(20L,List.of(),0L," "),false)).isInstanceOf(BusinessException.class);
    }
    @Test void 非系统成员不能被管理员指定() {
        assertThatThrownBy(()->generation.assign(actor,70,1,new AssignmentCommand(99L,List.of(),0L,"交接"),true)).isInstanceOf(BusinessException.class);
        verify(store,never()).updateTaskAssignment(anyLong(),anyLong(),anyLong(),anyLong(),anyLong(),anyLong());
    }
    @Test void 退出任务前必须移交未解决阻塞() {
        var block=mock(Block.class); when(block.ownerUserId()).thenReturn(10L);
        when(store.lockBlocks(7,70,1)).thenReturn(List.of(block));
        assertThatThrownBy(()->generation.assign(actor,70,1,new AssignmentCommand(20L,List.of(),0L,"交接"),false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("阻塞");
    }
    @Test void 按系统和部署单元展开预览并继承有效分工() {
        var version = new com.ccb.architecture.plan.model.PlanTemplateModels.TemplateVersion(1L,5,1,"snapshot",null,10,null);
        var detail = new com.ccb.architecture.plan.model.PlanTemplateModels.PlanTemplateDetail(null,List.of(),List.of(version));
        var physical = new com.ccb.architecture.plan.model.PlanTemplateModels.SnapshotTask(11L,1,"系统准备",
                com.ccb.architecture.plan.model.PlanTemplateModels.Dimension.PHYSICAL_SUBSYSTEM,List.of(),List.of());
        var unit = new com.ccb.architecture.plan.model.PlanTemplateModels.SnapshotTask(12L,1,"单元部署",
                com.ccb.architecture.plan.model.PlanTemplateModels.Dimension.DEPLOYMENT_UNIT,List.of(),List.of());
        when(templates.detailForGeneration(7,5)).thenReturn(detail);
        when(templates.parseSnapshot("snapshot")).thenReturn(List.of(
                new com.ccb.architecture.plan.model.PlanTemplateModels.SnapshotStage(1L,"搭建",1,null,null,List.of(physical,unit),List.of())));
        when(store.listPhysicalSubsystemRefs(7,70,List.of(100L))).thenReturn(List.of(new PlanStore.TargetRef(100,"SYS","系统","ACTIVE")));
        when(store.listDeploymentUnitRefs(7,70,List.of(300L))).thenReturn(List.of(new PlanStore.TargetRef(300,"UNIT","单元","ACTIVE")));
        when(store.unitSystemId(7,70,300)).thenReturn(Optional.of(100L));
        var result = generation.preview(actor,70,new CreatePlanCommand(1,5,"验收",99,List.of(100L),List.of(300L),List.of(),null,null));
        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(task -> {
            assertThat(task.ownerUserId()).isEqualTo(10L);
            assertThat(task.participantUserIds()).containsExactly(10L,20L);
        });
        assertThat(result).extracting(PlanGenerationService.PreviewTask::key).doesNotHaveDuplicates();
    }
    @Test void 零任务计划可以创建并保留后续补任务的模板环节() {
        var engine = mock(PlanEngine.class);
        var refs = mock(SystemReferenceQuery.class);
        var service = new PlanGenerationService(store,templates,engine,mock(PlanNotificationService.class),refs,new ObjectMapper());
        var template = new com.ccb.architecture.plan.model.PlanTemplateModels.PlanTemplate(5L,"部署模板",null,
                com.ccb.architecture.plan.model.PlanTemplateModels.TemplateStatus.ACTIVE,1,0,10,10);
        var version = new com.ccb.architecture.plan.model.PlanTemplateModels.TemplateVersion(1L,5,1,"empty",null,10,null);
        when(templates.detailForGeneration(7,5)).thenReturn(new com.ccb.architecture.plan.model.PlanTemplateModels.PlanTemplateDetail(template,List.of(),List.of(version)));
        when(templates.parseSnapshot("empty")).thenReturn(List.of(new com.ccb.architecture.plan.model.PlanTemplateModels.SnapshotStage(
                1L,"待部署",1,null,null,List.of(new com.ccb.architecture.plan.model.PlanTemplateModels.SnapshotTask(1L,1,"部署单元工作",
                com.ccb.architecture.plan.model.PlanTemplateModels.Dimension.DEPLOYMENT_UNIT,List.of(),List.of())),List.of())));
        when(store.envReference(7,70,1)).thenReturn(Optional.of(new PlanStore.EnvironmentRef(1,"TEST","测试环境","ACTIVE")));
        when(store.listPhysicalSubsystemRefs(7,70,List.of(100L))).thenReturn(List.of(new PlanStore.TargetRef(100,"SYS","系统","ACTIVE")));
        when(refs.findUser(actor,10,true)).thenReturn(Optional.of(new SystemUserReference(10,"负责人","owner",null,true)));
        var saved = new java.util.concurrent.atomic.AtomicReference<Plan>();
        doAnswer(call -> { saved.set(call.getArgument(1)); return null; }).when(store).insertPlan(eq(7L),any());
        when(engine.requirePlan(eq(actor),eq(70L),anyLong())).thenAnswer(call -> saved.get());
        var result = service.createPlan(actor,70,new CreatePlanCommand(1,5,"空计划",10,List.of(100L),List.of(),List.of(),null,null,List.of()));
        assertThat(result.status()).isEqualTo(PlanStatus.NOT_STARTED);
        verify(store).insertStage(eq(7L),eq(70L),any());
        verify(store,never()).insertTask(anyLong(),anyLong(),any());
        verify(engine).recompute(eq(7L),eq(70L),eq(result.id()),any());
        // 同一目标后来匹配到任务时，旧的空预览不能默默创建新任务。
        when(templates.parseSnapshot("empty")).thenReturn(List.of(new com.ccb.architecture.plan.model.PlanTemplateModels.SnapshotStage(
                1L,"待部署",1,null,null,List.of(new com.ccb.architecture.plan.model.PlanTemplateModels.SnapshotTask(1L,1,"系统工作",
                com.ccb.architecture.plan.model.PlanTemplateModels.Dimension.PHYSICAL_SUBSYSTEM,List.of(),List.of())),List.of())));
        when(engine.participation()).thenReturn(new PlanParticipationService(store,systems,mock(ProjectMemberReferenceQuery.class),refs));
        assertThatThrownBy(() -> service.createPlan(actor,70,new CreatePlanCommand(1,5,"空计划",10,List.of(100L),List.of(),List.of(),null,null,List.of())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("重新预览");
    }
    @Test void 公共任务创建必须采用预览中重新指定的分工() {
        var engine = mock(PlanEngine.class);
        var refs = mock(SystemReferenceQuery.class);
        var participation = mock(PlanParticipationService.class);
        when(engine.participation()).thenReturn(participation);
        when(participation.defaults(actor,70,null,null,10L))
                .thenReturn(new PlanParticipationService.Assignment(null,List.of(),List.of()));
        when(participation.validate(actor,70,null,null,20L,List.of(20L)))
                .thenReturn(new PlanParticipationService.Assignment(20L,List.of(20L),List.of()));
        var service = new PlanGenerationService(store,templates,engine,mock(PlanNotificationService.class),refs,new ObjectMapper());
        var version = new com.ccb.architecture.plan.model.PlanTemplateModels.TemplateVersion(1L,5,1,"common",null,10,null);
        var template = mock(com.ccb.architecture.plan.model.PlanTemplateModels.PlanTemplate.class);
        when(template.latestVersionNo()).thenReturn(1);
        when(template.name()).thenReturn("公共模板");
        when(templates.detailForGeneration(7,5)).thenReturn(new com.ccb.architecture.plan.model.PlanTemplateModels.PlanTemplateDetail(template,List.of(),List.of(version)));
        when(templates.parseSnapshot("common")).thenReturn(List.of(new com.ccb.architecture.plan.model.PlanTemplateModels.SnapshotStage(
                1L,"公共环节",1,null,null,List.of(new com.ccb.architecture.plan.model.PlanTemplateModels.SnapshotTask(11L,1,"公共任务",
                com.ccb.architecture.plan.model.PlanTemplateModels.Dimension.NONE,List.of(),List.of())),List.of())));
        when(store.envReference(7,70,1)).thenReturn(Optional.of(new PlanStore.EnvironmentRef(1,"TEST","测试环境","ACTIVE")));
        when(store.listPhysicalSubsystemRefs(7,70,List.of(100L))).thenReturn(List.of(new PlanStore.TargetRef(100,"SYS","系统","ACTIVE")));
        when(refs.findUser(actor,10,true)).thenReturn(Optional.of(new SystemUserReference(10,"计划负责人","owner",null,true)));
        var cmd = new CreatePlanCommand(1,5,"公共分派",10,List.of(100L),List.of(),List.of(),null,null);
        var key = service.preview(actor,70,cmd).get(0).key();
        service.createPlan(actor,70,new CreatePlanCommand(1,5,"公共分派",10,List.of(100L),List.of(),List.of(),null,null,
                List.of(new TaskAssignment(key,20L,List.of(20L)))));
        verify(store).insertTask(eq(7L),eq(70L),argThat(created -> created.ownerUserId()==20L && created.targetId()==null));
        verify(participation).validate(actor,70,null,null,20L,List.of(20L));
    }
    @Test void 零任务重算不会将计划标记完成() {
        var engine = new PlanEngine(store);
        var plan = mock(Plan.class);
        when(store.lockPlan(7,70,2)).thenReturn(Optional.of(plan));
        engine.recompute(7,70,2,java.time.LocalDateTime.now());
        verify(store).updatePlanStatus(eq(7L),eq(70L),eq(2L),eq(PlanStatus.NOT_STARTED),anyBoolean(),any(),any(),any());
        assertThat(PlanStatusCalculator.progressPercent(0,0,0)).isNull();
    }
    @Test void 后补任务默认分工限定同计划有效目标并继承系统人员() {
        when(store.findTarget(7,70,2,TargetType.PHYSICAL_SUBSYSTEM,100))
                .thenReturn(Optional.of(new PlanTarget(5,2,TargetType.PHYSICAL_SUBSYSTEM,100,"SYS","系统",false,null)));
        var result = generation.newTaskAssignment(actor,70,2,100L,false);
        assertThat(result.ownerUserId()).isEqualTo(10L);
        assertThat(result.participantUserIds()).containsExactly(10L,20L);
        assertThatThrownBy(() -> generation.newTaskAssignment(actor,70,2,999L,true)).isInstanceOf(BusinessException.class).hasMessageContaining("计划范围");
        when(store.findTarget(7,70,2,TargetType.PHYSICAL_SUBSYSTEM,100))
                .thenReturn(Optional.of(new PlanTarget(5,2,TargetType.PHYSICAL_SUBSYSTEM,100,"SYS","系统",true,"移出")));
        assertThatThrownBy(() -> generation.newTaskAssignment(actor,70,2,100L,true)).isInstanceOf(BusinessException.class).hasMessageContaining("移出");
    }
    @Test void 后补任务非负责人不能查询分工且不合格公共负责人留空() {
        var other = new AuthUser(99,7,"other","test","其他人",1,true);
        assertThatThrownBy(() -> generation.newTaskAssignment(other,70,2,null,false)).isInstanceOf(BusinessException.class);
        assertThat(generation.newTaskAssignment(actor,70,2,null,false).ownerUserId()).isNull();
    }
}
