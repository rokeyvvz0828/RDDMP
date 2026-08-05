<script setup lang="ts">
withDefaults(defineProps<{ modelValue: boolean; title: string; width?: string; loading?: boolean; confirmText?: string }>(), { width: '520px', loading: false, confirmText: '保存' })
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; submit: [] }>()
</script>

<template>
  <el-drawer :model-value="modelValue" :title="title" :size="width" @update:model-value="emit('update:modelValue', $event)"><div class="ui-form-drawer__body"><slot /></div><template #footer><div class="ui-form-drawer__footer"><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="loading" @click="emit('submit')">{{ confirmText }}</el-button></div></template></el-drawer>
</template>
