<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Delete, Edit, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../api/error'
import { useAuthStore } from '../../stores/auth'
import {
  createEnvironment,
  deactivateEnvironment,
  deleteEnvironment,
  getEnvironment,
  listEnvironments,
  listEnvironmentTypes,
  reactivateEnvironment,
  updateEnvironment
} from './api'
import type { Environment, EnvironmentDetail, EnvironmentPayload, EnvironmentRecordStatus, EnvironmentType } from './types'
import { environmentStatusLabels, environmentStatusTone, formatDateTime, httpStatus } from './utils'
import './architecture.css'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const environments = ref<Environment[]>([])
const types = ref<EnvironmentType[]>([])
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<EnvironmentDetail | null>(null)
const page = ref(1)
const pageSize = ref(20)
const filters = reactive({
  keyword: '',
  typeCode: null as string | null,
  status: '' as EnvironmentRecordStatus | ''
})

const environmentFormOpen = ref(false)
const environmentMode = ref<'create' | 'edit'>('create')
const environmentSubmitting = ref(false)
const environmentFormError = ref('')
const editingEnvironmentId = ref<number | null>(null)
const environmentForm = reactive<EnvironmentPayload>({
  code: '',
  name: '',
  typeCode: null,
  description: null,
  remark: null,
  rowVersion: null
})

let listSequence = 0
let detailSequence = 0
const statusOptions: EnvironmentRecordStatus[] = ['ACTIVE', 'INACTIVE']
const canView = computed(() => ['architecture:environment:view', 'architecture:environment:manage', 'architecture:view', 'architecture:manage'].some(permission => auth.hasPermission(permission)))
const canManage = computed(() => auth.hasPermission('architecture:environment:manage') || auth.hasPermission('architecture:manage'))
const activeTypes = computed(() => types.value)
const hasNext = computed(() => environments.value.length === pageSize.value)
const environmentTypePlaceholder = computed(() => activeTypes.value.length ? '请选择' : '请先在系统字典维护环境类型')

async function load() {
  if (!canView.value) return
  const request = ++listSequence
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const [typeRows, environmentRows] = await Promise.all([
      listEnvironmentTypes(),
      listEnvironments({
        keyword: filters.keyword,
        typeCode: filters.typeCode,
        status: filters.status,
        limit: pageSize.value,
        offset: (page.value - 1) * pageSize.value
      })
    ])
    if (request !== listSequence) return
    types.value = typeRows
    environments.value = environmentRows
  } catch (error) {
    if (request !== listSequence) return
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '具体环境加载失败')
  } finally {
    if (request === listSequence) loading.value = false
  }
}

async function refresh() {
  await load()
  if (!loadError.value && !forbidden.value) ElMessage.success('列表已刷新')
}

function search() {
  page.value = 1
  void load()
}

function reset() {
  Object.assign(filters, { keyword: '', typeCode: null, status: '' })
  page.value = 1
  void load()
}

async function showDetail(row: Environment) {
  const request = ++detailSequence
  detailOpen.value = true
  detailLoading.value = true
  detail.value = { environment: row, resourceSummary: emptySummary(row.id) }
  try {
    const result = await getEnvironment(row.id)
    if (request === detailSequence) detail.value = result
  } catch (error) {
    if (request === detailSequence) ElMessage.error(apiErrorMessage(error, '环境详情加载失败'))
  } finally {
    if (request === detailSequence) detailLoading.value = false
  }
}

function remindDictionaryBeforeEnvironment() {
  environmentFormOpen.value = false
  ElMessage.warning('请先在系统字典维护 ARCH_ENVIRONMENT_TYPE 环境类型，再新增具体环境')
}

function openEnvironmentCreate() {
  if (!activeTypes.value.length) {
    remindDictionaryBeforeEnvironment()
    return
  }
  Object.assign(environmentForm, { code: '', name: '', typeCode: activeTypes.value[0]?.code ?? null, description: null, remark: null, rowVersion: null })
  editingEnvironmentId.value = null
  environmentMode.value = 'create'
  environmentFormError.value = ''
  environmentFormOpen.value = true
}

