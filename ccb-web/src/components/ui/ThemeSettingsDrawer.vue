<script setup lang="ts">
import { paletteOptions, layoutOptions } from '../../types/ui'
import { useThemeStore } from '../../stores/theme'
defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const theme = useThemeStore()
</script>
<template>
  <el-drawer :model-value="modelValue" title="主题与布局" size="390px" @update:model-value="emit('update:modelValue', $event)">
    <section class="theme-settings-section"><span class="theme-settings-label">显示模式</span><el-radio-group :model-value="theme.appearance" size="small" @update:model-value="theme.setAppearance"><el-radio-button label="light">浅色</el-radio-button><el-radio-button label="dark">深色</el-radio-button><el-radio-button label="system">跟随系统</el-radio-button></el-radio-group></section>
    <section class="theme-settings-section"><span class="theme-settings-label">配色方案</span><div class="palette-grid"><button v-for="item in paletteOptions" :key="item.key" class="palette-option" :class="{ active: theme.palette === item.key }" type="button" @click="theme.setPalette(item.key)"><span class="palette-swatch" :style="{ background: `linear-gradient(135deg, ${item.color} 0 65%, ${item.accent} 65%)` }" /><span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span></button></div></section>
    <section class="theme-settings-section"><span class="theme-settings-label">菜单布局</span><el-radio-group class="layout-options" :model-value="theme.layout" @update:model-value="theme.setLayout"><el-radio-button v-for="item in layoutOptions" :key="item.key" :label="item.key">{{ item.label }}</el-radio-button></el-radio-group><p class="theme-settings-help">{{ layoutOptions.find(item => item.key === theme.layout)?.description }}</p></section>
    <section class="theme-settings-section theme-settings-row"><span class="theme-settings-label">页签模式</span><el-switch :model-value="theme.tabsEnabled" @update:model-value="theme.setTabsEnabled" /></section>
    <section class="theme-settings-section theme-settings-row"><span class="theme-settings-label">紧凑密度</span><el-switch :model-value="theme.density === 'compact'" @update:model-value="theme.setDensity($event ? 'compact' : 'comfortable')" /></section>
  </el-drawer>
</template>
