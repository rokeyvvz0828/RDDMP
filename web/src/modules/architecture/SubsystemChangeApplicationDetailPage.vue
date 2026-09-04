<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, Edit, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import { apiErrorMessage } from '../../api/error'
import {
  decideWorkflowTask,
  getCurrentWorkflowTaskContext,
  type WorkflowTaskAction,
  type WorkflowTaskContext
} from '../../api/workflow'
import { useAuthStore } from '../../stores/auth'
import {
  cancelSubsystemChangeApplication,
  getPhysicalSubsystem,
  getSubsystemChangeApplication,
  loadBusinessComponentOptions,
  loadOrganizationOptions,
  loadParameterOptions,
  loadUserOptions
} from './api'
import SubsystemChangePhysicalCard from './components/SubsystemChangePhysicalCard.vue'
import SubsystemChangeTimeline from './components/SubsystemChangeTimeline.vue'
import type {
  OrganizationOption,
  ParameterOption,
  PhysicalDraftInput,
  PhysicalSubsystem,
  SubsystemChangeApplicationDetail,
  UserOption
} from './types'
import {
  actionTypeLabels,
  applicationStatusLabels,
  applicationStatusTone,
  canCancelApplication,
  canEditApplication,
  cancelled,
  formatDateTime,
  httpStatus,
  optionLabel,
  targetKindLabels
} from './utils'
import './architecture.css'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const id = Number(route.params.id)
const detail = ref<SubsystemChangeApplicationDetail | null>(null)
const currentPhysical = ref<PhysicalSubsystem | null>(null)
const workflowTask = ref<WorkflowTaskContext | null>(null)
const physicalDrafts = ref<PhysicalDraftInput[]>([])
const organizations = ref<OrganizationOption[]>([])
const users = ref<UserOption[]>([])
const businessComponents = ref<ParameterOption[]>([])
const runtimes = ref<ParameterOption[]>([])
const levels = ref<ParameterOption[]>([])
const frameworks = ref<ParameterOption[]>([])
const deploymentPlatforms = ref<ParameterOption[]>([])
const disasterRecoveryModes = ref<ParameterOption[]>([])
const loading = ref(true)
const loadError = ref('')
const forbidden = ref(false)
const deciding = ref<WorkflowTaskAction | ''>('')
const cancelling = ref(false)

const application = computed(() => detail.value?.application || null)
const canApply = computed(() => auth.hasPermission('architecture:apply') || auth.hasPermission('architecture:manage'))
const canManage = computed(() => auth.hasPermission('architecture:manage'))
const owns = computed(() => application.value?.applicantId === auth.user?.id)
const canEdit = computed(() => Boolean(application.value && canApply.value && owns.value && canEditApplication(application.value.status)))
const canCancel = computed(() => Boolean(application.value && canApply.value && owns.value && canCancelApplication(application.value.status)))
const allowedDecisions = computed(() => {
  if (!canManage.value || !workflowTask.value?.actionable) return [] as WorkflowTaskAction[]
  return workflowTask.value.allowed_actions.filter(action => ['APPROVE', 'RETURN', 'REJECT'].includes(action))
})
const title = computed(() => application.value ? `工单 #${application.value.id} · ${targetKindLabels[application.value.targetKind]}` : '架构子系统变更工单')

const comparison = computed(() => {
  if (!detail.value || detail.value.application.actionType === 'CREATE') return []
  const draft = detail.value.physicalDrafts[0]
  const current = currentPhysical.value
  if (!draft || !current) return []
  return [
    { label: '系统编号', current: current.code, draft: draft.code },
    { label: '系统简称', current: current.shortName, draft: draft.shortName },
    { label: '系统名称', current: current.name, draft: draft.name },
    { label: '所属逻辑子系统', current: current.logicalSubsystemName || '—', draft: draft.logicalSubsystemName || '—' },
    { label: '业务组件编号', current: optionLabel(businessComponents.value, current.businessComponentCode), draft: optionLabel(businessComponents.value, draft.businessComponentCode) },
    { label: '负责团队', current: current.responsibleTeamDisplayName, draft: draft.responsibleTeamNameSnapshot },
    { label: '状态', current: current.status, draft: actionTypeLabels[detail.value.application.actionType] }
  ]
})

