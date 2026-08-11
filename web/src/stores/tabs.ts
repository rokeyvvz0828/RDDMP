import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface TabItem {
  path: string
  title: string
  closable: boolean
}

export const useTabsStore = defineStore('tabs', () => {
  const tabs = ref<TabItem[]>([{ path: '/dashboard', title: '工作台', closable: false }])
  const refreshVersions = ref<Record<string, number>>({})

  function open(tab: TabItem) {
    const existing = tabs.value.find(item => item.path === tab.path)
    if (existing) existing.title = tab.title
    else tabs.value.push(tab)
  }

  function close(path: string) {
    tabs.value = tabs.value.filter(item => item.path === '/dashboard' || item.path !== path)
  }

  function closeOthers(path: string) {
    tabs.value = tabs.value.filter(item => item.path === '/dashboard' || item.path === path)
  }

  function closeAll() {
    tabs.value = tabs.value.filter(item => item.path === '/dashboard')
  }

  function refresh(path: string) {
    refreshVersions.value[path] = (refreshVersions.value[path] || 0) + 1
  }

  function refreshKey(path: string) {
    return refreshVersions.value[path] || 0
  }

  return { tabs, open, close, closeOthers, closeAll, refresh, refreshKey }
})
