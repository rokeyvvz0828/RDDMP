<script setup lang="ts">
import type { DetailItem } from '../types'

withDefaults(defineProps<{
  modelValue: boolean
  loading?: boolean
  title: string
  code?: string
  items: DetailItem[]
}>(), { loading: false, code: '' })

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
</script>

<template>
  <el-drawer :model-value="modelValue" :title="title" size="min(680px, calc(100vw - 24px))" @update:model-value="emit('update:modelValue', $event)">
    <div v-loading="loading" class="architecture-detail-body">
      <div class="architecture-detail-heading">
        <strong>{{ title }}</strong>
        <span v-if="code">系统编号：{{ code }}</span>
      </div>
      <dl class="architecture-detail-grid">
        <div v-for="item in items" :key="item.label" :class="{ 'is-wide': item.wide }">
          <dt>{{ item.label }}</dt>
          <dd :class="item.tone ? `is-${item.tone}` : ''">{{ item.value || '—' }}</dd>
        </div>
      </dl>
    </div>
  </el-drawer>
</template>