function toInput(source: SubsystemChangeApplicationDetail['physicalDrafts'][number]): PhysicalDraftInput {
  return {
    lineNo: source.lineNo,
    code: source.code,
    shortName: source.shortName,
    name: source.name,
    logicalSubsystemName: source.logicalSubsystemName,
    businessComponentCode: source.businessComponentCode,
    englishName: source.englishName,
    businessGroupName: source.businessGroupName,
    deploymentPlatform: source.deploymentPlatform,
    disasterRecoveryMode: source.disasterRecoveryMode,
    responsibleTeamOrgId: source.responsibleTeamOrgId,
    responsibleTeamNameSnapshot: source.responsibleTeamNameSnapshot,
    runtimeCode: source.runtimeCode,
    systemLevelCode: source.systemLevelCode,
    developmentFrameworkCode: source.developmentFrameworkCode,
    ownerUserId: source.ownerUserId,
    description: source.description,
    remark: source.remark,
    sourceRowVersion: source.sourceRowVersion
  }
}

async function loadReferences() {
  const results = await Promise.allSettled([
    loadOrganizationOptions('physical-subsystem', '', 100),
    loadUserOptions('physical-subsystem', '', 100),
    loadBusinessComponentOptions(),
    loadParameterOptions('physical-subsystem', 'ARCH_RUNTIME'),
    loadParameterOptions('physical-subsystem', 'ARCH_SYSTEM_LEVEL'),
    loadParameterOptions('physical-subsystem', 'ARCH_DEVELOPMENT_FRAMEWORK'),
    loadParameterOptions('physical-subsystem', 'ARCH_DEPLOYMENT_PLATFORM'),
    loadParameterOptions('physical-subsystem', 'ARCH_DISASTER_RECOVERY_MODE')
  ])
  if (results[0].status === 'fulfilled') organizations.value = results[0].value
  if (results[1].status === 'fulfilled') users.value = results[1].value
  if (results[2].status === 'fulfilled') businessComponents.value = results[2].value
  if (results[3].status === 'fulfilled') runtimes.value = results[3].value
  if (results[4].status === 'fulfilled') levels.value = results[4].value
  if (results[5].status === 'fulfilled') frameworks.value = results[5].value
  if (results[6].status === 'fulfilled') deploymentPlatforms.value = results[6].value
  if (results[7].status === 'fulfilled') disasterRecoveryModes.value = results[7].value
}

function userLabel(id: number) {
  const user = users.value.find(item => item.id === id)
  return user ? `${user.displayName}（${user.username}）` : `用户 #${id}`
}

async function loadPublished(applicationDetail: SubsystemChangeApplicationDetail) {
  currentPhysical.value = null
  const targetId = applicationDetail.application.targetId
  if (!targetId || applicationDetail.application.actionType === 'CREATE') return
  if (applicationDetail.application.targetKind !== 'PHYSICAL') return
  try {
    currentPhysical.value = await getPhysicalSubsystem(targetId)
  } catch {
    ElMessage.warning('当前发布数据已变化或不可访问，仍可查看本工单提交快照')
  }
}

async function loadWorkflow(applicationDetail: SubsystemChangeApplicationDetail) {
  workflowTask.value = null
  if (!canManage.value || applicationDetail.application.status !== 'IN_REVIEW') return
  try {
    workflowTask.value = (await getCurrentWorkflowTaskContext(
      'architecture_subsystem_change',
      String(applicationDetail.application.id)
    )).data.data
  } catch (error) {
    ElMessage.warning(apiErrorMessage(error, '当前审批任务加载失败'))
  }
}

