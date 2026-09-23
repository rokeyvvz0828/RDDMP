/** 数据迁移生命周期任务管理平台类型契约（T1：底座选项契约；后续批次补充状态枚举/快照载荷类型）。 */

/** 四档数据范围（与后端 LifecycleDataScope 一一对应）：数据迁移管理员 > 审核人 > 负责人 > 执行人。 */
export type LifecycleDataScope = 'ADMIN' | 'REVIEWER' | 'OWNER' | 'EXECUTOR'

/** 成员选择器选项：仅启用成员（复用平台成员契约）。 */
export interface LifecycleMemberOption {
  id: number
  displayName: string
  username: string
  phone?: string | null
  active: boolean
}

/** 组件选择器选项：仅可用组件（dm_component.deleted=0）。 */
export interface LifecycleComponentOption {
  id: number
  tenantId: number
  projectId: number
  projectCode: string
  projectName: string
  physicalSubsystemCode?: string | null
  systemShortName?: string | null
  systemName?: string | null
  businessGroupName?: string | null
  ownerId: number
}

/** 角色选择器选项：仅启用角色。 */
export interface LifecycleRoleOption {
  id: number
  tenantId: number
  roleCode: string
  roleName: string
}

export interface LifecyclePage<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/** ===== 基线文档第 3 章状态枚举单源声明（后端枚举 code 与前端命名 1:1，禁止扩展/改名） ===== */

export type WorkOrderStatus = 'WAIT_PRE' | 'WAIT_ACCEPT' | 'EXECUTING' | 'SUSPENDED' | 'REVIEWING' | 'REVIEW_REJECTED' | 'CLOSED' | 'ARCHIVED' | 'CANCELLED'
export type ProcessNodeStatus = 'LOCKED' | 'EXECUTING' | 'REVIEWING' | 'REJECTED' | 'CLOSED'
export type ActivityStatus = 'ACTIVE' | 'INACTIVE' | 'OBSOLETE'
export type IssueStatus = 'WAIT_RECTIFY' | 'RECTIFYING' | 'CLOSED' | 'CANCELLED'
export type RiskStatus = 'WAIT_PREVENT' | 'PREVENTING' | 'AVOIDED' | 'OCCURRED' | 'CLOSED' | 'CANCELLED'
export type AuditStatus = 'WAIT_REVIEW' | 'PASSED' | 'REJECTED' | 'RECHECK'
export type TimelinessMark = 'NORMAL' | 'NEAR_OVERDUE' | 'OVERDUE'
export type ProcessConfigStatus = 'DRAFT' | 'READY'

/** 状态枚举 1:1 显示文案（铁律 #2：同一枚举一种文案；类别内不允许同名）。 */
export const LifecycleStatusText: Record<string, string> = {
  WAIT_PRE: '待前置',
  WAIT_ACCEPT: '待接收',
  EXECUTING: '执行中',
  SUSPENDED: '已暂停',
  REVIEWING: '审核中',
  REVIEW_REJECTED: '审核打回',
  CLOSED: '已闭环',
  ARCHIVED: '已归档',
  CANCELLED: '已作废',
  LOCKED: '未解锁',
  REJECTED: '审核打回',
  PASSED: '审核通过',
  RECHECK: '整改复审',
  ACTIVE: '启用',
  INACTIVE: '停用',
  OBSOLETE: '作废',
  WAIT_RECTIFY: '待整改',
  RECTIFYING: '整改中',
  WAIT_PREVENT: '待防控',
  PREVENTING: '防控中',
  AVOIDED: '已规避',
  OCCURRED: '已发生',
  WAIT_REVIEW: '待审核',
  NORMAL: '正常',
  NEAR_OVERDUE: '即将超时',
  OVERDUE: '已超时',
  DRAFT: '草稿',
  READY: '就绪',
}

/** 校验顺序固定（铁律 #12）：存在性 → 数量约束 → 状态约束。 */
export type LifecycleValidationPhase = 'EXISTENCE' | 'QUANTITY' | 'STATE'