function openEnvironmentEdit(row: Environment) {
  editingEnvironmentId.value = row.id
  Object.assign(environmentForm, {
    code: row.code,
    name: row.name,
    typeCode: row.typeCode,
    description: row.description,
    remark: row.remark,
    rowVersion: row.rowVersion
  })
  environmentMode.value = 'edit'
  environmentFormError.value = ''
  environmentFormOpen.value = true
}

async function submitEnvironmentForm() {
  if (environmentSubmitting.value) return
  if (!activeTypes.value.length) {
    environmentFormError.value = '请先在系统字典维护 ARCH_ENVIRONMENT_TYPE 环境类型'
    return
  }
  if (!environmentForm.code.trim() || !environmentForm.name.trim() || !environmentForm.typeCode) {
    environmentFormError.value = '请填写环境编码、名称和环境类型'
    return
  }
  environmentSubmitting.value = true
  environmentFormError.value = ''
  try {
    if (environmentMode.value === 'create') {
      await createEnvironment({ ...environmentForm })
      ElMessage.success('具体环境已创建')
    } else if (editingEnvironmentId.value) {
      const updated = await updateEnvironment(editingEnvironmentId.value, { ...environmentForm })
      if (detail.value?.environment.id === updated.id) detail.value = { ...detail.value, environment: updated }
      ElMessage.success('具体环境已更新')
    }
    environmentFormOpen.value = false
    void load()
  } catch (error) {
    environmentFormError.value = apiErrorMessage(error, '保存具体环境失败')
  } finally {
    environmentSubmitting.value = false
  }
}

async function changeEnvironmentStatus(row: Environment, next: EnvironmentRecordStatus) {
  try {
    const action = next === 'ACTIVE' ? '重新启用' : '停用'
    await ElMessageBox.confirm(`${action}「${row.name}」？`, action + '具体环境', { confirmButtonText: action, cancelButtonText: '取消', type: 'warning' })
    if (next === 'ACTIVE') await reactivateEnvironment(row.id, row.rowVersion)
    else await deactivateEnvironment(row.id, row.rowVersion)
    ElMessage.success(action + '成功')
    void load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, '状态变更失败'))
  }
}

async function removeEnvironment(row: Environment) {
  try {
    await ElMessageBox.confirm(`删除「${row.name}」？`, '删除具体环境', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'error' })
    await deleteEnvironment(row.id, row.rowVersion)
    ElMessage.success('具体环境已删除')
    void load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, '删除具体环境失败'))
  }
}

function previous() {
  if (page.value <= 1) return
  page.value -= 1
  void load()
}

function next() {
  if (!hasNext.value) return
  page.value += 1
  void load()
}

function emptySummary(environmentId: number) {
  return {
    environmentId,
    requestCount: 0,
    approvedRequestCount: 0,
    pendingRequestCount: 0,
    requestedCpuCores: 0,
    requestedMemoryGb: 0,
    requestedStorageGb: 0,
    requestedNodeCount: 0,
    actualCpuCores: 0,
    actualMemoryGb: 0,
    actualStorageGb: 0,
    actualNodeCount: 0
  }
}

watch(canView, allowed => {
  if (allowed) void load()
}, { immediate: true })
</script>

