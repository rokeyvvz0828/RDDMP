<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Edit, Plus, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiErrorMessage } from '../../../api/error'
import {
  createReleaseDrillRound,
  deleteReleaseDrillRound,
  getReleaseDrillPlan,
  saveReleaseDrillPlan,
  updateReleaseDrillRound,
  type ReleaseDrillPlanDto,
  type ReleaseDrillRoundDto,
  type ReleaseDrillRoundWrite,
  type ReleaseDrillStatus
} from '../../../api/release'
import { useAuthStore } from '../../../stores/auth'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'

const props = defineProps<{ projectId: number }>()
const auth = useAuthStore()
const plan = ref<ReleaseDrillPlanDto | null>(null)
const loading = ref(false)
const savingPlan = ref(false)
const savingRound = ref(false)
const error = ref('')
const forbidden = ref(false)
const roundDialogOpen = ref(false)
const editingRound = ref<ReleaseDrillRoundDto | null>(null)
const planForm = reactive({ scenarioContent: '', environmentContent: '' })
const roundForm = reactive<ReleaseDrillRoundWrite>({ roundName: '', plannedAt: '', status: 'PLANNED', resultContent: '', rowVersion: 0 })
const canManage = computed(() => auth.hasPermission('release-operations:drill:manage'))
const statusLabels: Record<ReleaseDrillStatus, string> = { PLANNED: '待演练', RUNNING: '演练中', COMPLETED: '已完成' }

function projectId() { return props.projectId }
function minute(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '未安排' }
function tone(status: ReleaseDrillStatus) { return status === 'COMPLETED' ? 'success' : status === 'RUNNING' ? 'warning' : 'info' }
function isForbidden(cause: unknown) { return (cause as { response?: { status?: number } }).response?.status === 403 }

async function load() {
  if (!projectId()) return
  loading.value = true
  error.value = ''
  forbidden.value = false
  try {
    plan.value = (await getReleaseDrillPlan(projectId())).data.data
    planForm.scenarioContent = plan.value?.scenarioContent || ''
    planForm.environmentContent = plan.value?.environmentContent || ''
  } catch (cause) {
    plan.value = null
    error.value = apiErrorMessage(cause, '投产演练计划加载失败，请稍后重试')
    forbidden.value = isForbidden(cause)
  } finally { loading.value = false }
}

async function savePlan() {
  if (!canManage.value || savingPlan.value) return
  savingPlan.value = true
  error.value = ''
  try {
    plan.value = (await saveReleaseDrillPlan(projectId(), { ...planForm, rowVersion: plan.value?.rowVersion || 0 })).data.data
    planForm.scenarioContent = plan.value.scenarioContent || ''
    planForm.environmentContent = plan.value.environmentContent || ''
    ElMessage.success('演练方案已保存')
  } catch (cause) { error.value = apiErrorMessage(cause, '演练方案保存失败，请重试')
  } finally { savingPlan.value = false }
}

function openRound(round?: ReleaseDrillRoundDto) {
  editingRound.value = round || null
  roundForm.roundName = round?.roundName || `第 ${(plan.value?.rounds.length || 0) + 1} 轮演练`
  roundForm.plannedAt = round?.plannedAt || ''
  roundForm.status = round?.status || 'PLANNED'
  roundForm.resultContent = round?.resultContent || ''
  roundForm.rowVersion = round?.rowVersion || 0
  roundDialogOpen.value = true
}

async function saveRound() {
  if (!roundForm.roundName.trim() || savingRound.value) return
  savingRound.value = true
  try {
    const response = editingRound.value
      ? await updateReleaseDrillRound(projectId(), editingRound.value.id, { ...roundForm })
      : await createReleaseDrillRound(projectId(), { ...roundForm })
    if (plan.value) {
      const next = plan.value.rounds.filter(item => item.id !== response.data.data.id)
      next.push(response.data.data)
      plan.value = { ...plan.value, rounds: next.sort((a, b) => a.roundNo - b.roundNo) }
    } else await load()
    roundDialogOpen.value = false
    ElMessage.success(editingRound.value ? '演练轮次已更新' : '演练轮次已添加')
  } catch (cause) { ElMessage.error(apiErrorMessage(cause, '演练轮次保存失败，请重试'))
  } finally { savingRound.value = false }
}

