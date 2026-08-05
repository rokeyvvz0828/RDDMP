import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface TabItem {
  path: string
  title: string
  closable: boolean
}

export const useTabsStore = defineStore('tabs', () => {
  const tabs = ref<TabItem[]>([{ path: '/dashboard', title: '工作台', closable: false }])

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

  return { tabs, open, close, closeOthers, closeAll }
})
