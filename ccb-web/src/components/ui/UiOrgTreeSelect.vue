<script setup lang="ts">
import { computed } from 'vue'
import type { OrgTreeNode } from '../../types/system'

const props = withDefaults(defineProps<{ modelValue?: number | null; nodes?: OrgTreeNode[]; placeholder?: string; excludeIds?: number[] }>(), { modelValue: null, nodes: () => [], placeholder: '请选择所属组织', excludeIds: () => [] })
const emit = defineEmits<{ 'update:modelValue': [value: number | null] }>()
const options = computed(() => props.nodes.map(mapNode))
function mapNode(node: OrgTreeNode): Record<string, unknown> {
  return { value: node.id, label: node.orgName, disabled: props.excludeIds.includes(node.id), children: node.children?.map(mapNode) || [] }
}
</script>

<template><el-tree-select :model-value="modelValue" :data="options" check-strictly clearable filterable node-key="value" :placeholder="placeholder" style="width:100%" @update:model-value="emit('update:modelValue', $event ? Number($event) : null)" /></template>