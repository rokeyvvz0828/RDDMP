package com.ccb.datamigration.lifecycle.activity.model;

import java.util.List;

/** 拓扑保存入参：工序（含排序/配置） + 依赖连线（sourceProcessId -> targetProcessId）。 */
public record TopologyInput(List<ProcessInput> processes, List<EdgeInput> edges) {
    public record EdgeInput(long sourceProcessId, long targetProcessId) {
    }
}
