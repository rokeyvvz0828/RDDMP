<script setup lang="ts">
import ReleaseDrawerHeader from './ReleaseDrawerHeader.vue'
import { useReleaseDrawerFullscreen } from '../composables/useReleaseDrawerFullscreen'
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowRight, Check, Delete, Edit, Plus, Refresh, RefreshLeft, Right } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiErrorMessage } from '../../../api/error'
import { createReleasePlan, createReleasePlanItem, createReleasePlanTimeline, deleteReleasePlan, deleteReleasePlanItem, deleteReleasePlanTimeline, listReleaseOperationMemberOptions, listReleasePlans, updateReleasePlan, updateReleasePlanItem, updateReleasePlanTimeline, type ReleaseMemberOptionDto, type ReleasePlanDto, type ReleasePlanItemDto, type ReleasePlanItemType, type ReleasePlanTimelineDto } from '../../../api/release'
import { useAuthStore } from '../../../stores/auth'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'

const planStatusLabels = { DRAFT: '草稿', ACTIVE: '启用', ARCHIVED: '归档' }

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
const { fullscreen: timelineFullscreen, size: timelineSize, toggle: toggleTimelineFullscreen } =
  useReleaseDrawerFullscreen(timelineDrawer, 'min(980px, 100vw)')
const timelineDialog = ref(false)
const itemDrawer = ref(false)
const { fullscreen: itemFullscreen, size: itemSize, toggle: toggleItemFullscreen } =
  useReleaseDrawerFullscreen(itemDrawer, 'min(680px, 94vw)')
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
  <div v-if="forbidden" class="release-operations-state"><el-result icon="warning" title="无权查看投产方案" sub-title="请联系项目管理员申请查看权限。" /></div>
  <div v-else-if="error" class="release-operations-state release-operations-state--error"><el-result icon="error" title="投产方案加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></div>
  <div v-else v-loading="loading" class="release-plan-page" :aria-busy="loading">
    <UiPageHeader title="投产方案">
      <template #actions>
        <el-tooltip content="刷新投产方案"><el-button :icon="Refresh" circle aria-label="刷新投产方案" :disabled="loading" @click="load" /></el-tooltip>
        <el-button v-if="canManage" type="primary" :icon="Plus" @click="openPlan()">新增方案</el-button>
      </template>
    </UiPageHeader>

    <UiEmptyState v-if="!plans.length && !loading" title="暂无投产方案">
      <template #action><el-button v-if="canManage" type="primary" :icon="Plus" @click="openPlan()">新增方案</el-button></template>
    </UiEmptyState>
    <div v-else class="release-plan-workspace">
      <aside class="plan-directory" aria-label="方案目录">
        <header class="plan-directory__header"><h2>方案目录</h2><span>{{ plans.length }} 项</span></header>
        <div class="plan-directory__list">
          <button v-for="plan in plans" :key="plan.id" type="button" class="plan-directory__item"
            :class="{ 'is-selected': selectedPlan?.id === plan.id }" :aria-pressed="selectedPlan?.id === plan.id"
            @click="selectedId = plan.id">
            <span class="plan-directory__title">
              <strong>{{ plan.planName }}</strong>
              <UiStatusTag :value="plan.status" :labels="planStatusLabels" :tone="plan.status === 'ACTIVE' ? 'success' : 'info'" />
            </span>
            <span class="plan-directory__code">{{ plan.planCode }}</span>
            <span class="plan-directory__meta"><span>{{ plan.versionNo || '未填写版本' }}</span><span>{{ planItemCount(plan) }} 个指令</span></span>
          </button>
        </div>
      </aside>

      <section v-if="selectedPlan" class="plan-workspace-detail" aria-label="方案详情">
        <header class="plan-detail-heading">
          <div class="plan-detail-heading__identity">
            <span class="plan-section-label">方案详情</span>
            <div class="plan-detail-heading__title"><h2>{{ selectedPlan.planName }}</h2><UiStatusTag :value="selectedPlan.status" :labels="planStatusLabels" :tone="selectedPlan.status === 'ACTIVE' ? 'success' : 'info'" /></div>
          </div>
          <div v-if="canManage" class="plan-icon-actions">
            <el-tooltip content="编辑投产方案"><el-button :icon="Edit" circle aria-label="编辑投产方案" @click="openPlan(selectedPlan)" /></el-tooltip>
            <el-tooltip content="删除投产方案"><el-button :icon="Delete" circle plain type="danger" aria-label="删除投产方案" @click="removePlan(selectedPlan)" /></el-tooltip>
          </div>
        </header>
        <dl class="plan-detail-meta">
          <div><dt>方案编码</dt><dd>{{ selectedPlan.planCode }}</dd></div>
          <div><dt>版本号</dt><dd>{{ selectedPlan.versionNo || '未填写' }}</dd></div>
        </dl>
        <section class="plan-description"><h3>方案说明</h3><p>{{ selectedPlan.description || '暂无方案说明' }}</p></section>
        <section class="plan-sequence-entries" aria-label="方案时序">
          <h3>方案时序</h3>
          <article class="plan-sequence-entry">
            <el-icon class="plan-sequence-entry__icon"><Right /></el-icon>
            <div class="plan-sequence-entry__content">
              <span class="plan-section-label">正向执行</span>
              <h4>{{ selectedPlan.normalTimelineName || '正向投产时序' }}</h4>
              <p>{{ normalTimelines.length }} 个时序<span aria-hidden="true"> · </span>{{ normalTimelines.reduce((total, item) => total + item.items.length, 0) }} 个指令</p>
            </div>
            <el-button plain type="primary" @click="openTimeline('NORMAL')">查看正向时序<el-icon class="plan-entry-arrow"><ArrowRight /></el-icon></el-button>
          </article>
          <article class="plan-sequence-entry plan-sequence-entry--rollback">
            <el-icon class="plan-sequence-entry__icon"><RefreshLeft /></el-icon>
            <div class="plan-sequence-entry__content">
              <span class="plan-section-label">应急回退</span>
              <h4>{{ selectedPlan.rollbackTimelineName || '回退时序' }}</h4>
              <p>{{ rollbackTimelines.length }} 个时序<span aria-hidden="true"> · </span>{{ rollbackTimelines.reduce((total, item) => total + item.items.length, 0) }} 个指令</p>
            </div>
            <el-button plain @click="openTimeline('ROLLBACK')">查看回退时序<el-icon class="plan-entry-arrow"><ArrowRight /></el-icon></el-button>
          </article>
        </section>
      </section>
    </div>
  </div>

  <el-dialog v-model="planDialog" :title="editingPlan ? '编辑投产方案' : '新增投产方案'" width="min(680px, 92vw)" destroy-on-close><el-form label-position="top" class="release-operations-form-grid"><el-form-item label="方案名称" required><el-input v-model="planForm.planName" maxlength="128" /></el-form-item><el-form-item label="方案编码" required><el-input v-model="planForm.planCode" maxlength="64" /></el-form-item><el-form-item label="版本号"><el-input v-model="planForm.versionNo" maxlength="64" /></el-form-item><el-form-item label="状态"><el-select v-model="planForm.status"><el-option label="草稿" value="DRAFT" /><el-option label="启用" value="ACTIVE" /><el-option label="归档" value="ARCHIVED" /></el-select></el-form-item><el-form-item label="方案说明" class="is-wide"><el-input v-model="planForm.description" type="textarea" :rows="4" maxlength="2000" /></el-form-item></el-form><template #footer><el-button @click="planDialog = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!planForm.planName.trim() || !planForm.planCode.trim()" @click="savePlan">保存</el-button></template></el-dialog>

  <el-drawer v-model="timelineDrawer" :title="timelineName" direction="rtl" :size="timelineSize" :class="{ 'release-operations-fullscreen-drawer': timelineFullscreen }" destroy-on-close class="plan-sequence-drawer"><template #header="{ titleId, titleClass }"><ReleaseDrawerHeader :title="timelineName" :title-id="titleId" :title-class="titleClass" :fullscreen="timelineFullscreen" @toggle="toggleTimelineFullscreen" /></template>
    <div class="plan-sequence-workspace" :class="{ 'is-rollback': timelineType === 'ROLLBACK' }">
      <div class="plan-sequence-context">
        <div><el-tag :type="timelineType === 'NORMAL' ? 'primary' : 'warning'" effect="plain">{{ timelineType === 'NORMAL' ? '正向执行' : '应急回退' }}</el-tag><strong>{{ selectedPlan?.planName }}</strong></div>
        <span>{{ timelineSequences.length }} 个时序 · {{ timelineSequences.reduce((total, item) => total + item.items.length, 0) }} 个指令</span>
      </div>
      <div class="plan-sequence-toolbar">
        <el-form label-position="top" class="plan-group-name">
          <el-form-item label="时序组名称" required>
            <div class="plan-group-name__field">
              <el-input v-model="timelineForm.timelineName" :readonly="!canManage" maxlength="128" />
              <el-tooltip v-if="canManage" content="保存时序组名称"><el-button :icon="Check" aria-label="保存时序组名称" :loading="timelineSaving" :disabled="!timelineForm.timelineName.trim()" @click="saveTimelineName" /></el-tooltip>
            </div>
          </el-form-item>
        </el-form>
        <el-button v-if="canManage" type="primary" :icon="Plus" @click="openTimelineEditor()">新增时序</el-button>
      </div>
      <UiEmptyState v-if="!timelineSequences.length" title="暂无投产时序" />
      <div v-else class="plan-sequence-scroll" tabindex="0" role="region" aria-label="投产时序及其指令">
        <div class="plan-sequence-track">
          <section v-for="timeline in timelineSequences" :key="timeline.id" class="plan-sequence-column">
            <header class="plan-sequence-column__header">
              <div class="plan-sequence-column__top">
                <span class="plan-sequence-number">P{{ timeline.seqNo }}</span>
                <div v-if="canManage" class="plan-icon-actions">
                  <el-tooltip content="编辑投产时序"><el-button text :icon="Edit" aria-label="编辑投产时序" @click="openTimelineEditor(timeline)" /></el-tooltip>
                  <el-tooltip content="删除投产时序"><el-button text type="danger" :icon="Delete" aria-label="删除投产时序" @click="removeTimeline(timeline)" /></el-tooltip>
                </div>
              </div>
              <h3>{{ timeline.timelineName }}</h3>
              <p v-if="timeline.description" class="plan-sequence-description">{{ timeline.description }}</p>
              <span class="plan-section-label">{{ timeline.items.length }} 条指令</span>
            </header>
            <div class="plan-command-list">
              <p v-if="!timeline.items.length" class="plan-command-empty">暂无指令</p>
              <article v-for="(item, index) in timeline.items" :key="item.id" class="plan-command">
                <div class="plan-command__heading"><span class="plan-command__number">指令 {{ index + 1 }}</span><el-tag size="small" effect="plain" :type="item.status === 'COMPLETED' ? 'success' : item.status === 'RUNNING' ? 'primary' : 'info'">{{ statusLabel(item.status) }}</el-tag></div>
                <h4>{{ item.itemName }}</h4>
                <dl class="plan-command__time">
                  <div><dt>开始</dt><dd><time>{{ minute(item.plannedStart) }}</time></dd></div>
                  <div><dt>结束</dt><dd><time>{{ minute(item.plannedEnd) }}</time></dd></div>
                </dl>
                <p class="plan-command__owner">{{ item.ownerName || '未指定负责人' }}</p>
                <p v-if="item.description" class="plan-command__description">{{ item.description }}</p>
                <div v-if="canManage" class="plan-command__actions plan-icon-actions">
                  <el-tooltip content="编辑方案指令"><el-button text :icon="Edit" aria-label="编辑方案指令" @click="openItem(timeline, item)" /></el-tooltip>
                  <el-tooltip content="删除方案指令"><el-button text type="danger" :icon="Delete" aria-label="删除方案指令" @click="removeItem(timeline, item)" /></el-tooltip>
                </div>
              </article>
            </div>
            <footer v-if="canManage" class="plan-sequence-column__footer"><el-button plain :icon="Plus" @click="openItem(timeline)">新增指令</el-button></footer>
          </section>
        </div>
      </div>
    </div>
  </el-drawer>

  <el-dialog v-model="timelineDialog" :title="editingTimeline ? '编辑投产时序' : '新增投产时序'" width="min(560px, 92vw)" destroy-on-close><el-form label-position="top" class="release-operations-form-grid"><el-form-item label="时序编号" required><el-input-number v-model="sequenceForm.seqNo" :min="1" :max="999" controls-position="right" /></el-form-item><el-form-item label="时序名称" required><el-input v-model="sequenceForm.timelineName" maxlength="128" placeholder="例如：应用版本部署" /></el-form-item><el-form-item label="时序说明" class="is-wide"><el-input v-model="sequenceForm.description" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="timelineDialog = false">取消</el-button><el-button type="primary" :loading="timelineSaving" :disabled="!sequenceForm.seqNo || !sequenceForm.timelineName.trim()" @click="saveTimeline">保存</el-button></template></el-dialog>

  <el-drawer v-model="itemDrawer" :title="editingItem ? '编辑方案指令' : '新增方案指令'" direction="rtl" :size="itemSize" :class="{ 'release-operations-fullscreen-drawer': itemFullscreen }" destroy-on-close><template #header="{ titleId, titleClass }"><ReleaseDrawerHeader :title="editingItem ? '编辑方案指令' : '新增方案指令'" :title-id="titleId" :title-class="titleClass" :fullscreen="itemFullscreen" @toggle="toggleItemFullscreen" /></template><el-form label-position="top" class="release-operations-form-grid release-drawer-form release-operations-fullscreen-form"><el-form-item label="所属时序"><el-tag>{{ timelineName }}</el-tag></el-form-item><el-form-item label="指令序号"><el-input-number v-model="itemForm.seqNo" :min="1" :max="999" controls-position="right" /></el-form-item><el-form-item label="指令名称" required class="is-wide"><el-input v-model="itemForm.itemName" maxlength="128" /></el-form-item><el-form-item label="计划时间范围" required class="is-wide"><el-date-picker v-model="itemForm.range" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" /></el-form-item><el-form-item label="负责人"><el-select v-model="itemForm.ownerId" clearable><el-option v-for="member in members" :key="member.userId" :label="`${member.displayName}（${member.username}）`" :value="member.userId" /></el-select></el-form-item><el-form-item label="状态"><el-select v-model="itemForm.status"><el-option label="待开始" value="PENDING" /><el-option label="进行中" value="RUNNING" /><el-option label="已完成" value="COMPLETED" /><el-option label="已跳过" value="SKIPPED" /></el-select></el-form-item><el-form-item label="指令说明" class="is-wide"><el-input v-model="itemForm.description" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item></el-form><template #footer><div class="release-drawer-actions"><el-button @click="itemDrawer = false">取消</el-button><el-button type="primary" :loading="itemSaving" :disabled="!itemForm.itemName.trim() || itemForm.range.length !== 2" @click="saveItem">保存</el-button></div></template></el-drawer>
