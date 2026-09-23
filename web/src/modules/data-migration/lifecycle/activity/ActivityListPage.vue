<!--
  用途：活动管理列表（基线第 9 章）
  说明：活动为系统唯一任务标准模板来源；支持多维度检索、新建/编辑/启用停用/作废（三不准）、
        工序与依赖直达（铁律 #19 入口可发现性）、N/M 完善统计直达、模板导入导出。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Collection, Delete, Edit, Download, Plus, Refresh, Search, UploadFilled, View } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import { apiErrorMessage } from '../../../../api/error'
import { useAuthStore } from '../../../../stores/auth'
import {
  createLifecycleActivity,
  exportLifecycleTemplate,
  importLifecycleTemplate,
  listLifecycleActivities,
  listLifecycleComponentOptions,
  listLifecycleRoleOptions,
  listLifecycleStages,
  obsoleteLifecycleActivity,
  setLifecycleActivityStatus,
  updateLifecycleActivity
} from '../../../../api/data-migration-lifecycle'
import type { LifecycleActivityView, LifecycleStageOption } from '../../../../types/data-migration-lifecycle'
import { LifecycleStatusText } from '../../../../types/data-migration-lifecycle'

const router = useRouter()
const auth = useAuthStore()

const canRead = computed(() =>
  auth.hasPermission('data-migration-lifecycle:activity') || auth.hasPermission('data-migration:access') || auth.hasPermission('system:admin'))
const canWrite = computed(() =>
  auth.hasPermission('data-migration-lifecycle:activity:create') || auth.hasPermission('data-migration-lifecycle:activity:update')
  || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canDelete = computed(() =>
  auth.hasPermission('data-migration-lifecycle:activity:delete') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))

const loading = ref(false)
const forbidden = ref(false)
const error = ref('')
const records = ref<LifecycleActivityView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const actionBusy = ref(false)

const stages = ref<LifecycleStageOption[]>([])
const componentOptions = ref<Array<{ id: number; projectName: string; systemName: string | null | undefined }>>([])
const roleOptions = ref<Array<{ id: number; roleCode: string; roleName: string }>>([])

const filters = reactive<Record<string, unknown>>({
  activityCode: '',
  activityName: '',
  lifecycleStageId: undefined,
  granularity: '',
  activityStatus: '',
  createdFrom: undefined,
  createdTo: undefined
})

const TYPE_TEXT: Record<string, string> = { NORMAL: '普通基础活动', TOPIC: '专题聚合活动' }
const GRAN_TEXT: Record<string, string> = { PROJECT: '项目级', COMPONENT: '组件级' }

async function loadList() {
  if (!canRead.value) { forbidden.value = true; return }
  loading.value = true
  error.value = ''
  try {
    const params: Record<string, unknown> = { page: page.value, size: size.value }
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== '' && value !== undefined && value !== null) params[key] = value
    })
    const res = await listLifecycleActivities(params)
    records.value = res.data.data?.records ?? []
    total.value = res.data.data?.total ?? 0
  } catch (e) {
    error.value = apiErrorMessage(e, '活动列表加载失败')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  Object.assign(filters, {
    activityCode: '', activityName: '', lifecycleStageId: undefined, granularity: '',
    activityStatus: '', createdFrom: undefined, createdTo: undefined
  })
  page.value = 1
  loadList()
}

const formOpen = ref(false)
const saving = ref(false)
const editing = ref(false)
const editId = ref<number | null>(null)
const form = reactive<Record<string, unknown>>({
  activityName: '',
  activityType: 'NORMAL',
  lifecycleStageId: undefined,
  granularity: 'PROJECT',
  scene: '',
  goal: '',
  overallEntryCond: '',
  overallExitDesc: '',
  overallDeliverables: '',
  componentIds: [] as number[]
})

function openCreate() {
  editing.value = false
  editId.value = null
  Object.assign(form, {
    activityName: '', activityType: 'NORMAL', lifecycleStageId: undefined, granularity: 'PROJECT',
    scene: '', goal: '', overallEntryCond: '', overallExitDesc: '', overallDeliverables: '', componentIds: []
  })
  formOpen.value = true
}

function openEdit(item: LifecycleActivityView) {
  editing.value = true
  editId.value = item.id
  Object.assign(form, {
    activityName: item.activityName,
    activityType: item.activityType,
    lifecycleStageId: item.lifecycleStageId ?? undefined,
    granularity: item.granularity,
    scene: item.scene ?? '', goal: item.goal ?? '',
    overallEntryCond: item.overallEntryCond ?? '', overallExitDesc: item.overallExitDesc ?? '',
    overallDeliverables: item.overallDeliverables ?? '', componentIds: []
  })
  formOpen.value = true
}

