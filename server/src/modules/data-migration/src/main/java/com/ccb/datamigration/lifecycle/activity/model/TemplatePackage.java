package com.ccb.datamigration.lifecycle.activity.model;

import java.util.List;

/** 活动模板导入导出包（JSON 通路，不含项目/组件/人员实例数据；依赖边按工序 seq 定位）。 */
public record TemplatePackage(String templateName, String activityType, String granularity, Long lifecycleStageId,
                              String lifecycleStageCode, String scene, String goal, String overallEntryCond,
                              String overallExitDesc, String overallDeliverables, List<ProcessInput> processes,
                              List<EdgeInput> edges) {
    public record EdgeInput(int sourceSeq, int targetSeq) {
    }
}
