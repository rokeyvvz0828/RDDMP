<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../api/error'
import { useAuthStore } from '../../stores/auth'
import {
  createNetworkZone,
  createNetworkZoneSubnet,
  deactivateNetworkZone,
  deactivateNetworkZoneSubnet,
  listNetworkZoneSubnets,
  listNetworkZones,
  loadNetworkZoneOptions,
  reactivateNetworkZone,
  reactivateNetworkZoneSubnet,
  updateNetworkZoneSubnet,
  updateNetworkZone
} from './api'
import type { EnvironmentRecordStatus, NetworkZone, NetworkZoneOption, NetworkZoneSubnet } from './types'
import { environmentStatusLabels, environmentStatusTone, formatDateTime, httpStatus } from './utils'
import './architecture.css'

type NetworkZoneForm = {
  parentId: number | null
  code: string
  name: string
  restrictionLevel: number
  description: string | null
  remark: string | null
  rowVersion: number | null
}

type SubnetForm = {
  cidrBlock: string
  gatewayIp: string | null
  purpose: string | null
  remark: string | null
  rowVersion: number | null
}

const auth = useAuthStore()
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const rows = ref<NetworkZone[]>([])
const parentOptions = ref<NetworkZoneOption[]>([])
const filters = reactive({
  keyword: '',
  status: '' as EnvironmentRecordStatus | ''
})
const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formError = ref('')
const editingId = ref<number | null>(null)
const form = reactive<NetworkZoneForm>({
  parentId: null,
  code: '',
  name: '',
  restrictionLevel: 1,
  description: null,
  remark: null,
  rowVersion: null
})
const subnetOpen = ref(false)
const subnetLoading = ref(false)
const subnetSubmitting = ref(false)
const subnetError = ref('')
const selectedZone = ref<NetworkZone | null>(null)
const subnets = ref<NetworkZoneSubnet[]>([])
const subnetEditingId = ref<number | null>(null)
const subnetForm = reactive<SubnetForm>({
  cidrBlock: '',
  gatewayIp: null,
  purpose: null,
  remark: null,
  rowVersion: null
})

let sequence = 0
const statusOptions: EnvironmentRecordStatus[] = ['ACTIVE', 'INACTIVE']
const canView = computed(() => ['architecture:network-zone:view', 'architecture:network-zone:manage', 'architecture:view', 'architecture:manage'].some(permission => auth.hasPermission(permission)))
const canManage = computed(() => auth.hasPermission('architecture:network-zone:manage') || auth.hasPermission('architecture:manage'))
const selectableParents = computed(() => parentOptions.value.filter(option => option.id !== editingId.value))

async function load() {
  if (!canView.value) return
  const request = ++sequence
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const [zoneRows, options] = await Promise.all([
      listNetworkZones({ keyword: filters.keyword, status: filters.status }),
      loadNetworkZoneOptions(false)
    ])
    if (request !== sequence) return
    rows.value = zoneRows
    parentOptions.value = options
  } catch (error) {
    if (request !== sequence) return
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '网络分区加载失败')
  } finally {
    if (request === sequence) loading.value = false
  }
}

function search() {
  void load()
}

function reset() {
  filters.keyword = ''
  filters.status = ''
  void load()
}

async function refresh() {
  await load()
  if (!loadError.value && !forbidden.value) ElMessage.success('网络分区已刷新')
}

function clearForm() {
  Object.assign(form, {
    parentId: null,
    code: '',
    name: '',
    restrictionLevel: 1,
    description: null,
    remark: null,
    rowVersion: null
  })
  editingId.value = null
  formError.value = ''
}

function openCreate(parentId: number | null = null) {
  clearForm()
  form.parentId = parentId
  formMode.value = 'create'
  formOpen.value = true
}

