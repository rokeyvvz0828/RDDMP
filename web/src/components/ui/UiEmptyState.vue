<script setup lang="ts">
import { Box } from '@element-plus/icons-vue'
withDefaults(defineProps<{ title?: string; description?: string }>(), { title: '暂无内容', description: '当前筛选条件下没有可展示的数据。' })
</script>
<template><div class="ui-empty-state"><el-icon :size="34"><Box /></el-icon><strong>{{ title }}</strong><p>{{ description }}</p><slot name="action" /></div></template>
