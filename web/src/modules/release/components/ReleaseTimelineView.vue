<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiErrorMessage } from '../../../api/error'
import {
  createReleaseTimelineItem,
  deleteReleaseTimelineItem,
  getReleaseTimeline,
  listReleaseOperationMemberOptions,
  saveReleaseTimeline,
  updateReleaseTimelineItem,
  type ReleaseMemberOptionDto,
  type ReleaseTimelineDto,
  type ReleaseTimelineItemDto,
  type ReleaseTimelineItemWrite,
  type ReleaseTimelineType
} from '../../../api/release'
import { useAuthStore } from '../../../stores/auth'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'

const props = defineProps<{ timelineType: ReleaseTimelineType; projectId: number }>()
const auth = useAuthStore()
const timeline = ref<ReleaseTimelineDto | null>(null)
const members = ref<ReleaseMemberOptionDto[]>([])
const loading = ref(false)
const savingHeader = ref(false)
const savingItem = ref(false)
const error = ref('')
const forbidden = ref(false)
const itemDialogOpen = ref(false)
const editingItem = ref<ReleaseTimelineItemDto | null>(null)
const headerForm = reactive({ timelineName: '', description: '' })
const itemForm = reactive<ReleaseTimelineItemWrite>({ seqNo: undefined, itemName: '', plannedStart: '', plannedEnd: '', ownerId: undefined, status: 'PENDING', description: '', rowVersion: 0 })
const canManage = computed(() => auth.hasPermission(props.timelineType === 'ROLLBACK' ? 'release-operations:rollback-timeline:manage' : 'release-operations:timeline:manage'))
const title = computed(() => props.timelineType === 'ROLLBACK' ? '投产回退时序' : '投产时序')

function projectId() { return props.projectId }
function minute(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '未安排' }
function isForbidden(cause: unknown) { return (cause as { response?: { status?: number } }).response?.status === 403 }

async function load() {
  if (!projectId()) return
  loading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const responses = await Promise.all([
      getReleaseTimeline(projectId(), props.timelineType),
      listReleaseOperationMemberOptions(projectId())
    ])
    timeline.value = responses[0].data.data
    members.value = responses[1].data.data
    headerForm.timelineName = timeline.value?.timelineName || (props.timelineType === 'ROLLBACK' ? '投产回退时序' : '投产时序')
    headerForm.description = timeline.value?.description || ''
  } catch (cause) {
    timeline.value = null
    members.value = []
    error.value = apiErrorMessage(cause, `${title.value}加载失败，请稍后重试`)
    forbidden.value = isForbidden(cause)
  } finally { loading.value = false }
}

async function saveHeader() {
  if (!canManage.value || savingHeader.value || !headerForm.timelineName.trim()) return
  savingHeader.value = true
  try {
    timeline.value = (await saveReleaseTimeline(projectId(), props.timelineType, { ...headerForm, rowVersion: timeline.value?.rowVersion || 0 })).data.data
    headerForm.timelineName = timeline.value.timelineName
    headerForm.description = timeline.value.description || ''
    ElMessage.success(`${title.value}已保存`)
  } catch (cause) { ElMessage.error(apiErrorMessage(cause, `${title.value}保存失败，请重试`))
  } finally { savingHeader.value = false }
}

function openItem(item?: ReleaseTimelineItemDto) {
  editingItem.value = item || null
  itemForm.seqNo = item?.seqNo || undefined
  itemForm.itemName = item?.itemName || ''
  itemForm.plannedStart = item?.plannedStart || ''
  itemForm.plannedEnd = item?.plannedEnd || ''
  itemForm.ownerId = item?.ownerId
  itemForm.status = item?.status || 'PENDING'
  itemForm.description = item?.description || ''
  itemForm.rowVersion = item?.rowVersion || 0
  itemDialogOpen.value = true
}

async function saveItem() {
  if (!timeline.value || !itemForm.itemName.trim() || savingItem.value) return
  if (itemForm.plannedStart && itemForm.plannedEnd && itemForm.plannedEnd < itemForm.plannedStart) { ElMessage.warning('计划结束时间不能早于开始时间'); return }
  savingItem.value = true
  try {
    const response = editingItem.value
      ? await updateReleaseTimelineItem(projectId(), props.timelineType, editingItem.value.id, { ...itemForm })
      : await createReleaseTimelineItem(projectId(), props.timelineType, { ...itemForm })
    const nextItems = timeline.value.items.filter(item => item.id !== response.data.data.id)
    nextItems.push(response.data.data)
    timeline.value = { ...timeline.value, items: nextItems.sort((a, b) => a.seqNo - b.seqNo || a.id - b.id) }
    itemDialogOpen.value = false
    ElMessage.success(editingItem.value ? '时序明细已更新' : '时序明细已添加')
  } catch (cause) { ElMessage.error(apiErrorMessage(cause, '时序明细保存失败，请重试'))
  } finally { savingItem.value = false }
}

