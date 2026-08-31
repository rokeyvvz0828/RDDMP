package com.ccb.architecture.plan.service;

import com.ccb.architecture.plan.model.PlanTemplateModels.CheckItemDraft;
import com.ccb.architecture.plan.model.PlanTemplateModels.CreateTemplateCommand;
import com.ccb.architecture.plan.model.PlanTemplateModels.Dimension;
import com.ccb.architecture.plan.model.PlanTemplateModels.PlanTemplate;
import com.ccb.architecture.plan.model.PlanTemplateModels.StageCommand;
import com.ccb.architecture.plan.model.PlanTemplateModels.StageDraft;
import com.ccb.architecture.plan.model.PlanTemplateModels.TaskTemplateCommand;
import com.ccb.architecture.plan.model.PlanTemplateModels.TaskTemplateDraft;
import com.ccb.architecture.plan.model.PlanTemplateModels.TemplateStatus;
import com.ccb.architecture.plan.persistence.PlanTemplateStore;
import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 计划模板业务规则（REQ-20260830-056）。 */
@ExtendWith(MockitoExtension.class)
class PlanTemplateServiceTest {
    private static final AuthUser ACTOR = new AuthUser(9L, 7L, "admin", "hash", "模板管理员", 11L, true);

    @Mock
    private PlanTemplateStore store;

    private final AtomicLong ids = new AtomicLong(100_000L);
    private PlanTemplateService service;

    @BeforeEach
    void setUp() {
        service = new PlanTemplateService(store, new ObjectMapper(), ids::incrementAndGet);
        lenient().when(store.findTemplate(7L, 1L)).thenReturn(Optional.of(
                new PlanTemplate(1L, "示例模板", null, TemplateStatus.DRAFT, 1, 0, 9L, 9L)));
    }

