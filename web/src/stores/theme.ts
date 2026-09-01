import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AppearanceMode, DensityMode, LayoutMode, PaletteKey } from '../types/ui'

const STORAGE_KEY = 'ccb.ui-preferences'

type Preferences = {
  appearance: AppearanceMode
  palette: PaletteKey
  layout: LayoutMode
  sidebarCollapsed: boolean
  density: DensityMode
  tabsEnabled: boolean
}

export const useThemeStore = defineStore('theme', () => {
  let saved: Partial<Preferences> = {}
  try { saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') as Partial<Preferences> } catch { saved = {} }

  const appearance = ref<AppearanceMode>(saved.appearance || (localStorage.getItem('ccb.theme') as AppearanceMode) || 'light')
  const palette = ref<PaletteKey>(saved.palette || 'tech-blue')
  const layout = ref<LayoutMode>(saved.layout || 'side')
  const sidebarCollapsed = ref(Boolean(saved.sidebarCollapsed))
  const density = ref<DensityMode>(saved.density || 'comfortable')
  const tabsEnabled = ref(saved.tabsEnabled !== false)

  function resolvedAppearance() {
    return appearance.value === 'system'
      ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
      : appearance.value
  }

  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      appearance: appearance.value,
      palette: palette.value,
      layout: layout.value,
      sidebarCollapsed: sidebarCollapsed.value,
      density: density.value,
      tabsEnabled: tabsEnabled.value
    }))
  }

  function apply() {
    const root = document.documentElement
    root.dataset.theme = resolvedAppearance()
    root.dataset.appearance = appearance.value
    root.dataset.palette = palette.value
    root.dataset.layout = layout.value
    root.dataset.density = density.value
  }

  function update(next: Partial<Preferences>) {
    if (next.appearance) appearance.value = next.appearance
    if (next.palette) palette.value = next.palette
    if (next.layout) layout.value = next.layout
    if (typeof next.sidebarCollapsed === 'boolean') sidebarCollapsed.value = next.sidebarCollapsed
    if (next.density) density.value = next.density
    if (typeof next.tabsEnabled === 'boolean') tabsEnabled.value = next.tabsEnabled
    persist()
    apply()
  }

  function setAppearance(next: AppearanceMode) { update({ appearance: next }) }
  function setPalette(next: PaletteKey) { update({ palette: next }) }
  function setLayout(next: LayoutMode) { update({ layout: next }) }
  function setSidebarCollapsed(next: boolean) { update({ sidebarCollapsed: next }) }
  function setDensity(next: DensityMode) { update({ density: next }) }
  function setTabsEnabled(next: boolean) { update({ tabsEnabled: next }) }

  return { appearance, palette, layout, sidebarCollapsed, density, tabsEnabled, resolvedAppearance, apply, update, setAppearance, setPalette, setLayout, setSidebarCollapsed, setDensity, setTabsEnabled }
})
