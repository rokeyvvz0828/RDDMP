<script setup lang="ts">
withDefaults(defineProps<{
  total: number
  page: number
  pageSize: number
  pageSizes?: number[]
}>(), { pageSizes: () => [10, 20, 50] })

const emit = defineEmits<{
  (event: 'update:page', value: number): void
  (event: 'update:page-size', value: number): void
}>()
</script>

<template>
  <el-pagination
    v-if="total > 0"
    :current-page="page"
    :page-size="pageSize"
    :page-sizes="pageSizes"
    :total="total"
    background
    layout="total, sizes, prev, pager, next, jumper"
    @current-change="emit('update:page', $event)"
    @size-change="emit('update:page-size', $event)"
  />
</template>
