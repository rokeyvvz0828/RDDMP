<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Clock, Delete, EditPen, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiErrorMessage } from '../../../api/error'
import {
  listPublishedReleaseWorkflows,
  listReleaseWorkflowBindingHistory,
  listReleaseWorkflowBindings,
  updateReleaseWorkflowBinding,
  type ReleasePublishedWorkflowDto,
  type ReleaseWorkflowBindingDto,
  type ReleaseWorkflowBindingHistoryDto
} from '../../../api/release'
import type { ProjectContextItem } from '../../../types/project-context'

const props = defineProps<{ project: ProjectContextItem; canUpdate: boolean }>()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const rows = ref<ReleaseWorkflowBindingDto[]>([])
const definitions = ref<ReleasePublishedWorkflowDto[]>([])
const editOpen = ref(false)
const editing = ref<ReleaseWorkflowBindingDto | null>(null)
const form = reactive<{ workflowDefinitionId?: number; reason: string }>({ workflowDefinitionId: undefined, reason: '' })
const historyOpen = ref(false)
const historyLoading = ref(false)
const historyScene = ref('')
const history = ref<ReleaseWorkflowBindingHistoryDto[]>([])
let loadRequestId = 0
let historyRequestId = 0

const configuredCount = computed(() => rows.value.filter(row => row.valid).length)

async function load() {
  const projectRef = props.project.ref
  const requestId = ++loadRequestId
  loading.value = true
  error.value = ''
  try {
    const [bindingResponse, definitionResponse] = await Promise.all([
      listReleaseWorkflowBindings(projectRef),
      listPublishedReleaseWorkflows()
    ])
    if (requestId !== loadRequestId || projectRef !== props.project.ref) return
    rows.value = bindingResponse.data.data
    definitions.value = definitionResponse.data.data
  } catch (loadError) {
    if (requestId !== loadRequestId || projectRef !== props.project.ref) return
    rows.value = []
    definitions.value = []
    error.value = apiErrorMessage(loadError, '审批流程配置加载失败')
  } finally {
    if (requestId === loadRequestId) loading.value = false
  }
}

function openEdit(row: ReleaseWorkflowBindingDto) {
  editing.value = row
  form.workflowDefinitionId = row.workflowDefinitionId
  form.reason = ''
  editOpen.value = true
}

async function save() {
  if (!editing.value || !form.workflowDefinitionId) {
    ElMessage.warning('请选择已发布流程')
    return
  }
  if (!form.reason.trim()) {
    ElMessage.warning('请填写修改原因')
    return
  }
  saving.value = true
  const projectRef = props.project.ref
  try {
    await updateReleaseWorkflowBinding(editing.value.sceneCode, {
      projectRef,
      projectName: props.project.name,
      workflowDefinitionId: form.workflowDefinitionId,
      rowVersion: editing.value.rowVersion,
      reason: form.reason.trim()
    })
    if (props.project.ref !== projectRef) return
    editOpen.value = false
    ElMessage.success('审批流程配置已保存')
    await load()
  } catch (saveError) {
    ElMessage.error(apiErrorMessage(saveError, '审批流程配置保存失败'))
  } finally {
    saving.value = false
  }
}

async function unbind(row: ReleaseWorkflowBindingDto) {
  const projectRef = props.project.ref
  try {
    const answer = await ElMessageBox.prompt(`解除“${row.sceneName}”的流程绑定`, '解除绑定', {
      inputType: 'textarea',
      inputPlaceholder: '填写解除原因',
      inputValidator: value => Boolean(value?.trim()) || '请填写解除原因',
      confirmButtonText: '确认解除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await updateReleaseWorkflowBinding(row.sceneCode, {
      projectRef,
      projectName: props.project.name,
      workflowDefinitionId: undefined,
      rowVersion: row.rowVersion,
      reason: answer.value.trim()
    })
    if (props.project.ref !== projectRef) return
    ElMessage.success('流程绑定已解除')
    await load()
  } catch (unbindError) {
    if (!String(unbindError).includes('cancel')) ElMessage.error(apiErrorMessage(unbindError, '解除绑定失败'))
  }
}

