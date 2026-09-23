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

    private LifecycleErrorCode() {
    }
}