async function submitForm() {
  saving.value = true
  try {
    const name = String(form.activityName ?? '').trim()
    if (!name) { ElMessage.warning('请填写活动名称'); return }
    const activityType = String(form.activityType)
    if (activityType === 'NORMAL' && !form.lifecycleStageId) { ElMessage.warning('普通基础活动必须归属生命周期阶段'); return }
    if (form.granularity === 'COMPONENT' && (!Array.isArray(form.componentIds) || form.componentIds.length === 0)) {
      ElMessage.warning('组件级活动必须绑定至少 1 个组件'); return
    }
    const body: Record<string, unknown> = { ...form }
    delete body.componentIds
    if (form.granularity === 'PROJECT') body.componentIds = []
    if (editing.value && editId.value !== null) {
      const res = await updateLifecycleActivity(editId.value, body)
      if (res.data.data) {
        const idx = records.value.findIndex(r => r.id === editId.value)
        if (idx >= 0) records.value[idx] = res.data.data
        else await loadList()
      }
      ElMessage.success('活动已更新')
    } else {
      await createLifecycleActivity(body)
      ElMessage.success('活动已创建（编码自动生成）')
    }
    formOpen.value = false
    await loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function toggleStatus(item: LifecycleActivityView) {
  const target = item.activityStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  const label = target === 'ACTIVE' ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认${label}活动「${item.activityName}」？`, '状态确认', { type: 'warning' })
  } catch { return }
  actionBusy.value = true
  try {
    await setLifecycleActivityStatus(item.id, target)
    ElMessage.success(`已${label}`)
    await loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '状态切换失败'))
  } finally {
    actionBusy.value = false
  }
}

async function doObsolete(item: LifecycleActivityView) {
  try {
    await ElMessageBox.confirm(`作废为终态：活动「${item.activityName}」作废后不可编辑、不可重新启用、不可重复作废，确认？`, '作废确认', { type: 'error' })
  } catch { return }
  actionBusy.value = true
  try {
    await obsoleteLifecycleActivity(item.id)
    ElMessage.success('活动已作废')
    await loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '作废失败'))
  } finally {
    actionBusy.value = false
  }
}

function goWorkbench(id: number, tab: string) {
  router.push({ path: `/data-migration/lifecycle/activity/${id}`, query: { tab } })
}

async function doExport(item: LifecycleActivityView) {
  actionBusy.value = true
  try {
    const res = await exportLifecycleTemplate(item.id)
    const data = res.data.data ?? {}
    const pkg = typeof data.packageJson === 'string' ? data.packageJson : ''
    const blob = new Blob([pkg], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${item.activityCode}-template.json`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`模板已导出（${String(data.summary ?? '')}，不含实例数据）`)
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '导出失败'))
  } finally {
    actionBusy.value = false
  }
}

const importOpen = ref(false)
const importJson = ref('')
const importing = ref(false)

async function doImport() {
  importing.value = true
  try {
    const res = await importLifecycleTemplate(importJson.value)
    const data = res.data.data ?? {}
    const hint = String(data.nameDuplicateHint ?? '')
    ElMessage.success(`模板导入成功：${String(data.activityCode ?? '')}（停用草稿，待复核启用）${hint ? '，' + hint : ''}`)
    importOpen.value = false
    importJson.value = ''
    await loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '导入失败'))
  } finally {
    importing.value = false
  }
}

function readyProgress(item: LifecycleActivityView) {
  return `${item.processReady}/${item.processTotal}`
}

onMounted(async () => {
  try {
    const [stageRes, componentRes, roleRes] = await Promise.all([
      listLifecycleStages(), listLifecycleComponentOptions(), listLifecycleRoleOptions()
    ])
    stages.value = stageRes.data.data ?? []
    componentOptions.value = (componentRes.data.data ?? []).map(c => ({ id: c.id, projectName: c.projectName, systemName: c.systemName }))
    roleOptions.value = roleRes.data.data ?? []
  } catch { /* 选项失败不阻塞列表 */ }
  loadList()
})
</script>