function openEdit(row: NetworkZone) {
  editingId.value = row.id
  Object.assign(form, {
    parentId: row.parentId,
    code: row.code,
    name: row.name,
    restrictionLevel: row.restrictionLevel,
    description: row.description,
    remark: row.remark,
    rowVersion: row.rowVersion
  })
  formMode.value = 'edit'
  formError.value = ''
  formOpen.value = true
}

function parentLabel(row: NetworkZone) {
  return row.parentName || '根分区'
}

function zoneOptionLabel(option: NetworkZoneOption) {
  return `${option.name} (${option.code}) · L${option.restrictionLevel}${option.leaf ? '' : ' · 父级'}`
}

function isLeafZone(row: NetworkZone) {
  return parentOptions.value.find(option => option.id === row.id)?.leaf ?? false
}

function clearSubnetForm() {
  Object.assign(subnetForm, {
    cidrBlock: '',
    gatewayIp: null,
    purpose: null,
    remark: null,
    rowVersion: null
  })
  subnetEditingId.value = null
}

async function loadSubnets() {
  if (!selectedZone.value) return
  subnetLoading.value = true
  subnetError.value = ''
  try {
    subnets.value = await listNetworkZoneSubnets(selectedZone.value.id)
  } catch (error) {
    subnetError.value = apiErrorMessage(error, '网络分区网段加载失败')
  } finally {
    subnetLoading.value = false
  }
}

async function openSubnets(row: NetworkZone) {
  selectedZone.value = row
  clearSubnetForm()
  subnetOpen.value = true
  await loadSubnets()
}

function editSubnet(row: NetworkZoneSubnet) {
  subnetEditingId.value = row.id
  Object.assign(subnetForm, {
    cidrBlock: row.cidrBlock,
    gatewayIp: row.gatewayIp,
    purpose: row.purpose,
    remark: row.remark,
    rowVersion: row.rowVersion
  })
}

async function submitSubnet() {
  if (!selectedZone.value || subnetSubmitting.value) return
  if (!subnetForm.cidrBlock.trim()) {
    subnetError.value = '请填写 CIDR 网段'
    return
  }
  subnetSubmitting.value = true
  subnetError.value = ''
  const payload = {
    cidrBlock: subnetForm.cidrBlock.trim(),
    gatewayIp: subnetForm.gatewayIp?.trim() || null,
    purpose: subnetForm.purpose?.trim() || null,
    remark: subnetForm.remark?.trim() || null
  }
  try {
    if (subnetEditingId.value && subnetForm.rowVersion !== null) {
      await updateNetworkZoneSubnet(selectedZone.value.id, subnetEditingId.value, {
        ...payload,
        rowVersion: subnetForm.rowVersion
      })
      ElMessage.success('网络分区网段已更新')
    } else {
      await createNetworkZoneSubnet(selectedZone.value.id, payload)
      ElMessage.success('网络分区网段已创建')
    }
    clearSubnetForm()
    await loadSubnets()
  } catch (error) {
    subnetError.value = apiErrorMessage(error, '保存网络分区网段失败')
  } finally {
    subnetSubmitting.value = false
  }
}

async function changeSubnetStatus(row: NetworkZoneSubnet, next: EnvironmentRecordStatus) {
  if (!selectedZone.value) return
  const action = next === 'ACTIVE' ? '重新启用' : '停用'
  try {
    await ElMessageBox.confirm(`${action}「${row.cidrBlock}」？停用后该网段不能用于新增实例 IP 匹配。`, `${action}网段`, {
      confirmButtonText: action,
      cancelButtonText: '取消',
      type: 'warning'
    })
    if (next === 'ACTIVE') await reactivateNetworkZoneSubnet(selectedZone.value.id, row.id)
    else await deactivateNetworkZoneSubnet(selectedZone.value.id, row.id)
    ElMessage.success(`${action}成功`)
    await loadSubnets()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, `${action}网络分区网段失败`))
  }
}

