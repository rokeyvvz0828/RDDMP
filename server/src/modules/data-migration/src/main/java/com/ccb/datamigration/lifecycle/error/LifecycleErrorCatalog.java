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
        register(LifecycleErrorCode.TASK_ACTIVITY_NOT_ACTIVE, "仅启用活动可下发任务，停用/作废模板禁止生成新任务");
        register(LifecycleErrorCode.TASK_PROJECT_COMPONENT_FORBIDDEN, "项目级活动/专题禁止携带组件下发");
        register(LifecycleErrorCode.TASK_COMPONENT_REQUIRED, "组件级活动/专题必须勾选至少 1 个有效组件");
        register(LifecycleErrorCode.TASK_COMPONENT_NOT_IN_SCOPE, "勾选组件必须在活动/专题关联组件范围内");
        register(LifecycleErrorCode.TASK_COMPONENT_DISABLED, "停用组件不参与任务下发");
        register(LifecycleErrorCode.TASK_PLAN_FINISH_TIME_REQUIRED, "任务下发时必须填写计划完成时间");
        register(LifecycleErrorCode.TASK_PLAN_FINISH_TIME_IN_PAST, "计划完成时间不得早于当前时间");
        register(LifecycleErrorCode.TASK_ACTIVITY_SNAPSHOT_MISSING, "活动尚未发布快照，禁止下发任务");
        register(LifecycleErrorCode.TASK_EXECUTOR_INACTIVE, "执行人仅可从已激活成员中选取");
        register(LifecycleErrorCode.TASK_NOT_FOUND, "任务不存在");
        register(LifecycleErrorCode.TASK_TERMINAL_READONLY, "已闭环/已归档任务禁止编辑");
        register(LifecycleErrorCode.ORDER_NOT_FOUND, "工单不存在");
        register(LifecycleErrorCode.ORDER_STATUS_IMMUTABLE, "工单状态禁止人工直改（唯一驱动因子=执行反馈+审核结果）");
        register(LifecycleErrorCode.ORDER_NO_ACTIVE_FLOW, "仅流转中工单可执行暂停/重启等干预操作");
        register(LifecycleErrorCode.ORDER_SUSPEND_BEFORE_MISSING, "暂停态必须记录暂停前状态作为恢复回退依据");
        register(LifecycleErrorCode.ORDER_TRANSFER_REASON_REQUIRED, "转交必须填写原因并留痕");
        register(LifecycleErrorCode.ORDER_TRANSFER_SAME_MEMBER, "转交目标不能与当前执行人相同");
        register(LifecycleErrorCode.ORDER_RESTART_NOT_ALLOWED, "异常重启仅数据迁移管理员可对锁死异常工单执行");
        register(LifecycleErrorCode.ORDER_SLA_REASON_REQUIRED, "时效调整/豁免必须填写原因并留痕");
        register(LifecycleErrorCode.ORDER_ARCHIVE_BLOCKED, "存在未闭环或打回未整改工序，禁止归档");
        register(LifecycleErrorCode.ORDER_READONLY_STATE, "已暂停/已作废/已归档工单为只读态，禁止一切写操作");
        register(LifecycleErrorCode.ORDER_PROCESS_NOT_FOUND, "工单工序实例不存在");
        register(LifecycleErrorCode.ORDER_PRECONDITION_OPEN, "前置工序未闭环，后置工序强制锁止");
        register(LifecycleErrorCode.ORDER_DELIVERABLE_MISSING, "交付物缺失，工序流转被锁止");
        register(LifecycleErrorCode.ORDER_AUDIT_REJECTED, "审核未通过，后置工序锁止");
        register(LifecycleErrorCode.ORDER_SUBMIT_ALREADY_REVIEWING, "工序已在审核中，重复提审被拒");
        register(LifecycleErrorCode.FEEDBACK_READONLY_STATE, "工单处于只读态（已暂停/已作废/已归档），禁止反馈写操作");
        register(LifecycleErrorCode.FEEDBACK_NO_AUDIT_STAGE, "本工序配置为无需审核，无审核环节");
        register(LifecycleErrorCode.FEEDBACK_DUPLICATE_SUBMIT, "工序已提审，重复提审被拒");
        register(LifecycleErrorCode.FEEDBACK_PROCESS_LOCKED, "工序未解锁，禁止填写反馈");
        register(LifecycleErrorCode.FEEDBACK_CLOSED_LOCKED, "工序已闭环，执行内容锁定禁止再编辑");
        register(LifecycleErrorCode.FEEDBACK_EXECUTOR_ONLY, "仅工单执行人/参与人可编辑反馈内容");
        register(LifecycleErrorCode.FEEDBACK_PROGRESS_REQUIRED, "工序执行进度为必填项，空白禁止提交");
        register(LifecycleErrorCode.FEEDBACK_EXIT_REQUIRED, "准出标准内容为必填项");
        register(LifecycleErrorCode.FEEDBACK_DELIVERABLE_REQUIRED, "需提交交付件工序：必选交付物缺失禁止提交");
        register(LifecycleErrorCode.FEEDBACK_ISSUE_FIELDS_REQUIRED, "问题上报必填三项：标题/描述/发生场景");
        register(LifecycleErrorCode.FEEDBACK_RISK_FIELDS_REQUIRED, "风险上报必填五项：标题/等级/描述/概率/影响范围");
        register(LifecycleErrorCode.FEEDBACK_ADMIN_FORBIDDEN, "管理员不得代填执行反馈，仅执行人/参与人可编辑");
        register(LifecycleErrorCode.AUDIT_PERMISSION_DENIED, "仅审核角色可执行审核，管理员不得代审");
        register(LifecycleErrorCode.AUDIT_OPINION_REQUIRED, "审核意见必填（通过=合规验收结论；打回=问题定位与不合规点）");
        register(LifecycleErrorCode.AUDIT_RECTIFY_REQUIRED, "打回整改要求必填（整改内容/整改标准/补充资料/复审条件）");
        register(LifecycleErrorCode.AUDIT_PROCESS_NOT_REVIEWING, "工序不在审核中，无法写入审核结果");
        register(LifecycleErrorCode.AUDIT_BATCH_LIMIT, "批量审核单批上限 50 条，超出需分批执行");
        register(LifecycleErrorCode.AUDIT_BATCH_REASON_REQUIRED, "批量打回必须填写统一打回原因与整改要求");
        register(LifecycleErrorCode.AUDIT_BATCH_CONFIRM_REQUIRED, "批量打回必须二次确认（工单数量与清单一致）");
        register(LifecycleErrorCode.AUDIT_REVOKE_WINDOW_EXPIRED, "批量打回撤销窗口（10 分钟）已过，不可撤销");
        register(LifecycleErrorCode.AUDIT_REVOKE_SUBMITTED_AGAIN, "已重新提审的工单不可整批撤销");
        register(LifecycleErrorCode.AUDIT_RECORD_NOT_FOUND, "审核记录不存在");
        register(LifecycleErrorCode.AUDIT_ORDER_NOT_FOUND, "待审工单不存在");
        register(LifecycleErrorCode.AUDIT_CLOSED_LOCKED, "工序已闭环，审核记录固化不可删改");
        register(LifecycleErrorCode.ISSUE_NOT_FOUND, "问题不存在");
        register(LifecycleErrorCode.ISSUE_CODE_DUPLICATE, "问题编码重复");
        register(LifecycleErrorCode.ISSUE_FIELD_REQUIRED, "必填字段缺失（批量导入/新增）");
        register(LifecycleErrorCode.ISSUE_STATUS_INVALID, "问题状态非法，仅 4 态枚举");
        register(LifecycleErrorCode.ISSUE_CLOSED_READONLY, "已闭环问题禁止修改、删除");
        register(LifecycleErrorCode.ISSUE_CANCELLED_READONLY, "已作废问题禁止修改");
        register(LifecycleErrorCode.ISSUE_RECTIFY_PERMISSION_DENIED, "无整改权限：仅执行人/负责人提交整改进度");
        register(LifecycleErrorCode.ISSUE_ADMIN_ONLY, "仅数据迁移管理员拥有台账/知识维护权限");
        register(LifecycleErrorCode.ISSUE_IMPORT_INVALID, "批量导入校验失败，异常明细见响应");
        register(LifecycleErrorCode.ISSUE_SYNC_CONFLICT, "对账同步与上报单归属冲突，拒绝归集");
        register(LifecycleErrorCode.KNOWLEDGE_NOT_FOUND, "知识条目不存在");
        register(LifecycleErrorCode.KNOWLEDGE_DELETE_FORBIDDEN, "知识条目禁止删除，仅可标注失效");
        register(LifecycleErrorCode.KNOWLEDGE_ADMIN_ONLY, "知识条目编辑/合并/下线仅数据迁移管理员");
        register(LifecycleErrorCode.KNOWLEDGE_TAG_NOT_FOUND, "知识标签不存在");
        register(LifecycleErrorCode.ISSUE_CLOSE_SOLUTION_REQUIRED, "闭环前必须填写标准化解决方案");
        register(LifecycleErrorCode.RISK_NOT_FOUND, "风险不存在");
        register(LifecycleErrorCode.RISK_CODE_DUPLICATE, "风险编码重复");
        register(LifecycleErrorCode.RISK_LEVEL_SINGLE, "风险等级仅可配置单一值");
        register(LifecycleErrorCode.RISK_PROBABILITY_SINGLE, "发生概率仅可配置单一值");
        register(LifecycleErrorCode.RISK_STATUS_INVALID, "风险状态非法或三态互斥冲突");
        register(LifecycleErrorCode.RISK_CLOSED_ARCHIVED, "已闭环风险固化归档，禁止修改删除");
        register(LifecycleErrorCode.RISK_STRATEGY_PERMISSION_DENIED, "风险策略维护仅管理员/项目负责人");
        register(LifecycleErrorCode.RISK_PREVENT_PERMISSION_DENIED, "无风险防控操作权限");
        register(LifecycleErrorCode.RISK_CANCEL_FORBIDDEN, "普通用户不可作废风险");
        register(LifecycleErrorCode.RISK_STRATEGY_NOT_FOUND, "策略库条目不存在");
        register(LifecycleErrorCode.RISK_STRATEGY_DELETE_FORBIDDEN, "策略库条目禁止删除，仅可下线");
        register(LifecycleErrorCode.RISK_SYNC_CONFLICT, "风险对账同步与上报单归属冲突，拒绝归集");
        register(LifecycleErrorCode.RISK_FIELD_REQUIRED, "风险必填字段缺失");
        register(LifecycleErrorCode.RISK_IMPORT_INVALID, "批量归集数据校验失败，异常明细见响应");
        register(LifecycleErrorCode.DASHBOARD_DEADLINE_VIEW_MISMATCH, "看板实时时效视图与工单冗余字段不一致，拒绝出数");
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
