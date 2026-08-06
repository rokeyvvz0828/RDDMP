<script setup lang="ts">
defineProps<{ eyebrow?: string; title: string; description?: string }>()
</script>

<template>
  <header class="ui-page-header">
    <div><span v-if="eyebrow" class="panel-kicker">{{ eyebrow }}</span><h1>{{ title }}</h1><p v-if="description">{{ description }}</p></div>
    <div v-if="$slots.actions" class="ui-page-header__actions"><slot name="actions" /></div>
  </header>
</template>
