package com.ccb.datamigration.lifecycle.activity.model;

import java.util.List;

/** 拓扑视图（配置工作台 topology/process 页签数据）。 */
public record TopologyView(long activityId, String topologyVersion, List<ProcessView> processes, List<EdgeView> edges,
                           List<String> warnings) {
    public record EdgeView(long sourceProcessId, long targetProcessId) {
    }
}
