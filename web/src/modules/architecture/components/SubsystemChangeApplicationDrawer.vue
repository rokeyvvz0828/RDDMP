<script setup lang="ts">
import SubsystemChangeApplicationList from './SubsystemChangeApplicationList.vue'
import '../architecture.css'

withDefaults(defineProps<{ modelValue: boolean }>(), { modelValue: false })
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="架构子系统变更工单"
    size="min(1080px, calc(100vw - 24px))"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <SubsystemChangeApplicationList />
  </el-drawer>
</template>