async function submitForm() {
  if (submitting.value) return
  if (!form.code.trim() || !form.name.trim()) {
    formError.value = '请填写分区编码和名称'
    return
  }
  if (!Number.isInteger(form.restrictionLevel) || form.restrictionLevel < 0) {
    formError.value = '限制级别必须为非负整数'
    return
  }
  submitting.value = true
  formError.value = ''
  try {
    const payload = {
      parentId: form.parentId,
      code: form.code.trim(),
      name: form.name.trim(),
      restrictionLevel: form.restrictionLevel,
      description: form.description?.trim() || null,
      remark: form.remark?.trim() || null
    }
    if (formMode.value === 'create') {
      await createNetworkZone(payload)
      ElMessage.success('网络分区已创建')
    } else if (editingId.value && form.rowVersion !== null) {
      await updateNetworkZone(editingId.value, { ...payload, rowVersion: form.rowVersion })
      ElMessage.success('网络分区已更新')
    }
    formOpen.value = false
    void load()
  } catch (error) {
    formError.value = apiErrorMessage(error, '保存网络分区失败')
  } finally {
    submitting.value = false
  }
}

async function changeStatus(row: NetworkZone, next: EnvironmentRecordStatus) {
  const action = next === 'ACTIVE' ? '重新启用' : '停用'
  try {
    await ElMessageBox.confirm(`${action}「${row.name}」？停用后不能作为新资源和实例的网络分区。`, `${action}网络分区`, {
      confirmButtonText: action,
      cancelButtonText: '取消',
      type: 'warning'
    })
    if (next === 'ACTIVE') await reactivateNetworkZone(row.id)
    else await deactivateNetworkZone(row.id)
    ElMessage.success(`${action}成功`)
    void load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, `${action}网络分区失败`))
  }
}

watch(canView, allowed => {
  if (allowed) void load()
}, { immediate: true })
</script>

