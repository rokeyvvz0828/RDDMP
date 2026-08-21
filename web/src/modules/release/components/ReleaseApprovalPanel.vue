<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Check, Close, Lock, Refresh, RefreshLeft, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiErrorMessage } from '../../../api/error'
import { decideWorkflowTask, getCurrentWorkflowTaskContext, getWorkflowTaskContext, type WorkflowTaskAction, type WorkflowTaskContext } from '../../../api/workflow'
import { useAuthStore } from '../../../stores/auth'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'

type PanelState = 'idle' | 'loading' | 'ready' | 'readonly' | 'forbidden' | 'stale' | 'error'
type ApprovalAction = Extract<WorkflowTaskAction, 'APPROVE' | 'RETURN' | 'REJECT'>

const props = defineProps<{ applicationCode: string; taskId?: number }>()
const emit = defineEmits<{ decided: [] }>()
const auth = useAuthStore()
const panelState = ref<PanelState>('idle')
const context = ref<WorkflowTaskContext | null>(null)
const opinion = ref('')
const signed = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const activeTaskId = ref<number>()
const signerName = computed(() => auth.user?.displayName || auth.user?.username || '当前登录用户')
const actions = computed(() => (context.value?.allowed_actions || []).filter((action): action is ApprovalAction => ['APPROVE', 'RETURN', 'REJECT'].includes(action)))
const actionMeta: Record<ApprovalAction, { label: string; type: 'primary' | 'warning' | 'danger'; plain?: boolean }> = {
  APPROVE: { label: '同意', type: 'primary' },
  RETURN: { label: '退回修改', type: 'warning', plain: true },
  REJECT: { label: '不通过', type: 'danger', plain: true }
}

function errorStatus(error: unknown) {
  return (error as { response?: { status?: number } })?.response?.status
}

async function loadContext() {
  context.value = null
  activeTaskId.value = undefined
  errorMessage.value = ''
  panelState.value = 'loading'
  try {
    const next = props.taskId
      ? (await getWorkflowTaskContext(props.taskId)).data.data
      : (await getCurrentWorkflowTaskContext('release_application', props.applicationCode)).data.data
    if (!next) {
      panelState.value = 'idle'
      return
    }
    if (next.business_key.toUpperCase() !== props.applicationCode.toUpperCase()) {
      panelState.value = 'error'
      errorMessage.value = '待办任务与当前版本申请不一致，审批操作已关闭。'
      return
    }
    context.value = next
    activeTaskId.value = next.task_id
    panelState.value = next.actionable ? 'ready' : 'readonly'
  } catch (error) {
    const status = errorStatus(error)
    panelState.value = status === 403 ? 'forbidden' : status === 409 ? 'stale' : 'error'
    errorMessage.value = apiErrorMessage(error, '审批任务加载失败，请稍后重试。')
  }
}

async function submit(action: ApprovalAction) {
  const current = context.value
  const taskId = activeTaskId.value
  if (!taskId || !current || panelState.value !== 'ready') return
  if (!opinion.value.trim()) {
    ElMessage.warning('请填写审批意见')
    return
  }
  if (current.signature_required && !signed.value) {
    ElMessage.warning('请使用当前登录身份确认签署')
    return
  }

  const meta = actionMeta[action]
  await ElMessageBox.confirm(`确认对 ${props.applicationCode} 执行“${meta.label}”？`, '提交审批', {
    type: action === 'APPROVE' ? 'success' : action === 'REJECT' ? 'error' : 'warning',
    confirmButtonText: '确认提交',
    cancelButtonText: '取消'
  })

  const submittedOpinion = opinion.value.trim()
  submitting.value = true
  try {
    await decideWorkflowTask(taskId, action, submittedOpinion, { signatureConfirmed: current.signature_required ? signed.value : undefined })
    opinion.value = ''
    signed.value = false
    ElMessage.success(`审批已${meta.label}`)
    await loadContext()
    emit('decided')
  } catch (error) {
    const status = errorStatus(error)
    if (status === 409) {
      panelState.value = 'stale'
      errorMessage.value = '该任务状态已变化，审批意见已保留，请刷新后确认。'
    } else if (status === 403) {
      panelState.value = 'forbidden'
      errorMessage.value = '当前账号无权处理该任务，页面已切换为只读。'
    } else {
      errorMessage.value = apiErrorMessage(error, '审批提交失败，请稍后重试。')
      ElMessage.error(errorMessage.value)
    }
  } finally {
    submitting.value = false
  }
}

