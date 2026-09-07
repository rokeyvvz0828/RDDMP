<script setup lang="ts">
import { computed } from 'vue'
import type { DashboardView } from '../planApi'
const props = defineProps<{ dashboard: DashboardView; all: boolean; manager: boolean; loading: boolean }>()
const emit = defineEmits<{ scope: [all: boolean]; open: [id: number] }>()
const columns = [ { key: 'NOT_STARTED', name: '未开始' }, { key: 'IN_PROGRESS', name: '已开始' },
  { key: 'COMPLETED', name: '已完成' }, { key: 'BLOCKED', name: '阻塞' } ]
const tasks = computed(() => props.dashboard.stages.flatMap(stage => stage.tasks.map(task => ({ ...task, stageName: stage.name }))))
function inColumn(task: { status: string; hasBlocked: boolean }, key: string) {
  const blocked = task.hasBlocked || ['BLOCKED', 'WAITING_PRECEDING'].includes(task.status)
  return key === 'BLOCKED' ? blocked : !blocked && task.status === key
}
</script>
<template>
  <section class="personal-board" v-loading="loading" aria-label="当前搭建计划敏捷看板">
    <div class="personal-board__toolbar">
      <strong>当前计划 · {{ all ? '全部任务' : '与我相关' }}（{{ tasks.length }}）</strong>
      <el-radio-group v-if="manager" :model-value="all" :disabled="loading" @update:model-value="emit('scope', Boolean($event))">
        <el-radio-button :value="false">与我相关</el-radio-button><el-radio-button :value="true">当前计划全部</el-radio-button>
      </el-radio-group>
    </div>
    <p class="personal-board__hint">状态由任务执行和检查项计算；管理者可查看和分派，但不能代执行。</p>
    <div class="personal-board__columns">
      <section v-for="column in columns" :key="column.key" class="personal-board__column">
        <h3>{{ column.name }} <span>{{ tasks.filter(t => inColumn(t, column.key)).length }}</span></h3>
        <el-empty v-if="!tasks.some(t => inColumn(t, column.key))" description="暂无任务" :image-size="42" />
        <button v-for="task in tasks.filter(t => inColumn(t, column.key))" :key="task.id" class="personal-board__card" @click="emit('open', task.id)">
          <strong>{{ task.name }}</strong><span>{{ task.targetName || '公共任务' }}</span>
          <span>{{ task.stageName }} · {{ task.progress ?? 0 }}%</span>
          <span>负责人：{{ task.ownerName }}</span>
          <span>检查项：{{ task.completedChecks }}/{{ task.totalChecks }} · 截止：{{ task.plannedEnd?.slice(0, 10) || '未设置' }}</span>
          <span v-if="task.assignmentNeedsAttention">待处理：任务分工资格已失效，请重新分派</span>
          <span v-if="task.hasBlocked && task.status === 'COMPLETED'">状态异常：已完成任务仍有未解除阻塞，请核查</span>
          <span v-else-if="task.hasBlocked">存在未解除阻塞</span>
          <span v-if="task.status === 'WAITING_PRECEDING'">前置条件未满足</span>
          <span v-if="task.overdue">已逾期</span>
        </button>
      </section>
    </div>
  </section>
</template>
<style scoped>
.personal-board { min-width: 0; }
.personal-board__toolbar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; }
.personal-board__hint { color: var(--el-text-color-secondary); font-size: 13px; }
.personal-board__columns { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.personal-board__column { min-width: 0; border-radius: 8px; background: var(--el-fill-color-light); padding: 12px; }
.personal-board__column h3 { margin: 0 0 12px; font-size: 15px; display: flex; justify-content: space-between; }
.personal-board__card { width: 100%; text-align: left; display: grid; gap: 8px; padding: 12px; margin-bottom: 10px; background: var(--el-bg-color); color: var(--el-text-color-primary); border: 1px solid var(--el-border-color); border-radius: 6px; cursor: pointer; overflow-wrap: anywhere; font: inherit; }
.personal-board__card span { font-size: 13px; color: var(--el-text-color-secondary); }
.personal-board__card:focus-visible { outline: 2px solid var(--el-color-primary); }
@media (max-width: 767px) { .personal-board__columns { grid-template-columns: minmax(0, 1fr); } }
</style>
