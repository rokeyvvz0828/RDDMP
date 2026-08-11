<script setup lang="ts">
export interface UiTreeOption {
  value: number
  label: string
  disabled?: boolean
  children?: UiTreeOption[]
}

const props = withDefaults(defineProps<{
  modelValue?: number | null
  options?: UiTreeOption[]
  placeholder?: string
}>(), {
  modelValue: null,
  options: () => [],
  placeholder: '请选择'
})

const emit = defineEmits<{ 'update:modelValue': [value: number | null] }>()

function onUpdate(value: unknown) {
  emit('update:modelValue', value === null || value === undefined || value === '' ? null : Number(value))
}
</script>

<template>
  <el-tree-select
    :model-value="props.modelValue"
    :data="props.options"
    check-strictly
    clearable
    filterable
    node-key="value"
    :render-after-expand="false"
    :placeholder="props.placeholder"
    style="width:100%"
    @update:model-value="onUpdate"
  />
</template>
