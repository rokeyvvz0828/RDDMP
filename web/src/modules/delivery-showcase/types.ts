export type DeliveryStatus = '进行中' | '待启动' | '有风险' | '已完成'
export type DeliveryPriority = 'P0' | 'P1' | 'P2'
export type DeliveryStage = '需求' | '研发' | '测试' | '迁移' | '投产'

export interface DeliveryMilestone {
  id: number
  name: string
  stage: DeliveryStage
  start: string
  end: string
  progress: number
  owner: string
  status: '未开始' | '进行中' | '已完成' | '延期'
}

export interface DeliveryRisk {
  id: number
  title: string
  level: '高' | '中' | '低'
  owner: string
  dueDate: string
  status: '待处理' | '处理中' | '已关闭'
}

export interface DeliveryProject {
  id: number
  code: string
  name: string
  type: '产品迭代' | '系统建设' | '数据迁移' | '基础设施'
  status: DeliveryStatus
  priority: DeliveryPriority
  stage: DeliveryStage
  owner: string
  department: string
  progress: number
  budget: number
  startDate: string
  endDate: string
  updatedAt: string
  description: string
  tags: string[]
  members: string[]
  milestones: DeliveryMilestone[]
  risks: DeliveryRisk[]
}

export interface ProjectDraft {
  name: string
  type: DeliveryProject['type']
  priority: DeliveryPriority
  owner: string
  department: string
  startDate: string
  endDate: string
  budget: number
  description: string
  members: string[]
  deliveryMode: '常规发布' | '灰度发布' | '双轨运行'
  dataMigration: boolean
}

export interface ApprovalEvent {
  id: number
  node: string
  operator: string
  action: '提交' | '同意' | '待审批' | '退回' | '驳回'
  time: string
  comment: string
}

export type DemoAsyncState = 'normal' | 'loading' | 'empty' | 'error' | 'forbidden'