async function load() {
  if (!Number.isInteger(id) || id <= 0) {
    loadError.value = '工单编号无效'
    loading.value = false
    return
  }
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    await loadReferences()
    const result = await getSubsystemChangeApplication(id)
    detail.value = result
    physicalDrafts.value = result.physicalDrafts.map(toInput)
    await Promise.all([loadPublished(result), loadWorkflow(result)])
  } catch (error) {
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '工单详情加载失败')
  } finally {
    loading.value = false
  }
}

async function decide(action: WorkflowTaskAction) {
  const task = workflowTask.value
  if (!task || deciding.value) return
  const label = action === 'APPROVE' ? '批准' : action === 'RETURN' ? '退回' : '拒绝'
  try {
    const prompt = await ElMessageBox.prompt(
      action === 'APPROVE' ? '可填写审批意见。' : '请填写处理原因。',
      `${label}工单`,
      {
        confirmButtonText: label,
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputValidator: value => action === 'APPROVE' || Boolean(value?.trim()) || '退回或拒绝必须填写原因'
      }
    )
    deciding.value = action
    await decideWorkflowTask(task.task_id, action, prompt.value.trim())
    ElMessage.success(`已${label}，业务状态将由工作流事件更新`)
    await load()
  } catch (error) {
    if (!cancelled(error)) ElMessage.error(apiErrorMessage(error, `${label}失败`))
  } finally {
    deciding.value = ''
  }
}

async function cancelApplication() {
  if (!application.value || cancelling.value) return
  try {
    await ElMessageBox.confirm(
      application.value.status === 'IN_REVIEW' ? '取消后将终止当前审批流程，确认继续？' : '确认取消当前工单？',
      '取消工单',
      { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '保留工单' }
    )
    cancelling.value = true
    const result = await cancelSubsystemChangeApplication(application.value.id, application.value.rowVersion)
    detail.value = result
    physicalDrafts.value = result.physicalDrafts.map(toInput)
    ElMessage.success('工单已取消')
    await loadWorkflow(result)
  } catch (error) {
    if (!cancelled(error)) ElMessage.error(apiErrorMessage(error, '取消工单失败'))
  } finally {
    cancelling.value = false
  }
}

function edit() {
  void router.push({ name: 'architecture-subsystem-change-application-edit', params: { id } })
}

onMounted(() => { void load() })
</script>

