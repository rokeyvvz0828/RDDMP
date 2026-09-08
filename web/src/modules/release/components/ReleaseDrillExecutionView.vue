<script setup lang="ts">
import ReleaseDrawerHeader from './ReleaseDrawerHeader.vue'
import { useReleaseDrawerFullscreen } from '../composables/useReleaseDrawerFullscreen'
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiErrorMessage } from '../../../api/error'
import { createReleaseDrill, createReleaseDrillStep, deleteReleaseDrill, deleteReleaseDrillStep, listReleaseDrillEnvironments, listReleaseOperationMemberOptions, listReleasePlans, listReleaseDrills, updateReleaseDrill, updateReleaseDrillStep, type ReleaseDrillEnvironmentDto, type ReleaseDrillExecutionDto, type ReleaseDrillExecutionWrite, type ReleaseDrillStepDto, type ReleaseMemberOptionDto, type ReleasePlanDto } from '../../../api/release'
import { useAuthStore } from '../../../stores/auth'
import UiPageHeader from '../../../components/ui/UiPageHeader.vue'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'

const props = defineProps<{ projectId: number }>()
const auth = useAuthStore()
const rounds = ref<ReleaseDrillExecutionDto[]>([])
const plans = ref<ReleasePlanDto[]>([])
const environments = ref<ReleaseDrillEnvironmentDto[]>([])
const members = ref<ReleaseMemberOptionDto[]>([])
const selectedId = ref<number>()
const loading = ref(false)
const error = ref('')
const forbidden = ref(false)
const saving = ref(false)
const stepSaving = ref(false)
const roundDialog = ref(false)
const stepDrawer = ref(false)
const { fullscreen: stepFullscreen, size: stepSize, toggle: toggleStepFullscreen } =
  useReleaseDrawerFullscreen(stepDrawer, 'min(680px, calc(100vw - 24px))')
const editingRound = ref<ReleaseDrillExecutionDto | null>(null)
const editingStep = ref<ReleaseDrillStepDto | null>(null)
const stepRoundId = ref<number>()
const roundForm = reactive<ReleaseDrillExecutionWrite>({ releasePlanId: 0, environmentId: 0, roundName: '', plannedAt: '', status: 'PLANNED', resultContent: '', rowVersion: 0 })
const stepForm = reactive({ stepName: '', ownerId: undefined as number | undefined, range: [] as string[] | null, status: 'PENDING', resultContent: '', description: '', rowVersion: 0, seqNo: undefined as number | undefined })
const canManage = () => auth.hasPermission('release-operations:drill:manage')
const selectedRound = () => rounds.value.find(item => item.id === selectedId.value) || rounds.value[0]
const statusLabels: Record<string, string> = { PLANNED: '待演练', RUNNING: '演练中', COMPLETED: '已完成', PENDING: '待开始', SKIPPED: '已跳过' }
let disposed = false
let loadGeneration = 0
const currentProject = (projectId: number) => !disposed && props.projectId === projectId

function minute(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '未安排' }
function isForbidden(cause: unknown) { return (cause as { response?: { status?: number } }).response?.status === 403 }
function statusTone(value: string) { return value === 'COMPLETED' ? 'success' : value === 'RUNNING' ? 'warning' : 'info' }

async function load() {
  const projectId = props.projectId
  const generation = ++loadGeneration
  const isCurrent = () => currentProject(projectId) && generation === loadGeneration
  loading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const [r, p, e, m] = await Promise.all([listReleaseDrills(projectId), listReleasePlans(projectId), listReleaseDrillEnvironments(projectId), listReleaseOperationMemberOptions(projectId)])
    if (!isCurrent()) return
    rounds.value = r.data.data
    plans.value = p.data.data
    environments.value = e.data.data
    members.value = m.data.data
    if (!rounds.value.some(item => item.id === selectedId.value)) selectedId.value = rounds.value[0]?.id
  } catch (cause) {
    if (!isCurrent()) return
    rounds.value = []
    error.value = apiErrorMessage(cause, '投产演练加载失败，请稍后重试')
    forbidden.value = isForbidden(cause)
  } finally {
    if (isCurrent()) loading.value = false
  }
}

