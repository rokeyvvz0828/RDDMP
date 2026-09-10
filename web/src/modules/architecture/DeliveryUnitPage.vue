<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Filter, MoreFilled, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../../components/ui/UiFormDrawer.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../api/error'
import { useAuthStore } from '../../stores/auth'
import { useProjectContextStore } from '../../stores/project-context'
import {
  createDeliveryUnit,
  deleteDeliveryUnit,
  deactivateDeliveryUnit,
  getDeliveryUnit,
  listDeliveryUnits,
  loadDeliveryUnitPhysicalSubsystemOptions,
  reactivateDeliveryUnit,
  searchDeliveryUnitDeploymentUnitOptions,
  updateDeliveryUnit
} from './api'
import DeliveryUnitDetailDrawer from './components/DeliveryUnitDetailDrawer.vue'
import type {
  DeliveryUnit,
  DeliveryUnitPayload,
  DeliveryUnitStatus,
  PhysicalSubsystemOption,
  RelatedDeploymentUnit
} from './types'
import {
  deliveryUnitStatusLabels,
  deliveryUnitStatusTone,
  formatDateTime,
  httpStatus
} from './utils'
import './architecture.css'

const auth = useAuthStore()
const projectContext = useProjectContextStore()
const rows = ref<DeliveryUnit[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const advanced = ref(false)

const filters = reactive({
  name: '',
  physicalSubsystemId: null as number | null,
  status: '' as DeliveryUnitStatus | ''
})
const statusOptions: DeliveryUnitStatus[] = ['ACTIVE', 'INACTIVE']
const physicalOptions = ref<PhysicalSubsystemOption[]>([])

const drawerOpen = ref(false)
const detail = ref<DeliveryUnit | null>(null)
const detailLoading = ref(false)

const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formSubmitting = ref(false)
const formError = ref('')
const formBaseline = ref('')
const form = reactive<DeliveryUnitPayload>({
  physicalSubsystemId: null,
  name: '',
  description: null,
  remark: null,
  relatedDeploymentUnitIds: [],
  rowVersion: null
})
const relatedOptions = ref<RelatedDeploymentUnit[]>([])
const relatedLoading = ref(false)
const relatedError = ref('')

let listRequest = 0
let detailRequest = 0
let relatedRequest = 0
// el-drawer 在父层把 model-value 置为 false 时也会回发 update:modelValue，
// 保存成功主动关闭需要通过该标记跳过未保存确认，避免重复弹窗。
let suppressCloseGuard = false

const canView = computed(() => auth.hasPermission('architecture:delivery-unit:view')
  || auth.hasPermission('architecture:delivery-unit:manage')
  || ['architecture:view', 'architecture:apply', 'architecture:manage'].some(p => auth.hasPermission(p)))
const canManage = computed(() => auth.hasPermission('architecture:delivery-unit:manage'))

function text(value: string | null | undefined) {
  const normalized = value?.trim()
  return normalized || null
}

function formSnapshot() {
  return JSON.stringify({ ...form, relatedDeploymentUnitIds: [...form.relatedDeploymentUnitIds].sort((a, b) => a - b) })
}

async function requestCloseForm(value: boolean) {
  if (value) {
    formOpen.value = true
    return
  }
  if (suppressCloseGuard) {
    suppressCloseGuard = false
    formOpen.value = false
    return
  }
  if (formSubmitting.value) return
  if (formSnapshot() !== formBaseline.value) {
    try {
      await ElMessageBox.confirm('当前交付单元信息尚未保存，关闭后修改将丢失。', '放弃未保存修改？', {
        confirmButtonText: '放弃修改',
        cancelButtonText: '继续编辑',
        type: 'warning'
      })
    } catch {
      return
    }
  }
  formOpen.value = false
}

function mergeRelatedOptions(items: RelatedDeploymentUnit[]) {
  const selectedIds = new Set(form.relatedDeploymentUnitIds)
  const retained = relatedOptions.value.filter(item => selectedIds.has(item.id))
  const merged = new Map(retained.map(item => [item.id, item]))
  items.forEach(item => merged.set(item.id, item))
  relatedOptions.value = [...merged.values()]
}

async function searchRelatedOptions(keyword = '') {
  const physicalSubsystemId = form.physicalSubsystemId
  if (!physicalSubsystemId) {
    relatedOptions.value = []
    return
  }
  const request = ++relatedRequest
  relatedLoading.value = true
  relatedError.value = ''
  try {
    const result = await searchDeliveryUnitDeploymentUnitOptions({ physicalSubsystemId, keyword, page: 1, size: 50 })
    if (request !== relatedRequest) return
    mergeRelatedOptions(result.records)
  } catch (error) {
    if (request !== relatedRequest) return
    relatedError.value = apiErrorMessage(error, '同物理子系统部署单元搜索失败')
  } finally {
    if (request === relatedRequest) relatedLoading.value = false
  }
}

async function loadPhysicals() {
  if (!projectContext.currentRef) return
  try {
    physicalOptions.value = await loadDeliveryUnitPhysicalSubsystemOptions('', 100)
  } catch (error) {
    if (httpStatus(error) !== 403) ElMessage.warning(apiErrorMessage(error, '物理子系统选项加载失败'))
  }
}

async function load() {
  if (!canView.value || !projectContext.currentRef) return
  const request = ++listRequest
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const result = await listDeliveryUnits({ page: page.value, size: pageSize.value, ...filters })
    if (request !== listRequest) return
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    if (request !== listRequest) return
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '交付单元列表加载失败')
  } finally {
    if (request === listRequest) loading.value = false
  }
}