watch(() => [props.taskId, props.applicationCode], () => { void loadContext() }, { immediate: true })
</script>

<template>
  <aside class="release-approval-panel" aria-label="审批操作">
    <header>
      <div><span class="release-panel-kicker">当前任务</span><strong>审批操作</strong></div>
      <UiStatusTag v-if="context" :value="context.actionable ? '待处理' : '已处理'" :tone="context.actionable ? 'primary' : 'info'" />
    </header>

    <div v-if="panelState === 'loading'" class="release-approval-state"><el-skeleton :rows="4" animated /></div>
    <div v-else-if="panelState === 'idle'" class="release-approval-state is-muted"><el-icon><Lock /></el-icon><strong>当前账号暂无待审批任务</strong><span>申请信息可正常查看；流程流转到当前账号后，此处会自动显示审批操作。</span></div>
    <div v-else-if="panelState === 'forbidden'" class="release-approval-state is-warning"><el-icon><WarningFilled /></el-icon><strong>无权处理当前任务</strong><span>{{ errorMessage }}</span></div>
    <div v-else-if="panelState === 'stale'" class="release-approval-state is-warning"><el-icon><WarningFilled /></el-icon><strong>任务状态已变化</strong><span>{{ errorMessage }}</span><el-button :icon="Refresh" @click="loadContext">刷新任务状态</el-button></div>
    <div v-else-if="panelState === 'error'" class="release-approval-state is-warning"><el-icon><WarningFilled /></el-icon><strong>审批任务不可用</strong><span>{{ errorMessage }}</span><el-button :icon="Refresh" @click="loadContext">重新加载</el-button></div>
    <div v-else-if="panelState === 'readonly'" class="release-approval-state is-muted"><el-icon><Check /></el-icon><strong>当前任务已处理</strong><span>{{ context?.node_name || '该审批节点' }}不再接受操作，可继续查看申请和流转记录。</span></div>

    <template v-else-if="context">
      <dl class="release-approval-context">
        <div><dt>当前节点</dt><dd>{{ context.node_name || context.task_key }}</dd></div>
        <div><dt>审批轮次</dt><dd>第 {{ context.business_round || 1 }} 轮</dd></div>
      </dl>
      <el-form label-position="top" class="release-approval-form">
        <el-form-item label="审批意见" required><el-input v-model="opinion" type="textarea" :rows="5" maxlength="300" show-word-limit placeholder="填写本次审批意见" /></el-form-item>
      </el-form>
      <label v-if="context.signature_required" class="release-sign-confirm"><el-checkbox v-model="signed" :disabled="submitting" /><span><strong>确认签署</strong><small><el-icon><Lock /></el-icon>使用当前登录身份：{{ signerName }}</small></span></label>
      <div v-if="actions.length" class="release-review-buttons" :style="{ gridTemplateColumns: `repeat(${actions.length}, minmax(0, 1fr))` }">
        <el-button v-for="action in actions" :key="action" :class="`is-action-${action.toLowerCase()}`" :type="actionMeta[action].type" :plain="actionMeta[action].plain" :loading="submitting" :disabled="submitting" @click="submit(action)"><el-icon v-if="action === 'APPROVE'"><Check /></el-icon><el-icon v-else-if="action === 'RETURN'"><RefreshLeft /></el-icon><el-icon v-else><Close /></el-icon>{{ actionMeta[action].label }}</el-button>
      </div>
      <div v-else class="release-approval-state is-muted"><strong>当前节点无业务审批动作</strong><span>请由流程管理员检查节点动作配置。</span></div>
    </template>
  </aside>
</template>