function openRound(round?: ReleaseDrillExecutionDto) {
  if (!canManage() || saving.value || disposed) return
  editingRound.value = round || null
  Object.assign(roundForm, { releasePlanId: round?.releasePlanId || plans.value[0]?.id || 0, environmentId: round?.environmentId || environments.value[0]?.id || 0, roundName: round?.roundName || `第 ${rounds.value.length + 1} 轮演练`, plannedAt: round?.plannedAt || '', status: round?.status || 'PLANNED', resultContent: round?.resultContent || '', rowVersion: round?.rowVersion || 0 })
  roundDialog.value = true
}
function closeRound(done?: () => void) {
  if (saving.value) return
  if (done) done()
  else roundDialog.value = false
}
async function saveRound() {
  if (!canManage() || disposed || !roundForm.releasePlanId || !roundForm.environmentId || !roundForm.roundName.trim() || saving.value) return
  const projectId = props.projectId
  const editingId = editingRound.value?.id
  saving.value = true
  try {
    const response = editingId ? await updateReleaseDrill(projectId, editingId, { ...roundForm }) : await createReleaseDrill(projectId, { ...roundForm })
    if (!currentProject(projectId)) return
    const value = response.data.data
    rounds.value = editingId ? rounds.value.map(item => item.id === value.id ? value : item) : [...rounds.value, value]
    selectedId.value = value.id
    roundDialog.value = false
    ElMessage.success(editingId ? '演练轮次已更新' : '演练轮次已创建')
  } catch (cause) {
    if (currentProject(projectId)) ElMessage.error(apiErrorMessage(cause, '演练轮次保存失败，请重试'))
  } finally {
    if (currentProject(projectId)) saving.value = false
  }
}
async function removeRound(round: ReleaseDrillExecutionDto) {
  if (!canManage() || disposed) return
  const projectId = props.projectId
  try {
    await ElMessageBox.confirm(`将删除“${round.roundName}”，删除后不可恢复。`, '删除演练轮次', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    if (!currentProject(projectId) || !canManage()) return
    await deleteReleaseDrill(projectId, round.id, round.rowVersion)
    if (!currentProject(projectId)) return
    rounds.value = rounds.value.filter(item => item.id !== round.id)
    selectedId.value = rounds.value[0]?.id
    ElMessage.success('演练轮次已删除')
  } catch (cause) {
    if (currentProject(projectId) && cause !== 'cancel' && cause !== 'close') ElMessage.error(apiErrorMessage(cause, '演练轮次删除失败，请刷新后重试'))
  }
}
function openStep(step?: ReleaseDrillStepDto) {
  if (!canManage() || stepSaving.value || disposed || !selectedRound()) return
  // Retain the round being edited even if the selected navigation item changes.
  stepRoundId.value = selectedRound()!.id
  editingStep.value = step || null
  Object.assign(stepForm, { stepName: step?.stepName || '', ownerId: step?.ownerId, range: step?.plannedStart && step?.plannedEnd ? [step.plannedStart, step.plannedEnd] : [], status: step?.status || 'PENDING', resultContent: step?.resultContent || '', description: step?.description || '', rowVersion: step?.rowVersion || 0, seqNo: step?.seqNo })
  stepDrawer.value = true
}
function closeStep(done?: () => void) {
  if (stepSaving.value) return
  if (done) done()
  else stepDrawer.value = false
}
async function saveStep() {
  const round = rounds.value.find(item => item.id === stepRoundId.value)
  if (!canManage() || disposed || !round || !stepForm.stepName.trim() || stepSaving.value) return
  const projectId = props.projectId
  const editingId = editingStep.value?.id
  stepSaving.value = true
  try {
    const data = { stepName: stepForm.stepName, ownerId: stepForm.ownerId, plannedStart: stepForm.range?.[0], plannedEnd: stepForm.range?.[1], status: stepForm.status, resultContent: stepForm.resultContent, description: stepForm.description, rowVersion: stepForm.rowVersion, seqNo: stepForm.seqNo }
    const response = editingId ? await updateReleaseDrillStep(projectId, round.id, editingId, data) : await createReleaseDrillStep(projectId, round.id, data)
    if (!currentProject(projectId)) return
    const value = response.data.data
    rounds.value = rounds.value.map(item => item.id === round.id ? { ...item, steps: [...item.steps.filter(step => step.id !== value.id), value].sort((a, b) => a.seqNo - b.seqNo) } : item)
    stepDrawer.value = false
    ElMessage.success(editingId ? '演练步骤已更新' : '演练步骤已添加')
  } catch (cause) {
    if (currentProject(projectId)) ElMessage.error(apiErrorMessage(cause, '演练步骤保存失败，请重试'))
  } finally {
    if (currentProject(projectId)) stepSaving.value = false
  }
}
async function removeStep(step: ReleaseDrillStepDto) {
  const round = selectedRound()
  if (!canManage() || disposed || !round) return
  const projectId = props.projectId
  try {
    await ElMessageBox.confirm(`将删除“${step.stepName}”，删除后不可恢复。`, '删除演练步骤', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    if (!currentProject(projectId) || !canManage()) return
    await deleteReleaseDrillStep(projectId, round.id, step.id, step.rowVersion)
    if (!currentProject(projectId)) return
    rounds.value = rounds.value.map(item => item.id === round.id ? { ...item, steps: item.steps.filter(item => item.id !== step.id) } : item)
    ElMessage.success('演练步骤已删除')
  } catch (cause) {
    if (currentProject(projectId) && cause !== 'cancel' && cause !== 'close') ElMessage.error(apiErrorMessage(cause, '演练步骤删除失败，请刷新后重试'))
  }
}
onMounted(load)
onBeforeUnmount(() => { disposed = true; loadGeneration++ })
</script>

<template>
  <div v-if="forbidden" class="release-operations-state"><el-result icon="warning" title="无权查看投产演练" sub-title="请联系项目管理员申请查看权限。" /></div>
  <div v-else-if="error" class="release-operations-state release-operations-state--error">
    <el-result icon="error" title="投产演练加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result>
  </div>
  <section v-else class="drill-page">
    <UiPageHeader title="投产演练">
      <template #actions>
        <el-tooltip content="刷新投产演练"><el-button :icon="Refresh" circle :loading="loading" aria-label="刷新投产演练" @click="load" /></el-tooltip>
        <el-button v-if="canManage()" type="primary" :icon="Plus" :disabled="loading || !plans.length || !environments.length" @click="openRound()">新增轮次</el-button>
      </template>
    </UiPageHeader>
    <el-alert v-if="!loading && canManage() && (!plans.length || !environments.length)" type="warning" :closable="false" show-icon :title="!plans.length && !environments.length ? '当前项目暂无投产方案和演练环境' : !plans.length ? '当前项目暂无投产方案' : '当前项目暂无演练环境'" />
    <div v-loading="loading" class="drill-content" :aria-busy="loading">
      <UiEmptyState v-if="!rounds.length && !loading" title="暂无演练轮次" />
      <div v-else class="drill-layout">
        <aside class="drill-sidebar" aria-label="演练轮次">
          <div class="drill-sidebar-heading"><h2>演练轮次</h2><span>{{ rounds.length }} 轮</span></div>
          <nav class="drill-round-list" aria-label="轮次列表">
            <button v-for="round in rounds" :key="round.id" type="button" class="drill-round" :class="{ 'is-active': selectedRound()?.id === round.id }" :aria-current="selectedRound()?.id === round.id ? 'true' : undefined" @click="selectedId = round.id">
              <span class="drill-round-meta"><span>第 {{ round.roundNo }} 轮</span><UiStatusTag :value="statusLabels[round.status] || round.status" :tone="statusTone(round.status)" /></span>
              <strong>{{ round.roundName }}</strong>
              <span class="drill-round-footer"><span>{{ minute(round.plannedAt) }}</span><span>{{ round.steps.length }} 步</span></span>
            </button>
          </nav>
        </aside>
        <div class="drill-mobile-selector">
          <label for="drill-round-select">演练轮次</label>
          <el-select id="drill-round-select" v-model="selectedId" aria-label="选择演练轮次">
            <el-option v-for="round in rounds" :key="round.id" :value="round.id" :label="`第 ${round.roundNo} 轮 · ${round.roundName}`" />
          </el-select>
        </div>
        <section v-if="selectedRound()" class="drill-detail">
          <header class="drill-detail-heading">
            <div class="drill-detail-title"><span class="drill-label">第 {{ selectedRound()!.roundNo }} 轮</span><h2>{{ selectedRound()!.roundName }}</h2><UiStatusTag :value="statusLabels[selectedRound()!.status]" :tone="statusTone(selectedRound()!.status)" /></div>
            <div v-if="canManage()" class="drill-actions">
              <el-tooltip content="编辑轮次"><el-button :icon="Edit" text aria-label="编辑演练轮次" @click="openRound(selectedRound())" /></el-tooltip>
              <el-tooltip content="删除轮次"><el-button :icon="Delete" text type="danger" aria-label="删除演练轮次" @click="removeRound(selectedRound()!)" /></el-tooltip>
            </div>
          </header>
          <dl class="drill-facts">
            <div><dt>投产方案</dt><dd>{{ selectedRound()!.releasePlanName || '未关联' }}</dd></div>
            <div><dt>演练环境</dt><dd>{{ selectedRound()!.environmentName || '未关联' }}</dd></div>
            <div><dt>计划时间</dt><dd>{{ minute(selectedRound()!.plannedAt) }}</dd></div>
          </dl>
          <section class="drill-round-result"><h3>演练结果</h3><p>{{ selectedRound()!.resultContent || '暂无结果记录' }}</p></section>
          <section class="drill-steps">
            <header class="drill-steps-heading"><div><h3>演练步骤</h3><span>{{ selectedRound()!.steps.length }} 个步骤</span></div><el-button v-if="canManage()" :icon="Plus" type="primary" plain @click="openStep()">新增步骤</el-button></header>
            <UiEmptyState v-if="!selectedRound()!.steps.length" title="暂无演练步骤" />
            <ol v-else class="drill-step-track" aria-label="演练步骤顺序">
              <li v-for="step in selectedRound()!.steps" :key="step.id" class="drill-step">
                <span class="drill-step-number">{{ step.seqNo }}</span>
                <article class="drill-step-card">
                  <header><h4>{{ step.stepName }}</h4><UiStatusTag :value="statusLabels[step.status] || step.status" :tone="statusTone(step.status)" /></header>
                  <dl class="drill-step-facts">
                    <div><dt>负责人</dt><dd>{{ step.ownerName || '未指定' }}</dd></div>
                    <div><dt>开始时间</dt><dd>{{ minute(step.plannedStart) }}</dd></div>
                    <div><dt>结束时间</dt><dd>{{ minute(step.plannedEnd) }}</dd></div>
                  </dl>
                  <div class="drill-step-result"><span>执行结果</span><p>{{ step.resultContent || '暂无结果记录' }}</p></div>
                  <footer v-if="canManage()" class="drill-actions">
                    <el-tooltip content="编辑步骤"><el-button text :icon="Edit" aria-label="编辑演练步骤" @click="openStep(step)" /></el-tooltip>
                    <el-tooltip content="删除步骤"><el-button text type="danger" :icon="Delete" aria-label="删除演练步骤" @click="removeStep(step)" /></el-tooltip>
                  </footer>
                </article>
              </li>
            </ol>
          </section>
        </section>
        <UiEmptyState v-else-if="!loading" title="请选择演练轮次" />
      </div>
    </div>
  </section>
  <el-dialog v-model="roundDialog" class="drill-round-dialog" :title="editingRound ? '编辑投产演练轮次' : '新增投产演练轮次'" width="min(680px, calc(100vw - 24px))" top="12px" :before-close="closeRound" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close>
    <el-form label-position="top" :disabled="saving || !canManage()" @submit.prevent="saveRound">
      <section class="drill-form-section">
        <h3>轮次安排</h3>
        <div class="drill-form-grid">
          <el-form-item label="轮次名称" required class="is-wide"><el-input v-model="roundForm.roundName" maxlength="128" /></el-form-item>
          <el-form-item label="投产方案" required><el-select v-model="roundForm.releasePlanId"><el-option v-for="plan in plans" :key="plan.id" :label="`${plan.planName}（${plan.planCode}）`" :value="plan.id" /></el-select></el-form-item>
          <el-form-item label="投产环境" required><el-select v-model="roundForm.environmentId"><el-option v-for="environment in environments" :key="environment.id" :label="environment.environmentName" :value="environment.id" /></el-select></el-form-item>
          <el-form-item label="计划时间"><el-date-picker v-model="roundForm.plannedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
          <el-form-item label="状态"><el-select v-model="roundForm.status"><el-option label="待演练" value="PLANNED" /><el-option label="演练中" value="RUNNING" /><el-option label="已完成" value="COMPLETED" /></el-select></el-form-item>
        </div>
      </section>
      <section class="drill-form-section">
        <h3>演练结果</h3>
        <el-form-item label="结果记录"><el-input v-model="roundForm.resultContent" type="textarea" :rows="4" maxlength="2000" /></el-form-item>
      </section>
    </el-form>
    <template #footer>
      <div class="drill-form-actions">
        <el-button :disabled="saving" @click="closeRound()">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!canManage() || !roundForm.releasePlanId || !roundForm.environmentId || !roundForm.roundName.trim()" @click="saveRound">保存</el-button>
      </div>
    </template>
  </el-dialog>
  <el-drawer v-model="stepDrawer" class="drill-step-drawer" :title="editingStep ? '编辑演练步骤' : '新增演练步骤'" direction="rtl" :size="stepSize" :class="{ 'release-operations-fullscreen-drawer': stepFullscreen }" :before-close="closeStep" :close-on-click-modal="!stepSaving" :close-on-press-escape="!stepSaving" :show-close="!stepSaving" destroy-on-close><template #header="{ titleId, titleClass }"><ReleaseDrawerHeader :title="editingStep ? '编辑演练步骤' : '新增演练步骤'" :title-id="titleId" :title-class="titleClass" :fullscreen="stepFullscreen" @toggle="toggleStepFullscreen" /></template>
    <el-form class="release-operations-fullscreen-form" label-position="top" :disabled="stepSaving || !canManage()" @submit.prevent="saveStep">
      <section class="drill-form-section">
        <h3>步骤安排</h3>
        <div class="drill-form-grid">
          <el-form-item label="步骤名称" required class="is-wide"><el-input v-model="stepForm.stepName" maxlength="128" /></el-form-item>
          <el-form-item label="序号"><el-input-number v-model="stepForm.seqNo" :min="1" :max="999" /></el-form-item>
          <el-form-item label="负责人"><el-select v-model="stepForm.ownerId" clearable><el-option v-for="member in members" :key="member.userId" :label="`${member.displayName}（${member.username}）`" :value="member.userId" /></el-select></el-form-item>
          <el-form-item label="计划时间范围" class="is-wide"><el-date-picker v-model="stepForm.range" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" /></el-form-item>
          <el-form-item label="状态"><el-select v-model="stepForm.status"><el-option label="待开始" value="PENDING" /><el-option label="进行中" value="RUNNING" /><el-option label="已完成" value="COMPLETED" /><el-option label="已跳过" value="SKIPPED" /></el-select></el-form-item>
          <el-form-item label="步骤说明" class="is-wide"><el-input v-model="stepForm.description" type="textarea" :rows="4" maxlength="2000" /></el-form-item>
        </div>
      </section>
      <section class="drill-form-section">
        <h3>执行结果</h3>
        <el-form-item label="结果记录"><el-input v-model="stepForm.resultContent" type="textarea" :rows="4" maxlength="2000" /></el-form-item>
      </section>
    </el-form>
    <template #footer>
      <div class="drill-form-actions">
        <el-button :disabled="stepSaving" @click="closeStep()">取消</el-button>
        <el-button type="primary" :loading="stepSaving" :disabled="!canManage() || !stepForm.stepName.trim()" @click="saveStep">保存</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.drill-page { display: grid; gap: 20px; min-width: 0; color: var(--text); }
.drill-content { min-width: 0; min-height: 240px; }
.drill-layout { display: grid; grid-template-columns: 260px minmax(0, 1fr); gap: 24px; min-width: 0; align-items: start; }
.drill-sidebar { min-width: 0; padding-right: 20px; border-right: 1px solid var(--line); }
.drill-sidebar-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.drill-sidebar-heading h2 { margin: 0; font-size: 14px; font-weight: 600; }
.drill-sidebar-heading > span, .drill-round-footer, .drill-label { color: var(--muted); font-size: 12px; }
.drill-round-list { display: grid; gap: 4px; align-content: start; max-height: max(240px, calc(100dvh - 250px)); overflow-y: auto; }
.drill-round { display: grid; gap: 12px; width: 100%; min-width: 0; padding: 14px 12px; border: 0; border-left: 3px solid transparent; background: transparent; color: var(--text); text-align: left; font: inherit; cursor: pointer; }
.drill-round:hover { background: var(--panel-muted); }
.drill-round.is-active { border-left-color: var(--brand); background: var(--panel-muted); }
.drill-round strong { font-size: 14px; font-weight: 600; line-height: 1.5; overflow-wrap: anywhere; }
.drill-round.is-active strong { color: var(--brand); }
.drill-round-meta, .drill-round-footer { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; }
.drill-round-meta > span:first-child { font-size: 12px; color: var(--muted); }
.drill-mobile-selector { display: none; min-width: 0; }
.drill-detail { min-width: 0; }
.drill-detail-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding-bottom: 20px; border-bottom: 1px solid var(--line); }
.drill-detail-title { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; min-width: 0; }
.drill-detail-title .drill-label { flex-basis: 100%; }
.drill-detail-title h2 { margin: 0; font-size: 20px; line-height: 1.5; overflow-wrap: anywhere; }
.drill-actions { display: flex; gap: 4px; flex-shrink: 0; }
.drill-actions .el-button { width: 34px; height: 34px; padding: 0; margin-left: 0; }
.drill-facts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px 24px; margin: 22px 0; }
.drill-facts > div, .drill-step-facts > div { min-width: 0; }
.drill-facts dt, .drill-step-facts dt, .drill-step-result > span { color: var(--muted); font-size: 12px; line-height: 1.5; }
.drill-facts dd, .drill-step-facts dd { margin: 6px 0 0; font-size: 14px; line-height: 1.6; overflow-wrap: anywhere; }
.drill-round-result { margin-bottom: 28px; }
.drill-round-result h3, .drill-steps-heading h3 { margin: 0; font-size: 14px; font-weight: 600; }
.drill-round-result p { margin: 10px 0 0; font-size: 14px; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.drill-steps { min-width: 0; border-top: 1px solid var(--line); padding-top: 20px; }
.drill-steps-heading, .drill-steps-heading > div { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; }
.drill-steps-heading > div > span { font-size: 12px; color: var(--muted); }
.drill-step-track { display: flex; gap: 16px; min-width: 0; max-width: 100%; margin: 20px 0 0; padding: 0 0 16px; list-style: none; overflow-x: auto; }
.drill-step { position: relative; display: flex; flex-direction: column; flex: 0 0 260px; min-width: 0; }
.drill-step:not(:last-child)::after { content: ''; position: absolute; top: 13px; left: 28px; width: calc(100% - 12px); height: 1px; background: var(--line); }
.drill-step-number { display: grid; place-items: center; width: 28px; height: 28px; border: 1px solid var(--line); border-radius: 4px; background: var(--panel-bg); color: var(--text); font-size: 12px; font-weight: 600; }
.drill-step-card { display: flex; flex-direction: column; flex: 1; min-width: 0; margin-top: 12px; padding: 16px; border: 1px solid var(--line); border-radius: 6px; background: var(--panel-bg); }
.drill-step-card > header { display: flex; flex-wrap: wrap; align-items: flex-start; justify-content: space-between; gap: 8px; }
.drill-step-card h4 { flex: 1 1 120px; min-width: 0; margin: 0; font-size: 14px; line-height: 1.6; overflow-wrap: anywhere; }
.drill-step-facts { display: grid; gap: 12px; margin: 18px 0; }
.drill-step-result { flex: 1; min-width: 0; }
.drill-step-result p { margin: 6px 0 0; font-size: 13px; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.drill-step-card > footer { justify-content: flex-end; border-top: 1px solid var(--line); margin-top: 16px; padding-top: 8px; }
.drill-form-section { min-width: 0; }
.drill-form-section + .drill-form-section { border-top: 1px solid var(--line); margin-top: 8px; padding-top: 20px; }
.drill-form-section h3 { margin: 0 0 18px; color: var(--text); font-size: 14px; font-weight: 600; }
.drill-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 20px; }
.drill-form-grid > .is-wide { grid-column: 1 / -1; }
.drill-form-section :deep(.el-form-item), .drill-form-section :deep(.el-form-item__content) { min-width: 0; }
.drill-form-section :deep(.el-select), .drill-form-section :deep(.el-date-editor), .drill-form-section :deep(.el-input-number) { width: 100%; min-width: 0; box-sizing: border-box; }
.drill-form-section :deep(.el-range-input) { min-width: 0; }
.drill-form-actions { display: flex; justify-content: flex-end; gap: 12px; }
.drill-form-actions .el-button { margin-left: 0; }
:global(.drill-round-dialog.el-dialog) { display: flex; flex-direction: column; max-height: calc(100dvh - 24px); margin-bottom: 12px; }
:global(.drill-round-dialog .el-dialog__body) { flex: 1; min-height: 0; overflow-y: auto; }
:global(.drill-round-dialog .el-dialog__header), :global(.drill-round-dialog .el-dialog__footer) { flex-shrink: 0; }
:global(.drill-round-dialog .el-dialog__footer), :global(.drill-step-drawer .el-drawer__footer) { border-top: 1px solid var(--line); padding-top: 16px; }
:global(.drill-step-drawer .el-drawer__header) { margin-bottom: 0; padding-bottom: 20px; border-bottom: 1px solid var(--line); }
:global(.drill-step-drawer .el-drawer__body) { min-height: 0; overflow-y: auto; }
@media (max-width: 1050px) {
  .drill-layout { grid-template-columns: 220px minmax(0, 1fr); gap: 16px; }
  .drill-facts { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 760px) {
  .drill-form-grid { grid-template-columns: minmax(0, 1fr); }
  :global(.drill-round-dialog.el-dialog) { margin-top: 12px; max-height: calc(100dvh - 24px); }
  :global(.drill-step-drawer .el-drawer__body) { padding: 16px; }
  .drill-page { gap: 16px; }
  .drill-layout { grid-template-columns: minmax(0, 1fr); gap: 20px; }
  .drill-sidebar { display: none; }
  .drill-mobile-selector { display: grid; gap: 8px; }
  .drill-mobile-selector label { font-size: 13px; color: var(--muted); }
  .drill-mobile-selector .el-select { width: 100%; }
  .drill-detail-title h2 { font-size: 18px; }
  .drill-detail-heading { flex-wrap: wrap; gap: 12px; }
  .drill-facts { gap: 16px; }
  .drill-step-track { flex-direction: column; overflow-x: visible; padding-bottom: 0; }
  .drill-step { display: grid; grid-template-columns: 28px minmax(0, 1fr); gap: 12px; flex: none; width: 100%; }
  .drill-step:not(:last-child)::after { top: 28px; left: 13px; width: 1px; height: calc(100% - 12px); }
  .drill-step-card { margin-top: 0; }
  .drill-step-facts { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .drill-step-facts > div:first-child { grid-column: 1 / -1; }
}
</style>
