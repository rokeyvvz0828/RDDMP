<script setup lang="ts">
import { computed } from 'vue'
import { Edit, MoreFilled, Rank, View } from '@element-plus/icons-vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import { ACTION_LABELS, WORK_ITEM_STATUS_LABELS, statusTone, type WorkItem } from '../types'

const props = defineProps<{ item: WorkItem; busy?: boolean }>()
const emit = defineEmits<{ open: [item: WorkItem]; edit: [item: WorkItem]; action: [item: WorkItem, action: string]; 'drag-start': [item: WorkItem]; 'drag-end': [] }>()
const actions = computed(() => ['START', 'SUBMIT', 'ACCEPT', 'RETURN', 'REOPEN'].filter(action => props.item.allowedActions.includes(action)))
const primary = computed(() => actions.value.find(action => ['START', 'SUBMIT', 'ACCEPT'].includes(action)))
const more = computed(() => actions.value.filter(action => !['START', 'SUBMIT', 'ACCEPT'].includes(action)))
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const overdue = computed(() => props.item.status !== 'DONE' && Boolean(props.item.plannedEnd && props.item.plannedEnd < today))
function drag(event: DragEvent) {
  if (props.busy || !actions.value.length) { event.preventDefault(); return }
  event.dataTransfer?.setData('application/x-rddmp-work-item', props.item.id)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
  emit('drag-start', props.item)
}
</script>

<template>
  <article class="dev-board-card" :class="{ 'is-busy': busy }" :data-work-item-id="item.id" :draggable="actions.length > 0 && !busy" @dragstart="drag" @dragend="emit('drag-end')">
    <header><button class="dev-record-link" type="button" @click="emit('open', item)"><strong>{{ item.title }}</strong></button><el-icon v-if="actions.length" class="dev-drag-handle" title="拖动工作项"><Rank /></el-icon></header>
    <small class="dev-board-card__task">{{ item.taskNumber }}</small>
    <div class="dev-tags"><UiStatusTag :value="item.status" :labels="WORK_ITEM_STATUS_LABELS" :tone="statusTone(item.status)" /><UiStatusTag v-if="item.blocked" value="阻塞" tone="danger" /><UiStatusTag v-if="overdue" value="逾期" tone="danger" /></div>
    <dl><div><dt>系统</dt><dd>{{ item.system.name }}</dd></div><div><dt>来源</dt><dd>{{ item.source?.number || '自主创建' }}</dd></div><div><dt>指定人员</dt><dd>{{ item.assignee.name }}</dd></div><div><dt>计划日期</dt><dd class="dev-date-range"><span>{{ item.plannedStart || '未定' }}</span><span>~</span><span>{{ item.plannedEnd || '未定' }}</span></dd></div></dl>
    <p v-if="item.blocked" class="dev-danger">{{ item.blockReason }}</p><p v-for="warning in item.warnings" :key="warning" class="dev-warning">{{ warning }}</p>
    <footer><el-button v-if="primary" link type="primary" :loading="busy" @click="emit('action', item, primary)">{{ ACTION_LABELS[primary] }}</el-button><el-tooltip content="查看或编辑工作项"><el-button :icon="item.allowedActions.includes('UPDATE') ? Edit : View" text circle :disabled="busy" :aria-label="`打开工作项 ${item.title}`" @click="emit('edit', item)" /></el-tooltip><el-dropdown v-if="more.length" @command="(action: string) => emit('action', item, action)"><el-button :icon="MoreFilled" text circle :disabled="busy" aria-label="更多工作项操作" /><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="action in more" :key="action" :command="action">{{ ACTION_LABELS[action] }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown></footer>
  </article>
</template>
