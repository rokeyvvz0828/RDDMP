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
    /** ===== 452xx 任务发布域（基线第 10 章，T4） ===== */
    /** 仅启用（ACTIVE）活动可下发；停用/作废模板禁止生成新任务。 */
    public static final int TASK_ACTIVITY_NOT_ACTIVE = 45201;
    /** 项目级活动/专题禁止携带组件（颗粒度强约束）。 */
    public static final int TASK_PROJECT_COMPONENT_FORBIDDEN = 45202;
    /** 组件级活动/专题必须勾选至少 1 个有效组件。 */
    public static final int TASK_COMPONENT_REQUIRED = 45203;
    /** 勾选组件须在活动/专题关联组件范围内（交集拆分前提）。 */
    public static final int TASK_COMPONENT_NOT_IN_SCOPE = 45204;
    /** 停用组件不参与下发（deleted=0 视为可用）。 */
    public static final int TASK_COMPONENT_DISABLED = 45205;
    /** 任务下发时计划完成时间必填。 */
    public static final int TASK_PLAN_FINISH_TIME_REQUIRED = 45206;
    /** 计划完成时间不得早于当前时间。 */
    public static final int TASK_PLAN_FINISH_TIME_IN_PAST = 45207;
    /** 活动未发布（无快照）禁止下发。 */
    public static final int TASK_ACTIVITY_SNAPSHOT_MISSING = 45208;
    /** 执行人仅可从已激活成员中选取。 */
    public static final int TASK_EXECUTOR_INACTIVE = 45209;
    /** 任务不存在。 */
    public static final int TASK_NOT_FOUND = 45210;
    /** 已完结/已归档任务禁止任何编辑操作。 */
    public static final int TASK_TERMINAL_READONLY = 45211;
    /** ===== 453xx 流转引擎域（基线第 11/18 章，T5） ===== */
    /** 工单不存在。 */
    public static final int ORDER_NOT_FOUND = 45301;
    /** 工单状态禁止人工直改（唯一驱动因子=执行反馈+审核结果）。 */
    public static final int ORDER_STATUS_IMMUTABLE = 45302;
    /** 暂停/重启等干预仅限流转中工单（非终态、非已归档）。 */
    public static final int ORDER_NO_ACTIVE_FLOW = 45303;
    /** 暂停态必须携带 suspend_before_status 作为恢复回退依据。 */
    public static final int ORDER_SUSPEND_BEFORE_MISSING = 45304;
    /** 转交必须填写原因并写留痕。 */
    public static final int ORDER_TRANSFER_REASON_REQUIRED = 45305;
    /** 转交目标不能与当前执行人相同。 */
    public static final int ORDER_TRANSFER_SAME_MEMBER = 45306;
    /** 异常重启仅限管理员对锁死异常工单执行。 */
    public static final int ORDER_RESTART_NOT_ALLOWED = 45307;
    /** 时效调整/豁免必须填写原因并留痕。 */
    public static final int ORDER_SLA_REASON_REQUIRED = 45308;
    /** 存在未闭环/打回未整改工序时禁止归档。 */
    public static final int ORDER_ARCHIVE_BLOCKED = 45309;
    /** 三态只读（已暂停/已作废/已归档）：一切写操作拒绝。 */
    public static final int ORDER_READONLY_STATE = 45310;
    /** 工序实例不存在。 */
    public static final int ORDER_PROCESS_NOT_FOUND = 45311;
    /** 前置工序未闭环强制锁止后置工序。 */
    public static final int ORDER_PRECONDITION_OPEN = 45312;
    /** 交付物缺失锁止流转。 */
    public static final int ORDER_DELIVERABLE_MISSING = 45313;
    /** 审核未通过锁止后置工序。 */
    public static final int ORDER_AUDIT_REJECTED = 45314;
    /** 工序已在审核中，重复提审被拒。 */
    public static final int ORDER_SUBMIT_ALREADY_REVIEWING = 45315;
    /** ===== 454xx 进度反馈域（基线第 12 章，T6） ===== */
    /** 只读态（CANCELLED/ARCHIVED/SUSPENDED）：任何反馈写操作一律拒绝。 */
    public static final int FEEDBACK_READONLY_STATE = 45401;
    /** 契约前置：must_audit=false 的工序无审核环节。 */
    public static final int FEEDBACK_NO_AUDIT_STAGE = 45402;
    /** 契约前置：工序已提审，重复提审被拒。 */
    public static final int FEEDBACK_DUPLICATE_SUBMIT = 45403;
    /** 工序锁态：未解锁禁止写反馈。 */
    public static final int FEEDBACK_PROCESS_LOCKED = 45404;
    /** 工序已闭环，内容锁定禁止再编辑。 */
    public static final int FEEDBACK_CLOSED_LOCKED = 45405;
    /** 身份权限：仅执行人/参与人可写反馈。 */
    public static final int FEEDBACK_EXECUTOR_ONLY = 45406;
    /** 基础进度必填，空白禁止保存/提审。 */
    public static final int FEEDBACK_PROGRESS_REQUIRED = 45407;
    /** 准出标准内容必填。 */
    public static final int FEEDBACK_EXIT_REQUIRED = 45408;
    /** 需提交交付件工序：必选交付物缺失禁止提交。 */
    public static final int FEEDBACK_DELIVERABLE_REQUIRED = 45409;
    /** 问题上报三项必填（标题/描述/发生场景）。 */
    public static final int FEEDBACK_ISSUE_FIELDS_REQUIRED = 45410;
    /** 风险上报五项必填（标题/等级/描述/概率/影响范围）。 */
    public static final int FEEDBACK_RISK_FIELDS_REQUIRED = 45411;
    /** 管理员不得代填执行反馈（作业权责主体只能是执行人/参与人）。 */
    public static final int FEEDBACK_ADMIN_FORBIDDEN = 45412;

    /** ===== 455xx 审核管理域（基线第 15 章，T7） ===== */
    /** 仅审核角色可执行审核；管理员可查看全量台账但不得代审。 */
    public static final int AUDIT_PERMISSION_DENIED = 45501;
    /** 审核意见必填（通过填合规验收结论；打回填问题定位与不合规点）。 */
    public static final int AUDIT_OPINION_REQUIRED = 45502;
    /** 打回整改要求为打回状态强制必填（整改内容/整改标准/补充资料/复审条件）。 */
    public static final int AUDIT_RECTIFY_REQUIRED = 45503;
    /** 工序不在审核中，无法写入审核结果。 */
    public static final int AUDIT_PROCESS_NOT_REVIEWING = 45504;
    /** 批量审核单批上限 50 条，超出需分批执行。 */
    public static final int AUDIT_BATCH_LIMIT = 45505;
    /** 批量打回必须填写统一打回原因与整改要求。 */
    public static final int AUDIT_BATCH_REASON_REQUIRED = 45506;
    /** 批量打回必须二次确认（工单数量与清单一致）。 */
    public static final int AUDIT_BATCH_CONFIRM_REQUIRED = 45507;
    /** 批量打回撤销窗口（提交后 10 分钟）已过，不可撤销。 */
    public static final int AUDIT_REVOKE_WINDOW_EXPIRED = 45508;
    /** 已重新提审的工单不可整批撤销。 */
    public static final int AUDIT_REVOKE_SUBMITTED_AGAIN = 45509;
    /** 审核记录不存在。 */
    public static final int AUDIT_RECORD_NOT_FOUND = 45510;
    /** 待审工单不存在。 */
    public static final int AUDIT_ORDER_NOT_FOUND = 45511;
    /** 工序已闭环，审核记录固化不可删改。 */
    public static final int AUDIT_CLOSED_LOCKED = 45512;
    /** ===== 456xx 问题管理 + 历史问题知识库域（基线第 13 章，T8） ===== */
    /** 问题不存在。 */
    public static final int ISSUE_NOT_FOUND = 45601;
    /** 问题编码重复（新增/导入/同步）。 */
    public static final int ISSUE_CODE_DUPLICATE = 45602;
    /** 必填字段缺失（批量导入四类校验之一）。 */
    public static final int ISSUE_FIELD_REQUIRED = 45603;
    /** 问题状态非法（仅 4 态枚举）。 */
    public static final int ISSUE_STATUS_INVALID = 45604;
    /** 已闭环问题禁止修改、删除、作废。 */
    public static final int ISSUE_CLOSED_READONLY = 45605;
    /** 已作废问题禁止修改。 */
    public static final int ISSUE_CANCELLED_READONLY = 45606;
    /** 无整改权限：仅执行人/负责人提交整改进度。 */
    public static final int ISSUE_RECTIFY_PERMISSION_DENIED = 45607;
    /** 仅数据迁移管理员拥有台账维护/知识维护权限。 */
    public static final int ISSUE_ADMIN_ONLY = 45608;
    /** 批量导入校验失败，异常明细随响应返回。 */
    public static final int ISSUE_IMPORT_INVALID = 45609;
    /** 对账同步与上报单归属冲突，拒绝归集。 */
    public static final int ISSUE_SYNC_CONFLICT = 45610;
    /** 知识条目不存在。 */
    public static final int KNOWLEDGE_NOT_FOUND = 45611;
    /** 知识条目禁止物理删除，仅可标注失效（ACTIVE/INVALID）。 */
    public static final int KNOWLEDGE_DELETE_FORBIDDEN = 45612;
    /** 知识条目的编辑/合并/下线仅数据迁移管理员拥有。 */
    public static final int KNOWLEDGE_ADMIN_ONLY = 45613;
    /** 知识标签不存在。 */
    public static final int KNOWLEDGE_TAG_NOT_FOUND = 45614;
    /** 闭环前必须填写标准化解决方案（知识沉淀依据）。 */
    public static final int ISSUE_CLOSE_SOLUTION_REQUIRED = 45615;
    /** ===== 457xx 风险管理 + 风险策略库域（基线第 14 章，T9） ===== */
    /** 风险不存在。 */
    public static final int RISK_NOT_FOUND = 45701;
    /** 风险编码重复（新增/同步）。 */
    public static final int RISK_CODE_DUPLICATE = 45702;
    /** 风险等级仅可配置单一值，禁止多选或组合表达。 */
    public static final int RISK_LEVEL_SINGLE = 45703;
    /** 发生概率仅可配置单一值，禁止多选或组合表达。 */
    public static final int RISK_PROBABILITY_SINGLE = 45704;
    /** 风险状态非法或 6 态互斥冲突（已规避/已发生/已闭环互斥）。 */
    public static final int RISK_STATUS_INVALID = 45705;
    /** 已闭环风险策略/记录/日志固化归档，禁止修改删除。 */
    public static final int RISK_CLOSED_ARCHIVED = 45706;
    /** 风险策略配置/维护仅数据迁移管理员/项目负责人拥有。 */
    public static final int RISK_STRATEGY_PERMISSION_DENIED = 45707;
    /** 无风险防控操作权限（仅执行人/防控责任人）。 */
    public static final int RISK_PREVENT_PERMISSION_DENIED = 45708;
    /** 普通用户不可作废风险。 */
    public static final int RISK_CANCEL_FORBIDDEN = 45709;
    /** 策略库条目不存在。 */
    public static final int RISK_STRATEGY_NOT_FOUND = 45710;
    /** 策略库条目禁止删除，仅可下线（ACTIVE/INVALID）。 */
    public static final int RISK_STRATEGY_DELETE_FORBIDDEN = 45711;
    /** 对账同步与上报单归属冲突，拒绝归集。 */
    public static final int RISK_SYNC_CONFLICT = 45712;
    /** 风险必填字段缺失。 */
    public static final int RISK_FIELD_REQUIRED = 45713;
    /** 批量归集数据校验失败，异常明细随响应返回。 */
    public static final int RISK_IMPORT_INVALID = 45714;
    /** ===== 458xx 数据看板域（基线第 16 章，T10） ===== */
    /** 看板实时时效视图（v_order_deadline_live）与工单冗余字段逐单不一致，拒绝出数（16.2.3.6/16.7 时效对账）。 */
    public static final int DASHBOARD_DEADLINE_VIEW_MISMATCH = 45801;

    private LifecycleErrorCode() {
    }
}