async function openHistory(row: ReleaseWorkflowBindingDto) {
  const projectRef = props.project.ref
  const requestId = ++historyRequestId
  historyScene.value = row.sceneName
  history.value = []
  historyOpen.value = true
  historyLoading.value = true
  try {
    const response = await listReleaseWorkflowBindingHistory(row.sceneCode, projectRef)
    if (requestId !== historyRequestId || props.project.ref !== projectRef) return
    history.value = response.data.data
  } catch (historyError) {
    if (requestId !== historyRequestId || props.project.ref !== projectRef) return
    ElMessage.error(apiErrorMessage(historyError, '变更历史加载失败'))
  } finally {
    if (requestId === historyRequestId) historyLoading.value = false
  }
}

function workflowLabel(name?: string, code?: string, version?: number) {
  if (!name) return '未配置'
  return `${name} / ${code || '-'} / V${version || '-'}`
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

watch(() => props.project.ref, () => {
  loadRequestId += 1
  historyRequestId += 1
  rows.value = []
  definitions.value = []
  history.value = []
  editing.value = null
  editOpen.value = false
  historyOpen.value = false
  loading.value = false
  historyLoading.value = false
  error.value = ''
  void load()
})
onMounted(load)
</script>

<template>
  <section class="release-workflow-config">
    <header class="release-workflow-config__header">
      <div>
        <span class="release-panel-kicker">{{ project.shortName || project.name }}</span>
        <h2>审批流程配置</h2>
      </div>
      <div class="release-workflow-config__summary">
        <span>{{ configuredCount }} / {{ rows.length || 5 }} 已生效</span>
        <el-button :icon="Refresh" :loading="loading" circle title="刷新" @click="load" />
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div class="release-workflow-config__table">
      <el-table v-loading="loading" :data="rows" row-key="sceneCode">
        <el-table-column label="业务场景" min-width="145">
          <template #default="{ row }">
            <div class="release-workflow-scene"><strong>{{ row.sceneName }}</strong><code>{{ row.sceneCode }}</code></div>
          </template>
        </el-table-column>
        <el-table-column label="已绑定流程" min-width="262">
          <template #default="{ row }">
            <div class="release-workflow-definition">
              <strong>{{ workflowLabel(row.workflowName, row.workflowCode, row.workflowVersion) }}</strong>
              <small v-if="row.workflowDefinitionId">定义 ID {{ row.workflowDefinitionId }}</small>
              <small v-else>{{ row.invalidReason }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-tag v-if="row.valid" type="success" effect="plain">已生效</el-tag>
            <el-tooltip v-else :content="row.invalidReason" placement="top"><el-tag type="warning" effect="plain">{{ row.configured ? '已失效' : '未配置' }}</el-tag></el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="最近变更" width="145">
          <template #default="{ row }"><span class="release-muted">{{ formatTime(row.updatedAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canUpdate" :icon="EditPen" link type="primary" @click="openEdit(row)">配置</el-button>
            <el-button :icon="Clock" link @click="openHistory(row)">历史</el-button>
            <el-button v-if="canUpdate && row.configured" :icon="Delete" link type="danger" @click="unbind(row)" title="解除绑定" />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="editOpen" :title="`配置${editing?.sceneName || ''}审批流程`" width="520px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="已发布流程" required>
          <el-select v-model="form.workflowDefinitionId" filterable placeholder="选择流程" style="width: 100%">
            <el-option v-for="item in definitions" :key="item.definitionId" :value="item.definitionId" :label="`${item.workflowName} / ${item.workflowCode} / V${item.workflowVersion}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="修改原因" required>
          <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="editOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存配置</el-button></template>
    </el-dialog>

    <el-drawer v-model="historyOpen" :title="`${historyScene}变更历史`" size="520px">
      <div v-loading="historyLoading" class="release-workflow-history">
        <el-empty v-if="!historyLoading && !history.length" description="暂无变更记录" />
        <article v-for="item in history" :key="item.id">
          <header><strong>{{ item.operatorName }}</strong><time>{{ formatTime(item.occurredAt) }}</time></header>
          <div><span>{{ workflowLabel(item.beforeWorkflowName, item.beforeWorkflowCode, item.beforeWorkflowVersion) }}</span><b>→</b><span>{{ workflowLabel(item.afterWorkflowName, item.afterWorkflowCode, item.afterWorkflowVersion) }}</span></div>
          <p>{{ item.reason }}</p>
        </article>
      </div>
    </el-drawer>
  </section>
</template>