<template>
  <main class="architecture-page architecture-network-zone-page">
    <UiPageHeader title="网络分区" description="维护架构模块使用的网络分区树，部署单元、资源申请和环境部署实例使用启用叶子分区。">
      <template #actions>
        <div v-if="canManage" class="architecture-page__actions">
          <el-button type="primary" @click="openCreate()"><el-icon><Plus /></el-icon>网络分区</el-button>
        </div>
      </template>
    </UiPageHeader>

    <section v-if="auth.token && !auth.user" v-loading="true" class="architecture-state-panel" aria-label="正在确认访问权限" />
    <section v-else-if="!canView || forbidden" class="architecture-state-panel">
      <el-result icon="warning" title="暂无网络分区查看权限" sub-title="请申请 architecture:network-zone:view 或 manage 权限。" />
    </section>
    <section v-else-if="loadError" class="architecture-state-panel">
      <el-result icon="error" title="网络分区加载失败" :sub-title="loadError">
        <template #extra><el-button type="primary" @click="load">重新加载</el-button></template>
      </el-result>
    </section>
    <template v-else>
      <UiToolbar>
        <el-input v-model="filters.keyword" clearable placeholder="编码或名称" class="architecture-filter-input" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filters.status" clearable placeholder="状态" class="architecture-filter-select" @change="search">
          <el-option v-for="status in statusOptions" :key="status" :label="environmentStatusLabels[status]" :value="status" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <template #actions>
          <el-tooltip content="刷新列表">
            <el-button circle :loading="loading" aria-label="刷新网络分区列表" @click="refresh"><el-icon><Refresh /></el-icon></el-button>
          </el-tooltip>
        </template>
      </UiToolbar>

      <UiDataTable v-if="rows.length || loading" class="architecture-desktop-table" :data="rows" :loading="loading" row-key="id" border>
        <el-table-column label="网络分区" min-width="220">
          <template #default="scope">
            <button type="button" class="architecture-table-identity" @click="openEdit(scope.row)">
              <strong>{{ scope.row.name }}</strong>
              <small>{{ scope.row.code }} · {{ parentLabel(scope.row) }}</small>
            </button>
          </template>
        </el-table-column>
        <el-table-column label="限制级别" width="110"><template #default="scope">L{{ scope.row.restrictionLevel }}</template></el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <UiStatusTag :value="scope.row.status" :labels="environmentStatusLabels" :tone="environmentStatusTone(scope.row.status)" />
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="最后更新" width="150"><template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template></el-table-column>
        <el-table-column v-if="canManage" label="操作" width="260" fixed="right">
          <template #default="scope">
            <div class="architecture-table-actions">
              <el-button link type="primary" @click="openSubnets(scope.row)">网段</el-button>
              <el-button link type="primary" @click="openCreate(scope.row.id)"><el-icon><Plus /></el-icon>子分区</el-button>
              <el-button link type="primary" @click="openEdit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button>
              <el-button v-if="scope.row.status === 'ACTIVE'" link type="warning" @click="changeStatus(scope.row, 'INACTIVE')">停用</el-button>
              <el-button v-else link type="primary" @click="changeStatus(scope.row, 'ACTIVE')">启用</el-button>
            </div>
          </template>
        </el-table-column>
      </UiDataTable>

      <div v-if="rows.length || loading" v-loading="loading" class="architecture-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="row in rows" :key="row.id">
          <header>
            <div>
              <strong>{{ row.name }}</strong>
              <small>{{ row.code }} · {{ parentLabel(row) }}</small>
            </div>
            <UiStatusTag :value="row.status" :labels="environmentStatusLabels" :tone="environmentStatusTone(row.status)" />
          </header>
          <dl>
            <div><dt>限制级别</dt><dd>L{{ row.restrictionLevel }}</dd></div>
            <div><dt>最后更新</dt><dd>{{ formatDateTime(row.updatedAt) }}</dd></div>
            <div class="is-wide"><dt>说明</dt><dd>{{ row.description || '—' }}</dd></div>
          </dl>
          <footer v-if="canManage">
            <el-button link type="primary" @click="openSubnets(row)">网段</el-button>
            <el-button link type="primary" @click="openCreate(row.id)"><el-icon><Plus /></el-icon>子分区</el-button>
            <el-button link type="primary" @click="openEdit(row)"><el-icon><Edit /></el-icon>编辑</el-button>
            <el-button v-if="row.status === 'ACTIVE'" link type="warning" @click="changeStatus(row, 'INACTIVE')">停用</el-button>
            <el-button v-else link type="primary" @click="changeStatus(row, 'ACTIVE')">启用</el-button>
          </footer>
        </article>
      </div>

      <UiEmptyState v-if="!loading && !rows.length" title="暂无网络分区" description="当前筛选下没有网络分区记录。">
        <template #action>
          <el-button v-if="canManage" type="primary" @click="openCreate()">新建网络分区</el-button>
          <el-button v-else @click="reset">清空筛选</el-button>
        </template>
      </UiEmptyState>
    </template>

    <el-dialog v-model="formOpen" :title="formMode === 'create' ? '新建网络分区' : '编辑网络分区'" width="min(620px, 94vw)" destroy-on-close>
      <el-form label-position="top">
        <div class="architecture-form-grid">
          <el-form-item label="父分区">
            <el-select v-model="form.parentId" clearable filterable placeholder="根分区" style="width: 100%;">
              <el-option v-for="option in selectableParents" :key="option.id" :label="zoneOptionLabel(option)" :value="option.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="限制级别">
            <el-input-number v-model="form.restrictionLevel" :min="0" :max="99" :precision="0" style="width: 100%;" />
          </el-form-item>
          <el-form-item label="分区编码"><el-input v-model="form.code" maxlength="64" /></el-form-item>
          <el-form-item label="分区名称"><el-input v-model="form.name" maxlength="160" /></el-form-item>
          <el-form-item class="is-wide" label="说明"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item>
          <el-form-item class="is-wide" label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" show-word-limit /></el-form-item>
        </div>
        <el-alert v-if="formError" class="architecture-form-error" type="error" :closable="false" show-icon :title="formError" />
      </el-form>
      <template #footer>
        <el-button @click="formOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="subnetOpen" :title="selectedZone ? `网段维护：${selectedZone.name}` : '网段维护'" size="min(760px, 96vw)" destroy-on-close>
      <section v-if="selectedZone" class="architecture-subnet-drawer">
        <div class="architecture-detail-heading">
          <strong>{{ selectedZone.name }}</strong>
          <span>{{ selectedZone.code }} · {{ parentLabel(selectedZone) }} · {{ isLeafZone(selectedZone) ? '叶子分区' : '父级分区' }}</span>
        </div>

        <el-alert
          v-if="selectedZone.status !== 'ACTIVE' || !isLeafZone(selectedZone)"
          class="architecture-form-alert"
          type="warning"
          :closable="false"
          show-icon
          title="只有启用叶子分区可以新增或启用网段"
        />
        <el-alert v-if="subnetError" class="architecture-form-alert" type="error" :closable="false" show-icon :title="subnetError" />

        <el-form v-if="canManage && selectedZone.status === 'ACTIVE' && isLeafZone(selectedZone)" label-position="top" class="architecture-subnet-form">
          <div class="architecture-form-grid">
            <el-form-item label="CIDR 网段">
              <el-input v-model="subnetForm.cidrBlock" maxlength="64" placeholder="如 10.16.32.0/20" />
            </el-form-item>
            <el-form-item label="网关 IP">
              <el-input v-model="subnetForm.gatewayIp" maxlength="64" placeholder="可选，如 10.16.32.1" />
            </el-form-item>
            <el-form-item class="is-wide" label="用途">
              <el-input v-model="subnetForm.purpose" maxlength="500" placeholder="如 开放区 AP 下发" />
            </el-form-item>
            <el-form-item class="is-wide" label="备注">
              <el-input v-model="subnetForm.remark" type="textarea" :rows="2" maxlength="1000" show-word-limit />
            </el-form-item>
          </div>
          <div class="architecture-subnet-form__actions">
            <el-button v-if="subnetEditingId" @click="clearSubnetForm">取消编辑</el-button>
            <el-button type="primary" :loading="subnetSubmitting" @click="submitSubnet">{{ subnetEditingId ? '保存网段' : '新增网段' }}</el-button>
          </div>
        </el-form>

        <UiDataTable v-if="subnets.length || subnetLoading" :data="subnets" :loading="subnetLoading" row-key="id" border>
          <el-table-column prop="cidrBlock" label="CIDR 网段" min-width="150" />
          <el-table-column prop="gatewayIp" label="网关" min-width="130">
            <template #default="scope">{{ scope.row.gatewayIp || '—' }}</template>
          </el-table-column>
          <el-table-column prop="purpose" label="用途" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="scope">
              <UiStatusTag :value="scope.row.status" :labels="environmentStatusLabels" :tone="environmentStatusTone(scope.row.status)" />
            </template>
          </el-table-column>
          <el-table-column label="最后更新" width="150"><template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template></el-table-column>
          <el-table-column v-if="canManage" label="操作" width="170" fixed="right">
            <template #default="scope">
              <div class="architecture-table-actions">
                <el-button link type="primary" :disabled="scope.row.status !== 'ACTIVE'" @click="editSubnet(scope.row)">编辑</el-button>
                <el-button v-if="scope.row.status === 'ACTIVE'" link type="warning" @click="changeSubnetStatus(scope.row, 'INACTIVE')">停用</el-button>
                <el-button v-else link type="primary" @click="changeSubnetStatus(scope.row, 'ACTIVE')">启用</el-button>
              </div>
            </template>
          </el-table-column>
        </UiDataTable>

        <UiEmptyState v-if="!subnetLoading && !subnets.length" title="暂无网络分区网段" description="新增实例下发前，请先维护该叶子分区的启用 CIDR 网段。" />
      </section>
    </el-drawer>
  </main>
</template>
