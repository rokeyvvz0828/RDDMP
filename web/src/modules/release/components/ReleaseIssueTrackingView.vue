<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiErrorMessage } from '../../../api/error'
import {
  createReleaseOperationsIssue,
  deleteReleaseOperationsIssue,
  listReleaseOperationMemberOptions,
  listReleaseOperationsIssues,
  updateReleaseOperationsIssue,
  type ReleaseIssueDto,
  type ReleaseIssuePriority,
  type ReleaseIssueStatus,
  type ReleaseIssueWrite,
  type ReleaseMemberOptionDto
} from '../../../api/release'
import { useAuthStore } from '../../../stores/auth'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'

const props = defineProps<{ projectId: number }>()
const auth = useAuthStore()
const issues = ref<ReleaseIssueDto[]>([])
const members = ref<ReleaseMemberOptionDto[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const priority = ref<ReleaseIssuePriority>()
const status = ref<ReleaseIssueStatus>()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const forbidden = ref(false)
const dialogOpen = ref(false)
const editingIssue = ref<ReleaseIssueDto | null>(null)
const form = reactive<ReleaseIssueWrite>({ issueNo: '', issueTitle: '', priority: 'MEDIUM', issueStatus: 'OPEN', discoveredAt: '', ownerId: undefined, issueDescription: '', analysisContent: '', actionContent: '', followUpContent: '', closedAt: '', rowVersion: 0 })
const canManage = computed(() => auth.hasPermission('release-operations:issue:manage'))
const priorityLabels: Record<ReleaseIssuePriority, string> = { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '紧急' }
const statusLabels: Record<ReleaseIssueStatus, string> = { OPEN: '待处理', ANALYZING: '分析中', RESOLVED: '已解决', CLOSED: '已关闭' }

function projectId() { return props.projectId }
function minute(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '未记录' }
function priorityTone(value: ReleaseIssuePriority) { return value === 'CRITICAL' ? 'danger' : value === 'HIGH' ? 'warning' : value === 'LOW' ? 'info' : 'primary' }
function statusTone(value: ReleaseIssueStatus) { return value === 'CLOSED' || value === 'RESOLVED' ? 'success' : value === 'ANALYZING' ? 'warning' : 'info' }
function priorityText(value: unknown) { return priorityLabels[String(value) as ReleaseIssuePriority] || String(value || '-') }
function statusText(value: unknown) { return statusLabels[String(value) as ReleaseIssueStatus] || String(value || '-') }
function priorityTagTone(value: unknown) { return priorityTone(String(value) as ReleaseIssuePriority) }
function statusTagTone(value: unknown) { return statusTone(String(value) as ReleaseIssueStatus) }
function isForbidden(cause: unknown) { return (cause as { response?: { status?: number } }).response?.status === 403 }

async function load() {
  if (!projectId()) return
  loading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const response = await listReleaseOperationsIssues({ projectId: projectId(), keyword: keyword.value || undefined, priority: priority.value, status: status.value, page: page.value, size: pageSize.value })
    issues.value = response.data.data.records
    total.value = response.data.data.total
    if (!members.value.length) members.value = (await listReleaseOperationMemberOptions(projectId())).data.data
  } catch (cause) {
    issues.value = []
    total.value = 0
    error.value = apiErrorMessage(cause, '投产问题加载失败，请稍后重试')
    forbidden.value = isForbidden(cause)
  } finally { loading.value = false }
}

function applyFilter() { page.value = 1; void load() }
function openIssue(issue?: ReleaseIssueDto) {
  editingIssue.value = issue || null
  Object.assign(form, { issueNo: issue?.issueNo || '', issueTitle: issue?.issueTitle || '', priority: issue?.priority || 'MEDIUM', issueStatus: issue?.issueStatus || 'OPEN', discoveredAt: issue?.discoveredAt || '', ownerId: issue?.ownerId, issueDescription: issue?.issueDescription || '', analysisContent: issue?.analysisContent || '', actionContent: issue?.actionContent || '', followUpContent: issue?.followUpContent || '', closedAt: issue?.closedAt || '', rowVersion: issue?.rowVersion || 0 })
  dialogOpen.value = true
}

async function saveIssue() {
  if (!form.issueNo.trim() || !form.issueTitle.trim() || saving.value) return
  if (form.issueStatus === 'CLOSED' && !form.closedAt) { ElMessage.warning('问题关闭时必须填写关闭时间'); return }
  saving.value = true
  try {
    const response = editingIssue.value
      ? await updateReleaseOperationsIssue(projectId(), editingIssue.value.id, { ...form })
      : await createReleaseOperationsIssue(projectId(), { ...form })
    const next = issues.value.filter(item => item.id !== response.data.data.id)
    next.unshift(response.data.data)
    issues.value = next.slice(0, pageSize.value)
    dialogOpen.value = false
    ElMessage.success(editingIssue.value ? '问题已更新' : '问题已创建')
    await load()
  } catch (cause) { ElMessage.error(apiErrorMessage(cause, '问题保存失败，请重试'))
  } finally { saving.value = false }
}

async function removeIssue(issue: ReleaseIssueDto) {
  try { await ElMessageBox.confirm(`将删除问题“${issue.issueNo} · ${issue.issueTitle}”，删除后不可恢复。`, '删除投产问题', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) } catch { return }
  try { await deleteReleaseOperationsIssue(projectId(), issue.id, issue.rowVersion); ElMessage.success('问题已删除'); await load() }
  catch (cause) { ElMessage.error(apiErrorMessage(cause, '问题删除失败，请刷新后重试')) }
}

