<script setup lang="ts">
import ReleaseDrawerHeader from './ReleaseDrawerHeader.vue'
import { useReleaseDrawerFullscreen } from '../composables/useReleaseDrawerFullscreen'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Grid, List, MoreFilled, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiErrorMessage } from '../../../api/error'
import { createReleaseOperationsIssue, deleteReleaseOperationsIssue, listReleaseDrills, listReleaseOperationMemberOptions, listReleaseOperationsIssues, updateReleaseOperationsIssue, type ReleaseDrillExecutionDto, type ReleaseIssueDto, type ReleaseIssuePriority, type ReleaseIssueStatus, type ReleaseIssueWrite, type ReleaseMemberOptionDto } from '../../../api/release'
import { useAuthStore } from '../../../stores/auth'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../../components/ui/UiToolbar.vue'

const props = defineProps<{ projectId: number }>()
const auth = useAuthStore()
const issues = ref<ReleaseIssueDto[]>([])
const members = ref<ReleaseMemberOptionDto[]>([])
const rounds = ref<ReleaseDrillExecutionDto[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const priority = ref<ReleaseIssuePriority>()
const status = ref<ReleaseIssueStatus>()
const viewMode = ref<'list' | 'cards'>(window.matchMedia('(max-width: 760px)').matches ? 'cards' : 'list')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const forbidden = ref(false)
const drawerOpen = ref(false)
const { fullscreen: issueFullscreen, size: issueSize, toggle: toggleIssueFullscreen } =
  useReleaseDrawerFullscreen(drawerOpen, 'min(760px, calc(100vw - 24px))')
const drawerMode = ref<'detail' | 'create' | 'edit'>('detail')
const selectedIssue = ref<ReleaseIssueDto | null>(null)
const editingIssue = ref<ReleaseIssueDto | null>(null)
const confirmingClose = ref(false)
const submitted = ref(false)
const savedForm = ref('')
let loadVersion = 0
let disposed = false
const form = reactive<ReleaseIssueWrite>({ issueNo: '', issueTitle: '', priority: 'MEDIUM', issueStatus: 'OPEN', discoveredAt: '', ownerId: undefined, drillRoundId: undefined, issueDescription: '', analysisContent: '', actionContent: '', followUpContent: '', closedAt: '', rowVersion: 0 })
const canManage = () => auth.hasPermission('release-operations:issue:manage')
const priorityLabels: Record<ReleaseIssuePriority, string> = { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '紧急' }
const statusLabels: Record<ReleaseIssueStatus, string> = { OPEN: '待处理', ANALYZING: '分析中', RESOLVED: '已解决', CLOSED: '已关闭' }
const textSections = [
  { key: 'issueDescription', label: '问题描述' },
  { key: 'analysisContent', label: '分析结论' },
  { key: 'actionContent', label: '处理措施' },
  { key: 'followUpContent', label: '跟踪记录' }
] as const
const drawerTitle = computed(() => drawerMode.value === 'detail' ? '投产问题详情' : drawerMode.value === 'edit' ? '编辑投产问题' : '新增投产问题')
const formErrors = computed(() => ({
  issueNo: submitted.value && !form.issueNo.trim() ? '请输入问题编号' : '',
  issueTitle: submitted.value && !form.issueTitle.trim() ? '请输入问题标题' : '',
  closedAt: submitted.value && form.issueStatus === 'CLOSED' && !form.closedAt ? '问题关闭时必须填写关闭时间' : ''
}))

function minute(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '未记录' }
function priorityTone(value: ReleaseIssuePriority) { return value === 'CRITICAL' ? 'danger' : value === 'HIGH' ? 'warning' : value === 'LOW' ? 'info' : 'primary' }
function statusTone(value: ReleaseIssueStatus) { return value === 'CLOSED' || value === 'RESOLVED' ? 'success' : value === 'ANALYZING' ? 'warning' : 'info' }
function isForbidden(cause: unknown) { return (cause as { response?: { status?: number } }).response?.status === 403 }

async function load() {
  if (!props.projectId || disposed) return
  const version = ++loadVersion
  loading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const response = await listReleaseOperationsIssues({ projectId: props.projectId, keyword: keyword.value || undefined, priority: priority.value, status: status.value, page: page.value, size: pageSize.value })
    if (disposed || version !== loadVersion) return
    issues.value = response.data.data.records
    total.value = response.data.data.total
    // Read-only details do not require maintenance option APIs.
    if (canManage()) {
      const [memberResponse, roundResponse] = await Promise.all([listReleaseOperationMemberOptions(props.projectId), listReleaseDrills(props.projectId)])
      if (disposed || version !== loadVersion) return
      members.value = memberResponse.data.data
      rounds.value = roundResponse.data.data
    }
  } catch (cause) {
    if (disposed || version !== loadVersion) return
    issues.value = []
    total.value = 0
    error.value = apiErrorMessage(cause, '投产问题加载失败，请稍后重试')
    forbidden.value = isForbidden(cause)
  } finally {
    if (!disposed && version === loadVersion) loading.value = false
  }
}