<template>
  <main class="architecture-page architecture-environment-page">
    <UiPageHeader title="具体环境" description="维护具体运行环境，环境类型由系统字典 ARCH_ENVIRONMENT_TYPE 维护。">
      <template #actions>
        <div v-if="canManage" class="architecture-page__actions">
          <el-button type="primary" @click="openEnvironmentCreate"><el-icon><Plus /></el-icon>具体环境</el-button>
        </div>
      </template>
    </UiPageHeader>

    <section v-if="auth.token && !auth.user" v-loading="true" class="architecture-state-panel" aria-label="正在确认访问权限" />
    <section v-else-if="!canView || forbidden" class="architecture-state-panel">
      <el-result icon="warning" title="暂无具体环境查看权限" sub-title="请申请 architecture:environment:view 权限。" />
    </section>
    <section v-else-if="loadError" class="architecture-state-panel">
      <el-result icon="error" title="具体环境加载失败" :sub-title="loadError">
        <template #extra><el-button type="primary" @click="load">重新加载</el-button></template>
      </el-result>
    </section>
    <template v-else>
      <UiToolbar>
        <el-input v-model="filters.keyword" clearable placeholder="编码或名称" class="architecture-filter-input" @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-select v-model="filters.typeCode" clearable placeholder="环境类型" class="architecture-filter-select" @change="search"><el-option v-for="item in types" :key="item.code" :label="item.name" :value="item.code" /></el-select>
        <el-select v-model="filters.status" clearable placeholder="状态" class="architecture-filter-select" @change="search"><el-option v-for="status in statusOptions" :key="status" :label="environmentStatusLabels[status]" :value="status" /></el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <template #actions><el-tooltip content="刷新列表"><el-button circle :loading="loading" aria-label="刷新具体环境列表" @click="refresh"><el-icon><Refresh /></el-icon></el-button></el-tooltip></template>
      </UiToolbar>

      <UiDataTable v-if="environments.length || loading" class="architecture-desktop-table" :data="environments" :loading="loading" row-key="id" border>
        <el-table-column label="环境" min-width="220"><template #default="scope"><button type="button" class="architecture-table-identity" @click="showDetail(scope.row)"><strong>{{ scope.row.name }}</strong><small>{{ scope.row.code }} · {{ scope.row.typeName }}</small></button></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="environmentStatusLabels" :tone="environmentStatusTone(scope.row.status)" /></template></el-table-column>
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="最后更新" width="150"><template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="scope">
            <div class="architecture-table-actions">
              <el-button link type="primary" @click="showDetail(scope.row)"><el-icon><View /></el-icon>详情</el-button>
              <el-button v-if="canManage" link type="primary" @click="openEnvironmentEdit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button>
              <el-dropdown>
                <el-button link type="primary">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="router.push(`/architecture/instances?environmentId=${scope.row.id}`)">查看部署实例</el-dropdown-item>
                    <el-dropdown-item v-if="canManage && scope.row.status === 'ACTIVE'" @click="changeEnvironmentStatus(scope.row, 'INACTIVE')">停用</el-dropdown-item>
                    <el-dropdown-item v-if="canManage && scope.row.status === 'INACTIVE'" @click="changeEnvironmentStatus(scope.row, 'ACTIVE')">重新启用</el-dropdown-item>
                    <el-dropdown-item v-if="canManage" divided @click="removeEnvironment(scope.row)"><el-icon><Delete /></el-icon>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </UiDataTable>

      <div v-if="environments.length || loading" v-loading="loading" class="architecture-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="row in environments" :key="row.id">
          <header><div><strong>{{ row.name }}</strong><small>{{ row.code }} · {{ row.typeName }}</small></div><UiStatusTag :value="row.status" :labels="environmentStatusLabels" :tone="environmentStatusTone(row.status)" /></header>
          <dl><div><dt>说明</dt><dd>{{ row.description || '—' }}</dd></div><div><dt>最后更新</dt><dd>{{ formatDateTime(row.updatedAt) }}</dd></div></dl>
          <footer>
            <el-button link type="primary" @click="showDetail(row)"><el-icon><View /></el-icon>详情</el-button>
            <el-button link type="primary" @click="router.push(`/architecture/instances?environmentId=${row.id}`)">实例</el-button>
            <el-button v-if="canManage" link type="primary" @click="openEnvironmentEdit(row)"><el-icon><Edit /></el-icon>编辑</el-button>
          </footer>
        </article>
      </div>
      <UiEmptyState v-if="!loading && !environments.length" title="暂无具体环境" :description="activeTypes.length ? '当前筛选下没有环境记录。' : '请在系统字典维护 ARCH_ENVIRONMENT_TYPE 后刷新。'"><template #action><el-button v-if="canManage && activeTypes.length" type="primary" @click="openEnvironmentCreate">新建具体环境</el-button><el-button v-else @click="reset">清空筛选</el-button></template></UiEmptyState>
      <nav v-if="environments.length || page > 1" class="architecture-change-pagination" aria-label="具体环境分页">
        <span>第 {{ page }} 页</span>
        <div><el-button :disabled="page <= 1 || loading" @click="previous">上一页</el-button><el-button :disabled="!hasNext || loading" @click="next">下一页</el-button></div>
      </nav>
    </template>

    <el-drawer v-model="detailOpen" size="min(560px, 94vw)" :title="detail?.environment.name || '具体环境详情'">
      <div v-loading="detailLoading" class="architecture-drawer-body">
        <template v-if="detail">
          <div class="architecture-detail-heading"><strong>{{ detail.environment.name }}</strong><span>{{ detail.environment.code }} · {{ detail.environment.typeName }}</span></div>
          <dl class="architecture-detail-list">
            <div><dt>状态</dt><dd><UiStatusTag :value="detail.environment.status" :labels="environmentStatusLabels" :tone="environmentStatusTone(detail.environment.status)" /></dd></div>
            <div><dt>最后更新</dt><dd>{{ formatDateTime(detail.environment.updatedAt) }}</dd></div>
            <div class="is-wide"><dt>说明</dt><dd>{{ detail.environment.description || '—' }}</dd></div>
            <div class="is-wide"><dt>备注</dt><dd>{{ detail.environment.remark || '—' }}</dd></div>
          </dl>
          <section class="architecture-drawer-section">
            <header style="display: flex; justify-content: space-between; align-items: center;">
              <div><strong>资源汇总</strong><span class="architecture-muted" style="margin-left: 8px;">申请态 / 实际在用态</span></div>
              <el-button type="primary" link @click="router.push(`/architecture/instances?environmentId=${detail.environment.id}`)">
                查看部署实例 ({{ detail.resourceSummary.actualNodeCount }}) →
              </el-button>
            </header>
            <div class="architecture-resource-summary-grid">
              <article><span>申请态 CPU</span><strong>{{ detail.resourceSummary.requestedCpuCores }}</strong></article>
              <article><span>申请态内存</span><strong>{{ detail.resourceSummary.requestedMemoryGb }} GB</strong></article>
              <article><span>申请态存储</span><strong>{{ detail.resourceSummary.requestedStorageGb }} GB</strong></article>
              <article><span>申请态节点</span><strong>{{ detail.resourceSummary.requestedNodeCount }}</strong></article>
              <article><span>实际态 CPU</span><strong>{{ detail.resourceSummary.actualCpuCores }}</strong></article>
              <article><span>实际态内存</span><strong>{{ detail.resourceSummary.actualMemoryGb }} GB</strong></article>
              <article><span>实际态存储</span><strong>{{ detail.resourceSummary.actualStorageGb }} GB</strong></article>
              <article><span>实际态节点</span><strong>{{ detail.resourceSummary.actualNodeCount }}</strong></article>
            </div>
          </section>
          <div v-if="canManage" class="architecture-drawer-actions architecture-drawer-section">
            <el-button @click="openEnvironmentEdit(detail.environment)"><el-icon><Edit /></el-icon>编辑</el-button>
          </div>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="environmentFormOpen" :title="environmentMode === 'create' ? '新建具体环境' : '编辑具体环境'" width="min(560px, 94vw)" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="环境编码"><el-input v-model="environmentForm.code" maxlength="64" /></el-form-item>
        <el-form-item label="环境名称"><el-input v-model="environmentForm.name" maxlength="160" /></el-form-item>
        <el-form-item label="环境类型"><el-select v-model="environmentForm.typeCode" filterable style="width:100%" :disabled="!activeTypes.length" :placeholder="environmentTypePlaceholder" no-data-text="暂无启用环境类型"><el-option v-for="item in activeTypes" :key="item.code" :label="item.name" :value="item.code" /></el-select></el-form-item>
        <el-alert v-if="!activeTypes.length" type="warning" :closable="false" show-icon title="请先在系统字典维护 ARCH_ENVIRONMENT_TYPE 环境类型" />
        <el-form-item label="说明"><el-input v-model="environmentForm.description" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item>
        <el-form-item label="备注"><el-input v-model="environmentForm.remark" type="textarea" :rows="2" maxlength="1000" show-word-limit /></el-form-item>
        <el-alert v-if="environmentFormError" type="error" :closable="false" show-icon :title="environmentFormError" />
      </el-form>
      <template #footer><el-button @click="environmentFormOpen = false">取消</el-button><el-button type="primary" :loading="environmentSubmitting" @click="submitEnvironmentForm">保存</el-button></template>
    </el-dialog>
  </main>
</template>