async function showDetail(row: DeliveryUnit) {
  const request = ++detailRequest
  detail.value = row
  drawerOpen.value = true
  detailLoading.value = true
  try {
    const result = await getDeliveryUnit(row.id)
    if (request === detailRequest) detail.value = result
  } catch (error) {
    if (request === detailRequest) ElMessage.error(apiErrorMessage(error, '交付单元详情加载失败'))
  } finally {
    if (request === detailRequest) detailLoading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    physicalSubsystemId: physicalOptions.value.length === 1 ? physicalOptions.value[0].id : null,
    name: '',
    description: null,
    remark: null,
    relatedDeploymentUnitIds: [],
    rowVersion: null
  })
  formMode.value = 'create'
  formError.value = ''
  relatedOptions.value = []
  relatedError.value = ''
  suppressCloseGuard = false
  formBaseline.value = formSnapshot()
  formOpen.value = true
  void searchRelatedOptions()
}

function openEdit(unit: DeliveryUnit) {
  Object.assign(form, {
    physicalSubsystemId: unit.physicalSubsystemId,
    name: unit.name,
    description: unit.description,
    remark: unit.remark,
    relatedDeploymentUnitIds: unit.relatedDeploymentUnits.map(item => item.id),
    rowVersion: unit.rowVersion
  })
  formMode.value = 'edit'
  formError.value = ''
  relatedOptions.value = [...unit.relatedDeploymentUnits]
  relatedError.value = ''
  suppressCloseGuard = false
  formBaseline.value = formSnapshot()
  formOpen.value = true
  void searchRelatedOptions()
}

async function submitForm() {
  if (formSubmitting.value) return
  formError.value = ''
  if (!form.physicalSubsystemId) {
    formError.value = '请选择归属物理子系统'
    return
  }
  const name = text(form.name)
  if (!name || name.length < 2 || name.length > 200) {
    formError.value = '交付单元名称长度必须为 2—200 个字符'
    return
  }
  formSubmitting.value = true
  try {
    const payload: DeliveryUnitPayload = {
      physicalSubsystemId: form.physicalSubsystemId,
      name,
      description: text(form.description),
      remark: text(form.remark),
      relatedDeploymentUnitIds: [...form.relatedDeploymentUnitIds],
      rowVersion: form.rowVersion
    }
    if (formMode.value === 'create') {
      const created = await createDeliveryUnit(payload)
      ElMessage.success(`交付单元已创建，编号 ${created.code}`)
    } else {
      const updated = await updateDeliveryUnit(detail.value!.id, { ...payload, physicalSubsystemId: null })
      ElMessage.success('交付单元已更新')
      detail.value = updated
    }
    suppressCloseGuard = true
    formOpen.value = false
    void load()
  } catch (error) {
    formError.value = apiErrorMessage(error, formMode.value === 'create' ? '创建失败' : '保存失败')
  } finally {
    formSubmitting.value = false
  }
}

