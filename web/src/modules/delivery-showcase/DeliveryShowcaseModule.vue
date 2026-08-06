<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowDown, DataAnalysis, List, Plus, Promotion, Timer } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import DeliveryWorkbench from './components/DeliveryWorkbench.vue'
import DeliveryProjectList from './components/DeliveryProjectList.vue'
import DeliveryAnalytics from './components/DeliveryAnalytics.vue'
import DeliveryGantt from './components/DeliveryGantt.vue'
import DeliveryApproval from './components/DeliveryApproval.vue'
import ProjectFormDrawer from './components/ProjectFormDrawer.vue'
import ProjectWizardDialog from './components/ProjectWizardDialog.vue'
import ProjectDetailDrawer from './components/ProjectDetailDrawer.vue'
import ProjectStageDetailDialog from './components/ProjectStageDetailDialog.vue'
import RiskDialog from './components/RiskDialog.vue'
import { initialProjects } from './mock'
import type { DeliveryProject, DeliveryRisk, ProjectDraft } from './types'
import './delivery-showcase.css'

type ViewKey = 'overview' | 'projects' | 'analytics' | 'schedule' | 'approval'
const activeView = ref<ViewKey>('overview')
const projects = ref<DeliveryProject[]>(structuredClone(initialProjects))
const selectedProject = ref<DeliveryProject | null>(null)
const formOpen = ref(false)
const wizardOpen = ref(false)
const detailOpen = ref(false)
const stepDetailOpen = ref(false)
const riskOpen = ref(false)
const editingProject = ref<DeliveryProject | null>(null)
const views: Array<{ key: ViewKey; label: string; icon: typeof List }> = [
  { key: 'overview', label: '工作概览', icon: DataAnalysis },
  { key: 'projects', label: '交付项目', icon: List },
  { key: 'analytics', label: '数据分析', icon: Promotion },
  { key: 'schedule', label: '计划排期', icon: Timer },
  { key: 'approval', label: '流程审批', icon: ArrowDown }
]
const description = computed(() => ({
  overview: '聚合项目进度、风险、质量和阶段分布，支持高频交付决策。',
  projects: '检索、比较和维护交付项目，保留筛选、分页和操作上下文。',
  analytics: '使用统一业务数据展示柱状图、饼状图和折线趋势。',
  schedule: '按项目里程碑查看跨团队排期、进度和延期情况。',
  approval: '查看流程图、审批上下文和审计记录，并完成审批决策。'
}[activeView.value]))

function openCreate(mode: 'standard' | 'wizard') { editingProject.value = null; if (mode === 'wizard') wizardOpen.value = true; else formOpen.value = true }
function openDetail(project: DeliveryProject) { selectedProject.value = project; detailOpen.value = true }
function openStepDetail(project: DeliveryProject) { selectedProject.value = project; stepDetailOpen.value = true }
function openEdit(project: DeliveryProject) { editingProject.value = project; detailOpen.value = false; formOpen.value = true }
function openRisk(project: DeliveryProject) { selectedProject.value = project; riskOpen.value = true }
function createProject(draft: ProjectDraft) {
  const nextId = Math.max(...projects.value.map(item => item.id)) + 1
  const template = structuredClone(projects.value[0].milestones)
  projects.value.unshift({ id: nextId, code: `DLV-2026-${String(nextId + 14).padStart(3, '0')}`, name: draft.name, type: draft.type, status: '待启动', priority: draft.priority, stage: '需求', owner: draft.owner, department: draft.department, progress: 0, budget: draft.budget, startDate: draft.startDate, endDate: draft.endDate, updatedAt: '刚刚', description: draft.description || '待补充交付目标。', tags: [draft.deliveryMode, ...(draft.dataMigration ? ['数据迁移'] : [])], members: [...draft.members], risks: [], milestones: template.map((item, index) => ({ ...item, id: nextId * 10 + index, progress: 0, status: '未开始' })) })
  activeView.value = 'projects'; ElNotification({ title: '项目已创建', message: `${draft.name} 已加入交付项目列表。`, type: 'success' })
}
function saveProject(draft: ProjectDraft, project?: DeliveryProject | null) {
  if (!project) { createProject(draft); return }
  Object.assign(project, { ...draft, members: [...draft.members], updatedAt: '刚刚' }); ElMessage.success('项目资料已保存')
}
function saveRisk(risk: DeliveryRisk, project: DeliveryProject) { project.risks.push(risk); if (risk.level === '高') project.status = '有风险'; project.updatedAt = '刚刚'; ElNotification({ title: '风险已登记', message: `${project.code} 新增 1 项${risk.level}风险。`, type: risk.level === '高' ? 'warning' : 'success' }) }
async function removeProjects(ids: number[]) { projects.value = projects.value.filter(item => !ids.includes(item.id)); ElMessage.success(`已删除 ${ids.length} 个示范项目`) }
async function resetDemo() { await ElMessageBox.confirm('将恢复全部初始示范数据，并清除当前页面会话中的新增和编辑结果。', '重置示范数据', { type: 'warning', confirmButtonText: '恢复初始数据' }); projects.value = structuredClone(initialProjects); ElMessage.success('示范数据已恢复') }
</script>

<template>
  <section class="delivery-showcase-page">
    <UiPageHeader eyebrow="业务前端样式参考" title="交付示范中心" :description="description">
      <template #actions><el-button @click="resetDemo">重置数据</el-button><el-dropdown split-button type="primary" @click="openCreate('standard')" @command="openCreate"><el-icon><Plus /></el-icon>新建项目<template #dropdown><el-dropdown-menu><el-dropdown-item command="standard">标准表单新建</el-dropdown-item><el-dropdown-item command="wizard">分步表单新建</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template>
    </UiPageHeader>

    <nav class="delivery-module-nav" aria-label="交付示范中心视图"><button v-for="view in views" :key="view.key" type="button" :class="{ active: activeView === view.key }" @click="activeView = view.key"><el-icon><component :is="view.icon" /></el-icon><span>{{ view.label }}</span></button></nav>

    <DeliveryWorkbench v-if="activeView === 'overview'" :projects="projects" @navigate="activeView = $event as ViewKey" />
    <DeliveryProjectList v-else-if="activeView === 'projects'" :projects="projects" @detail="openDetail" @step-detail="openStepDetail" @edit="openEdit" @risk="openRisk" @remove="removeProjects" />
    <DeliveryAnalytics v-else-if="activeView === 'analytics'" :projects="projects" />
    <DeliveryGantt v-else-if="activeView === 'schedule'" :projects="projects" />
    <DeliveryApproval v-else :projects="projects" />

    <ProjectFormDrawer v-model="formOpen" :project="editingProject" @saved="saveProject" />
    <ProjectWizardDialog v-model="wizardOpen" @saved="createProject" />
    <ProjectDetailDrawer v-model="detailOpen" :project="selectedProject" @edit="openEdit" />
    <ProjectStageDetailDialog v-model="stepDetailOpen" :project="selectedProject" />
    <RiskDialog v-model="riskOpen" :project="selectedProject" @saved="saveRisk" />
  </section>
</template>
