<!--
  用途：数迁组件看板页
  说明：按组件维度展示资产数量统计（组件编码/名称/资产数量）；调用 getDataMigrationDashboard('component')
        拉取数据，自带加载/失败/空数据状态。
-->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getDataMigrationDashboard } from '../../../../api/data-migration'

const loading = ref(true)
const error = ref('')
const rows = ref<Array<Record<string, unknown>>>([])

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const data = (await getDataMigrationDashboard('component')).data.data ?? []
    rows.value = Array.isArray(data) ? data : []
  } catch (e) { error.value = messageOf(e) }
  finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <main class="dm-page">
    <header class="dm-page-header">
      <div><span class="dm-eyebrow">DATA MIGRATION</span><h1>组件看板</h1></div>
      <div class="dm-header-actions"><button class="dm-button" type="button" :disabled="loading" @click="load">刷新</button></div>
    </header>
    <p v-if="loading" class="dm-state">正在加载...</p>
    <p v-else-if="error" class="dm-state dm-error">{{ error }}</p>
    <section v-else class="dm-table-shell">
      <div class="dm-row dm-head"><span>组件编码</span><span>组件名称</span><span>资产数量</span></div>
      <div v-for="row in rows" :key="String(row.id)" class="dm-row"><span>{{ row.component_code }}</span><span>{{ row.component_name }}</span><span>{{ row.asset_count }}</span></div>
      <p v-if="!rows.length" class="dm-state">暂无组件数据</p>
    </section>
  </main>
</template>

<style scoped>
.dm-page { padding: 24px; color: var(--el-text-color-primary, #1f2937); }
.dm-page-header { display:flex; justify-content:space-between; align-items:center; gap:16px; margin-bottom:24px; }
.dm-header-actions { display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
.dm-eyebrow { color:#64748b; font-size:12px; letter-spacing:.08em; }
h1 { margin:4px 0 0; font-size:28px; }
.dm-button { border:1px solid #cbd5e1; background:#fff; padding:8px 14px; border-radius:6px; cursor:pointer; }
.dm-button:disabled { cursor:not-allowed; opacity:.55; }
.dm-table-shell { overflow:auto; border:1px solid #e2e8f0; border-radius:8px; background:#fff; }
.dm-row { min-width:640px; display:grid; grid-template-columns:.45fr 1fr 1.5fr 1fr .8fr; gap:16px; align-items:center; padding:14px 16px; border-bottom:1px solid #f1f5f9; }
.dm-head { font-weight:600; background:#f8fafc; }
.dm-state { padding:28px 0; color:#64748b; }
.dm-error { color:#b91c1c; }
@media (max-width: 640px) {
  .dm-page { padding:16px; }
  h1 { font-size:22px; }
  .dm-table-shell { border:0; background:transparent; overflow:visible; }
  .dm-row { min-width:0; grid-template-columns:1fr; gap:6px; background:#fff; border:1px solid #e2e8f0; border-radius:8px; margin-bottom:10px; }
  .dm-head { display:none; }
}
</style>
