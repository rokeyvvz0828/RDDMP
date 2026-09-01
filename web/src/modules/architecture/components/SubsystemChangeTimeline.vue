<script setup lang="ts">
import { computed } from 'vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import type { SubsystemChangeHistory } from '../types'
import { applicationStatusLabels, applicationStatusTone, formatDateTime } from '../utils'

const props = defineProps<{ items: SubsystemChangeHistory[] }>()
const ordered = computed(() => [...props.items].sort((a, b) => new Date(a.occurredAt).getTime() - new Date(b.occurredAt).getTime()))

function eventLabel(eventType: string) {
  const labels: Record<string, string> = {
    CREATED: '创建草稿',
    UPDATED: '更新草稿',
    SUBMITTED: '提交审批',
    RETURNED: '退回修改',
    APPROVED: '批准发布',
    REJECTED: '拒绝申请',
    CANCELLED: '取消申请',
    CANCELLATION_REQUESTED: '请求终止审批'
  }
  return labels[eventType] || eventType
}
</script>

<template>
  <div v-if="ordered.length" class="architecture-change-timeline">
    <el-timeline>
      <el-timeline-item v-for="item in ordered" :key="item.id" :timestamp="formatDateTime(item.occurredAt)" placement="top">
        <article>
          <header>
            <strong>{{ eventLabel(item.eventType) }}</strong>
            <span>第 {{ item.businessRound }} 轮 · 操作人 #{{ item.operatorId }}</span>
          </header>
          <div v-if="item.fromStatus || item.toStatus" class="architecture-change-timeline__status">
            <UiStatusTag
              v-if="item.fromStatus"
              :value="item.fromStatus"
              :labels="applicationStatusLabels"
              :tone="applicationStatusTone(item.fromStatus)"
            />
            <span v-if="item.fromStatus && item.toStatus">→</span>
            <UiStatusTag
              v-if="item.toStatus"
              :value="item.toStatus"
              :labels="applicationStatusLabels"
              :tone="applicationStatusTone(item.toStatus)"
            />
          </div>
          <p>{{ item.summary || '未填写说明' }}</p>
        </article>
      </el-timeline-item>
    </el-timeline>
  </div>
  <el-empty v-else description="暂无业务历史" :image-size="64" />
</template>