function applyFilter() { page.value = 1; void load() }
function openDetail(issue: ReleaseIssueDto) {
  selectedIssue.value = { ...issue }
  drawerMode.value = 'detail'
  drawerOpen.value = true
}
function openIssue(issue?: ReleaseIssueDto) {
  if (!canManage() || saving.value || confirmingClose.value) return
  editingIssue.value = issue ? { ...issue } : null
  Object.assign(form, {
    issueNo: issue?.issueNo || '', issueTitle: issue?.issueTitle || '',
    priority: issue?.priority || 'MEDIUM', issueStatus: issue?.issueStatus || 'OPEN',
    discoveredAt: issue?.discoveredAt || '', ownerId: issue?.ownerId, drillRoundId: issue?.drillRoundId,
    issueDescription: issue?.issueDescription || '', analysisContent: issue?.analysisContent || '',
    actionContent: issue?.actionContent || '', followUpContent: issue?.followUpContent || '',
    closedAt: issue?.closedAt || '', rowVersion: issue?.rowVersion || 0
  })
  savedForm.value = JSON.stringify(form)
  submitted.value = false
  drawerMode.value = issue ? 'edit' : 'create'
  drawerOpen.value = true
}

async function closeDrawer(done?: () => void) {
  if (saving.value || confirmingClose.value) return
  if (drawerMode.value !== 'detail' && JSON.stringify(form) !== savedForm.value) {
    confirmingClose.value = true
    try {
      await ElMessageBox.confirm('当前修改尚未保存，是否放弃修改？', '关闭问题维护', {
        type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑'
      })
    } catch {
      return
    } finally {
      confirmingClose.value = false
    }
  }
  if (done) done()
  else drawerOpen.value = false
}

async function saveIssue() {
  if (!canManage() || saving.value || confirmingClose.value || drawerMode.value === 'detail') return
  submitted.value = true
  if (Object.values(formErrors.value).some(Boolean)) return
  saving.value = true
  try {
    if (editingIssue.value) await updateReleaseOperationsIssue(props.projectId, editingIssue.value.id, { ...form })
    else await createReleaseOperationsIssue(props.projectId, { ...form })
    if (disposed) return
    drawerOpen.value = false
    ElMessage.success(editingIssue.value ? '问题已更新' : '问题已创建')
    await load()
  } catch (cause) {
    if (!disposed) ElMessage.error(apiErrorMessage(cause, '问题保存失败，请重试'))
  } finally {
    saving.value = false
  }
}

