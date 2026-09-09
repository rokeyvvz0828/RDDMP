<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiPagination from '../../../components/ui/UiPagination.vue'
import { developmentApi } from '../api'
import { apiErrorMessage } from '../../../api/error'
import { ACTION_LABELS, SOURCE_ROLE_LABELS, TASK_STATUS_LABELS, WORK_ITEM_STATUS_LABELS, type TaskChange } from '../types'

const props = defineProps<{ taskId: string }>()
const rows = ref<TaskChange[]>([]), total = ref(0), page = ref(1), loading = ref(false), error = ref('')
let ticket = 0
const labels: Record<string, string> = {
  title: '名称', description: '内容', owner: '负责人', assignee: '指定人员', status: '状态', reason: '操作原因',
  developmentPlanStart: '开发计划开始', developmentPlanEnd: '开发计划结束', testPlanStart: '测试计划开始', testPlanEnd: '测试计划结束',
  plannedStart: '计划开始', plannedEnd: '计划结束', actualStart: '实际开始', actualEnd: '实际结束', blocked: '阻塞', blockReason: '阻塞原因',
  designPlanStart: '设计计划开始', designPlanEnd: '设计计划结束', designDocumentPath: '设计文档路径', designAttachments: '设计附件',
  implementationActualStart: '实施实际开始', implementationActualEnd: '实施实际结束', codeWalkAttachments: '代码走查附件', testReportAttachments: '测试报告',
  notApplicableDesign: '设计不适用', notApplicableImplementation: '实施不适用', roles: '来源角色', systemCodes: '来源系统', revision: '来源版本'
}
const objectLabels: Record<string, string> = { TASK: '任务', WORK_ITEM: '工作项', STAGE: '阶段资料', SOURCE: '需求来源' }
const changeActionLabels: Record<string, string> = { ...ACTION_LABELS, COMPLETE: '完成', CANCEL: '取消', RESTORE: '恢复', REFRESH: '更新' }
function unwrap(value: Record<string, unknown> | null): Record<string, unknown> {
  if (!value) return {}
  const nested = value.task || value.workItem
  return nested && typeof nested === 'object' ? { ...(nested as Record<string, unknown>), reason: value.reason } : value
}
function display(value: unknown): string {
  if (value === null || value === undefined || value === '') return '未设置'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (Array.isArray(value)) return value.length ? value.map(display).join('、') : '无'
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>
    return String(record.fileName || record.name || record.number || record.id || '已更新')
  }
  const text = String(value)
  return ({ ...TASK_STATUS_LABELS, ...WORK_ITEM_STATUS_LABELS, ...SOURCE_ROLE_LABELS } as Record<string, string>)[text] || text
}
function differences(event: TaskChange) {
  const before = unwrap(event.before), after = unwrap(event.after)
  return Object.keys(labels).filter(key => JSON.stringify(before[key]) !== JSON.stringify(after[key]) && (key in before || key in after))
    .map(key => ({ label: labels[key], before: display(before[key]), after: display(after[key]) }))
}
async function load() {
  const current = ++ticket; loading.value = true; error.value = ''
  try { const result = await developmentApi.changes(props.taskId, page.value); if (current === ticket) { rows.value = result.records; total.value = result.total } }
  catch (cause) { if (current === ticket) error.value = apiErrorMessage(cause, '修改记录加载失败') }
  finally { if (current === ticket) loading.value = false }
}
watch(() => props.taskId, () => { rows.value = []; page.value = 1; void load() }, { immediate: true })
onBeforeUnmount(() => { ticket++ })
</script>

<template>
  <section v-loading="loading" class="dev-change-log">
    <div class="dev-section-heading"><h3>修改记录</h3><el-tooltip content="刷新修改记录"><el-button :icon="Refresh" circle :loading="loading" aria-label="刷新修改记录" @click="load" /></el-tooltip></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <article v-for="event in rows" :key="event.id" class="dev-change-row">
      <header><strong>{{ event.actor.name }} · {{ changeActionLabels[event.action] || event.action }}{{ objectLabels[event.objectType] || '' }}</strong><time>{{ event.createdAt.replace('T', ' ').slice(0, 19) }}</time></header>
      <dl class="dev-change-fields"><div v-for="(change, index) in differences(event)" :key="index"><dt>{{ change.label }}</dt><dd><del v-if="event.before">{{ change.before }}</del><span>{{ change.after }}</span></dd></div></dl>
      <small class="dev-muted">追踪号：{{ event.traceId }}</small>
    </article>
    <UiEmptyState v-if="!loading && !error && !rows.length" title="暂无修改记录" description="当前任务尚无可见变更" />
    <div class="dev-pagination"><UiPagination :total="total" :page="page" :page-size="20" :page-sizes="[20]" @update:page="page = $event; load()" /></div>
  </section>
</template>