async function removeItem(item: ReleaseTimelineItemDto) {
  try { await ElMessageBox.confirm(`将删除“${item.itemName}”，删除后不可恢复。`, '删除时序明细', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) } catch { return }
  try {
    await deleteReleaseTimelineItem(projectId(), props.timelineType, item.id, item.rowVersion)
    if (timeline.value) timeline.value = { ...timeline.value, items: timeline.value.items.filter(value => value.id !== item.id) }
    ElMessage.success('时序明细已删除')
  } catch (cause) { ElMessage.error(apiErrorMessage(cause, '时序明细删除失败，请刷新后重试')) }
}

onMounted(load)
</script>

<template>
  <div class="release-operations-layout">
    <div v-if="forbidden" class="release-operations-state"><el-result icon="warning" :title="`无权查看${title}`" sub-title="请联系项目管理员申请查看权限。" /></div>
    <div v-else-if="error" class="release-operations-state release-operations-state--error"><el-result icon="error" :title="`${title}加载失败`" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></div>
    <template v-else>
      <section v-loading="loading" class="release-operations-panel">
        <header class="release-operations-panel__header"><div><span class="release-operations-kicker">{{ props.timelineType }} TIMELINE</span><h2>{{ title }}</h2><p>维护节点顺序、负责人和计划时间，横向查看投产操作节奏。</p></div><el-button v-if="canManage" type="primary" :loading="savingHeader" @click="saveHeader">保存时序</el-button></header>
        <div class="release-operations-panel__body"><el-form label-position="top" class="release-operations-form-grid"><el-form-item label="时序名称" required><el-input v-model="headerForm.timelineName" maxlength="128" :disabled="!canManage" /></el-form-item><el-form-item label="说明"><el-input v-model="headerForm.description" maxlength="2000" :disabled="!canManage" placeholder="补充执行范围或注意事项" /></el-form-item></el-form></div>
        <div v-if="timeline?.items.length" class="release-operations-timeline"><div class="release-operations-timeline__track"><article v-for="(item, index) in timeline.items" :key="item.id" class="release-operations-timeline__item"><span class="release-operations-timeline__dot">{{ item.seqNo || index + 1 }}</span><div class="release-operations-timeline__card"><strong>{{ item.itemName }}</strong><small>{{ minute(item.plannedStart) }} - {{ minute(item.plannedEnd) }}</small><small>{{ item.ownerName || '未指定负责人' }} · {{ item.status }}</small><p v-if="item.description">{{ item.description }}</p><div v-if="canManage" class="release-operations-timeline__actions"><el-button link :icon="Edit" aria-label="编辑时序明细" @click="openItem(item)" /><el-button link type="danger" :icon="Delete" aria-label="删除时序明细" @click="removeItem(item)" /></div></div></article></div></div>
        <div v-else class="release-operations-empty"><UiEmptyState v-if="timeline" title="暂无时序明细" description="保存时序信息后，添加第一个投产节点。"><template #action><el-button v-if="canManage" type="primary" @click="openItem()">新增明细</el-button></template></UiEmptyState><UiEmptyState v-else title="尚未建立时序" description="先保存时序名称，再添加可排序的时序节点。" /></div>
        <footer v-if="timeline && canManage" class="release-operations-panel__body"><el-button type="primary" :icon="Plus" @click="openItem">新增时序明细</el-button><span class="release-operations-muted">共 {{ timeline.items.length }} 个节点</span></footer>
      </section>
    </template>
  </div>

  <el-dialog v-model="itemDialogOpen" :title="editingItem ? '编辑时序明细' : '新增时序明细'" width="650px" destroy-on-close>
    <el-form label-position="top" class="release-operations-dialog-form release-operations-form-grid"><el-form-item label="序号"><el-input-number v-model="itemForm.seqNo" :min="1" :max="999" controls-position="right" /></el-form-item><el-form-item label="节点名称" required><el-input v-model="itemForm.itemName" maxlength="128" /></el-form-item><el-form-item label="计划开始"><el-date-picker v-model="itemForm.plannedStart" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item><el-form-item label="计划结束"><el-date-picker v-model="itemForm.plannedEnd" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item><el-form-item label="负责人"><el-select v-model="itemForm.ownerId" clearable placeholder="选择当前项目成员"><el-option v-for="member in members" :key="member.userId" :value="member.userId" :label="`${member.displayName}（${member.username}）`" /></el-select></el-form-item><el-form-item label="状态"><el-select v-model="itemForm.status"><el-option label="待开始" value="PENDING" /><el-option label="进行中" value="RUNNING" /><el-option label="已完成" value="COMPLETED" /><el-option label="已跳过" value="SKIPPED" /></el-select></el-form-item><el-form-item label="节点说明" class="is-wide"><el-input v-model="itemForm.description" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item></el-form>
    <template #footer><el-button @click="itemDialogOpen = false">取消</el-button><el-button type="primary" :loading="savingItem" :disabled="!itemForm.itemName.trim()" @click="saveItem">保存</el-button></template>
  </el-dialog>
</template>
