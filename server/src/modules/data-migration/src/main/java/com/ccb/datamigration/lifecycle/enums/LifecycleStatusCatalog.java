package com.ccb.datamigration.lifecycle.enums;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 全系统状态枚举目录（基线文档第 3 章唯一基准的代码侧注册表）：供 1:1 检查与可达性自检遍历（铁律 #11/#18）。 */
public final class LifecycleStatusCatalog {
    public record StatusEntry(String code, String displayName, boolean terminal) {
    }

    public record Category(String name, String description, List<StatusEntry> entries) {
    }

    private static final Map<String, List<? extends LifecycleStatus>> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("工单整体状态（9 态，3.2）", List.of(WorkOrderStatus.values()));
        CATEGORIES.put("工序节点状态（5 态，3.3）", List.of(ProcessNodeStatus.values()));
        CATEGORIES.put("活动状态（3 态，3.4）", List.of(ActivityStatus.values()));
        CATEGORIES.put("问题状态（4 态，3.5）", List.of(IssueStatus.values()));
        CATEGORIES.put("风险状态（6 态，3.6）", List.of(RiskStatus.values()));
        CATEGORIES.put("审核状态（4 态，3.7）", List.of(AuditStatus.values()));
        CATEGORIES.put("时效标记（3 档，3.8）", List.of(TimelinessMark.values()));
        CATEGORIES.put("工序配置状态（2 态，3.9）", List.of(ProcessConfigStatus.values()));
    }

    private LifecycleStatusCatalog() {
    }

    /** 全部状态类别（含每个值的 code/displayName/终态标记），用于 1:1 与可达性自检。 */
    public static List<Category> all() {
        List<Category> categories = new ArrayList<>();
        CATEGORIES.forEach((name, statuses) -> {
            List<StatusEntry> entries = statuses.stream()
                    .map(status -> new StatusEntry(status.code(), status.displayName(), status.terminal()))
                    .toList();
            categories.add(new Category(name, resolveDescription(name), entries));
        });
        return categories;
    }

    private static String resolveDescription(String name) {
        if (name.contains("工单")) {
            return "任务流转引擎、任务审核、数据看板";
        }
        if (name.contains("工序节点")) {
            return "任务进度反馈、任务审核";
        }
        if (name.contains("活动状态")) {
            return "活动管理、任务发布、数据看板";
        }
        if (name.contains("问题")) {
            return "问题管理、历史问题知识库、数据看板";
        }
        if (name.contains("风险")) {
            return "风险管理、数据看板";
        }
        if (name.contains("审核")) {
            return "任务审核、任务进度反馈";
        }
        if (name.contains("时效")) {
            return "任务流转引擎、数据看板";
        }
        return "活动管理、任务发布";
    }
}
