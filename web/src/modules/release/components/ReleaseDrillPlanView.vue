<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiErrorMessage } from '../../../api/error'
import { createReleasePlan, createReleasePlanItem, createReleasePlanTimeline, deleteReleasePlan, deleteReleasePlanItem, deleteReleasePlanTimeline, listReleaseOperationMemberOptions, listReleasePlans, updateReleasePlan, updateReleasePlanItem, updateReleasePlanTimeline, type ReleaseMemberOptionDto, type ReleasePlanDto, type ReleasePlanItemDto, type ReleasePlanItemType, type ReleasePlanTimelineDto } from '../../../api/release'
import { useAuthStore } from '../../../stores/auth'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'

const props = defineProps<{ projectId: number }>()
const auth = useAuthStore()
const plans = ref<ReleasePlanDto[]>([])
const members = ref<ReleaseMemberOptionDto[]>([])
const selectedId = ref<number>()
const loading = ref(false)
const error = ref('')
const forbidden = ref(false)
const saving = ref(false)
const itemSaving = ref(false)
const timelineSaving = ref(false)
const planDialog = ref(false)
const timelineDrawer = ref(false)
const timelineDialog = ref(false)
const itemDrawer = ref(false)
const editingPlan = ref<ReleasePlanDto | null>(null)
const editingTimeline = ref<ReleasePlanTimelineDto | null>(null)
const editingItem = ref<ReleasePlanItemDto | null>(null)
const timelineType = ref<ReleasePlanItemType>('NORMAL')
const planForm = reactive({ planName: '', planCode: '', versionNo: '', status: 'DRAFT', description: '', rowVersion: 0 })
const timelineForm = reactive({ timelineName: '' })
const sequenceForm = reactive({ seqNo: undefined as number | undefined, timelineName: '', description: '', rowVersion: 0 })
const itemForm = reactive({ timelineId: undefined as number | undefined, seqNo: undefined as number | undefined, itemName: '', range: [] as string[], ownerId: undefined as number | undefined, status: 'PENDING', description: '', rowVersion: 0 })
const canManage = computed(() => auth.hasPermission('release-operations:plan:manage'))
const selectedPlan = computed(() => plans.value.find(item => item.id === selectedId.value) || plans.value[0])
const timelineName = computed(() => timelineType.value === 'NORMAL' ? selectedPlan.value?.normalTimelineName || '正向投产时序' : selectedPlan.value?.rollbackTimelineName || '回退时序')
const timelineSequences = computed(() => [...(selectedPlan.value?.timelines || [])].filter(item => item.itemType === timelineType.value).sort((left, right) => left.seqNo - right.seqNo))
const normalTimelines = computed(() => selectedPlan.value?.timelines?.filter(item => item.itemType === 'NORMAL') || [])
const rollbackTimelines = computed(() => selectedPlan.value?.timelines?.filter(item => item.itemType === 'ROLLBACK') || [])

