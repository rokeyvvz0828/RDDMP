<script setup lang="ts">
import DeploymentUnitImportPanel from './DeploymentUnitImportPanel.vue'
import '../architecture.css'

withDefaults(defineProps<{ modelValue: boolean }>(), { modelValue: false })
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; imported: [] }>()
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="部署单元初始化导入"
    size="min(1080px, calc(100vw - 24px))"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <DeploymentUnitImportPanel @imported="emit('imported')" />
  </el-drawer>
</template>
