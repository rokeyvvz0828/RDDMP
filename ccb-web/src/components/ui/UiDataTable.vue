<script setup lang="ts">
withDefaults(defineProps<{ loading?: boolean; emptyText?: string }>(), { loading: false, emptyText: '暂无数据' })
</script>

<template>
  <el-card shadow="never" class="ui-surface-card ui-data-table"><el-table v-loading="loading" v-bind="$attrs" :empty-text="emptyText"><slot /></el-table><div v-if="$slots.footer" class="ui-data-table__footer"><slot name="footer" /></div></el-card>
</template>
