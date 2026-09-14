<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ArrowDown, ArrowUp, Plus, Refresh } from '@element-plus/icons-vue'
import UiDataTable from '../../../components/ui/UiDataTable.vue'
import UiPagination from '../../../components/ui/UiPagination.vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import { apiErrorMessage } from '../../../api/error'
import { developmentApi } from '../api'
import { SOURCE_ROLE_LABELS, type PendingRequirement } from '../types'

const props = defineProps<{ projectRef: string; systemId?: string; keyword?: string; canClaim: boolean }>()
const emit = defineEmits<{ claim: [item: PendingRequirement] }>()
const expanded = ref(false), rows = ref<PendingRequirement[]>([]), total = ref<number | null>(null), page = ref(1), loading = ref(false), error = ref('')
let ticket = 0
async function load() {
  const current = ++ticket
  if (!props.projectRef) { rows.value = []; total.value = null; loading.value = false; return }
  loading.value = true; error.value = ''
  try {
    const result = await developmentApi.pending({ projectRef: props.projectRef, systemId: props.systemId || undefined, keyword: props.keyword, page: page.value, size: 20 })
    if (current === ticket) { rows.value = result.records; total.value = result.total }
  } catch (cause) { if (current === ticket) error.value = apiErrorMessage(cause, '待承接需求加载失败') }
  finally { if (current === ticket) loading.value = false }
}
watch(() => [props.projectRef, props.systemId, props.keyword], () => { ticket++; rows.value = []; total.value = null; page.value = 1; void load() }, { immediate: true })
function changePage(value: number) { page.value = value; void load() }
function rowKey(item: PendingRequirement) { return `${item.sourceRequirementId}:${item.system.id}` }
onBeforeUnmount(() => { ticket++ })
</script>

<template>
  <section class="dev-pending" :aria-busy="loading">
    <div class="dev-pending__summary">
      <el-button text :icon="expanded ? ArrowUp : ArrowDown" :aria-expanded="expanded" @click="expanded = !expanded">
        待承接需求 <strong class="dev-pending__count">{{ loading && total === null ? '...' : total ?? '-' }}</strong>
      </el-button>
      <span v-if="!loading && total === 0 && !error" class="dev-muted">当前范围内暂无待承接需求</span>
      <el-tooltip content="刷新待承接需求"><el-button :icon="Refresh" circle size="small" :loading="loading" aria-label="刷新待承接需求" @click="load" /></el-tooltip>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div v-if="expanded" class="dev-pending__body">
      <UiDataTable class="dev-desktop-table" :data="rows" :loading="loading" empty-text="当前范围内暂无待承接需求" :row-key="rowKey">
        <el-table-column label="需求" min-width="260"><template #default="{ row }"><strong>{{ row.sourceName }}</strong><div class="dev-muted">{{ row.sourceNumber }}</div></template></el-table-column>
        <el-table-column label="系统" min-width="180"><template #default="{ row }">{{ row.system.name }}<div class="dev-muted">{{ row.system.code }}</div></template></el-table-column>
        <el-table-column label="角色" width="140"><template #default="{ row }"><div class="dev-tags"><UiStatusTag v-for="role in row.roles" :key="role" :value="role" :labels="SOURCE_ROLE_LABELS" tone="info" /></div></template></el-table-column>
        <el-table-column label="操作" width="100"><template #default="{ row }"><el-button type="primary" link :icon="Plus" :disabled="!canClaim" @click="emit('claim', row)">承接</el-button></template></el-table-column>
      </UiDataTable>
      <div v-loading="loading" class="dev-mobile-cards">
        <article v-for="item in rows" :key="`${item.sourceRequirementId}:${item.system.id}`" class="dev-record-card">
          <header><div><strong>{{ item.sourceName }}</strong><small>{{ item.sourceNumber }}</small></div></header>
          <dl><div><dt>系统</dt><dd>{{ item.system.name }}</dd></div><div><dt>角色</dt><dd>{{ item.roles.map(role => SOURCE_ROLE_LABELS[role] || role).join('、') }}</dd></div></dl>
          <footer><el-button type="primary" link :icon="Plus" :disabled="!canClaim" @click="emit('claim', item)">承接</el-button></footer>
        </article>
        <el-empty v-if="!loading && !rows.length && !error" description="暂无待承接需求" :image-size="50" />
      </div>
      <div class="dev-pagination"><UiPagination :total="total || 0" :page="page" :page-size="20" :page-sizes="[20]" @update:page="changePage" /></div>
    </div>
  </section>
</template>