async function confirmLifecycle(action: 'deactivate' | 'reactivate' | 'delete', unit: DeliveryUnit) {
  const messages = {
    deactivate: {
      title: '停用交付单元',
      text: `停用「${unit.name}」后不能再调整其部署单元关联，既有信息与关联保留。是否继续？`,
      confirm: '停用'
    },
    reactivate: {
      title: '重新启用交付单元',
      text: `重新启用「${unit.name}」后可以继续调整部署单元关联。是否继续？`,
      confirm: '重新启用'
    },
    delete: {
      title: '删除交付单元',
      text: `删除「${unit.name}」将同时解除它与部署单元的关联；部署单元本身不受影响。删除后不可恢复，是否继续？`,
      confirm: '删除'
    }
  }[action]
  try {
    await ElMessageBox.confirm(messages.text, messages.title, {
      confirmButtonText: messages.confirm,
      cancelButtonText: '取消',
      type: action === 'delete' ? 'error' : 'warning'
    })
  } catch {
    return
  }
  try {
    if (action === 'delete') {
      await deleteDeliveryUnit(unit.id)
      ElMessage.success('删除成功')
      if (detail.value?.id === unit.id) {
        detail.value = null
        drawerOpen.value = false
      }
    } else {
      const updated = action === 'deactivate'
        ? await deactivateDeliveryUnit(unit.id)
        : await reactivateDeliveryUnit(unit.id)
      ElMessage.success(`${messages.confirm}成功`)
      detail.value = updated
    }
    void load()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, `${messages.confirm}失败`))
  }
}

function handleMaintainCommand(command: string | number | object, unit: DeliveryUnit) {
  if (command === 'edit') {
    openEdit(unit)
    return
  }
  if (command === 'deactivate' || command === 'reactivate' || command === 'delete') {
    void confirmLifecycle(command, unit)
  }
}

function search() { page.value = 1; void load() }
function reset() {
  Object.assign(filters, { name: '', physicalSubsystemId: null, status: '' })
  page.value = 1
  void load()
}
async function refresh() {
  await Promise.all([load(), loadPhysicals()])
  if (!loadError.value && !forbidden.value) ElMessage.success('列表已刷新')
}
function changePage(value: number) { page.value = value; void load() }
function changePageSize(value: number) { pageSize.value = value; page.value = 1; void load() }

function physicalLabel(row: { physicalSubsystemName: string | null; physicalSubsystemCode: string | null }) {
  if (!row.physicalSubsystemName && !row.physicalSubsystemCode) return '—'
  return `${row.physicalSubsystemName || '—'}${row.physicalSubsystemCode ? `（${row.physicalSubsystemCode}）` : ''}`
}

watch(() => [canView.value, projectContext.currentRef] as const, ([allowed, projectRef]) => {
  if (allowed && projectRef) void Promise.all([load(), loadPhysicals()])
}, { immediate: true })
</script>

