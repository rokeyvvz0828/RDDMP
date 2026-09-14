<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { WORK_ITEM_STATUSES, WORK_ITEM_STATUS_LABELS, type WorkItem, type WorkItemColumn, type WorkItemStatus } from '../types'
import WorkItemCard from './WorkItemCard.vue'

const props = defineProps<{ columns: Record<WorkItemStatus, WorkItemColumn>; busy: Set<string>; statusFilter: string; crossTask: boolean; pageSize: number }>()
const emit = defineEmits<{ open: [item: WorkItem]; edit: [item: WorkItem]; action: [item: WorkItem, action: string]; retry: [status: WorkItemStatus]; 'column-page': [status: WorkItemStatus, page: number]; 'select-status': [status: WorkItemStatus] }>()
const dragged = ref<WorkItem | null>(null)
const mobileStatus = computed(() => WORK_ITEM_STATUSES.includes(props.statusFilter as WorkItemStatus) ? props.statusFilter : 'TODO')
const statusOptions = computed(() => WORK_ITEM_STATUSES.map(status => ({ value: status, label: `${WORK_ITEM_STATUS_LABELS[status]} ${props.columns[status].total ?? '-'}` })))
function actionFor(item: WorkItem, target: WorkItemStatus) {
  const action = ({ 'TODO:IN_PROGRESS': 'START', 'IN_PROGRESS:IN_REVIEW': 'SUBMIT', 'IN_REVIEW:DONE': 'ACCEPT', 'IN_REVIEW:IN_PROGRESS': 'RETURN', 'DONE:IN_PROGRESS': 'REOPEN' } as Record<string, string>)[`${item.status}:${target}`]
  return action && item.allowedActions.includes(action) ? action : null
}
function canDrop(status: WorkItemStatus) { return dragged.value && !props.busy.has(dragged.value.id) && !props.columns[status].loading && actionFor(dragged.value, status) }
function dragOver(event: DragEvent, status: WorkItemStatus) { if (canDrop(status)) { event.preventDefault(); if (event.dataTransfer) event.dataTransfer.dropEffect = 'move' } }
function drop(event: DragEvent, status: WorkItemStatus) {
  event.preventDefault()
  const id = event.dataTransfer?.getData('application/x-rddmp-work-item') || dragged.value?.id
  const item = WORK_ITEM_STATUSES.flatMap(column => props.columns[column].records).find(row => row.id === id)
  const action = item ? actionFor(item, status) : null
  if (item && action && !props.busy.has(item.id) && !props.columns[status].loading) emit('action', item, action)
  dragged.value = null
}
</script>

<template>
  <div class="dev-board">
    <el-segmented class="dev-board-mobile-tabs" :model-value="mobileStatus" :options="statusOptions" aria-label="看板状态列" @change="(value: unknown) => emit('select-status', value as WorkItemStatus)" />
    <div class="dev-board-scroll"><div class="dev-board-columns" :class="{ 'has-status-filter': statusFilter }">
      <section v-for="status in WORK_ITEM_STATUSES" :key="status" class="dev-board-column" :class="{ 'is-mobile-active': mobileStatus === status, 'is-filter-hidden': statusFilter && statusFilter !== status, 'is-drop-target': canDrop(status) }" :data-board-status="status" @dragover="dragOver($event, status)" @drop="drop($event, status)">
        <header class="dev-board-column__heading"><h3>{{ WORK_ITEM_STATUS_LABELS[status] }}</h3><span>{{ columns[status].total ?? '-' }}</span></header>
        <div class="dev-board-column__body">
          <el-skeleton v-if="columns[status].loading && !columns[status].records.length" :rows="4" animated />
          <el-alert v-if="columns[status].error" :title="columns[status].error" type="error" :closable="false"><el-button link @click="emit('retry', status)">重试</el-button></el-alert>
          <template v-for="(item, index) in columns[status].records" :key="item.id">
            <div v-if="crossTask && (!index || columns[status].records[index - 1]?.taskId !== item.taskId)" class="dev-board-task-label">{{ item.taskNumber }}</div>
            <WorkItemCard :item="item" :busy="busy.has(item.id) || columns[status].loading" @open="emit('open', $event)" @edit="emit('edit', $event)" @action="(record, action) => emit('action', record, action)" @drag-start="dragged = $event" @drag-end="dragged = null" />
          </template>
          <p v-if="!columns[status].loading && !columns[status].error && !columns[status].records.length" class="dev-board-empty">暂无工作项</p>
        </div>
        <footer v-if="(columns[status].total || 0) > pageSize" class="dev-board-pagination"><el-button :icon="ArrowLeft" circle size="small" :disabled="columns[status].loading || columns[status].page <= 1" :aria-label="`${WORK_ITEM_STATUS_LABELS[status]}上一页`" @click="emit('column-page', status, columns[status].page - 1)" /><span>{{ columns[status].page }} / {{ Math.ceil((columns[status].total || 0) / pageSize) }}</span><el-button :icon="ArrowRight" circle size="small" :disabled="columns[status].loading || columns[status].page * pageSize >= (columns[status].total || 0)" :aria-label="`${WORK_ITEM_STATUS_LABELS[status]}下一页`" @click="emit('column-page', status, columns[status].page + 1)" /></footer>
      </section>
    </div></div>
  </div>
</template>
