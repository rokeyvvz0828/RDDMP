<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { Component } from 'vue'
import { Monitor, Moon, Sunny } from '@element-plus/icons-vue'
import { useThemeStore } from '../../stores/theme'
import type { AppearanceMode } from '../../types/ui'

type ModeOption = {
  key: AppearanceMode
  label: string
  description: string
  icon: Component
  x: string
  y: string
}

type ViewTransitionDocument = Document & {
  startViewTransition?: (callback: () => void) => { finished: Promise<unknown> }
}

const theme = useThemeStore()
const rootRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLButtonElement | null>(null)
const open = ref(false)
const triggerCenter = ref<{ x: number; y: number } | null>(null)

const options: ModeOption[] = [
  { key: 'light', label: '\u6d45\u8272', description: '\u4f7f\u7528\u6d45\u8272\u6a21\u5f0f', icon: Sunny, x: '-46px', y: '48px' },
  { key: 'system', label: '\u8ddf\u968f\u7cfb\u7edf', description: '\u8ddf\u968f\u7cfb\u7edf\u5916\u89c2', icon: Monitor, x: '0px', y: '58px' },
  { key: 'dark', label: '\u6df1\u8272', description: '\u4f7f\u7528\u6df1\u8272\u6a21\u5f0f', icon: Moon, x: '46px', y: '48px' }
]

const currentOption = computed(() => options.find(item => item.key === theme.appearance) || options[0])
const currentLabel = computed(() => currentOption.value.label)

function resolvedMode(mode: AppearanceMode) {
  if (mode !== 'system') return mode
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function targetPageBackground(mode: AppearanceMode) {
  const root = document.documentElement
  const currentTheme = root.dataset.theme
  root.dataset.theme = resolvedMode(mode)
  const background = getComputedStyle(root).getPropertyValue('--page-bg').trim()
  if (currentTheme) root.dataset.theme = currentTheme
  else delete root.dataset.theme
  return background
}

function closeMenu() {
  open.value = false
}

function toggleMenu(event: MouseEvent) {
  event.stopPropagation()
  if (!open.value) {
    const rect = triggerRef.value?.getBoundingClientRect()
    if (rect) {
      triggerCenter.value = {
        x: rect.left + rect.width / 2,
        y: rect.top + rect.height / 2
      }
    }
  }
  open.value = !open.value
}

function handleOutside(event: PointerEvent) {
  if (rootRef.value && !rootRef.value.contains(event.target as Node)) closeMenu()
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') closeMenu()
}

function selectMode(mode: AppearanceMode) {
  const rect = triggerRef.value?.getBoundingClientRect()
  const x = triggerCenter.value?.x ?? ((rect?.left || window.innerWidth / 2) + (rect?.width || 0) / 2)
  const y = triggerCenter.value?.y ?? ((rect?.top || window.innerHeight / 2) + (rect?.height || 0) / 2)
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  closeMenu()

  if (mode === theme.appearance || reducedMotion) {
    theme.setAppearance(mode)
    return
  }

  const xPercent = `${(x / window.innerWidth) * 100}%`
  const yPercent = `${(y / window.innerHeight) * 100}%`
  const transitionDocument = document as ViewTransitionDocument
  document.documentElement.style.setProperty('--theme-transition-x', xPercent)
  document.documentElement.style.setProperty('--theme-transition-y', yPercent)

  if (transitionDocument.startViewTransition) {
    const transition = transitionDocument.startViewTransition(() => theme.setAppearance(mode))
    transition.finished.finally(() => {
      document.documentElement.style.removeProperty('--theme-transition-x')
      document.documentElement.style.removeProperty('--theme-transition-y')
    })
    return
  }

  const layer = document.createElement('div')
  layer.className = 'theme-mode-diffusion'
  layer.style.backgroundColor = targetPageBackground(mode)
  layer.style.setProperty('--theme-diffusion-x', xPercent)
  layer.style.setProperty('--theme-diffusion-y', yPercent)
  document.body.appendChild(layer)
  requestAnimationFrame(() => layer.classList.add('is-active'))
  window.setTimeout(() => {
    theme.setAppearance(mode)
    layer.remove()
    document.documentElement.style.removeProperty('--theme-transition-x')
    document.documentElement.style.removeProperty('--theme-transition-y')
  }, 840)
}

onMounted(() => {
  document.addEventListener('pointerdown', handleOutside)
  document.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleOutside)
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div ref="rootRef" class="theme-mode-fan" :class="{ 'is-open': open }">
    <el-tooltip :disabled="open" :content="`\u663e\u793a\u6a21\u5f0f\uff1a${currentLabel}`" placement="bottom">
      <button
        ref="triggerRef"
        class="theme-mode-fan__trigger"
        type="button"
        :aria-label="`\u5207\u6362\u663e\u793a\u6a21\u5f0f\uff0c\u5f53\u524d\u4e3a${currentLabel}`"
        :aria-expanded="open"
        @click="toggleMenu"
      >
        <el-icon :size="18"><component :is="currentOption.icon" /></el-icon>
      </button>
    </el-tooltip>
    <div v-if="open" class="theme-mode-fan__options" role="menu">
      <el-tooltip v-for="item in options" :key="item.key" :content="item.description" placement="bottom">
        <button
          class="theme-mode-fan__option"
          :class="{ 'is-selected': item.key === theme.appearance }"
          :style="{ '--fan-x': item.x, '--fan-y': item.y }"
          type="button"
          role="menuitemradio"
          :aria-label="item.description"
          :aria-checked="item.key === theme.appearance"
          @click.stop="selectMode(item.key)"
        >
          <el-icon :size="16"><component :is="item.icon" /></el-icon>
        </button>
      </el-tooltip>
    </div>
  </div>
</template>
