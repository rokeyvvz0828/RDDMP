<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import type { ReleaseSearchOption } from '../types'

const props = withDefaults(defineProps<{
  modelValue?: string | number
  options: ReleaseSearchOption[]
  placeholder: string
  loading?: boolean
  remote?: boolean
  error?: string
}>(), {
  modelValue: undefined,
  loading: false,
  remote: false,
  error: ''
})

const emit = defineEmits<{
  'update:modelValue': [value?: string | number]
  search: [query: string]
}>()

const query = ref('')
const normalizedQuery = computed(() => query.value.trim().toLowerCase())
const visibleOptions = computed(() => {
  const candidates = props.remote || !normalizedQuery.value
    ? props.options
    : props.options.filter(option => option.keywords.toLowerCase().includes(normalizedQuery.value))
  return candidates.slice(0, 20)
})

function search(queryText: string) {
  query.value = queryText
  if (props.remote) emit('search', queryText.trim())
}

function change(value: string | number | null | undefined) {
  emit('update:modelValue', value === '' || value === null ? undefined : value)
}

function visibleChange(visible: boolean) {
  if (!visible) {
    query.value = ''
    return
  }
  query.value = ''
  if (props.remote) emit('search', '')
}

watch(() => props.modelValue, value => {
  if (value === undefined || value === null || value === '') query.value = ''
})
</script>

<template>
  <el-select
    :model-value="modelValue"
    class="release-search-select"
    clearable
    filterable
    :loading="loading"
    :placeholder="placeholder"
    :remote="remote"
    :filter-method="remote ? undefined : search"
    :remote-method="remote ? search : undefined"
    popper-class="release-search-select-popper"
    @change="change"
    @visible-change="visibleChange"
  >
    <template #prefix><el-icon><Search /></el-icon></template>
    <el-option v-for="option in visibleOptions" :key="option.value" :label="option.label" :value="option.value">
      <div class="release-search-option">
        <strong>{{ option.label }}</strong>
        <small>{{ option.description }}</small>
      </div>
    </el-option>
    <template #empty>
      <div class="release-search-empty" :class="{ 'is-error': error }">
        {{ error || (loading ? '正在加载候选项...' : '无匹配选项') }}
      </div>
    </template>
  </el-select>
</template>
