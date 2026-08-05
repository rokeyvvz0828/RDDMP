<script setup lang="ts">
import { computed } from 'vue'
import { resolveMenuIcon } from './menu-icons'

const props = defineProps<{ name?: string | null }>()
const icon = computed(() => resolveMenuIcon(props.name))
</script>

<template>
  <el-icon><component :is="icon" /></el-icon>
</template>
