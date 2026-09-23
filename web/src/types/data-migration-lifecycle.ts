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
}

/** 固化载荷自描述契约（铁律 #15）：版本号 + 归属主体标识 + 生成时间，版本与外层记录 1:1 恒等。 */
export interface LifecycleSnapshotEnvelope {
  schemaVersion: number
  entityType: string
  entityId: number
  frozenAt: string
}
