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
}
