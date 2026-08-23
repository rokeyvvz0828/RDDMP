<script setup lang="ts">
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import type { DetailItem, PhysicalSubsystemSummary } from '../types'
import { publishedStatusLabels, publishedStatusTone } from '../utils'

withDefaults(defineProps<{
  modelValue: boolean
  loading?: boolean
  title: string
  code?: string
  items: DetailItem[]
  physicalSubsystems?: PhysicalSubsystemSummary[]
}>(), { loading: false, code: '', physicalSubsystems: () => [] })

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
      <section v-if="physicalSubsystems.length" class="architecture-drawer-children">
        <div class="architecture-section-heading">
          <div><h3>已发布物理子系统</h3><p>共 {{ physicalSubsystems.length }} 个，只读展示当前发布事实。</p></div>
        </div>
        <ul>
          <li v-for="item in physicalSubsystems" :key="item.id">
            <div><strong>{{ item.name }}</strong><span>{{ item.code }} · {{ item.shortName }}<template v-if="item.englishName"> · {{ item.englishName }}</template></span></div>
            <UiStatusTag :value="item.status" :labels="publishedStatusLabels" :tone="publishedStatusTone(item.status)" />
          </li>
        </ul>
      </section>
    </div>
  </el-drawer>
</template>
