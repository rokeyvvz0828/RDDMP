<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { deleteDataMigrationAssets, deleteDataMigrationStructured, downloadDataMigrationAsset, exportDataMigrationStructured, getDataMigrationDashboard, inspectDataMigrationStructuredImport, listDataMigrationAssets, listDataMigrationComponents, listDataMigrationProjects, listDataMigrationRecycleBin, listDataMigrationStructured, purgeDataMigrationAssets, restoreDataMigrationAssets, updateDataMigrationComponent, updateDataMigrationProject, updateDataMigrationStructured, uploadDataMigrationAsset, type DataMigrationAsset, type DataMigrationComponent, type DataMigrationProject } from '../../api/data-migration'

const props = defineProps<{ group?: string; section?: string }>()
const loading = ref(true)
const error = ref('')
const projects = ref<DataMigrationProject[]>([])
const components = ref<DataMigrationComponent[]>([])
const assets = ref<DataMigrationAsset[]>([])
const dashboard = ref<Record<string, unknown> | Array<Record<string, unknown>>>({})
const keyword = ref('')
const selectedIds = ref<number[]>([])
const actionBusy = ref(false)
const uploadProjectId = ref('')
const uploadAssetCode = ref('')
const uploadInput = ref<HTMLInputElement | null>(null)
const structuredInput = ref<HTMLInputElement | null>(null)
const importResult = ref('')
const contentTypeBySection: Record<string, string> = {
  reports: 'REPORT', meetings: 'MEETING', plans: 'PLAN', mappings: 'MAPPING_DOC',
  'validation-rules': 'VALIDATION_DOC', parameters: 'PARAMETER', dependencies: 'DEPENDENCY',
  programs: 'SCRIPT', topics: 'TOPIC', 'release-drills': 'RELEASE_DRILL', issues: 'ISSUE'
}
const structuredTypeBySection: Record<string, string> = {
  'validation-rules': 'RULE', parameters: 'PARAMETER', issues: 'ISSUE', 'target-tables': 'TABLE_STRUCTURE', 'intermediate-tables': 'INTERMEDIATE_TABLE'
}
const activeView = computed(() => {
  if (props.group === 'base' && (!props.section || props.section === 'projects')) return 'projects'
  if (props.group === 'base' && props.section === 'components') return 'components'
  if (props.group === 'base' && structuredTypeBySection[props.section ?? '']) return 'structured'
  if (props.group === 'content' && props.section === 'recycle-bin') return 'recycle-bin'
  if (props.group === 'content' && structuredTypeBySection[props.section ?? '']) return 'structured'
  if (props.group === 'content') return 'assets'
  return 'dashboard'
})
const activeAssetType = computed(() => contentTypeBySection[props.section ?? ''] ?? props.section)
const activeStructuredType = computed(() => structuredTypeBySection[props.section ?? ''] ?? props.section ?? 'VALIDATION_RULE')
const dashboardView = computed<'overall' | 'component'>(() => props.section === 'components' ? 'component' : 'overall')
const dashboardRows = computed(() => Array.isArray(dashboard.value) ? dashboard.value : [])
const dashboardMetrics = computed(() => Array.isArray(dashboard.value) ? {} : dashboard.value)
const pageTitle = computed(() => props.group === 'content' ? '数迁资产内容管理' : props.group === 'base' ? '基础资料管理' : '数迁资产看板')
const isRecycleBin = computed(() => activeView.value === 'recycle-bin')
const canSelectAssets = computed(() => activeView.value === 'assets' || isRecycleBin.value || activeView.value === 'structured')
const canImportStructured = computed(() => activeView.value === 'structured' && Boolean(activeStructuredType.value))

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

async function load() {
  loading.value = true; error.value = ''
  selectedIds.value = []
  try {
    if (activeView.value === 'projects') projects.value = (await listDataMigrationProjects()).data.data ?? []
    else if (activeView.value === 'components') components.value = (await listDataMigrationComponents()).data.data ?? []
    else if (activeView.value === 'assets') assets.value = (await listDataMigrationAssets({ type: activeAssetType.value, keyword: keyword.value || undefined })).data.data ?? []
    else if (activeView.value === 'recycle-bin') assets.value = (await listDataMigrationRecycleBin({ keyword: keyword.value || undefined })).data.data ?? []
    else if (activeView.value === 'structured') assets.value = (await listDataMigrationStructured(activeStructuredType.value, { keyword: keyword.value || undefined })).data.data ?? []
    else dashboard.value = (await getDataMigrationDashboard(dashboardView.value)).data.data ?? {}
  } catch (e) { error.value = messageOf(e) }
  finally { loading.value = false }
}