async function removeIssue(issue: ReleaseIssueDto) {
  if (!canManage()) return
  try {
    await ElMessageBox.confirm(`将删除问题“${issue.issueNo} · ${issue.issueTitle}”，删除后不可恢复。`, '删除投产问题', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    if (disposed || !canManage()) return
    await deleteReleaseOperationsIssue(props.projectId, issue.id, issue.rowVersion)
    if (disposed) return
    ElMessage.success('问题已删除')
    await load()
  } catch (cause) {
    if (!disposed && cause !== 'cancel' && cause !== 'close') ElMessage.error(apiErrorMessage(cause, '问题删除失败，请刷新后重试'))
  }
}
function changePage(value: number) { page.value = value; void load() }
function changePageSize(value: number) { pageSize.value = value; page.value = 1; void load() }
onMounted(load)
onBeforeUnmount(() => { disposed = true; loadVersion++ })
</script>

<template>
  <div v-if="forbidden" class="release-operations-state"><el-result icon="warning" title="无权查看投产问题" sub-title="请联系项目管理员申请查看权限。" /></div>
  <div v-else-if="error" class="release-operations-state release-operations-state--error">
    <el-result icon="error" title="投产问题加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result>
  </div>
  <section v-else class="issue-page">
    <UiPageHeader title="投产问题分析及跟踪">
      <template #actions><el-button v-if="canManage()" type="primary" :icon="Plus" @click="openIssue()">新增问题</el-button></template>
    </UiPageHeader>
    <UiToolbar class="issue-toolbar">
      <el-input v-model="keyword" class="issue-search" clearable aria-label="问题关键词" placeholder="问题编号或标题" :prefix-icon="Search" @keyup.enter="applyFilter" />
      <el-select v-model="priority" class="issue-filter" clearable aria-label="优先级筛选" placeholder="全部优先级"><el-option v-for="(label, value) in priorityLabels" :key="value" :label="label" :value="value" /></el-select>
      <el-select v-model="status" class="issue-filter" clearable aria-label="状态筛选" placeholder="全部状态"><el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value" /></el-select>
      <el-button :icon="Search" :disabled="loading" @click="applyFilter">查询</el-button>
      <template #actions>
        <el-button-group class="issue-view-switch" role="group" aria-label="问题展示方式">
          <el-tooltip content="列表视图"><el-button :icon="List" :type="viewMode === 'list' ? 'primary' : 'default'" :aria-pressed="viewMode === 'list'" aria-label="列表视图" @click="viewMode = 'list'" /></el-tooltip>
          <el-tooltip content="卡片视图"><el-button :icon="Grid" :type="viewMode === 'cards' ? 'primary' : 'default'" :aria-pressed="viewMode === 'cards'" aria-label="卡片视图" @click="viewMode = 'cards'" /></el-tooltip>
        </el-button-group>
        <el-tooltip content="刷新投产问题"><el-button :icon="Refresh" :loading="loading" circle aria-label="刷新投产问题" @click="load" /></el-tooltip>
      </template>
    </UiToolbar>
    <div v-loading="loading" class="issue-results" :aria-busy="loading">
      <UiEmptyState v-if="!issues.length && !loading" title="暂无投产问题" description="当前项目没有符合条件的问题记录。" />
      <div v-else-if="viewMode === 'cards'" class="issue-cards">
        <article v-for="issue in issues" :key="issue.id" class="issue-card">
          <header>
            <button class="issue-title" type="button" @click="openDetail(issue)"><span>{{ issue.issueNo }}</span><strong>{{ issue.issueTitle }}</strong></button>
            <UiStatusTag :value="statusLabels[issue.issueStatus]" :tone="statusTone(issue.issueStatus)" />
          </header>
          <dl class="issue-facts">
            <div><dt>优先级</dt><dd><UiStatusTag :value="priorityLabels[issue.priority]" :tone="priorityTone(issue.priority)" /></dd></div>
            <div><dt>负责人</dt><dd>{{ issue.ownerName || '未指定' }}</dd></div>
            <div><dt>演练轮次</dt><dd>{{ issue.drillRoundName || '未关联' }}</dd></div>
            <div><dt>发现时间</dt><dd>{{ minute(issue.discoveredAt) }}</dd></div>
          </dl>
          <div class="issue-card-summary"><span>分析结论</span><p>{{ issue.analysisContent || '未填写' }}</p></div>
          <footer>
            <el-button link type="primary" :icon="View" @click="openDetail(issue)">查看详情</el-button>
            <div v-if="canManage()" class="issue-card-actions">
              <el-tooltip content="编辑问题"><el-button :icon="Edit" aria-label="编辑投产问题" text @click="openIssue(issue)" /></el-tooltip>
              <el-dropdown trigger="click" @command="removeIssue(issue)">
                <el-button :icon="MoreFilled" text aria-label="更多问题操作" title="更多问题操作" />
                <template #dropdown><el-dropdown-menu><el-dropdown-item command="delete" :icon="Delete">删除问题</el-dropdown-item></el-dropdown-menu></template>
              </el-dropdown>
            </div>
          </footer>
        </article>
      </div>
      <div v-else class="issue-table-scroll">
        <el-table :data="issues" row-key="id" stripe>
          <el-table-column label="问题" min-width="240"><template #default="scope"><button class="issue-title" type="button" @click="openDetail(scope.row)"><strong>{{ scope.row.issueTitle }}</strong><span>{{ scope.row.issueNo }} · {{ minute(scope.row.discoveredAt) }}</span></button></template></el-table-column>
          <el-table-column label="演练轮次" min-width="140"><template #default="scope">{{ scope.row.drillRoundName || '未关联' }}</template></el-table-column>
          <el-table-column label="优先级" width="90"><template #default="scope"><UiStatusTag :value="priorityLabels[scope.row.priority as ReleaseIssuePriority]" :tone="priorityTone(scope.row.priority)" /></template></el-table-column>
          <el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="statusLabels[scope.row.issueStatus as ReleaseIssueStatus]" :tone="statusTone(scope.row.issueStatus)" /></template></el-table-column>
          <el-table-column label="负责人" width="110"><template #default="scope">{{ scope.row.ownerName || '未指定' }}</template></el-table-column>
          <el-table-column label="分析结论" min-width="180" show-overflow-tooltip><template #default="scope">{{ scope.row.analysisContent || '未填写' }}</template></el-table-column>
          <el-table-column label="操作" :width="canManage() ? 130 : 64" fixed="right"><template #default="scope">
            <el-tooltip content="查看详情"><el-button link :icon="View" aria-label="查看问题详情" @click="openDetail(scope.row)" /></el-tooltip>
            <template v-if="canManage()">
              <el-tooltip content="编辑问题"><el-button link :icon="Edit" aria-label="编辑投产问题" @click="openIssue(scope.row)" /></el-tooltip>
              <el-tooltip content="删除问题"><el-button link type="danger" :icon="Delete" aria-label="删除投产问题" @click="removeIssue(scope.row)" /></el-tooltip>
            </template>
          </template></el-table-column>
        </el-table>
      </div>
    </div>
    <footer v-if="total" class="issue-pagination">
      <span>共 {{ total }} 条</span>
      <el-pagination :current-page="page" :page-size="pageSize" :page-sizes="[10, 20, 50]" :pager-count="5" :total="total" layout="sizes, prev, pager, next" @current-change="changePage" @size-change="changePageSize" />
    </footer>
  </section>

  <el-drawer v-model="drawerOpen" :title="drawerTitle" :size="issueSize" :class="{ 'release-operations-fullscreen-drawer': issueFullscreen }" class="issue-drawer" append-to-body destroy-on-close :before-close="closeDrawer" :close-on-click-modal="!saving && !confirmingClose" :close-on-press-escape="!saving && !confirmingClose" :show-close="!saving"><template #header="{ titleId, titleClass }"><ReleaseDrawerHeader :title="drawerTitle" :title-id="titleId" :title-class="titleClass" :fullscreen="issueFullscreen" @toggle="toggleIssueFullscreen" /></template>
    <div v-if="drawerMode === 'detail' && selectedIssue" class="issue-detail release-operations-fullscreen-form">
      <header class="issue-detail-heading"><span>{{ selectedIssue.issueNo }}</span><h2>{{ selectedIssue.issueTitle }}</h2><div class="issue-tags"><UiStatusTag :value="priorityLabels[selectedIssue.priority]" :tone="priorityTone(selectedIssue.priority)" /><UiStatusTag :value="statusLabels[selectedIssue.issueStatus]" :tone="statusTone(selectedIssue.issueStatus)" /></div></header>
      <dl class="issue-facts">
        <div><dt>关联演练轮次</dt><dd>{{ selectedIssue.drillRoundName || '未关联' }}</dd></div>
        <div><dt>负责人</dt><dd>{{ selectedIssue.ownerName || '未指定' }}</dd></div>
        <div><dt>发现时间</dt><dd>{{ minute(selectedIssue.discoveredAt) }}</dd></div>
        <div><dt>关闭时间</dt><dd>{{ minute(selectedIssue.closedAt) }}</dd></div>
        <div><dt>更新时间</dt><dd>{{ minute(selectedIssue.updatedAt) }}</dd></div>
      </dl>
      <section v-for="item in textSections" :key="item.key" class="issue-detail-section"><h3>{{ item.label }}</h3><p>{{ selectedIssue[item.key] || '未填写' }}</p></section>
    </div>
    <el-form v-else :model="form" :disabled="saving || confirmingClose" label-position="top" class="issue-form release-operations-fullscreen-form" @submit.prevent="saveIssue">
      <section class="issue-form-section"><h3>基本信息</h3><div class="issue-form-grid">
        <el-form-item label="问题编号" required :error="formErrors.issueNo"><el-input v-model="form.issueNo" maxlength="64" /></el-form-item>
        <el-form-item label="问题标题" required :error="formErrors.issueTitle"><el-input v-model="form.issueTitle" maxlength="256" /></el-form-item>
        <el-form-item label="关联演练轮次"><el-select v-model="form.drillRoundId" clearable filterable placeholder="选择当前项目轮次"><el-option v-for="round in rounds" :key="round.id" :value="round.id" :label="`第 ${round.roundNo} 轮 · ${round.roundName}`" /></el-select></el-form-item>
        <el-form-item label="负责人"><el-select v-model="form.ownerId" clearable filterable placeholder="选择负责人"><el-option v-for="member in members" :key="member.userId" :value="member.userId" :label="`${member.displayName}（${member.username}）`" /></el-select></el-form-item>
        <el-form-item label="优先级"><el-select v-model="form.priority"><el-option v-for="(label, value) in priorityLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.issueStatus"><el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="发现时间"><el-date-picker v-model="form.discoveredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item v-if="form.issueStatus === 'CLOSED'" label="关闭时间" required :error="formErrors.closedAt"><el-date-picker v-model="form.closedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
      </div></section>
      <section class="issue-form-section"><h3>分析与跟踪</h3>
        <el-form-item v-for="item in textSections" :key="item.key" :label="item.label"><el-input v-model="form[item.key]" type="textarea" :rows="4" maxlength="4000" show-word-limit /></el-form-item>
      </section>
    </el-form>
    <template #footer>
      <div class="issue-drawer-actions">
        <el-button :disabled="saving || confirmingClose" @click="closeDrawer()">{{ drawerMode === 'detail' ? '关闭' : '取消' }}</el-button>
        <el-button v-if="drawerMode === 'detail' && selectedIssue && canManage()" type="primary" :icon="Edit" @click="openIssue(selectedIssue)">编辑问题</el-button>
        <el-button v-else-if="drawerMode !== 'detail' && canManage()" type="primary" :loading="saving" :disabled="confirmingClose" @click="saveIssue">保存</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.issue-page { display: grid; gap: 16px; min-width: 0; width: 100%; }
.issue-toolbar :deep(.ui-toolbar__filters) { flex: 1; min-width: 0; }
.issue-search { width: 260px; max-width: 100%; }
.issue-filter { width: 140px; }
.issue-results { min-width: 0; min-height: 160px; }
.issue-view-switch { display: inline-flex; flex-shrink: 0; }
.issue-view-switch .el-button { width: 38px; height: 32px; padding: 0; }
.issue-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 320px), 1fr)); gap: 16px; }
.issue-card { display: flex; flex-direction: column; min-width: 0; padding: 18px; border: 1px solid var(--line); border-radius: 8px; background: var(--panel-bg); }
.issue-card > header { display: flex; align-items: flex-start; gap: 12px; }
.issue-card > header .issue-title { flex: 1; min-width: 0; }
.issue-card > header > :last-child { flex-shrink: 0; }
.issue-card .issue-facts { margin: 20px 0; gap: 16px; }
.issue-card-summary { flex: 1; margin-bottom: 16px; min-width: 0; }
.issue-card-summary > span { color: var(--muted); font-size: 12px; }
.issue-card-summary p { display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; overflow-wrap: anywhere; margin: 6px 0 0; font-size: 14px; line-height: 1.7; color: var(--text); }
.issue-card > footer { display: flex; justify-content: space-between; align-items: center; gap: 8px; border-top: 1px solid var(--line); padding-top: 12px; min-height: 44px; }
.issue-card-actions { display: flex; align-items: center; gap: 4px; }
.issue-card-actions .el-button { width: 36px; height: 36px; padding: 0; }
.issue-table-scroll { width: 100%; min-width: 0; overflow-x: auto; border: 1px solid var(--line); }
.issue-table-scroll > .el-table { width: 100%; }
.issue-title { display: grid; gap: 5px; width: 100%; padding: 0; border: 0; background: transparent; color: var(--text); font: inherit; text-align: left; cursor: pointer; overflow-wrap: anywhere; }
.issue-title strong { font-weight: 600; color: var(--brand); }
.issue-title:hover strong { text-decoration: underline; }
.issue-title span, .issue-detail-heading > span { color: var(--muted); font-size: 12px; }
.issue-pagination { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; color: var(--muted); font-size: 13px; }
.issue-pagination :deep(.el-pagination) { flex-wrap: wrap; gap: 8px; }
.issue-tags { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.issue-detail, .issue-form { min-width: 0; }
.issue-detail-heading { display: grid; gap: 12px; }
.issue-detail-heading h2 { margin: 0; font-size: 20px; line-height: 1.5; overflow-wrap: anywhere; }
.issue-facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px 24px; margin: 24px 0; }
.issue-facts div { min-width: 0; }
.issue-facts dt { color: var(--muted); font-size: 12px; margin-bottom: 6px; }
.issue-facts dd { margin: 0; font-size: 14px; overflow-wrap: anywhere; }
.issue-detail-section, .issue-form-section { border-top: 1px solid var(--line); padding-top: 20px; margin-top: 20px; }
.issue-form-section:first-child { border-top: 0; padding-top: 0; margin-top: 0; }
.issue-detail-section h3, .issue-form-section h3 { margin: 0 0 16px; color: var(--text); font-size: 15px; font-weight: 600; }
.issue-detail-section p { margin: 0; color: var(--text); line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }
.issue-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; }
.issue-form :deep(.el-select), .issue-form :deep(.el-date-editor.el-input) { width: 100%; min-width: 0; }
.issue-form :deep(.el-form-item__content) { min-width: 0; }
.issue-drawer-actions { display: flex; justify-content: flex-end; gap: 12px; }
.issue-drawer-actions .el-button + .el-button { margin-left: 0; }
@media (max-width: 760px) {
  .issue-cards { grid-template-columns: minmax(0, 1fr); }
  .issue-card { padding: 16px; }
  .issue-search { width: 100%; }
  .issue-filter { flex: 1 1 120px; width: auto; }
  .issue-form-grid { grid-template-columns: minmax(0, 1fr); }
  .issue-pagination :deep(.el-pagination) { width: 100%; }
  .issue-pagination :deep(.el-pagination__sizes) { width: 100%; margin: 0; }
  .issue-facts { gap: 16px; }
}
</style>

<style>
.issue-drawer.el-drawer { max-width: calc(100vw - 24px); color: var(--text); background: var(--panel-bg); }
.issue-drawer .el-drawer__header { margin-bottom: 0; padding: 20px 24px; border-bottom: 1px solid var(--line); color: var(--text); }
.issue-drawer .el-drawer__body { padding: 24px; overflow-y: auto; min-height: 0; }
.issue-drawer .el-drawer__footer { padding: 16px 24px; border-top: 1px solid var(--line); }
@media (max-width: 760px) {
  .issue-drawer .el-drawer__header, .issue-drawer .el-drawer__body, .issue-drawer .el-drawer__footer { padding: 16px; }
}
</style>