/** 生命周期平台错误码声明（与后端 LifecycleErrorCode 同名同值，铁律 #12 前后端共用命名）。 */
export const LifecycleErrorCodes = {
  DATA_SCOPE_FORBIDDEN: 45001,
  OPTION_SOURCE_UNAVAILABLE: 45002,
  MEMBER_INACTIVE: 45003,
  COMPONENT_DISABLED: 45004,
  ACTIVITY_OBSOLETE_READONLY: 45101,
  EXPORT_INSTANCE_DATA_LEAK: 45102,
  OWNER_INACTIVE: 45103,
  ACTIVITY_NOT_FOUND: 45110,
  ACTIVITY_NAME_DUPLICATE: 45111,
  GRANULARITY_IMMUTABLE: 45112,
  COMPONENT_BINDING_MISMATCH: 45113,
  ACTIVITY_INACTIVE_NO_PROCESS: 45114,
  ACTIVITY_INACTIVE_NO_DISPATCH: 45115,
  LIFE_STAGE_REQUIRED_FOR_NORMAL: 45116,
  ACTIVITY_CODE_CONFLICT: 45117,
  PROCESS_NOT_FOUND: 45120,
  PROCESS_NAME_DUPLICATE: 45121,
  PROCESS_SEQ_INVALID: 45122,
  PROCESS_MIN_ONE: 45123,
  EXIT_TRIAD_INCOMPLETE: 45124,
  TOPOLOGY_SELF_LOOP: 45130,
  TOPOLOGY_DUPLICATE_EDGE: 45131,
  TOPOLOGY_CYCLE: 45132,
  TOPOLOGY_MISSING_NODE: 45133,
  TOPOLOGY_LIMIT_EXCEEDED: 45134,
  TOPOLOGY_NOPRE_CONFLICT: 45135,
  SNAPSHOT_VERSION_MISMATCH: 45136,
  TEMPLATE_INVALID_JSON: 45140,
  TEMPLATE_FIELD_INCOMPLETE: 45141,
  TEMPLATE_GRANULARITY_MISMATCH: 45142,
  TEMPLATE_DEPENDENCY_INVALID: 45143,
  TEMPLATE_EXPORT_FORBIDDEN: 45144,
  TOPIC_GRANULARITY_MISMATCH: 45150,
} as const

export type LifecycleErrorCodeName = keyof typeof LifecycleErrorCodes

/** 错误码 1:1 文案（铁律 #12：与后端 LifecycleErrorCatalog 一一对应）。 */
export const LifecycleErrorMessages: Record<LifecycleErrorCodeName, string> = {
  DATA_SCOPE_FORBIDDEN: '无该数据范围的操作权限',
  OPTION_SOURCE_UNAVAILABLE: '选项数据源不可用',
  MEMBER_INACTIVE: '人员必须为已激活成员',
  COMPONENT_DISABLED: '停用组件不得参与新任务下发',
  ACTIVITY_OBSOLETE_READONLY: '已作废活动只读，禁止任何写操作',
  EXPORT_INSTANCE_DATA_LEAK: '导出模板包不得携带项目/组件/人员实例数据',
  OWNER_INACTIVE: '负责人必须为已激活成员且仅可绑定 1 名',
  ACTIVITY_NOT_FOUND: '活动不存在',
  ACTIVITY_NAME_DUPLICATE: '活动名称冲突，请确认是否覆盖',
  GRANULARITY_IMMUTABLE: '活动颗粒度创建后不可变更',
  COMPONENT_BINDING_MISMATCH: '组件级活动必须绑定组件、项目级活动禁止绑定组件',
  ACTIVITY_INACTIVE_NO_PROCESS: '停用活动不允许新增工序',
  ACTIVITY_INACTIVE_NO_DISPATCH: '停用/作废活动不允许下发新任务或聚合引用',
  LIFE_STAGE_REQUIRED_FOR_NORMAL: '普通基础活动必须归属生命周期阶段',
  ACTIVITY_CODE_CONFLICT: '活动编码冲突，请重试',
  PROCESS_NOT_FOUND: '工序不存在',
  PROCESS_NAME_DUPLICATE: '工序名称在同活动内必须唯一',
  PROCESS_SEQ_INVALID: '工序序号必须连续 1..n 且不重复',
  PROCESS_MIN_ONE: '活动至少保留 1 道工序',
  EXIT_TRIAD_INCOMPLETE: '准出三要素（准出内容/准出交付物清单/合格判定规则）齐备方可发布',
  TOPOLOGY_SELF_LOOP: '禁止自连依赖',
  TOPOLOGY_DUPLICATE_EDGE: '禁止重复依赖连线',
  TOPOLOGY_CYCLE: '禁止成环依赖',
  TOPOLOGY_MISSING_NODE: '禁止引用缺失节点',
  TOPOLOGY_LIMIT_EXCEEDED: '单活动工序数不得超过 30 节点',
  TOPOLOGY_NOPRE_CONFLICT: '有入边仍标记无前置任务冲突',
  SNAPSHOT_VERSION_MISMATCH: '快照版本号与外层记录版本号必须恒等',
  TEMPLATE_INVALID_JSON: '模板包 JSON 格式错误',
  TEMPLATE_FIELD_INCOMPLETE: '模板字段完整性校验失败',
  TEMPLATE_GRANULARITY_MISMATCH: '模板颗粒度一致性校验失败',
  TEMPLATE_DEPENDENCY_INVALID: '模板工序依赖合法性校验失败',
  TEMPLATE_EXPORT_FORBIDDEN: '模板导入导出仅数据迁移管理员拥有',
  TOPIC_GRANULARITY_MISMATCH: '专题聚合活动仅可聚合同颗粒度普通活动',
}

