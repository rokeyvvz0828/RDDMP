import type { ApprovalEvent, DeliveryMilestone, DeliveryProject, ProjectDraft } from './types'

const milestoneTemplates: Array<Omit<DeliveryMilestone, 'id' | 'start' | 'end' | 'progress' | 'status'>> = [
  { name: '需求基线确认', stage: '需求', owner: '周宁' },
  { name: '核心功能研发', stage: '研发', owner: '林川' },
  { name: '集成测试准入', stage: '测试', owner: '陈曦' },
  { name: '迁移演练', stage: '迁移', owner: '顾言' },
  { name: '生产投产', stage: '投产', owner: '沈越' }
]

function projectMilestones(seed: number, progress: number): DeliveryMilestone[] {
  return milestoneTemplates.map((item, index) => {
    const month = 7 + Math.floor((seed + index) / 26)
    const startDay = 2 + ((seed * 3 + index * 6) % 24)
    const endDay = Math.min(28, startDay + 5 + (index % 3))
    const itemProgress = Math.max(0, Math.min(100, progress + (2 - index) * 24))
    return {
      ...item,
      id: seed * 10 + index,
      start: `2026-${String(month).padStart(2, '0')}-${String(startDay).padStart(2, '0')}`,
      end: `2026-${String(month).padStart(2, '0')}-${String(endDay).padStart(2, '0')}`,
      progress: itemProgress,
      status: itemProgress === 100 ? '已完成' : itemProgress > 0 ? '进行中' : seed % 4 === 0 && index === 2 ? '延期' : '未开始'
    }
  })
}

const rawProjects: Array<Omit<DeliveryProject, 'id' | 'milestones' | 'risks'>> = [
  { code: 'DLV-2026-014', name: '统一支付能力升级', type: '产品迭代', status: '进行中', priority: 'P0', stage: '研发', owner: '林川', department: '支付产品组', progress: 62, budget: 186, startDate: '2026-07-06', endDate: '2026-09-18', updatedAt: '今天 09:42', description: '整合支付路由、对账和异常补偿能力，支持多渠道灰度发布。', tags: ['核心链路', '灰度发布'], members: ['林川', '周宁', '陈曦', '沈越'] },
  { code: 'DLV-2026-013', name: '客户主数据治理二期', type: '数据迁移', status: '有风险', priority: 'P0', stage: '迁移', owner: '顾言', department: '数据平台组', progress: 71, budget: 245, startDate: '2026-06-16', endDate: '2026-08-28', updatedAt: '今天 08:15', description: '完成存量客户主数据清洗、映射、核对和增量切换。', tags: ['数据治理', '迁移'], members: ['顾言', '唐唯', '苏禾'] },
  { code: 'DLV-2026-012', name: '研发效能度量平台', type: '系统建设', status: '进行中', priority: 'P1', stage: '测试', owner: '周宁', department: '工程效能组', progress: 78, budget: 128, startDate: '2026-05-22', endDate: '2026-08-21', updatedAt: '昨天 17:36', description: '建设从需求到投产的交付度量、质量门禁和趋势分析。', tags: ['效能度量', '质量'], members: ['周宁', '陈曦', '陆遥'] },
  { code: 'DLV-2026-011', name: '渠道服务容灾改造', type: '基础设施', status: '待启动', priority: 'P1', stage: '需求', owner: '沈越', department: '基础平台组', progress: 8, budget: 96, startDate: '2026-08-17', endDate: '2026-10-30', updatedAt: '08-04 14:20', description: '提升渠道服务跨可用区容灾和故障切换能力。', tags: ['高可用'], members: ['沈越', '叶青'] },
  { code: 'DLV-2026-010', name: '移动端统一登录改版', type: '产品迭代', status: '已完成', priority: 'P1', stage: '投产', owner: '陈曦', department: '用户体验组', progress: 100, budget: 72, startDate: '2026-04-08', endDate: '2026-07-25', updatedAt: '07-28 11:06', description: '统一移动端身份认证、设备绑定和异常登录提示体验。', tags: ['移动端', '安全'], members: ['陈曦', '林川'] },
  { code: 'DLV-2026-009', name: '历史订单归档迁移', type: '数据迁移', status: '有风险', priority: 'P1', stage: '测试', owner: '唐唯', department: '数据平台组', progress: 54, budget: 114, startDate: '2026-06-03', endDate: '2026-09-12', updatedAt: '08-03 16:42', description: '归档五年以上历史订单并完成在线查询兼容。', tags: ['归档', '大数据量'], members: ['唐唯', '顾言', '叶青'] },
  { code: 'DLV-2026-008', name: '营销规则引擎升级', type: '系统建设', status: '进行中', priority: 'P2', stage: '研发', owner: '苏禾', department: '营销技术组', progress: 43, budget: 153, startDate: '2026-07-12', endDate: '2026-10-09', updatedAt: '08-02 10:18', description: '升级规则编排、仿真验证和发布回滚能力。', tags: ['规则引擎'], members: ['苏禾', '陆遥'] },
  { code: 'DLV-2026-007', name: '测试环境云化扩容', type: '基础设施', status: '已完成', priority: 'P2', stage: '投产', owner: '叶青', department: '基础平台组', progress: 100, budget: 68, startDate: '2026-03-10', endDate: '2026-07-08', updatedAt: '07-09 09:10', description: '扩充并统一管理测试环境弹性资源池。', tags: ['云资源'], members: ['叶青', '沈越'] },
  { code: 'DLV-2026-006', name: '版本发布编排一期', type: '系统建设', status: '待启动', priority: 'P2', stage: '需求', owner: '陆遥', department: '工程效能组', progress: 4, budget: 132, startDate: '2026-09-01', endDate: '2026-11-28', updatedAt: '07-30 15:04', description: '统一版本窗口、发布批次、检查项和回退预案管理。', tags: ['发布编排'], members: ['陆遥', '周宁'] }
]

export const initialProjects: DeliveryProject[] = rawProjects.map((project, index) => ({
  ...project,
  id: index + 1,
  milestones: projectMilestones(index + 1, project.progress),
  risks: project.status === '有风险' ? [{ id: index + 100, title: index % 2 ? '迁移校验吞吐低于计划' : '联调环境交付延期', level: '高', owner: project.owner, dueDate: '2026-08-12', status: '处理中' }] : project.status === '进行中' ? [{ id: index + 100, title: '关键人员资源窗口冲突', level: '中', owner: project.owner, dueDate: '2026-08-18', status: '待处理' }] : []
}))

export const emptyDraft = (): ProjectDraft => ({
  name: '', type: '产品迭代', priority: 'P1', owner: '', department: '', startDate: '', endDate: '', budget: 0,
  description: '', members: [], deliveryMode: '常规发布', dataMigration: false
})

export const approvalEvents: ApprovalEvent[] = [
  { id: 1, node: '交付申请', operator: '林川', action: '提交', time: '2026-08-05 09:12', comment: '已完成范围、排期和资源确认。' },
  { id: 2, node: '技术负责人审批', operator: '周宁', action: '同意', time: '2026-08-05 11:36', comment: '技术方案可行，关注灰度期间容量指标。' },
  { id: 3, node: '质量负责人审批', operator: '陈曦', action: '待审批', time: '等待处理', comment: '待补充性能基线验证结果。' }
]

export const owners = ['林川', '周宁', '陈曦', '顾言', '沈越', '唐唯', '苏禾', '叶青', '陆遥']
export const departments = ['支付产品组', '数据平台组', '工程效能组', '基础平台组', '用户体验组', '营销技术组']
