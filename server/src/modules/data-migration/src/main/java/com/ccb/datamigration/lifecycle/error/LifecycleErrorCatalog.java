package com.ccb.datamigration.lifecycle.error;

import java.util.LinkedHashMap;
import java.util.Map;

/** 错误码 1:1 文案注册表（铁律 #12：枚举值与前端文案 1:1，禁止同值异名 / 异名同值）。 */
public final class LifecycleErrorCatalog {
    private static final Map<Integer, String> MESSAGES = new LinkedHashMap<>();

    static {
        register(LifecycleErrorCode.DATA_SCOPE_FORBIDDEN, "无该数据范围的操作权限");
        register(LifecycleErrorCode.OPTION_SOURCE_UNAVAILABLE, "选项数据源不可用");
        register(LifecycleErrorCode.MEMBER_INACTIVE, "人员必须为已激活成员");
        register(LifecycleErrorCode.COMPONENT_DISABLED, "停用组件不得参与新任务下发");
        register(LifecycleErrorCode.ACTIVITY_OBSOLETE_READONLY, "已作废活动只读，禁止任何写操作");
        register(LifecycleErrorCode.EXPORT_INSTANCE_DATA_LEAK, "导出模板包不得携带项目/组件/人员实例数据");
        register(LifecycleErrorCode.OWNER_INACTIVE, "负责人必须为已激活成员且仅可绑定 1 名");
        register(LifecycleErrorCode.ACTIVITY_NOT_FOUND, "活动不存在");
        register(LifecycleErrorCode.ACTIVITY_NAME_DUPLICATE, "活动名称冲突，请确认是否覆盖");
        register(LifecycleErrorCode.GRANULARITY_IMMUTABLE, "活动颗粒度创建后不可变更");
        register(LifecycleErrorCode.COMPONENT_BINDING_MISMATCH, "组件级活动必须绑定组件、项目级活动禁止绑定组件");
        register(LifecycleErrorCode.ACTIVITY_INACTIVE_NO_PROCESS, "停用活动不允许新增工序");
        register(LifecycleErrorCode.ACTIVITY_INACTIVE_NO_DISPATCH, "停用/作废活动不允许下发新任务或聚合引用");
        register(LifecycleErrorCode.LIFE_STAGE_REQUIRED_FOR_NORMAL, "普通基础活动必须归属生命周期阶段");
        register(LifecycleErrorCode.ACTIVITY_CODE_CONFLICT, "活动编码冲突，请重试");
        register(LifecycleErrorCode.PROCESS_NOT_FOUND, "工序不存在");
        register(LifecycleErrorCode.PROCESS_NAME_DUPLICATE, "工序名称在同活动内必须唯一");
        register(LifecycleErrorCode.PROCESS_SEQ_INVALID, "工序序号必须连续 1..n 且不重复");
        register(LifecycleErrorCode.PROCESS_MIN_ONE, "活动至少保留 1 道工序");
        register(LifecycleErrorCode.EXIT_TRIAD_INCOMPLETE, "准出三要素（准出内容/准出交付物清单/合格判定规则）齐备方可发布");
        register(LifecycleErrorCode.TOPOLOGY_SELF_LOOP, "禁止自连依赖");
        register(LifecycleErrorCode.TOPOLOGY_DUPLICATE_EDGE, "禁止重复依赖连线");
        register(LifecycleErrorCode.TOPOLOGY_CYCLE, "禁止成环依赖");
        register(LifecycleErrorCode.TOPOLOGY_MISSING_NODE, "禁止引用缺失节点");
        register(LifecycleErrorCode.TOPOLOGY_LIMIT_EXCEEDED, "单活动工序数不得超过 30 节点");
        register(LifecycleErrorCode.TOPOLOGY_NOPRE_CONFLICT, "有入边仍标记无前置任务冲突");
        register(LifecycleErrorCode.SNAPSHOT_VERSION_MISMATCH, "快照版本号与外层记录版本号必须恒等");
        register(LifecycleErrorCode.TEMPLATE_INVALID_JSON, "模板包 JSON 格式错误");
        register(LifecycleErrorCode.TEMPLATE_FIELD_INCOMPLETE, "模板字段完整性校验失败");
        register(LifecycleErrorCode.TEMPLATE_GRANULARITY_MISMATCH, "模板颗粒度一致性校验失败");
        register(LifecycleErrorCode.TEMPLATE_DEPENDENCY_INVALID, "模板工序依赖合法性校验失败");
        register(LifecycleErrorCode.TEMPLATE_EXPORT_FORBIDDEN, "模板导入导出仅数据迁移管理员拥有");
        register(LifecycleErrorCode.TOPIC_GRANULARITY_MISMATCH, "专题聚合活动仅可聚合同颗粒度普通活动");
    }

    private LifecycleErrorCatalog() {
    }

    private static void register(int code, String message) {
        if (MESSAGES.containsKey(code)) {
            throw new IllegalStateException("错误码重复定义：" + code);
        }
        if (MESSAGES.containsValue(message)) {
            throw new IllegalStateException("错误码文案重复：" + message);
        }
        MESSAGES.put(code, message);
    }

    /** 返回错误码唯一文案；未注册时抛出（强制 1:1，禁止静默返回 null）。 */
    public static String message(int code) {
        String message = MESSAGES.get(code);
        if (message == null) {
            throw new IllegalArgumentException("错误码未注册文案：" + code);
        }
        return message;
    }

    public static Map<Integer, String> all() {
        return Map.copyOf(MESSAGES);
    }
}