/** 固化载荷自描述契约（铁律 #15）：版本号 + 归属主体标识 + 生成时间，版本与外层记录 1:1 恒等。 */
export interface LifecycleSnapshotEnvelope {
  schemaVersion: number
  entityType: string
  entityId: number
  frozenAt: string
}

/** ===== 基线文档第 9 章活动域契约（T3） ===== */

export type LifecycleActivityType = 'NORMAL' | 'TOPIC'
export type LifecycleGranularity = 'PROJECT' | 'COMPONENT'

export interface LifecycleStageOption {
  id: number
  stageCode: string
  stageName: string
  sortNo: number
}

export interface LifecycleActivityView {
  id: number
  tenantId: number
  activityCode: string
  activityName: string
  activityType: LifecycleActivityType
  lifecycleStageId: number | null
  stageCode: string | null
  stageName: string | null
  granularity: LifecycleGranularity
  activityStatus: ActivityStatus
  scene: string | null
  goal: string | null
  overallEntryCond: string | null
  overallExitDesc: string | null
  overallDeliverables: string | null
  createdBy: number
  updatedBy: number
  createdAt: string
  updatedAt: string
  processTotal: number
  processReady: number
}

export interface LifecycleProcessInput {
  id: number | null
  seq: number
  processName: string
  ownerRoleId: number | null
  isRequired: boolean
  entryConfig: string | null
  execConfig: string | null
  exitContent: string
  exitDeliverableList: string | null
  qualifiedRule: string
  mustAudit: boolean
  mustSubmitDeliverable: boolean
  deliverableTemplateId: number | null
}

export interface LifecycleProcessView {
  id: number
  activityId: number
  seq: number
  processName: string
  ownerRoleId: number | null
  isRequired: boolean
  entryConfig: string | null
  noPredecessor: boolean
  execConfig: string | null
  exitContent: string
  exitDeliverableList: string | null
  qualifiedRule: string
  mustAudit: boolean
  mustSubmitDeliverable: boolean
  deliverableTemplateId: number | null
  configStatus: ProcessConfigStatus
  predecessorProcessIds: number[]
}

export interface LifecycleTopologyInput {
  processes: LifecycleProcessInput[]
  edges: Array<{ sourceProcessId: number; targetProcessId: number }>
}

export interface LifecycleTopologyView {
  activityId: number
  topologyVersion: string | null
  processes: LifecycleProcessView[]
  edges: Array<{ sourceProcessId: number; targetProcessId: number }>
  warnings: string[]
}

export interface LifecycleTemplatePackage {
  templateName: string
  activityType: LifecycleActivityType
  granularity: LifecycleGranularity
  lifecycleStageId: number | null
  lifecycleStageCode: string | null
  scene: string | null
  goal: string | null
  overallEntryCond: string | null
  overallExitDesc: string | null
  overallDeliverables: string | null
  processes: LifecycleProcessInput[]
  edges: Array<{ sourceSeq: number; targetSeq: number }>
}

export interface LifecyclePublishStatus {
  activityId: number
  processTotal: number
  processReady: number
  missingExitProcesses: string[]
  topologyOk: boolean
  publishable: boolean
}

export interface LifecycleTopicCandidate {
  id: number
  activityCode: string
  activityName: string
  stageName: string | null
  activityStatus: ActivityStatus
}
