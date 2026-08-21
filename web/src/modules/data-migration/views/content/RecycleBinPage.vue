<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listDataMigrationRecycleBin, purgeDataMigrationAssets, restoreDataMigrationAssets, type DataMigrationAsset } from '../../../../api/data-migration'

const loading = ref(true)
const error = ref('')
const assets = ref<DataMigrationAsset[]>([])
const keyword = ref('')
const selectedIds = ref<number[]>([])
const actionBusy = ref(false)

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

async function load() {
  loading.value = true; error.value = ''
  selectedIds.value = []
  try {
    assets.value = (await listDataMigrationRecycleBin({ keyword: keyword.value || undefined })).data.data ?? []
  } catch (e) { error.value = messageOf(e) }
  finally { loading.value = false }
}

function toggleSelection(id: number) {
  selectedIds.value = selectedIds.value.includes(id) ? selectedIds.value.filter(item => item !== id) : [...selectedIds.value, id]
}

async function restoreSelected() {
  if (!selectedIds.value.length) return
  actionBusy.value = true; error.value = ''
  try {
    await restoreDataMigrationAssets(selectedIds.value)
    await load()
  } catch (e) { error.value = messageOf(e) }
  finally { actionBusy.value = false }
}

async function purgeSelected() {
  if (!selectedIds.value.length) return
  actionBusy.value = true; error.value = ''
  try {
    await purgeDataMigrationAssets(selectedIds.value)
    await load()
  } catch (e) { error.value = messageOf(e) }
  finally { actionBusy.value = false }
}

onMounted(load)
</script>

<template>
  <main class="dm-page">
    <header class="dm-page-header">
      <div><span class="dm-eyebrow">DATA MIGRATION</span><h1>回收站</h1></div>
      <div class="dm-header-actions"><button class="dm-button" type="button" :disabled="loading || actionBusy" @click="load">刷新</button></div>
    </header>
    <section class="dm-toolbar" aria-label="回收站操作">
      <label class="dm-field"><span>关键词</span><input v-model="keyword" type="search" placeholder="编号或名称" @keyup.enter="load"></label>
      <button class="dm-button" type="button" :disabled="loading || actionBusy" @click="load">查询</button>
      <button class="dm-button" type="button" :disabled="!selectedIds.length || actionBusy" @click="restoreSelected">恢复 ({{ selectedIds.length }})</button>
      <button class="dm-button dm-danger" type="button" :disabled="!selectedIds.length || actionBusy" @click="purgeSelected">彻底清理 ({{ selectedIds.length }})</button>
    </section>
    <p v-if="loading" class="dm-state">正在加载...</p>
    <p v-else-if="error" class="dm-state dm-error">{{ error }}</p>
    <section v-else class="dm-table-shell">
      <div class="dm-row dm-head"><span>选择</span><span>资产编码</span><span>名称</span><span>类型</span><span>操作</span></div>
      <div v-for="asset in assets" :key="asset.id" class="dm-row">
        <span><input type="checkbox" :checked="selectedIds.includes(asset.id)" :aria-label="`选择 ${asset.asset_name}`" @change="toggleSelection(asset.id)"></span>
        <span>{{ asset.asset_code }}</span><span>{{ asset.asset_name }}</span><span>{{ asset.asset_type }}</span>
        <span class="dm-row-actions"></span>
      </div>
      <p v-if="!assets.length" class="dm-state">暂无资产</p>
    </section>
  </main>
</template>

<style scoped>
.dm-page { padding: 24px; color: var(--el-text-color-primary, #1f2937); }
.dm-page-header { display:flex; justify-content:space-between; align-items:center; gap:16px; margin-bottom:24px; }
.dm-header-actions, .dm-toolbar, .dm-row-actions { display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
.dm-eyebrow { color:#64748b; font-size:12px; letter-spacing:.08em; }
h1 { margin:4px 0 0; font-size:28px; }
.dm-button { border:1px solid #cbd5e1; background:#fff; padding:8px 14px; border-radius:6px; cursor:pointer; }
.dm-danger { color:#b91c1c; border-color:#fecaca; }
.dm-button:disabled { cursor:not-allowed; opacity:.55; }
.dm-toolbar { margin-bottom:16px; padding:12px; border:1px solid #e2e8f0; border-radius:8px; background:#fff; }
.dm-field { display:flex; align-items:center; gap:8px; color:#475569; font-size:13px; }
.dm-field input { min-width:120px; border:1px solid #cbd5e1; border-radius:6px; padding:7px 9px; color:inherit; }
.dm-table-shell { overflow:auto; border:1px solid #e2e8f0; border-radius:8px; background:#fff; }
.dm-row { min-width:640px; display:grid; grid-template-columns:.45fr 1fr 1.5fr 1fr .8fr; gap:16px; align-items:center; padding:14px 16px; border-bottom:1px solid #f1f5f9; }
.dm-head { font-weight:600; background:#f8fafc; }
.dm-state { padding:28px 0; color:#64748b; }
.dm-error { color:#b91c1c; }
@media (max-width: 640px) {
  .dm-page { padding:16px; }
  h1 { font-size:22px; }
  .dm-toolbar { align-items:stretch; }
  .dm-field { width:100%; justify-content:space-between; }
  .dm-field input { flex:1; min-width:0; }
  .dm-toolbar button { flex:1; }
  .dm-table-shell { border:0; background:transparent; overflow:visible; }
  .dm-row { min-width:0; grid-template-columns:1fr; gap:6px; background:#fff; border:1px solid #e2e8f0; border-radius:8px; margin-bottom:10px; }
  .dm-head { display:none; }
}
</style>