<template>
  <main class="architecture-page architecture-change-page">
    <UiPageHeader :title="title" description="申请字段、提交快照和业务历史只读展示；审批人不能修改申请内容。">
      <template #actions>
        <div class="architecture-page__actions">
          <el-button @click="router.push({ name: 'architecture-subsystem-change-applications' })"><el-icon><ArrowLeft /></el-icon>返回列表</el-button>
          <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
        </div>
      </template>
    </UiPageHeader>

    <section v-if="forbidden" class="architecture-state-panel">
      <el-result icon="warning" title="暂无工单查看权限" sub-title="需要 architecture:view、architecture:apply 或 architecture:manage 权限。" />
    </section>
    <section v-else-if="loadError" class="architecture-state-panel">
      <el-result icon="error" title="工单详情加载失败" :sub-title="loadError"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result>
    </section>
    <div v-else v-loading="loading" class="architecture-change-detail-shell">
      <section v-if="application" class="architecture-change-hero">
        <div>
          <span>APPLICATION #{{ application.id }}</span>
          <h2>{{ actionTypeLabels[application.actionType] }}{{ targetKindLabels[application.targetKind] }}</h2>
          <p>{{ application.reason }}</p>
        </div>
        <UiStatusTag :value="application.status" :labels="applicationStatusLabels" :tone="applicationStatusTone(application.status)" />
        <dl>
          <div><dt>申请人</dt><dd>{{ userLabel(application.applicantId) }}</dd></div>
          <div><dt>业务轮次</dt><dd>第 {{ application.currentBusinessRound }} 轮</dd></div>
          <div><dt>创建时间</dt><dd>{{ formatDateTime(application.createdAt) }}</dd></div>
          <div><dt>最后更新</dt><dd>{{ formatDateTime(application.updatedAt) }}</dd></div>
        </dl>
      </section>

      <section v-if="physicalDrafts.length" class="architecture-change-section">
        <div class="architecture-change-section__heading"><div><span>PHYSICAL SUBSYSTEM SNAPSHOT</span><h2>物理子系统草稿</h2></div></div>
        <div class="architecture-change-card-list">
          <SubsystemChangePhysicalCard
            v-for="(draft, index) in physicalDrafts"
            :key="draft.lineNo"
            v-model="physicalDrafts[index]"
            :organizations="organizations"
            :users="users"
            :business-components="businessComponents"
            :runtimes="runtimes"
            :levels="levels"
            :frameworks="frameworks"
            :deployment-platforms="deploymentPlatforms"
            :disaster-recovery-modes="disasterRecoveryModes"
            :title="`物理子系统 ${index + 1}`"
            readonly
          />
        </div>
      </section>

      <section v-if="comparison.length" class="architecture-change-section">
        <div class="architecture-change-section__heading"><div><span>CHANGE COMPARISON</span><h2>当前发布值与申请值</h2></div></div>
        <div class="architecture-change-comparison">
          <article v-for="item in comparison" :key="item.label">
            <strong>{{ item.label }}</strong>
            <div><span>当前发布</span><p>{{ item.current || '—' }}</p></div>
            <div><span>申请值</span><p>{{ item.draft || '—' }}</p></div>
          </article>
        </div>
      </section>

      <section v-if="detail" class="architecture-change-section">
        <div class="architecture-change-section__heading"><div><span>BUSINESS HISTORY</span><h2>业务历史</h2></div></div>
        <SubsystemChangeTimeline :items="detail.history" />
      </section>

      <section v-if="canManage && application?.status === 'IN_REVIEW'" class="architecture-change-section architecture-change-approval">
        <div class="architecture-change-section__heading">
          <div><span>WORKFLOW TASK</span><h2>审批处理</h2><small>审批区只处理工作流动作，不提供业务字段编辑。</small></div>
        </div>
        <el-alert v-if="!workflowTask" type="info" :closable="false" show-icon title="当前没有可由你处理的审批任务，请刷新或检查任务处理人。" />
        <div v-else class="architecture-change-approval__body">
          <dl>
            <div><dt>节点</dt><dd>{{ workflowTask.node_name || workflowTask.task_key }}</dd></div>
            <div><dt>任务状态</dt><dd>{{ workflowTask.task_status }}</dd></div>
            <div><dt>流程实例</dt><dd>#{{ workflowTask.instance_id }}</dd></div>
          </dl>
          <div class="architecture-change-approval__actions">
            <el-button v-if="allowedDecisions.includes('RETURN')" :loading="deciding === 'RETURN'" :disabled="Boolean(deciding)" @click="decide('RETURN')">退回修改</el-button>
            <el-button v-if="allowedDecisions.includes('REJECT')" type="danger" plain :loading="deciding === 'REJECT'" :disabled="Boolean(deciding)" @click="decide('REJECT')">拒绝</el-button>
            <el-button v-if="allowedDecisions.includes('APPROVE')" type="primary" :loading="deciding === 'APPROVE'" :disabled="Boolean(deciding)" @click="decide('APPROVE')">批准</el-button>
          </div>
        </div>
      </section>
    </div>

    <footer v-if="application && (canEdit || canCancel)" class="architecture-change-sticky-actions">
      <div><strong>申请人操作</strong><span>管理权限不会扩大他人草稿的编辑、提交或取消范围。</span></div>
      <div>
        <el-button v-if="canCancel" type="danger" plain :loading="cancelling" @click="cancelApplication">取消工单</el-button>
        <el-button v-if="canEdit" type="primary" @click="edit"><el-icon><Edit /></el-icon>编辑并重新提交</el-button>
      </div>
    </footer>
  </main>
</template>