<template>
  <section class="dm-page-root">
    <UiToolbar>
      <template #extra>
        <span class="muted">活动为系统唯一任务标准模板来源，任务下发必须基于已启用标准活动生成</span>
      </template>
      <template #actions>
        <el-button v-if="canWrite" type="primary" :icon="Plus" @click="openCreate">新建活动</el-button>
        <el-button v-if="canWrite" :icon="UploadFilled" @click="importOpen = true; importJson = ''">导入模板</el-button>
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
      </template>
    </UiToolbar>

    <el-card shadow="never" class="ui-surface-card">
      <el-form inline class="dm-filter-bar">
        <el-form-item label="活动编码"><el-input v-model="filters.activityCode" clearable placeholder="ACT-PRJ-001" style="width: 160px" @keyup.enter="page = 1; loadList()" /></el-form-item>
        <el-form-item label="活动名称"><el-input v-model="filters.activityName" clearable placeholder="按名称模糊检索" style="width: 160px" @keyup.enter="page = 1; loadList()" /></el-form-item>
        <el-form-item label="生命周期阶段">
          <el-select v-model="filters.lifecycleStageId" clearable placeholder="全部阶段" style="width: 150px">
            <el-option v-for="stage in stages" :key="stage.id" :value="stage.id" :label="stage.stageName" />
          </el-select>
        </el-form-item>
        <el-form-item label="颗粒度">
          <el-select v-model="filters.granularity" clearable placeholder="全部" style="width: 120px">
            <el-option value="PROJECT" label="项目级" /><el-option value="COMPONENT" label="组件级" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.activityStatus" clearable placeholder="全部" style="width: 120px">
            <el-option value="ACTIVE" label="启用" /><el-option value="INACTIVE" label="停用" /><el-option value="OBSOLETE" label="作废" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker v-model="filters.createdFrom" type="date" placeholder="开始" value-format="YYYY-MM-DD" style="width: 140px" />
          <span class="dm-range-sep">至</span>
          <el-date-picker v-model="filters.createdTo" type="date" placeholder="结束" value-format="YYYY-MM-DD" style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="page = 1; loadList()">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <div v-if="forbidden" class="dm-state-panel">
        <UiEmptyState title="无权限查看活动管理" description="请确认账号已分配活动管理菜单权限（data-migration-lifecycle:activity）" />
      </div>
      <div v-else-if="error" class="dm-state-panel"><UiEmptyState title="加载失败" :description="error" /></div>
      <template v-else>
        <div class="dm-desktop-table">
          <UiDataTable :loading="loading" :data="records" empty-text="暂无活动">
            <el-table-column prop="activityCode" label="活动编码" width="140" show-overflow-tooltip />
            <el-table-column prop="activityName" label="活动名称" min-width="180" show-overflow-tooltip />
            <el-table-column label="类型" width="130">
              <template #default="{ row }">
                <el-tag :type="row.activityType === 'TOPIC' ? 'warning' : 'info'" size="small">{{ TYPE_TEXT[row.activityType] ?? row.activityType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="生命周期阶段" width="120">
              <template #default="{ row }">{{ row.stageName ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="颗粒度" width="90">
              <template #default="{ row }">{{ GRAN_TEXT[row.granularity] ?? row.granularity }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.activityStatus === 'ACTIVE' ? 'success' : row.activityStatus === 'INACTIVE' ? 'info' : 'danger'" size="small">
                  {{ LifecycleStatusText[row.activityStatus] ?? row.activityStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="工序完善" width="110">
              <template #default="{ row }">
                <el-link type="primary" :underline="false" @click="goWorkbench(row.id, 'process')" :title="`${row.processReady}/${row.processTotal} 道工序已就绪，点击直达工序配置`">
                  {{ readyProgress(row) }}
                </el-link>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="150">
              <template #default="{ row }">{{ row.updatedAt?.replace('T', ' ') }}</template>
            </el-table-column>
            <el-table-column label="操作" width="300" fixed="right">
              <template #default="{ row }">
                <div class="dm-table-actions">
                  <el-button link type="primary" :icon="View" @click="goWorkbench(row.id, 'basic')">详情</el-button>
                  <el-button v-if="canWrite && row.activityStatus !== 'OBSOLETE'" link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
                  <el-tooltip content="工序配置直达（配置工作台 process 页签）" placement="top">
                    <el-button link type="primary" @click="goWorkbench(row.id, 'process')">工序</el-button>
                  </el-tooltip>
                  <el-tooltip content="依赖拓扑直达（配置工作台 topology 页签）" placement="top">
                    <el-button link type="primary" @click="goWorkbench(row.id, 'topology')">依赖</el-button>
                  </el-tooltip>
                  <el-tooltip content="导出标准模板包（JSON，不含实例数据）" placement="top">
                    <el-button link type="primary" :icon="Download" :disabled="actionBusy" @click="doExport(row)">导出</el-button>
                  </el-tooltip>
                  <el-button v-if="canWrite && row.activityStatus === 'ACTIVE'" link type="warning" @click="toggleStatus(row)">停用</el-button>
                  <el-button v-if="canWrite && row.activityStatus === 'INACTIVE'" link type="success" @click="toggleStatus(row)">启用</el-button>
                  <el-button v-if="canDelete && row.activityStatus !== 'OBSOLETE'" link type="danger" :icon="Delete" @click="doObsolete(row)">作废</el-button>
                </div>
              </template>
            </el-table-column>
          </UiDataTable>
        </div>

        <div v-if="records.length === 0 && !loading" class="dm-state-panel">
          <UiEmptyState title="暂无活动" description="新建或导入标准活动模板，作为任务下发唯一来源" />
        </div>

        <div class="dm-table-footer">
          <span>共 {{ total }} 条</span>
          <UiPagination v-model:page="page" v-model:page-size="size" :total="total" @update:page="loadList" @update:page-size="page = 1; loadList()" />
        </div>
      </template>
    </el-card>

    <UiFormDrawer v-model="formOpen" :title="editing ? '编辑活动' : '新建活动'" :loading="saving" width="640px" @submit="submitForm">
      <el-form label-width="110px">
        <el-form-item label="活动名称" required>
          <el-input v-model="form.activityName" maxlength="160" show-word-limit placeholder="如：组件级批量数据迁移" />
        </el-form-item>
        <el-form-item label="活动类型" required>
          <el-radio-group v-model="form.activityType" :disabled="editing">
            <el-radio-button value="NORMAL">普通基础活动</el-radio-button>
            <el-radio-button value="TOPIC">专题聚合活动</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="form.activityType === 'NORMAL' ? '生命周期阶段' : '生命周期阶段（选填）'">
          <el-select v-model="form.lifecycleStageId" :clearable="form.activityType === 'TOPIC'" placeholder="普通基础活动必填，专题活动可空" style="width: 100%">
            <el-option v-for="stage in stages" :key="stage.id" :value="stage.id" :label="stage.stageName" />
          </el-select>
        </el-form-item>
        <el-form-item label="活动颗粒度" required>
          <el-radio-group v-model="form.granularity" :disabled="editing" :title="editing ? '颗粒度创建后不可变更' : ''">
            <el-radio-button value="PROJECT">项目级</el-radio-button>
            <el-radio-button value="COMPONENT">组件级</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.granularity === 'COMPONENT'" label="绑定组件" required>
          <el-select v-model="form.componentIds" multiple filterable placeholder="组件级活动必须绑定至少 1 个可用组件" style="width: 100%">
            <el-option v-for="item in componentOptions" :key="item.id" :value="item.id" :label="`${item.projectName}${item.systemName ? ' / ' + item.systemName : ''}`" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="绑定组件">
          <span class="muted">项目级活动禁止绑定组件</span>
        </el-form-item>
        <el-form-item label="适用场景"><el-input v-model="form.scene" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="执行目标"><el-input v-model="form.goal" type="textarea" :rows="2" maxlength="1000" show-word-limit /></el-form-item>
        <el-form-item label="整体准入条件"><el-input v-model="form.overallEntryCond" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="整体准出说明"><el-input v-model="form.overallExitDesc" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="整体交付物清单"><el-input v-model="form.overallDeliverables" type="textarea" :rows="2" /></el-form-item>
      </el-form>
    </UiFormDrawer>

    <el-dialog v-model="importOpen" title="导入活动模板（JSON 通路）" width="640px">
      <p class="muted">粘贴导出的模板包 JSON。系统将执行四类校验（字段完整性 / 编码冲突 / 颗粒度一致性 / 依赖合法性），通过后生成停用草稿活动，人工复核后启用。导入导入权限仅数据迁移管理员。</p>
      <el-input v-model="importJson" type="textarea" :rows="12" placeholder='{"templateName":"...","activityType":"NORMAL","granularity":"PROJECT","processes":[...],"edges":[...]}' />
      <template #footer>
        <el-button @click="importOpen = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="doImport">导入并校验</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.dm-filter-bar { margin-bottom: 4px; }
.dm-range-sep { margin: 0 6px; color: var(--muted); }
</style>