<template>
  <main class="architecture-page">
    <UiPageHeader title="交付单元" description="维护交付内容的稳定逻辑身份：每个交付单元归属一个物理子系统，可关联该物理子系统下的启用部署单元。">
      <template #actions><el-button v-if="canManage" type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新建交付单元</el-button></template>
    </UiPageHeader>

    <section v-if="auth.token && !auth.user" v-loading="true" class="architecture-state-panel" aria-label="正在确认访问权限" />
    <section v-else-if="!canView || forbidden" class="architecture-state-panel"><el-result icon="warning" title="暂无交付单元查看权限" sub-title="请申请 architecture:delivery-unit:view 权限。" /></section>
    <section v-else-if="loadError" class="architecture-state-panel"><el-result icon="error" title="交付单元加载失败" :sub-title="loadError"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></section>
    <template v-else>
      <UiToolbar>
        <el-input v-model="filters.name" clearable placeholder="交付单元名称" class="architecture-filter-input" @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-select v-model="filters.status" clearable placeholder="状态" class="architecture-filter-select"><el-option v-for="status in statusOptions" :key="status" :label="deliveryUnitStatusLabels[status]" :value="status" /></el-select>
        <el-button :type="advanced ? 'primary' : 'default'" plain @click="advanced = !advanced"><el-icon><Filter /></el-icon>更多筛选</el-button>
        <el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button>
        <template #actions><el-tooltip content="刷新列表"><el-button circle :loading="loading" aria-label="刷新交付单元列表" @click="refresh"><el-icon><Refresh /></el-icon></el-button></el-tooltip></template>
      </UiToolbar>
      <div v-if="advanced" class="architecture-advanced-filter"><el-form inline label-position="top"><el-form-item label="归属物理子系统"><el-select v-model="filters.physicalSubsystemId" clearable filterable style="width:260px"><el-option v-for="item in physicalOptions" :key="item.id" :label="`${item.name}（${item.code}）`" :value="item.id" /></el-select></el-form-item><el-form-item><el-button type="primary" @click="search">应用筛选</el-button></el-form-item></el-form></div>

      <UiDataTable v-if="rows.length || loading" class="architecture-desktop-table" :data="rows" :loading="loading" row-key="id" border>
        <el-table-column label="交付单元" min-width="220"><template #default="scope"><button type="button" class="architecture-table-identity" @click="showDetail(scope.row)"><strong>{{ scope.row.name }}</strong><small>{{ scope.row.code }}</small></button></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="deliveryUnitStatusLabels" :tone="deliveryUnitStatusTone(scope.row.status)" /></template></el-table-column>
        <el-table-column label="归属物理子系统" min-width="200"><template #default="scope">{{ scope.row.physicalSubsystemName }}<small class="architecture-inline-code">{{ scope.row.physicalSubsystemCode }}</small></template></el-table-column>
        <el-table-column label="关联部署单元" width="130"><template #default="scope">{{ scope.row.relatedDeploymentUnits.length }} 个</template></el-table-column>
        <el-table-column label="最后更新" width="145"><template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right"><template #default="scope"><div class="architecture-table-actions"><el-button link type="primary" @click="showDetail(scope.row)"><el-icon><View /></el-icon>详情</el-button><el-dropdown v-if="canManage" @command="(command: string | number | object) => handleMaintainCommand(command, scope.row)"><el-button link type="primary"><el-icon><MoreFilled /></el-icon>维护</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="edit">修改</el-dropdown-item><el-dropdown-item v-if="scope.row.status === 'ACTIVE'" command="deactivate" divided>停用</el-dropdown-item><el-dropdown-item v-if="scope.row.status === 'INACTIVE'" command="reactivate">重新启用</el-dropdown-item><el-dropdown-item command="delete" divided>删除</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></template></el-table-column>
        <template #footer><div class="architecture-table-footer"><span>共 {{ total }} 条记录</span><el-pagination :current-page="page" :page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @current-change="changePage" @size-change="changePageSize" /></div></template>
      </UiDataTable>

      <div v-if="rows.length || loading" v-loading="loading" class="architecture-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="row in rows" :key="row.id"><header><div><strong>{{ row.name }}</strong><small>{{ row.code }}</small></div><UiStatusTag :value="row.status" :labels="deliveryUnitStatusLabels" :tone="deliveryUnitStatusTone(row.status)" /></header><dl><div><dt>归属物理子系统</dt><dd>{{ physicalLabel(row) }}</dd></div><div><dt>关联部署单元</dt><dd>{{ row.relatedDeploymentUnits.length }} 个</dd></div><div><dt>最后更新</dt><dd>{{ formatDateTime(row.updatedAt) }}</dd></div></dl><footer><el-button link type="primary" @click="showDetail(row)"><el-icon><View /></el-icon>详情</el-button><el-dropdown v-if="canManage" @command="(command: string | number | object) => handleMaintainCommand(command, row)"><el-button link type="primary"><el-icon><MoreFilled /></el-icon>维护</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="edit">修改</el-dropdown-item><el-dropdown-item v-if="row.status === 'ACTIVE'" command="deactivate" divided>停用</el-dropdown-item><el-dropdown-item v-if="row.status === 'INACTIVE'" command="reactivate">重新启用</el-dropdown-item><el-dropdown-item command="delete" divided>删除</el-dropdown-item></el-dropdown-menu></template></el-dropdown></footer></article>
        <div class="architecture-table-footer"><el-pagination :current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="changePage" /></div>
      </div>
      <UiEmptyState v-if="!loading && !rows.length" title="暂无交付单元" description="选择归属物理子系统并填写交付单元名称后创建，创建后可在详情抽屉关联部署单元。"><template #action><el-button v-if="canManage" type="primary" @click="openCreate">新建交付单元</el-button><el-button v-else @click="reset">清空筛选</el-button></template></UiEmptyState>
    </template>

    <DeliveryUnitDetailDrawer
      v-model="drawerOpen"
      :loading="detailLoading"
      :can-manage="canManage"
      :unit="detail"
      :title="detail?.name || '交付单元详情'"
      @edit="detail && openEdit(detail)"
      @deactivate="detail && confirmLifecycle('deactivate', detail)"
      @reactivate="detail && confirmLifecycle('reactivate', detail)"
      @delete="detail && confirmLifecycle('delete', detail)"
      @updated="(unit: DeliveryUnit) => { detail = unit; void load() }"
    />

    <UiFormDrawer
      :model-value="formOpen"
      :title="formMode === 'create' ? '新建交付单元' : '修改交付单元'"
      width="min(620px, 94vw)"
      :loading="formSubmitting"
      :confirm-text="formMode === 'create' ? '创建' : '保存'"
      @update:model-value="requestCloseForm"
      @submit="submitForm"
    >
      <el-form label-position="top">
        <el-form-item label="归属物理子系统">
          <el-select v-model="form.physicalSubsystemId" filterable :disabled="formMode === 'edit'" placeholder="选择物理子系统" style="width:100%" @change="searchRelatedOptions()">
            <el-option v-for="item in physicalOptions" :key="item.id" :label="`${item.name}（${item.code}）`" :value="item.id" />
          </el-select>
          <p class="architecture-form-hint">{{ formMode === 'edit' ? '归属物理子系统创建后不可变更。' : '关联部署单元只能来自所选物理子系统。' }}</p>
        </el-form-item>
        <el-form-item label="交付单元名称">
          <el-input v-model="form.name" maxlength="200" show-word-limit placeholder="如 统一认证交付包" />
        </el-form-item>
        <el-form-item label="关联部署单元">
          <el-select
            v-model="form.relatedDeploymentUnitIds"
            multiple
            filterable
            remote
            reserve-keyword
            collapse-tags
            collapse-tags-tooltip
            :disabled="!form.physicalSubsystemId"
            :loading="relatedLoading"
            :remote-method="searchRelatedOptions"
            :no-data-text="relatedError || '当前物理子系统下没有匹配的启用部署单元'"
            placeholder="按名称或编号搜索，可多选"
            class="architecture-related-unit-select"
          >
            <el-option v-for="unit in relatedOptions" :key="unit.id" :label="`${unit.name}（${unit.code}）`" :value="unit.id" />
          </el-select>
          <p v-if="relatedError" class="architecture-field-error">{{ relatedError }}，已选项已保留，可重新输入关键字重试。</p>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" show-word-limit /></el-form-item>
        <el-alert v-if="formError" type="error" :closable="false" show-icon :title="formError" class="architecture-form-error" />
      </el-form>
    </UiFormDrawer>
  </main>
</template>