function toggleSelection(id: number) {
  selectedIds.value = selectedIds.value.includes(id) ? selectedIds.value.filter(item => item !== id) : [...selectedIds.value, id]
}

async function runAssetAction(action: 'delete' | 'restore' | 'purge') {
  if (!selectedIds.value.length) return
  actionBusy.value = true; error.value = ''
  try {
    if (action === 'delete' && activeView.value === 'structured') await deleteDataMigrationStructured(activeStructuredType.value, selectedIds.value)
    else if (action === 'delete') await deleteDataMigrationAssets(selectedIds.value)
    else if (action === 'restore') await restoreDataMigrationAssets(selectedIds.value)
    else await purgeDataMigrationAssets(selectedIds.value)
    await load()
  } catch (e) { error.value = messageOf(e) }
  finally { actionBusy.value = false }
}

async function downloadAsset(id: number) {
  actionBusy.value = true; error.value = ''
  try {
    const url = (await downloadDataMigrationAsset(id)).data.data
    if (url) window.open(url, '_blank', 'noopener,noreferrer')
  } catch (e) { error.value = messageOf(e) }
  finally { actionBusy.value = false }
}

function chooseUpload() { uploadInput.value?.click() }

function chooseStructuredImport() { structuredInput.value?.click() }

async function exportStructured() {
  actionBusy.value = true; error.value = ''
  try {
    const response = await exportDataMigrationStructured(activeStructuredType.value, { keyword: keyword.value || undefined })
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `data-migration-${activeStructuredType.value}.xlsx`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (e) { error.value = messageOf(e) }
  finally { actionBusy.value = false }
}

async function editStructured(asset: DataMigrationAsset) {
  const name = window.prompt('资产名称', asset.asset_name)?.trim()
  if (!name) return
  const current = typeof asset.structured_data === 'string' ? asset.structured_data : JSON.stringify(asset.structured_data ?? {}, null, 2)
  const raw = window.prompt('字段 JSON', current)
  if (raw == null) return
  let structuredData: unknown
  try { structuredData = JSON.parse(raw) } catch { error.value = '字段 JSON 格式无效'; return }
  actionBusy.value = true; error.value = ''
  try {
    await updateDataMigrationStructured(activeStructuredType.value, asset.id, { projectId: asset.project_id, componentId: asset.component_id, assetCode: asset.asset_code, assetName: name, structuredData })
    await load()
  } catch (e) { error.value = messageOf(e) }
  finally { actionBusy.value = false }
}

async function inspectStructuredImport(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  actionBusy.value = true; error.value = ''; importResult.value = ''
  try {
    const response = await inspectDataMigrationStructuredImport(activeStructuredType.value, file)
    const data = response.data.data ?? {}
    importResult.value = `导入完成：成功 ${String(data.accepted ?? 0)} 行，失败 ${String(data.failed ?? 0)} 行`
  } catch (e) { error.value = messageOf(e) }
  finally { actionBusy.value = false; if (structuredInput.value) structuredInput.value.value = '' }
}

async function uploadAsset(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  const projectId = Number(uploadProjectId.value)
  if (!Number.isInteger(projectId) || projectId <= 0 || !uploadAssetCode.value.trim()) {
    error.value = '上传前请填写有效的项目 ID 和文件编号'
    return
  }
  actionBusy.value = true; error.value = ''
  try {
    await uploadDataMigrationAsset(activeAssetType.value || 'REPORT', projectId, uploadAssetCode.value.trim(), file)
    uploadAssetCode.value = ''
    await load()
  } catch (e) { error.value = messageOf(e) }
  finally { actionBusy.value = false; if (uploadInput.value) uploadInput.value.value = '' }
}

async function editProject(project: DataMigrationProject) {
  const name = window.prompt('项目名称', project.project_name)?.trim()
  if (!name || name === project.project_name) return
  actionBusy.value = true; error.value = ''
  try { await updateDataMigrationProject(project.id, { projectName: name }); await load() } catch (e) { error.value = messageOf(e) } finally { actionBusy.value = false }
}

async function editComponent(component: DataMigrationComponent) {
  const name = window.prompt('组件名称', component.component_name)?.trim()
  if (!name || name === component.component_name) return
  actionBusy.value = true; error.value = ''
  try { await updateDataMigrationComponent(component.id, { componentName: name }); await load() } catch (e) { error.value = messageOf(e) } finally { actionBusy.value = false }
}

onMounted(load)
</script>

<template>
  <main class="data-migration-module">
    <header class="page-header"><div><span class="eyebrow">DATA MIGRATION</span><h1>{{ pageTitle }}</h1><p v-if="props.section" class="section-label">{{ props.section }}</p></div><div class="header-actions"><button class="refresh" type="button" :disabled="loading || actionBusy" @click="load">刷新</button></div></header>
    <section v-if="activeView === 'assets' || isRecycleBin || activeView === 'structured'" class="asset-toolbar" aria-label="资产操作">
      <label class="search-field"><span>关键词</span><input v-model="keyword" type="search" placeholder="编号或名称" @keyup.enter="load"></label>
      <button class="secondary" type="button" :disabled="loading || actionBusy" @click="load">查询</button>
      <template v-if="activeView === 'assets'">
        <label class="compact-field"><span>项目 ID</span><input v-model="uploadProjectId" inputmode="numeric" placeholder="必填"></label>
        <label class="compact-field"><span>文件编号</span><input v-model="uploadAssetCode" placeholder="必填"></label>
        <input ref="uploadInput" class="visually-hidden" type="file" @change="uploadAsset">
        <button class="primary" type="button" :disabled="actionBusy" @click="chooseUpload">上传文件</button>
        <button class="danger" type="button" :disabled="!selectedIds.length || actionBusy" @click="runAssetAction('delete')">移入回收站 ({{ selectedIds.length }})</button>
      </template>
      <template v-else-if="activeView === 'structured'">
        <input ref="structuredInput" class="visually-hidden" type="file" accept=".xlsx" @change="inspectStructuredImport">
        <button class="secondary" type="button" :disabled="actionBusy" @click="chooseStructuredImport">导入 Excel</button>
        <button class="secondary" type="button" :disabled="actionBusy" @click="exportStructured">导出 Excel</button>
        <button class="danger" type="button" :disabled="!selectedIds.length || actionBusy" @click="runAssetAction('delete')">删除 ({{ selectedIds.length }})</button>
        <span v-if="importResult" class="import-result" role="status">{{ importResult }}</span>
      </template>
      <template v-else>
        <button class="secondary" type="button" :disabled="!selectedIds.length || actionBusy" @click="runAssetAction('restore')">恢复 ({{ selectedIds.length }})</button>
        <button class="danger" type="button" :disabled="!selectedIds.length || actionBusy" @click="runAssetAction('purge')">彻底清理 ({{ selectedIds.length }})</button>
      </template>
    </section>
    <p v-if="loading" class="state">正在加载...</p>
    <p v-else-if="error" class="state error">{{ error }}</p>
    <section v-else-if="activeView === 'dashboard' && dashboardView === 'overall'" class="dashboard-grid">
      <article><span>项目</span><strong>{{ dashboardMetrics.projects ?? 0 }}</strong></article><article><span>组件</span><strong>{{ dashboardMetrics.components ?? 0 }}</strong></article><article><span>活动资产</span><strong>{{ dashboardMetrics.assets ?? 0 }}</strong></article>
    </section>
    <section v-else-if="activeView === 'dashboard'" class="table-shell"><div class="table-row table-head"><span>组件编码</span><span>组件名称</span><span>资产数量</span></div><div v-for="row in dashboardRows" :key="String(row.id)" class="table-row"><span>{{ row.component_code }}</span><span>{{ row.component_name }}</span><span>{{ row.asset_count }}</span></div><p v-if="!dashboardRows.length" class="state">暂无组件数据</p></section>
    <section v-else-if="activeView === 'projects'" class="table-shell"><div class="table-row table-head project-row"><span>项目编码</span><span>项目名称</span><span>状态</span><span>操作</span></div><div v-for="project in projects" :key="project.id" class="table-row project-row"><span>{{ project.project_code }}</span><span>{{ project.project_name }}</span><span>{{ project.status }}</span><button class="link-button" type="button" :disabled="actionBusy" @click="editProject(project)">编辑</button></div><p v-if="!projects.length" class="state">暂无项目</p></section>
    <section v-else-if="activeView === 'components'" class="table-shell"><div class="table-row table-head component-row"><span>组件编码</span><span>组件名称</span><span>项目 ID</span><span>操作</span></div><div v-for="component in components" :key="component.id" class="table-row component-row"><span>{{ component.component_code }}</span><span>{{ component.component_name }}</span><span>{{ component.project_id }}</span><button class="link-button" type="button" :disabled="actionBusy" @click="editComponent(component)">编辑</button></div><p v-if="!components.length" class="state">暂无组件</p></section>
    <section v-else class="table-shell"><div class="table-row table-head"><span v-if="canSelectAssets">选择</span><span>资产编码</span><span>名称</span><span>类型</span><span>操作</span></div><div v-for="asset in assets" :key="asset.id" class="table-row"><span v-if="canSelectAssets"><input type="checkbox" :checked="selectedIds.includes(asset.id)" :aria-label="`选择 ${asset.asset_name}`" @change="toggleSelection(asset.id)"></span><span>{{ asset.asset_code }}</span><span>{{ asset.asset_name }}</span><span>{{ asset.asset_type }}</span><span class="row-actions"><button v-if="activeView === 'structured'" class="link-button" type="button" :disabled="actionBusy" @click="editStructured(asset)">编辑字段</button><button v-if="!isRecycleBin" class="link-button" type="button" :disabled="actionBusy" @click="downloadAsset(asset.id)">下载</button></span></div><p v-if="!assets.length" class="state">暂无资产</p></section>
  </main>
</template>

<style scoped>
.data-migration-module { padding: 24px; color: var(--el-text-color-primary, #1f2937); }
.page-header { display:flex; justify-content:space-between; align-items:center; gap:16px; margin-bottom:24px; }
.header-actions, .asset-toolbar, .row-actions { display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
.eyebrow { color:#64748b; font-size:12px; letter-spacing:.08em; }
h1 { margin:4px 0 0; font-size:28px; }
.section-label { margin:6px 0 0; color:#64748b; font-size:13px; }
.refresh, .secondary, .primary, .danger, .link-button { border:1px solid #cbd5e1; background:#fff; padding:8px 14px; border-radius:6px; cursor:pointer; }
.primary { background:#2563eb; border-color:#2563eb; color:#fff; }
.danger { color:#b91c1c; border-color:#fecaca; }
.link-button { border:0; padding:0; color:#2563eb; background:transparent; }
.refresh:disabled, .secondary:disabled, .primary:disabled, .danger:disabled, .link-button:disabled { cursor:not-allowed; opacity:.55; }
.asset-toolbar { margin-bottom:16px; padding:12px; border:1px solid #e2e8f0; border-radius:8px; background:#fff; }
.import-result { color:#166534; font-size:13px; }
.search-field, .compact-field { display:flex; align-items:center; gap:8px; color:#475569; font-size:13px; }
.search-field input, .compact-field input { min-width:120px; border:1px solid #cbd5e1; border-radius:6px; padding:7px 9px; color:inherit; }
.visually-hidden { position:absolute; width:1px; height:1px; padding:0; margin:-1px; overflow:hidden; clip:rect(0,0,0,0); white-space:nowrap; border:0; }
.dashboard-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:16px; }
.dashboard-grid article { border:1px solid #e2e8f0; border-radius:8px; padding:20px; background:#fff; display:flex; flex-direction:column; gap:10px; }
.dashboard-grid strong { font-size:32px; }
.table-shell { overflow:auto; border:1px solid #e2e8f0; border-radius:8px; background:#fff; }
.table-row { min-width:640px; display:grid; grid-template-columns:.45fr 1fr 1.5fr 1fr .8fr; gap:16px; align-items:center; padding:14px 16px; border-bottom:1px solid #f1f5f9; }
.component-row { grid-template-columns:1fr 1.5fr 1fr .8fr; }
.project-row { grid-template-columns:1fr 1.5fr 1fr .8fr; }
.table-row:not(.table-head) { min-width:640px; }
.table-head { font-weight:600; background:#f8fafc; }
.state { padding:28px 0; color:#64748b; }
.error { color:#b91c1c; }
@media (max-width: 640px) { .data-migration-module { padding:16px; } h1 { font-size:22px; } .dashboard-grid { grid-template-columns:1fr; } .asset-toolbar { align-items:stretch; } .search-field, .compact-field { width:100%; justify-content:space-between; } .search-field input, .compact-field input { flex:1; min-width:0; } .asset-toolbar button { flex:1; } .table-shell { border:0; background:transparent; overflow:visible; } .table-row, .table-row:not(.table-head) { min-width:0; grid-template-columns:1fr; gap:6px; background:#fff; border:1px solid #e2e8f0; border-radius:8px; margin-bottom:10px; } .table-head { display:none; } }
</style>