async function removeRound(round: ReleaseDrillRoundDto) {
  try { await ElMessageBox.confirm(`将删除“${round.roundName}”，删除后不可恢复。`, '删除演练轮次', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) } catch { return }
  try {
    await deleteReleaseDrillRound(projectId(), round.id, round.rowVersion)
    if (plan.value) plan.value = { ...plan.value, rounds: plan.value.rounds.filter(item => item.id !== round.id) }
    ElMessage.success('演练轮次已删除')
  } catch (cause) { ElMessage.error(apiErrorMessage(cause, '演练轮次删除失败，请刷新后重试')) }
}

onMounted(load)
</script>

<template>
  <div class="release-operations-layout">
    <div v-if="forbidden" class="release-operations-state"><el-result icon="warning" title="无权查看投产演练计划" sub-title="请联系项目管理员申请查看权限。" /></div>
    <div v-else-if="error" class="release-operations-state release-operations-state--error"><el-result icon="error" title="投产演练计划加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></div>
    <template v-else>
      <div v-loading="loading" class="release-operations-grid">
        <section class="release-operations-panel">
          <header class="release-operations-panel__header"><div><span class="release-operations-kicker">DRILL BRIEF</span><h2>演练方案</h2><p>记录本项目投产演练的目标、范围与执行约束。</p></div><el-button v-if="canManage" type="primary" :loading="savingPlan" @click="savePlan">保存方案</el-button></header>
          <div class="release-operations-panel__body"><el-form label-position="top"><el-form-item label="演练方案"><el-input v-model="planForm.scenarioContent" type="textarea" :rows="7" maxlength="4000" show-word-limit :disabled="!canManage" placeholder="填写演练目标、步骤、验收标准和参与范围" /></el-form-item><el-form-item label="环境搭建说明"><el-input v-model="planForm.environmentContent" type="textarea" :rows="7" maxlength="4000" show-word-limit :disabled="!canManage" placeholder="填写环境、账号、数据准备和回收说明" /></el-form-item></el-form><div v-if="!plan && !loading" class="release-operations-muted">当前项目还没有保存过演练方案，填写后保存即可创建。</div></div>
        </section>
        <section class="release-operations-panel">
          <header class="release-operations-panel__header"><div><span class="release-operations-kicker">ROUND TRACKER</span><h2>N轮演练</h2><p>{{ plan?.rounds.length || 0 }} 个演练轮次，按轮次编号展示。</p></div><el-button v-if="canManage" :icon="Plus" type="primary" plain :disabled="!plan" @click="openRound()">新增轮次</el-button></header>
          <div class="release-operations-panel__body">
            <UiEmptyState v-if="!plan && !loading" title="请先保存演练方案" description="建立演练方案后即可添加多轮演练记录。" />
            <UiEmptyState v-else-if="plan && !plan.rounds.length && !loading" title="暂无演练轮次" description="按实际演练安排添加第一轮记录。"><template #action><el-button v-if="canManage" type="primary" @click="openRound()">新增轮次</el-button></template></UiEmptyState>
            <div v-else class="release-operations-rounds"><article v-for="round in plan?.rounds" :key="round.id" class="release-operations-round"><span class="release-operations-round__number">{{ round.roundNo }}</span><div class="release-operations-round__main"><strong>{{ round.roundName }}</strong><small>{{ minute(round.plannedAt) }} · <UiStatusTag :value="statusLabels[round.status]" :tone="tone(round.status)" /></small><small v-if="round.resultContent">{{ round.resultContent }}</small></div><div v-if="canManage" class="release-operations-round__actions"><el-button link :icon="Edit" aria-label="编辑演练轮次" @click="openRound(round)" /><el-button link type="danger" :icon="Delete" aria-label="删除演练轮次" @click="removeRound(round)" /></div></article></div>
          </div>
        </section>
      </div>
    </template>
  </div>

  <el-dialog v-model="roundDialogOpen" :title="editingRound ? '编辑演练轮次' : '新增演练轮次'" width="560px" destroy-on-close>
    <el-form label-position="top" class="release-operations-dialog-form"><el-form-item label="轮次名称" required><el-input v-model="roundForm.roundName" maxlength="128" /></el-form-item><el-form-item label="计划时间"><el-date-picker v-model="roundForm.plannedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择计划时间" /></el-form-item><el-form-item label="演练状态"><el-select v-model="roundForm.status"><el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item><el-form-item label="结果记录"><el-input v-model="roundForm.resultContent" type="textarea" :rows="4" maxlength="4000" show-word-limit /></el-form-item></el-form>
    <template #footer><el-button @click="roundDialogOpen = false">取消</el-button><el-button type="primary" :loading="savingRound" :disabled="!roundForm.roundName.trim()" @click="saveRound">保存</el-button></template>
  </el-dialog>
</template>