function minute(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '未安排' }
function statusLabel(status?: string) { return ({ PENDING: '待开始', RUNNING: '进行中', COMPLETED: '已完成', SKIPPED: '已跳过' } as Record<string, string>)[status || ''] || status || '未设置' }
function planItemCount(plan: ReleasePlanDto) { return (plan.timelines || []).reduce((total, timeline) => total + timeline.items.length, 0) }
function isForbidden(cause: unknown) { return (cause as { response?: { status?: number } }).response?.status === 403 }
async function load() {
  loading.value = true; error.value = ''; forbidden.value = false
  try {
    const [p, m] = await Promise.all([listReleasePlans(props.projectId), listReleaseOperationMemberOptions(props.projectId)])
    plans.value = p.data.data; members.value = m.data.data
    if (!plans.value.some(item => item.id === selectedId.value)) selectedId.value = plans.value[0]?.id
  } catch (cause) { plans.value = []; error.value = apiErrorMessage(cause, '投产方案加载失败，请稍后重试'); forbidden.value = isForbidden(cause) } finally { loading.value = false }
}
function openPlan(plan?: ReleasePlanDto) {
  editingPlan.value = plan || null
  Object.assign(planForm, { planName: plan?.planName || '', planCode: plan?.planCode || '', versionNo: plan?.versionNo || '', status: plan?.status || 'DRAFT', description: plan?.description || '', rowVersion: plan?.rowVersion || 0 })
  planDialog.value = true
}
async function savePlan() {
  if (!planForm.planName.trim() || !planForm.planCode.trim() || saving.value) return
  saving.value = true
  try {
    const response = editingPlan.value ? await updateReleasePlan(props.projectId, editingPlan.value.id, { ...planForm }) : await createReleasePlan(props.projectId, { ...planForm })
    const value = response.data.data
    plans.value = editingPlan.value ? plans.value.map(item => item.id === value.id ? value : item) : [...plans.value, value]
    selectedId.value = value.id; planDialog.value = false
    ElMessage.success(editingPlan.value ? '投产方案已更新' : '投产方案已创建')
  } catch (cause) { ElMessage.error(apiErrorMessage(cause, '投产方案保存失败，请重试')) } finally { saving.value = false }
}
async function removePlan(plan: ReleasePlanDto) {
  try { await ElMessageBox.confirm(`将删除“${plan.planName}”，删除后不可恢复。`, '删除投产方案', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }); await deleteReleasePlan(props.projectId, plan.id, plan.rowVersion); plans.value = plans.value.filter(item => item.id !== plan.id); selectedId.value = plans.value[0]?.id; ElMessage.success('投产方案已删除') } catch (cause) { if (cause !== 'cancel' && cause !== 'close') ElMessage.error(apiErrorMessage(cause, '投产方案删除失败，请刷新后重试')) }
}
function openTimeline(type: ReleasePlanItemType) {
  if (!selectedPlan.value) return
  timelineType.value = type
  timelineForm.timelineName = type === 'NORMAL' ? selectedPlan.value.normalTimelineName || '正向投产时序' : selectedPlan.value.rollbackTimelineName || '回退时序'
  timelineDrawer.value = true
}
function openTimelineEditor(timeline?: ReleasePlanTimelineDto) {
  editingTimeline.value = timeline || null
  const nextSeqNo = timelineSequences.value.reduce((max, item) => Math.max(max, item.seqNo), 0) + 1
  Object.assign(sequenceForm, { seqNo: timeline?.seqNo || nextSeqNo, timelineName: timeline?.timelineName || `P${nextSeqNo} 时序`, description: timeline?.description || '', rowVersion: timeline?.rowVersion || 0 })
  timelineDialog.value = true
}
async function saveTimelineName() {
  if (!selectedPlan.value || !timelineForm.timelineName.trim() || timelineSaving.value) return
  timelineSaving.value = true
  try {
    const plan = selectedPlan.value
    const response = await updateReleasePlan(props.projectId, plan.id, { planName: plan.planName, planCode: plan.planCode, description: plan.description, versionNo: plan.versionNo, status: plan.status, normalTimelineName: timelineType.value === 'NORMAL' ? timelineForm.timelineName.trim() : plan.normalTimelineName, rollbackTimelineName: timelineType.value === 'ROLLBACK' ? timelineForm.timelineName.trim() : plan.rollbackTimelineName, rowVersion: plan.rowVersion })
    plans.value = plans.value.map(item => item.id === response.data.data.id ? response.data.data : item)
    ElMessage.success('时序组名称已保存')
  } catch (cause) { ElMessage.error(apiErrorMessage(cause, '时序组名称保存失败，请重试')) } finally { timelineSaving.value = false }
}
async function saveTimeline() {
  if (!selectedPlan.value || !sequenceForm.timelineName.trim() || !sequenceForm.seqNo || timelineSaving.value) return
  timelineSaving.value = true
  try {
    const data = { seqNo: sequenceForm.seqNo, timelineName: sequenceForm.timelineName.trim(), description: sequenceForm.description, rowVersion: sequenceForm.rowVersion }
    const response = editingTimeline.value ? await updateReleasePlanTimeline(props.projectId, selectedPlan.value.id, timelineType.value, editingTimeline.value.id, data) : await createReleasePlanTimeline(props.projectId, selectedPlan.value.id, timelineType.value, data)
    await load(); timelineDialog.value = false
    ElMessage.success(editingTimeline.value ? '投产时序已更新' : '投产时序已添加')
    void response
  } catch (cause) { ElMessage.error(apiErrorMessage(cause, '投产时序保存失败，请重试')) } finally { timelineSaving.value = false }
}
async function removeTimeline(timeline: ReleasePlanTimelineDto) {
  try { await ElMessageBox.confirm(`将删除“P${timeline.seqNo} ${timeline.timelineName}”及其下全部指令，删除后不可恢复。`, '删除投产时序', { type: 'warning', confirmButtonText: '删除时序', cancelButtonText: '取消' }); await deleteReleasePlanTimeline(props.projectId, timeline.planId, timeline.itemType, timeline.id, timeline.rowVersion); await load(); ElMessage.success('投产时序已删除') } catch (cause) { if (cause !== 'cancel' && cause !== 'close') ElMessage.error(apiErrorMessage(cause, '投产时序删除失败，请刷新后重试')) }
}
function openItem(timeline: ReleasePlanTimelineDto, item?: ReleasePlanItemDto) {
  editingItem.value = item || null
  Object.assign(itemForm, { timelineId: timeline.id, seqNo: item?.seqNo, itemName: item?.itemName || '', range: item?.plannedStart && item?.plannedEnd ? [item.plannedStart, item.plannedEnd] : [], ownerId: item?.ownerId, status: item?.status || 'PENDING', description: item?.description || '', rowVersion: item?.rowVersion || 0 })
  timelineType.value = timeline.itemType
  itemDrawer.value = true
}
async function saveItem() {
  if (!selectedPlan.value || !itemForm.timelineId || !itemForm.itemName.trim() || itemForm.range.length !== 2 || itemSaving.value) return
  itemSaving.value = true
  try {
    const data = { seqNo: itemForm.seqNo, itemName: itemForm.itemName.trim(), plannedStart: itemForm.range[0], plannedEnd: itemForm.range[1], ownerId: itemForm.ownerId, status: itemForm.status, description: itemForm.description, rowVersion: itemForm.rowVersion }
    const response = editingItem.value ? await updateReleasePlanItem(props.projectId, selectedPlan.value.id, timelineType.value, itemForm.timelineId, editingItem.value.id, data) : await createReleasePlanItem(props.projectId, selectedPlan.value.id, timelineType.value, itemForm.timelineId, data)
    await load(); itemDrawer.value = false
    ElMessage.success(editingItem.value ? '方案指令已更新' : '方案指令已添加')
    void response
  } catch (cause) { ElMessage.error(apiErrorMessage(cause, '方案指令保存失败，请重试')) } finally { itemSaving.value = false }
}
async function removeItem(timeline: ReleasePlanTimelineDto, item: ReleasePlanItemDto) {
  try { await ElMessageBox.confirm(`将删除“${item.itemName}”，删除后不可恢复。`, '删除方案指令', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }); await deleteReleasePlanItem(props.projectId, timeline.planId, timeline.itemType, timeline.id, item.id, item.rowVersion); await load(); ElMessage.success('方案指令已删除') } catch (cause) { if (cause !== 'cancel' && cause !== 'close') ElMessage.error(apiErrorMessage(cause, '方案指令删除失败，请刷新后重试')) }
}
onMounted(load)
</script>

