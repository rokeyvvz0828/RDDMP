package com.ccb.datamigration.lifecycle.error;

/**
 * 生命周期平台专属错误码唯一声明处（铁律 #12：每个校验项唯一错误码、前后端共用命名）。
 * 编号规则：45001 起，与共享 ErrorCode（40000/40100/40300/40900/50000）不冲突。
 * 各业务域按区段扩展：45xxx 横切支持、451xx 活动域、452xx 发布域、453xx 流转域、
 * 454xx 反馈域、455xx 审核域、456xx 问题域、457xx 风险域、458xx 看板域。
 * 批次任务扩展时必须同步：前端 types/data-migration-lifecycle.ts 同名常量与文案、1:1 检查测试。
 */
public final class LifecycleErrorCode {
    /** 无该数据范围的操作权限（四档数据范围拒绝，返回 403 语义）。 */
    public static final int DATA_SCOPE_FORBIDDEN = 45001;
    /** 选项数据源不可用（成员/组件/角色选择器）。 */
    public static final int OPTION_SOURCE_UNAVAILABLE = 45002;
    /** 人员必须为已激活成员（铁律 #5：禁止非激活成员参与任务绑定）。 */
    public static final int MEMBER_INACTIVE = 45003;
    /** 停用组件不得参与新任务下发 / 批量筛选（铁律 #6）。 */
    public static final int COMPONENT_DISABLED = 45004;
    /** 已作废活动只读，禁止任何写操作（三不准，基线文档 9.2.1）。 */
    public static final int ACTIVITY_OBSOLETE_READONLY = 45101;
    /** 导出模板包携带实例数据，禁止导出（脱敏验收口径，基线文档 9.2.6）。 */
    public static final int EXPORT_INSTANCE_DATA_LEAK = 45102;
    /** 负责人必须为已激活成员，且仅可绑定 1 名（铁律 #5 + #12 反例修正）。 */
    public static final int OWNER_INACTIVE = 45103;
    /** 活动不存在（存在性校验，第一段）。 */
    public static final int ACTIVITY_NOT_FOUND = 45110;
    /** 活动名称冲突（导入时提示人工确认是否覆盖）。 */
    public static final int ACTIVITY_NAME_DUPLICATE = 45111;
    /** 活动颗粒度创建后不可变更。 */
    public static final int GRANULARITY_IMMUTABLE = 45112;
    /** 组件级活动必须绑定组件、项目级活动禁止绑定组件（颗粒度绑定互斥）。 */
    public static final int COMPONENT_BINDING_MISMATCH = 45113;
    /** 停用活动不允许新增工序。 */
    public static final int ACTIVITY_INACTIVE_NO_PROCESS = 45114;
    /** 停用/作废活动不允许下发新任务、不允许被专题聚合关联。 */
    public static final int ACTIVITY_INACTIVE_NO_DISPATCH = 45115;
    /** 普通基础活动必须归属生命周期阶段（专题活动非必填）。 */
    public static final int LIFE_STAGE_REQUIRED_FOR_NORMAL = 45116;
    /** 活动编码全局唯一冲突（并发候选重复，需重试）。 */
    public static final int ACTIVITY_CODE_CONFLICT = 45117;
    /** 工序不存在。 */
    public static final int PROCESS_NOT_FOUND = 45120;
    /** 工序名称同活动内唯一。 */
    public static final int PROCESS_NAME_DUPLICATE = 45121;
    /** 工序序号无效或不连续（删除后需重排 1..n）。 */
    public static final int PROCESS_SEQ_INVALID = 45122;
    /** 活动至少保留 1 道工序。 */
    public static final int PROCESS_MIN_ONE = 45123;
    /** 准出三要素（准出内容/准出交付物清单/合格判定规则）必填方可发布。 */
    public static final int EXIT_TRIAD_INCOMPLETE = 45124;
    /** 禁止自连依赖。 */
    public static final int TOPOLOGY_SELF_LOOP = 45130;
    /** 禁止重复依赖。 */
    public static final int TOPOLOGY_DUPLICATE_EDGE = 45131;
    /** 禁止成环依赖。 */
    public static final int TOPOLOGY_CYCLE = 45132;
    /** 禁止引用缺失节点。 */
    public static final int TOPOLOGY_MISSING_NODE = 45133;
    /** 单活动工序数不得超过 30 节点。 */
    public static final int TOPOLOGY_LIMIT_EXCEEDED = 45134;
    /** 有入边仍标记无前置任务冲突（is_no_predecessor 为依赖连线派生值）。 */
    public static final int TOPOLOGY_NOPRE_CONFLICT = 45135;
    /** 快照版本号与外层记录版本号必须 1:1 恒等（铁律 #15）。 */
    public static final int SNAPSHOT_VERSION_MISMATCH = 45136;
    /** 模板包 JSON 格式错误。 */
    public static final int TEMPLATE_INVALID_JSON = 45140;
    /** 模板字段完整性校验失败（四类校验之一）。 */
    public static final int TEMPLATE_FIELD_INCOMPLETE = 45141;
    /** 模板颗粒度一致性校验失败（四类校验之一）。 */
    public static final int TEMPLATE_GRANULARITY_MISMATCH = 45142;
    /** 模板工序依赖合法性校验失败（缺失节点引用/环路，四类校验之一）。 */
    public static final int TEMPLATE_DEPENDENCY_INVALID = 45143;
    /** 模板导入导出仅数据迁移管理员拥有。 */
    public static final int TEMPLATE_EXPORT_FORBIDDEN = 45144;
    /** 专题聚合活动仅可聚合同颗粒度普通活动（专题四道闸门：颗粒度）。 */
    public static final int TOPIC_GRANULARITY_MISMATCH = 45150;

    private LifecycleErrorCode() {
    }
}