    @Test
    void createDraftValidatesName() {
        assertThatThrownBy(() -> service.createDraft(ACTOR, new CreateTemplateCommand(" ", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板名称");
        assertThatThrownBy(() -> service.createDraft(ACTOR, new CreateTemplateCommand(null, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void createDraftStoresTemplateAndActivity() {
        when(store.findTemplate(7L, 100001L)).thenReturn(Optional.of(
                new PlanTemplate(100001L, "SIT 环境搭建模板", "说明", TemplateStatus.DRAFT, 0, 0, 9L, 9L)));
        PlanTemplate template = service.createDraft(ACTOR,
                new CreateTemplateCommand("SIT 环境搭建模板", "说明"));
        assertThat(template.id()).isEqualTo(100001L);
        assertThat(template.status()).isEqualTo(TemplateStatus.DRAFT);
        verify(store).insertTemplate(eq(7L), org.mockito.ArgumentMatchers.any());
        verify(store).insertActivity(eq(7L), anyLong(), eq("TEMPLATE"), eq(100001L), eq("TEMPLATE"),
                eq(100001L), eq("TEMPLATE_CREATED"), eq(9L), eq(null), eq(null),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void publishRejectsEmptyStageList() {
        when(store.lockTemplate(7L, 1L)).thenReturn(Optional.of(
                new PlanTemplate(1L, "示例模板", null, TemplateStatus.ACTIVE, 1, 0, 9L, 9L)));
        when(store.findStages(7L, 1L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.publish(ACTOR, 1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要一个环节");
    }

    @Test
    void publishRejectsStageWithoutTaskTemplate() {
        when(store.lockTemplate(7L, 1L)).thenReturn(Optional.of(
                new PlanTemplate(1L, "示例模板", null, TemplateStatus.ACTIVE, 1, 0, 9L, 9L)));
        when(store.findStages(7L, 1L)).thenReturn(List.of(
                new StageDraft(11L, "资源下发", 1, List.of())));
        assertThatThrownBy(() -> service.publish(ACTOR, 1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要一个任务模板");
    }

    @Test
    void publishCreatesVersionSnapshot() {
        when(store.lockTemplate(7L, 1L)).thenReturn(Optional.of(
                new PlanTemplate(1L, "示例模板", null, TemplateStatus.ACTIVE, 1, 0, 9L, 9L)));
        when(store.findStages(7L, 1L)).thenReturn(List.of(
                new StageDraft(11L, "资源下发", 1, List.of(
                        new TaskTemplateDraft(21L, 1L, "下发资源", Dimension.DEPLOYMENT_UNIT,
                                List.of(new CheckItemDraft("机器就绪", 1),
                                        new CheckItemDraft("IP 分配", 2)),
                                TemplateStatus.DRAFT, 0, 0)))));
        when(store.findTaskTemplates(7L, null, 11L)).thenReturn(List.of(
                new TaskTemplateDraft(21L, 1L, "下发资源", Dimension.DEPLOYMENT_UNIT,
                        List.of(new CheckItemDraft("机器就绪", 1), new CheckItemDraft("IP 分配", 2)),
                        TemplateStatus.DRAFT, 0, 0)));
        when(store.latestTaskTemplateVersion(7L, 21L)).thenReturn(0);
        when(store.findTemplateVersion(7L, 1L, 2)).thenReturn(Optional.of(
                new com.ccb.architecture.plan.model.PlanTemplateModels.TemplateVersion(31L, 1L, 2,
                        "[]", "首版", 9L, java.time.LocalDateTime.of(2026, 8, 30, 9, 0))));
        service.publish(ACTOR, 1L, "首版");
        verify(store).insertTemplateVersion(eq(7L), anyLong(), eq(1L), eq(2),
                org.mockito.ArgumentMatchers.contains("下发资源"), eq("首版"), eq(9L));
        verify(store).insertTaskTemplateVersion(eq(7L), anyLong(), eq(21L), eq(1), eq("下发资源"),
                eq(Dimension.DEPLOYMENT_UNIT), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), eq(9L));
        verify(store).updateTemplateLatestVersion(7L, 1L, 2, 9L);
    }

    @Test
    void changeStatusRequiresPublishedTemplate() {
        long draftId = 99L;
        when(store.findTemplate(7L, draftId)).thenReturn(Optional.of(
                new PlanTemplate(draftId, "未发布", null, TemplateStatus.DRAFT, 0, 0, 9L, 9L)));
        assertThatThrownBy(() -> service.changeStatus(ACTOR, draftId, TemplateStatus.ACTIVE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未发布");
    }

    @Test
    void validateCheckItemsRejectsEmptyOrDuplicate() {
        when(store.findStages(7L, 1L)).thenReturn(List.of(
                new StageDraft(11L, "资源下发", 1, List.of())));
        assertThatThrownBy(() -> service.addTaskTemplate(ACTOR, 1L, 11L,
                new TaskTemplateCommand("任务", Dimension.NONE, List.of(), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("检查项");
        assertThatThrownBy(() -> service.addTaskTemplate(ACTOR, 1L, 11L,
                new TaskTemplateCommand("任务", Dimension.NONE,
                        List.of(new CheckItemDraft("重复", 1), new CheckItemDraft("重复", 2)), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复");
    }

    @Test
    void setStageDependenciesOnlyRebuildsOwnPredecessors() {
        // 环节 A(11) 依赖 B(12)，B(12) 前置为空；重设 B 的前置时不得触碰「A 依赖 B」的记录。
        PlanTemplateService.StageRef stageA = new PlanTemplateService.StageRef(11L, 1L, "A", 1, null, null);
        PlanTemplateService.StageRef stageB = new PlanTemplateService.StageRef(12L, 1L, "B", 2, null, null);
        when(store.findStageRef(7L, 12L)).thenReturn(Optional.of(stageB));
        when(store.findStageRef(7L, 13L)).thenReturn(Optional.of(new PlanTemplateService.StageRef(13L, 1L, "C", 3, null, null)));
        when(store.findStageDependencies(7L, 1L)).thenReturn(List.<Long[]>of(new Long[]{11L, 12L}));
        service.setStageDependencies(ACTOR, 12L, List.of(13L));
        // 关键断言：删除请求只针对「B 作为后续」的记录，不包含「A 依赖 B」
        verify(store).deleteStageDependencies(7L, 12L);
        verify(store).insertStageDependency(eq(7L), anyLong(), eq(1L), eq(12L), eq(13L), eq(9L));
        verify(store, org.mockito.Mockito.times(1)).insertStageDependency(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void setStageDependenciesRejectsSelfAndOtherTemplate() {
        when(store.findStageRef(7L, 12L)).thenReturn(Optional.of(new PlanTemplateService.StageRef(12L, 1L, "B", 2, null, null)));
        assertThatThrownBy(() -> service.setStageDependencies(ACTOR, 12L, List.of(12L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能依赖自身");
        when(store.findStageRef(7L, 99L)).thenReturn(Optional.of(new PlanTemplateService.StageRef(99L, 2L, "其他模板环节", 1, null, null)));
        assertThatThrownBy(() -> service.setStageDependencies(ACTOR, 12L, List.of(99L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同一模板");
    }

    @Test
    void setTaskTemplateDependenciesOnlyRebuildsOwnPredecessors() {
        // 任务 T1(21) 依赖 T2(22)（同环节 11）；重设 T2 的前置时不得触碰「T1 依赖 T2」。
        when(store.findTaskTemplate(7L, 22L)).thenReturn(Optional.of(new TaskTemplateDraft(22L, 1L, "T2", Dimension.NONE,
                List.of(new CheckItemDraft("检查", 1)), TemplateStatus.DRAFT, 0, 0)));
        when(store.stageIdOfTaskTemplate(7L, 22L)).thenReturn(11L);
        when(store.findTaskTemplates(7L, null, 11L)).thenReturn(List.of(
                new TaskTemplateDraft(21L, 1L, "T1", Dimension.NONE,
                        List.of(new CheckItemDraft("检查", 1)), TemplateStatus.DRAFT, 0, 0),
                new TaskTemplateDraft(22L, 1L, "T2", Dimension.NONE,
                        List.of(new CheckItemDraft("检查", 1)), TemplateStatus.DRAFT, 0, 0),
                new TaskTemplateDraft(23L, 1L, "T3", Dimension.NONE,
                        List.of(new CheckItemDraft("检查", 1)), TemplateStatus.DRAFT, 0, 0)));
        when(store.findTaskTemplateDependencies(7L, null, 11L))
                .thenReturn(List.<Long[]>of(new Long[]{21L, 22L}));
        service.setTaskTemplateDependencies(ACTOR, 22L, List.of(23L));
        // 删除仅针对「T2 作为后续」的记录
        verify(store).deleteTaskTemplateDependencies(7L, null, 11L, 22L);
        verify(store).insertTaskTemplateDependency(eq(7L), anyLong(), eq(1L), eq(11L),
                eq(22L), eq(23L), eq(9L));
        verify(store, org.mockito.Mockito.times(1)).insertTaskTemplateDependency(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void setTaskTemplateDependenciesRejectsCrossStageAndDimensionMismatch() {
        when(store.findTaskTemplate(7L, 22L)).thenReturn(Optional.of(new TaskTemplateDraft(22L, 1L, "T2", Dimension.PHYSICAL_SUBSYSTEM,
                List.of(new CheckItemDraft("检查", 1)), TemplateStatus.DRAFT, 0, 0)));
        when(store.stageIdOfTaskTemplate(7L, 22L)).thenReturn(11L);
        when(store.findTaskTemplates(7L, null, 11L)).thenReturn(List.of(
                new TaskTemplateDraft(22L, 1L, "T2", Dimension.PHYSICAL_SUBSYSTEM,
                        List.of(new CheckItemDraft("检查", 1)), TemplateStatus.DRAFT, 0, 0),
                new TaskTemplateDraft(23L, 1L, "T3", Dimension.DEPLOYMENT_UNIT,
                        List.of(new CheckItemDraft("检查", 1)), TemplateStatus.DRAFT, 0, 0)));
        assertThatThrownBy(() -> service.setTaskTemplateDependencies(ACTOR, 22L, List.of(99L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同一环节");
        assertThatThrownBy(() -> service.setTaskTemplateDependencies(ACTOR, 22L, List.of(23L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同生成维度");
    }
}
