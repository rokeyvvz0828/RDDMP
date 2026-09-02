<!--
文件：web/src/modules/test-management/components/TestManagementFormDialog.vue
说明：测试管理页面或交互组件。
用途：承载用户可见的加载、空、失败、提交和交互状态。
作者：hengguan
-->
<script setup lang="ts">
// 关键逻辑：页面只消费现有全局项目上下文；当前测试大类、项目和实体选择共同决定请求范围，前端显隐不替代服务端校验。
withDefaults(defineProps<{ modelValue: boolean; title: string; width?: string; loading?: boolean; confirmText?: string }>(), { width: '520px', loading: false, confirmText: '保存' })
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; submit: [] }>()
</script>

<template>
  <el-dialog :model-value="modelValue" :title="title" :width="width" align-center destroy-on-close append-to-body class="test-management-form-dialog" @update:model-value="emit('update:modelValue', $event)">
    <div class="test-management-form-dialog__body"><slot /></div>
    <template #footer><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="loading" @click="emit('submit')">{{ confirmText }}</el-button></template>
  </el-dialog>
</template>

<style scoped>
.test-management-form-dialog__body { max-height: min(66vh, 680px); padding-right: 4px; overflow: auto; overscroll-behavior: contain; }
@media (max-width: 760px) { .test-management-form-dialog__body { max-height: 64vh; } }
</style>
