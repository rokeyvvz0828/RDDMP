<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getDataMigrationDashboard } from '../../../../api/data-migration'

const loading = ref(true)
const error = ref('')
const metrics = ref<Record<string, unknown>>({})

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const data = (await getDataMigrationDashboard('overall')).data.data ?? {}
    metrics.value = Array.isArray(data) ? {} : data
  } catch (e) { error.value = messageOf(e) }
  finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <main class="dm-page">
    <header class="dm-page-header">
      <div><span class="dm-eyebrow">DATA MIGRATION</span><h1>整体看板</h1></div>
      <div class="dm-header-actions"><button class="dm-button" type="button" :disabled="loading" @click="load">刷新</button></div>
    </header>
    <p v-if="loading" class="dm-state">正在加载...</p>
    <p v-else-if="error" class="dm-state dm-error">{{ error }}</p>
    <section v-else class="dm-dashboard-grid">
      <article><span>项目</span><strong>{{ metrics.projects ?? 0 }}</strong></article>
      <article><span>组件</span><strong>{{ metrics.components ?? 0 }}</strong></article>
      <article><span>活动资产</span><strong>{{ metrics.assets ?? 0 }}</strong></article>
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
.dm-dashboard-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:16px; }
.dm-dashboard-grid article { border:1px solid #e2e8f0; border-radius:8px; padding:20px; background:#fff; display:flex; flex-direction:column; gap:10px; }
.dm-dashboard-grid strong { font-size:32px; }
.dm-state { padding:28px 0; color:#64748b; }
.dm-error { color:#b91c1c; }
@media (max-width: 640px) {
  .dm-page { padding:16px; }
  h1 { font-size:22px; }
  .dm-dashboard-grid { grid-template-columns:1fr; }
}
</style>
