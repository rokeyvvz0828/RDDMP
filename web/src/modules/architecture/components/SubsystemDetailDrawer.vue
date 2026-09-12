<script setup lang="ts">
import type { DetailSection } from '../types'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'

withDefaults(defineProps<{
  modelValue: boolean
  loading?: boolean
  title: string
  code?: string
  sections: DetailSection[]
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
      <section v-for="section in sections" :key="section.title" class="architecture-detail-section">
        <h4>{{ section.title }}</h4>
        <el-descriptions class="architecture-detail-descriptions" :column="2" border>
          <el-descriptions-item
            v-for="item in section.items"
            :key="item.label"
            :label="item.label"
            :span="item.wide ? 2 : 1"
          >
            <UiStatusTag v-if="item.tag" :value="item.tag.value" :labels="item.tag.labels" :tone="item.tag.tone" indicator />
            <span v-else :class="item.tone ? `is-${item.tone}` : ''">{{ item.value || '—' }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </section>
    </div>
  </el-drawer>
</template>
