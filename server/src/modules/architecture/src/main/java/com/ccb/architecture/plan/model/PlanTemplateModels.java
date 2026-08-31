package com.ccb.architecture.plan.model;

import java.util.List;

/** 搭建计划模板领域模型（REQ-20260830-056）。草稿结构可编辑，发布时生成不可变版本快照。 */
public final class PlanTemplateModels {

    public enum TemplateStatus {
        DRAFT, ACTIVE, INACTIVE
    }

    public enum Dimension {
        NONE, PHYSICAL_SUBSYSTEM, DEPLOYMENT_UNIT
    }

    public record CheckItemDraft(String name, int sortNo, String guide) {
        public CheckItemDraft(String name, int sortNo) {
            this(name, sortNo, null);
        }
    }

    public record TaskTemplateDraft(Long id, long templateId, String name, Dimension dimension,
                                    List<CheckItemDraft> checkItems, TemplateStatus status,
                                    int latestVersionNo, long rowVersion) {
    }

    public record StageDraft(Long id, String name, int sortNo, Integer startOffsetDays,
                             Integer durationDays, List<TaskTemplateDraft> tasks) {
        public StageDraft(Long id, String name, int sortNo, List<TaskTemplateDraft> tasks) {
            this(id, name, sortNo, null, null, tasks);
        }
    }

    /** 计划模板主记录（当前草稿结构 + 已发布版本信息）。 */
    public record PlanTemplate(Long id, String name, String description, TemplateStatus status,
                               int latestVersionNo, long rowVersion, long createdBy, long updatedBy) {
    }

    /** 模板详情：草稿结构 + 依赖 + 已发布版本列表。 */
    public record PlanTemplateDetail(PlanTemplate template, List<StageDraft> stages,
                                     List<Long[]> stageDependencies, List<Long[]> taskDependencies,
                                     List<TemplateVersion> versions) {
        public PlanTemplateDetail(PlanTemplate template, List<StageDraft> stages,
                                  List<TemplateVersion> versions) {
            this(template, stages, List.of(), List.of(), versions);
        }
    }

    /** 模板已发布版本快照（不可变）。 */
    public record TemplateVersion(Long id, long templateId, int versionNo, String contentJson,
                                  String note, long publishedBy, java.time.LocalDateTime publishedAt) {
    }

    /** 版本快照中的任务模板结构（解析自 content_json）。 */
    public record SnapshotTask(Long taskTemplateId, int taskTemplateVersionNo, String name,
                               Dimension dimension, List<CheckItemDraft> checkItems,
                               List<Long> dependsOnTaskTemplateIds) {
    }

    /** 版本快照中的环节结构。 */
    public record SnapshotStage(Long stageId, String stageName, int sortNo, Integer startOffsetDays,
                                Integer durationDays, List<SnapshotTask> tasks,
                                List<Long> dependencyStageIds) {
    }

    public record CreateTemplateCommand(String name, String description) {
    }

    public record UpdateTemplateCommand(String name, String description, Long rowVersion) {
    }

    public record StageCommand(String name, Integer sortNo, Integer startOffsetDays, Integer durationDays) {
        public StageCommand(String name, Integer sortNo) {
            this(name, sortNo, null, null);
        }
    }

    public record TaskTemplateCommand(String name, Dimension dimension, List<CheckItemDraft> checkItems,
                                      Long rowVersion) {
    }

    private PlanTemplateModels() {
    }
}