</template>

<style scoped>
.release-plan-page { min-width: 0; background: var(--panel-bg); color: var(--text); }
.release-plan-page :deep(.ui-page-header) { padding: 20px 24px; margin: 0; border-bottom: 1px solid var(--line); }
.release-plan-page :deep(.ui-page-header h1) { font-size: 20px; letter-spacing: 0; }
.release-plan-workspace { display: grid; grid-template-columns: minmax(240px, 300px) minmax(0, 1fr); min-height: 480px; }
.plan-directory { min-width: 0; border-right: 1px solid var(--line); }
.plan-directory__header { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 20px; }
.plan-directory__header h2 { margin: 0; font-size: 14px; }
.plan-directory__header > span, .plan-section-label { color: var(--muted); font-size: 12px; }
.plan-directory__list { max-height: 560px; overflow-y: auto; overscroll-behavior: contain; padding: 0 12px 16px; }
.plan-directory__item { display: grid; gap: 9px; width: 100%; padding: 16px 12px; border: 0; border-left: 3px solid transparent; border-bottom: 1px solid var(--line); background: transparent; color: var(--text); text-align: left; font: inherit; cursor: pointer; }
.plan-directory__item:hover { background: var(--panel-muted); }
.plan-directory__item.is-selected { border-left-color: var(--brand); background: color-mix(in srgb, var(--brand) 8%, var(--panel-bg)); }
.plan-directory__item:focus-visible { outline: 2px solid var(--brand); outline-offset: -2px; }
.plan-directory__title { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; min-width: 0; }
.plan-directory__title strong { font-size: 14px; line-height: 1.6; overflow-wrap: anywhere; }
.plan-directory__title :deep(.el-tag) { flex-shrink: 0; margin-top: 2px; }
.plan-directory__code { overflow-wrap: anywhere; font-size: 12px; color: var(--muted); }
.plan-directory__meta { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 6px 12px; color: var(--muted); font-size: 12px; }
.plan-directory__meta > span { overflow-wrap: anywhere; }
.plan-workspace-detail { min-width: 0; padding: 28px 32px 32px; }
.plan-detail-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.plan-detail-heading__identity { min-width: 0; }
.plan-detail-heading__title { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-top: 8px; }
.plan-detail-heading h2 { margin: 0; font-size: 20px; line-height: 1.5; overflow-wrap: anywhere; }
.plan-icon-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.plan-icon-actions :deep(.el-button + .el-button) { margin-left: 0; }
.plan-detail-meta { display: flex; flex-wrap: wrap; gap: 18px 48px; margin: 24px 0; }
.plan-detail-meta > div { display: grid; gap: 7px; min-width: 0; }
.plan-detail-meta dt { color: var(--muted); font-size: 12px; }
.plan-detail-meta dd { margin: 0; font-size: 14px; overflow-wrap: anywhere; }
.plan-description h3, .plan-sequence-entries > h3 { margin: 0; font-size: 14px; font-weight: 600; }
.plan-description p { margin: 10px 0 0; color: var(--muted); font-size: 13px; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }
.plan-sequence-entries { margin-top: 28px; border-top: 1px solid var(--line); padding-top: 24px; }
.plan-sequence-entry { display: grid; grid-template-columns: 40px minmax(0, 1fr) auto; align-items: center; gap: 16px; padding: 22px 0; border-bottom: 1px solid var(--line); }
.plan-sequence-entry__icon { width: 40px; height: 40px; font-size: 22px; color: var(--brand); background: color-mix(in srgb, var(--brand) 9%, var(--panel-bg)); border-radius: 6px; }
.plan-sequence-entry--rollback .plan-sequence-entry__icon { color: var(--warning); background: color-mix(in srgb, var(--warning) 9%, var(--panel-bg)); }
.plan-sequence-entry__content { min-width: 0; }
.plan-sequence-entry h4 { margin: 5px 0 0; font-size: 16px; line-height: 1.5; overflow-wrap: anywhere; }
.plan-sequence-entry p { margin: 7px 0 0; color: var(--muted); font-size: 12px; }
.plan-entry-arrow { margin-left: 6px; }
:global(.plan-sequence-drawer .el-drawer__header) { margin-bottom: 0; padding: 20px 24px; border-bottom: 1px solid var(--line); }
:global(.plan-sequence-drawer .el-drawer__title) { color: var(--text); font-size: 18px; font-weight: 600; overflow-wrap: anywhere; }
:global(.plan-sequence-drawer .el-drawer__body) { padding: 0; overflow-y: auto; }
.plan-sequence-workspace { min-width: 0; padding: 20px 24px 24px; color: var(--text); }
.plan-sequence-context { display: flex; align-items: center; justify-content: space-between; gap: 12px 24px; flex-wrap: wrap; padding-bottom: 20px; border-bottom: 1px solid var(--line); }
.plan-sequence-context > div { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; min-width: 0; }
.plan-sequence-context strong { font-size: 14px; overflow-wrap: anywhere; }
.plan-sequence-context > span { color: var(--muted); font-size: 12px; }
.plan-sequence-toolbar { display: flex; align-items: flex-end; gap: 16px; justify-content: space-between; margin: 20px 0 24px; }
.plan-group-name { width: 460px; max-width: 100%; min-width: 0; }
.plan-group-name :deep(.el-form-item) { margin: 0; }
.plan-group-name__field { display: flex; gap: 8px; width: 100%; }
.plan-group-name__field > .el-button { width: 36px; padding: 0; flex-shrink: 0; }
.plan-sequence-scroll { width: 100%; overflow-x: auto; overscroll-behavior-x: contain; padding-bottom: 12px; }
.plan-sequence-scroll:focus-visible { outline: 2px solid var(--brand); outline-offset: 2px; }
.plan-sequence-track { display: grid; grid-auto-columns: 280px; grid-auto-flow: column; align-items: stretch; gap: 16px; width: max-content; min-width: 100%; }
.plan-sequence-column { min-width: 0; display: flex; flex-direction: column; border-top: 3px solid var(--brand); background: var(--panel-muted); }
.is-rollback .plan-sequence-column { border-top-color: var(--warning); }
.plan-sequence-column__header { padding: 12px 16px 16px; border-bottom: 1px solid var(--line); }
.plan-sequence-column__top { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.plan-sequence-number { font-size: 15px; font-weight: 700; color: var(--brand-strong); }
.is-rollback .plan-sequence-number { color: var(--warning); }
.plan-sequence-column__header h3 { font-size: 15px; line-height: 1.6; margin: 8px 0; overflow-wrap: anywhere; }
.plan-sequence-description { font-size: 12px; line-height: 1.7; color: var(--muted); overflow-wrap: anywhere; white-space: pre-wrap; }
.plan-sequence-column .plan-icon-actions { gap: 2px; }
.plan-sequence-column .plan-icon-actions :deep(.el-button) { width: 32px; height: 32px; padding: 0; }
.plan-command-list { padding: 0 16px; flex: 1; min-width: 0; background: var(--panel-bg); }
.plan-command-empty { color: var(--muted); font-size: 13px; padding: 28px 0; text-align: center; }
.plan-command { padding: 18px 0 12px; border-bottom: 1px solid var(--line); min-width: 0; }
.plan-command:last-child { border-bottom: 0; }
.plan-command__heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.plan-command__number { color: var(--muted); font-size: 12px; }
.plan-command h4 { margin: 10px 0 12px; font-size: 14px; line-height: 1.7; overflow-wrap: anywhere; }
.plan-command__time { display: grid; gap: 6px; margin: 0; font-size: 12px; font-variant-numeric: tabular-nums; }
.plan-command__time > div { display: grid; grid-template-columns: 28px minmax(0, 1fr); gap: 8px; }
.plan-command__time dt { color: var(--muted); }
.plan-command__time dd { margin: 0; overflow-wrap: anywhere; }
.plan-command__owner { font-size: 12px; color: var(--muted); margin: 12px 0 0; overflow-wrap: anywhere; }
.plan-command__description { font-size: 12px; line-height: 1.7; color: var(--muted); margin: 8px 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.plan-command__actions { justify-content: flex-end; margin-top: 8px; }
.plan-sequence-column__footer { padding: 12px 16px 16px; background: var(--panel-bg); border-top: 1px solid var(--line); }
.plan-sequence-column__footer > .el-button { width: 100%; margin: 0; }
@media (max-width: 1100px) { .plan-workspace-detail { padding: 24px; } }
@media (max-width: 900px) {
  .release-plan-workspace { grid-template-columns: minmax(0, 1fr); min-height: 0; }
  .plan-directory { border-right: 0; border-bottom: 1px solid var(--line); }
  .plan-directory__list { max-height: 240px; }
}
@media (max-width: 600px) {
  :global(.plan-sequence-drawer .el-drawer__header) { padding: 16px; }
  .plan-sequence-workspace { padding: 16px; }
  .plan-sequence-toolbar { flex-wrap: wrap; gap: 12px; }
  .plan-group-name { width: 100%; }
  .plan-sequence-toolbar > .el-button { margin-left: auto; }
  .plan-sequence-track { grid-auto-columns: min(280px, calc(100vw - 48px)); gap: 12px; }
  .release-plan-page :deep(.ui-page-header) { padding: 16px; gap: 12px; }
  .plan-directory__header { padding: 16px; }
  .plan-workspace-detail { padding: 20px 16px; }
  .plan-detail-heading { flex-wrap: wrap; }
  .plan-detail-heading h2 { font-size: 18px; }
  .plan-detail-meta { gap: 16px 28px; }
  .plan-sequence-entry { grid-template-columns: 36px minmax(0, 1fr); gap: 12px; padding: 20px 0; }
  .plan-sequence-entry__icon { width: 36px; height: 36px; }
  .plan-sequence-entry > .el-button { grid-column: 2; justify-self: start; max-width: 100%; }
}
</style>