<template>
  <div v-if="forbidden" class="release-operations-state"><el-result icon="warning" title="无权查看投产演练计划" sub-title="请联系项目管理员申请查看权限。" /></div>
  <div v-else-if="error" class="release-operations-state release-operations-state--error"><el-result icon="error" title="投产方案加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></div>
  <div v-else v-loading="loading" class="release-operations-grid release-plan-manager">
    <section class="release-operations-panel"><header class="release-operations-panel__header"><div><span class="release-operations-kicker">RELEASE PLANS</span><h2>投产方案</h2><p>每个方案分别维护正向投产和回退时序。</p></div><div class="release-operations-header-actions"><el-button :icon="Refresh" circle aria-label="刷新投产方案" @click="load" /><el-button v-if="canManage" type="primary" :icon="Plus" @click="openPlan()">新增方案</el-button></div></header><div class="release-operations-panel__body release-plan-list"><UiEmptyState v-if="!plans.length && !loading" title="暂无投产方案" description="先创建方案，再配置正向和回退时序。"><template #action><el-button v-if="canManage" type="primary" @click="openPlan()">新增方案</el-button></template></UiEmptyState><button v-for="plan in plans" v-else :key="plan.id" type="button" class="release-plan-list__item" :class="{ 'is-active': selectedPlan?.id === plan.id }" @click="selectedId = plan.id"><span><strong>{{ plan.planName }}</strong><small>{{ plan.planCode }} · {{ plan.versionNo || '未填写版本' }}</small></span><em>{{ planItemCount(plan) }} 个指令</em></button></div></section>
    <section class="release-operations-panel"><header class="release-operations-panel__header"><div><span class="release-operations-kicker">PLAN DETAIL</span><h2>{{ selectedPlan?.planName || '选择方案' }}</h2><p>{{ selectedPlan?.description || '选择一条方案查看或维护时序。' }}</p></div><div v-if="selectedPlan && canManage" class="release-operations-header-actions"><el-button :icon="Edit" circle aria-label="编辑投产方案" @click="openPlan(selectedPlan)" /><el-button :icon="Delete" circle type="danger" aria-label="删除投产方案" @click="removePlan(selectedPlan)" /></div></header><div v-if="selectedPlan" class="release-operations-panel__body release-plan-detail"><article class="release-plan-timeline-entry"><div><span class="release-operations-kicker">NORMAL TIMELINE</span><h3>{{ selectedPlan.normalTimelineName || '正向投产时序' }}</h3><p>{{ normalTimelines.length }} 个时序 · {{ normalTimelines.reduce((total, item) => total + item.items.length, 0) }} 个指令</p></div><el-button type="primary" plain @click="openTimeline('NORMAL')">查看正向时序</el-button></article><article class="release-plan-timeline-entry"><div><span class="release-operations-kicker">ROLLBACK TIMELINE</span><h3>{{ selectedPlan.rollbackTimelineName || '回退时序' }}</h3><p>{{ rollbackTimelines.length }} 个时序 · {{ rollbackTimelines.reduce((total, item) => total + item.items.length, 0) }} 个指令</p></div><el-button type="warning" plain @click="openTimeline('ROLLBACK')">查看回退时序</el-button></article></div><UiEmptyState v-else title="请选择投产方案" description="左侧选择一条方案查看或维护时序。" /></section>
  </div>

  <el-dialog v-model="planDialog" :title="editingPlan ? '编辑投产方案' : '新增投产方案'" width="min(680px, 92vw)" destroy-on-close><el-form label-position="top" class="release-operations-form-grid"><el-form-item label="方案名称" required><el-input v-model="planForm.planName" maxlength="128" /></el-form-item><el-form-item label="方案编码" required><el-input v-model="planForm.planCode" maxlength="64" /></el-form-item><el-form-item label="版本号"><el-input v-model="planForm.versionNo" maxlength="64" /></el-form-item><el-form-item label="状态"><el-select v-model="planForm.status"><el-option label="草稿" value="DRAFT" /><el-option label="启用" value="ACTIVE" /><el-option label="归档" value="ARCHIVED" /></el-select></el-form-item><el-form-item label="方案说明" class="is-wide"><el-input v-model="planForm.description" type="textarea" :rows="4" maxlength="2000" /></el-form-item></el-form><template #footer><el-button @click="planDialog = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!planForm.planName.trim() || !planForm.planCode.trim()" @click="savePlan">保存</el-button></template></el-dialog>

  <el-drawer v-model="timelineDrawer" :title="timelineName" direction="rtl" size="min(980px, 96vw)" destroy-on-close class="release-timeline-drawer"><div class="release-timeline-drawer__body"><section class="release-timeline-drawer__intro"><div><span class="release-operations-kicker">{{ timelineType === 'NORMAL' ? 'NORMAL TIMELINE' : 'ROLLBACK TIMELINE' }}</span><h2>{{ timelineName }}</h2><p>维护 P1、P2 等投产时序；每个时序下可维护多条指令。</p></div><el-tag :type="timelineType === 'NORMAL' ? 'success' : 'warning'">{{ timelineSequences.length }} 个时序 · {{ timelineSequences.reduce((total, item) => total + item.items.length, 0) }} 个指令</el-tag></section><el-form label-position="top" class="release-operations-form-grid release-timeline-name-form"><el-form-item label="时序组名称" required class="is-wide"><el-input v-model="timelineForm.timelineName" maxlength="128" placeholder="输入正向或回退时序组名称" /></el-form-item></el-form><div class="release-timeline-drawer__actions"><el-button v-if="canManage" type="primary" :loading="timelineSaving" :disabled="!timelineForm.timelineName.trim()" @click="saveTimelineName">保存时序组名称</el-button><el-button v-if="canManage" plain :icon="Plus" @click="openTimelineEditor()">新增时序</el-button></div><UiEmptyState v-if="!timelineSequences.length" title="暂无投产时序" description="先新增 P1 时序，再维护该时序下的投产指令。"><template #action><el-button v-if="canManage" type="primary" :icon="Plus" @click="openTimelineEditor()">新增时序</el-button></template></UiEmptyState><div v-else class="release-visual-timeline" :style="{ '--timeline-stage-count': timelineSequences.length }" aria-label="投产时序及其指令"><div class="release-visual-timeline__stage-grid"><article v-for="timeline in timelineSequences" :key="timeline.id" class="release-visual-timeline__stage"><header class="release-visual-timeline__stage-header"><div class="release-visual-timeline__stage-heading"><span>P{{ timeline.seqNo }}</span><strong :title="timeline.timelineName">{{ timeline.timelineName }}</strong></div><div class="release-visual-timeline__stage-actions" v-if="canManage"><el-button link :icon="Edit" aria-label="编辑投产时序" @click="openTimelineEditor(timeline)" /><el-button link type="danger" :icon="Delete" aria-label="删除投产时序" @click="removeTimeline(timeline)" /></div><small>{{ timeline.items.length }} 条指令</small></header><div class="release-visual-timeline__commands"><div v-if="!timeline.items.length" class="release-plan-command-empty">暂无指令</div><article v-for="(item, index) in timeline.items" :key="item.id" class="release-visual-timeline__command"><div class="release-visual-timeline__command-marker" aria-hidden="true">{{ index + 1 }}</div><div class="release-visual-timeline__command-body"><time>{{ minute(item.plannedStart) }} 至 {{ minute(item.plannedEnd) }}</time><strong :title="item.itemName">{{ item.itemName }}</strong><small>{{ item.ownerName || '未指定负责人' }} · {{ statusLabel(item.status) }}</small><p v-if="item.description">{{ item.description }}</p><div v-if="canManage" class="release-visual-timeline__actions"><el-button link :icon="Edit" aria-label="编辑方案指令" @click="openItem(timeline, item)" /><el-button link type="danger" :icon="Delete" aria-label="删除方案指令" @click="removeItem(timeline, item)" /></div></div></article></div><footer class="release-visual-timeline__stage-footer"><el-button v-if="canManage" type="primary" plain :icon="Plus" @click="openItem(timeline)">新增指令</el-button></footer></article></div></div></div></el-drawer>

  <el-dialog v-model="timelineDialog" :title="editingTimeline ? '编辑投产时序' : '新增投产时序'" width="min(560px, 92vw)" destroy-on-close><el-form label-position="top" class="release-operations-form-grid"><el-form-item label="时序编号" required><el-input-number v-model="sequenceForm.seqNo" :min="1" :max="999" controls-position="right" /></el-form-item><el-form-item label="时序名称" required><el-input v-model="sequenceForm.timelineName" maxlength="128" placeholder="例如：应用版本部署" /></el-form-item><el-form-item label="时序说明" class="is-wide"><el-input v-model="sequenceForm.description" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="timelineDialog = false">取消</el-button><el-button type="primary" :loading="timelineSaving" :disabled="!sequenceForm.seqNo || !sequenceForm.timelineName.trim()" @click="saveTimeline">保存</el-button></template></el-dialog>

  <el-drawer v-model="itemDrawer" :title="editingItem ? '编辑方案指令' : '新增方案指令'" direction="rtl" size="min(680px, 94vw)" destroy-on-close><el-form label-position="top" class="release-operations-form-grid release-drawer-form"><el-form-item label="所属时序"><el-tag>{{ timelineName }}</el-tag></el-form-item><el-form-item label="指令序号"><el-input-number v-model="itemForm.seqNo" :min="1" :max="999" controls-position="right" /></el-form-item><el-form-item label="指令名称" required class="is-wide"><el-input v-model="itemForm.itemName" maxlength="128" /></el-form-item><el-form-item label="计划时间范围" required class="is-wide"><el-date-picker v-model="itemForm.range" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" /></el-form-item><el-form-item label="负责人"><el-select v-model="itemForm.ownerId" clearable><el-option v-for="member in members" :key="member.userId" :label="`${member.displayName}（${member.username}）`" :value="member.userId" /></el-select></el-form-item><el-form-item label="状态"><el-select v-model="itemForm.status"><el-option label="待开始" value="PENDING" /><el-option label="进行中" value="RUNNING" /><el-option label="已完成" value="COMPLETED" /><el-option label="已跳过" value="SKIPPED" /></el-select></el-form-item><el-form-item label="指令说明" class="is-wide"><el-input v-model="itemForm.description" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item></el-form><template #footer><div class="release-drawer-actions"><el-button @click="itemDrawer = false">取消</el-button><el-button type="primary" :loading="itemSaving" :disabled="!itemForm.itemName.trim() || itemForm.range.length !== 2" @click="saveItem">保存</el-button></div></template></el-drawer>
</template>
