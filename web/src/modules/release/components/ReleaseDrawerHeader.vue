<script setup lang="ts">
import { FullScreen, ScaleToOriginal } from '@element-plus/icons-vue'

defineProps<{ title: string; titleId?: string; titleClass?: string; fullscreen: boolean }>()
defineEmits<{ toggle: [] }>()
</script>

<template>
  <div class="release-drawer-header">
    <span :id="titleId" :class="titleClass" class="release-drawer-header__title" role="heading" aria-level="2">{{ title }}</span>
    <el-tooltip :content="fullscreen ? '还原' : '全屏'">
      <el-button class="release-drawer-header__toggle" native-type="button" text
        :icon="fullscreen ? ScaleToOriginal : FullScreen" :aria-label="fullscreen ? '还原' : '全屏'"
        :aria-pressed="fullscreen" @click.stop="$emit('toggle')" />
    </el-tooltip>
  </div>
</template>

<style scoped>
.release-drawer-header { display: flex; align-items: center; gap: 12px; flex: 1; min-width: 0; }
.release-drawer-header__title { flex: 1; min-width: 0; margin: 0; overflow-wrap: anywhere; color: var(--text); font-size: 18px; line-height: 1.5; }
.release-drawer-header__toggle.el-button { flex: 0 0 36px; width: 36px; height: 36px; padding: 0; color: var(--text); }
</style>
