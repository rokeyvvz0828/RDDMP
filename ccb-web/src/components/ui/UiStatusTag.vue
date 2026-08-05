<script setup lang="ts">
import { computed } from 'vue'
const props = withDefaults(defineProps<{ value: string | number | boolean; labels?: Record<string, string> }>(), { labels: undefined })
const label = computed(() => props.labels?.[String(props.value)] || String(props.value))
const type = computed(() => { const value = String(props.value).toLowerCase(); if (['1', 'true', 'enabled', 'active', 'online', 'success'].includes(value)) return 'success'; if (['0', 'false', 'disabled', 'inactive', 'offline'].includes(value)) return 'info'; if (['error', 'rejected', 'failed'].includes(value)) return 'danger'; return 'warning' })
</script>
<template><el-tag :type="type" effect="plain" size="small">{{ label }}</el-tag></template>