function changePage(value: number) { page.value = value; void load() }
function changePageSize(value: number) { pageSize.value = value; page.value = 1; void load() }

onMounted(load)
</script>

<template>
  <div class="release-operations-layout">
    <div v-if="forbidden" class="release-operations-state"><el-result icon="warning" title="无权查看投产问题" sub-title="请联系项目管理员申请查看权限。" /></div>
    <div v-else-if="error" class="release-operations-state release-operations-state--error"><el-result icon="error" title="投产问题加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></div>
    <section v-else v-loading="loading" class="release-operations-panel">
      <header class="release-operations-panel__header"><div><span class="release-operations-kicker">ISSUE CONTROL</span><h2>投产问题分析及跟踪</h2><p>集中记录发现、分析、处理与后续跟踪信息。</p></div><el-button v-if="canManage" type="primary" :icon="Plus" @click="openIssue()">新增问题</el-button></header>
      <div class="release-operations-filter"><el-form inline @submit.prevent="applyFilter"><el-form-item label="关键词"><el-input v-model="keyword" clearable placeholder="编号或标题" @keyup.enter="applyFilter" /></el-form-item><el-form-item label="优先级"><el-select v-model="priority" clearable placeholder="全部优先级"><el-option v-for="(label, value) in priorityLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item><el-form-item label="状态"><el-select v-model="status" clearable placeholder="全部状态"><el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item><el-form-item><el-button type="primary" :icon="Search" @click="applyFilter">查询</el-button><el-button :icon="Refresh" aria-label="刷新投产问题" @click="load" /></el-form-item></el-form></div>
      <div v-if="!issues.length && !loading" class="release-operations-empty"><UiEmptyState title="暂无投产问题" description="当前项目没有符合条件的问题记录。"><template #action><el-button v-if="canManage" type="primary" @click="openIssue()">新增问题</el-button></template></UiEmptyState></div>
      <div v-else class="release-operations-table-scroll"><el-table :data="issues" row-key="id" stripe><el-table-column label="问题" min-width="220"><template #default="scope"><div class="release-primary-cell is-static"><strong>{{ scope.row.issueNo }} · {{ scope.row.issueTitle }}</strong><span>{{ minute(scope.row.discoveredAt) }}</span></div></template></el-table-column><el-table-column label="优先级" width="100"><template #default="scope"><UiStatusTag :value="priorityText(scope.row.priority)" :tone="priorityTagTone(scope.row.priority)" /></template></el-table-column><el-table-column label="状态" width="110"><template #default="scope"><UiStatusTag :value="statusText(scope.row.issueStatus)" :tone="statusTagTone(scope.row.issueStatus)" /></template></el-table-column><el-table-column label="负责人" width="130"><template #default="scope">{{ scope.row.ownerName || '未指定' }}</template></el-table-column><el-table-column label="分析结论" min-width="230" show-overflow-tooltip><template #default="scope">{{ scope.row.analysisContent || '未填写' }}</template></el-table-column><el-table-column v-if="canManage" label="操作" width="110" fixed="right"><template #default="scope"><el-button link :icon="Edit" aria-label="编辑投产问题" @click="openIssue(scope.row)" /><el-button link type="danger" :icon="Delete" aria-label="删除投产问题" @click="removeIssue(scope.row)" /></template></el-table-column></el-table></div>
      <footer v-if="total" class="release-operations-panel__body"><div style="display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap"><span class="release-operations-muted">共 {{ total }} 条问题</span><el-pagination :current-page="page" :page-size="pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next" @current-change="changePage" @size-change="changePageSize" /></div></footer>
    </section>
  </div>

  <el-dialog v-model="dialogOpen" :title="editingIssue ? '编辑投产问题' : '新增投产问题'" width="760px" destroy-on-close>
    <el-form label-position="top" class="release-operations-dialog-form release-operations-form-grid"><el-form-item label="问题编号" required><el-input v-model="form.issueNo" maxlength="64" /></el-form-item><el-form-item label="问题标题" required><el-input v-model="form.issueTitle" maxlength="256" /></el-form-item><el-form-item label="优先级"><el-select v-model="form.priority"><el-option v-for="(label, value) in priorityLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item><el-form-item label="状态"><el-select v-model="form.issueStatus"><el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item><el-form-item label="发现时间"><el-date-picker v-model="form.discoveredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item><el-form-item label="负责人"><el-select v-model="form.ownerId" clearable placeholder="选择当前项目成员"><el-option v-for="member in members" :key="member.userId" :value="member.userId" :label="`${member.displayName}（${member.username}）`" /></el-select></el-form-item><el-form-item v-if="form.issueStatus === 'CLOSED'" label="关闭时间" required><el-date-picker v-model="form.closedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item><el-form-item label="问题描述" class="is-wide"><el-input v-model="form.issueDescription" type="textarea" :rows="3" maxlength="4000" show-word-limit /></el-form-item><el-form-item label="分析结论"><el-input v-model="form.analysisContent" type="textarea" :rows="3" maxlength="4000" show-word-limit /></el-form-item><el-form-item label="处理措施"><el-input v-model="form.actionContent" type="textarea" :rows="3" maxlength="4000" show-word-limit /></el-form-item><el-form-item label="跟踪记录" class="is-wide"><el-input v-model="form.followUpContent" type="textarea" :rows="3" maxlength="4000" show-word-limit /></el-form-item></el-form>
    <template #footer><el-button @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!form.issueNo.trim() || !form.issueTitle.trim()" @click="saveIssue">保存</el-button></template>
  </el-dialog>
</template>
