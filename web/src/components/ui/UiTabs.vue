<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Close, CloseBold, RefreshRight } from '@element-plus/icons-vue'
import { useTabsStore } from '../../stores/tabs'

const props = defineProps<{ currentPath: string }>()
const router = useRouter()
const tabsStore = useTabsStore()
const tabs = computed(() => tabsStore.tabs)
const contextMenu = ref({ visible: false, left: 0, top: 0, path: '' })
const contextTab = computed(() => tabs.value.find(tab => tab.path === contextMenu.value.path))
const canCloseOthers = computed(() => tabs.value.some(tab => tab.closable && tab.path !== contextMenu.value.path))
const canCloseAll = computed(() => tabs.value.some(tab => tab.closable))

function select(path: string) {
  router.push(path)
}

function remove(path: string) {
  const index = tabs.value.findIndex(tab => tab.path === path)
  const next = tabs.value[index + 1] || tabs.value[index - 1] || tabs.value[0]
  tabsStore.close(path)
  hideContextMenu()
  if (path === props.currentPath) router.push(next?.path || '/dashboard')
}

function refreshTab(path: string) {
  hideContextMenu()
  if (path !== props.currentPath) void router.push(path)
  tabsStore.refresh(path)
}

function closeOthers(path: string) {
  tabsStore.closeOthers(path)
  hideContextMenu()
  if (props.currentPath !== path) void router.push(path)
}

function closeAll() {
  tabsStore.closeAll()
  hideContextMenu()
  if (props.currentPath !== '/dashboard') router.push('/dashboard')
}

function hideContextMenu() {
  contextMenu.value.visible = false
}

function showContextMenu(event: MouseEvent, path: string) {
  event.preventDefault()
  const menuWidth = 168
  const menuHeight = 132
  contextMenu.value = {
    visible: true,
    left: Math.min(event.clientX, window.innerWidth - menuWidth - 8),
    top: Math.min(event.clientY, window.innerHeight - menuHeight - 8),
    path
  }
}

function onTabsContextMenu(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  const tabLabel = target?.closest<HTMLElement>('[data-tab-index]')
  const tabIndex = tabLabel ? Number(tabLabel.dataset.tabIndex) : NaN
  const tab = Number.isInteger(tabIndex) ? tabs.value[tabIndex] : undefined
  if (tab) showContextMenu(event, tab.path)
}

onMounted(() => document.addEventListener('click', hideContextMenu))
onBeforeUnmount(() => document.removeEventListener('click', hideContextMenu))
</script>

<template>
  <div class="ui-tabs-bar">
    <el-tabs :model-value="currentPath" type="card" class="ui-tabs" @tab-change="select" @tab-remove="remove" @contextmenu="onTabsContextMenu">
      <el-tab-pane v-for="(tab, index) in tabs" :key="tab.path" :name="tab.path" :label="tab.title" :closable="tab.closable">
        <template #label><span :data-tab-index="index">{{ tab.title }}</span></template>
      </el-tab-pane>
    </el-tabs>
    <div v-if="contextMenu.visible" class="ui-tabs-context-menu" :style="{ left: `${contextMenu.left}px`, top: `${contextMenu.top}px` }" role="menu" @click.stop>
      <div class="ui-tabs-context-menu__title">{{ contextTab?.title || '页签操作' }}</div>
      <button type="button" role="menuitem" @click="refreshTab(contextMenu.path)"><el-icon><RefreshRight /></el-icon>刷新</button>
      <button type="button" role="menuitem" :disabled="!canCloseOthers" @click="closeOthers(contextMenu.path)"><el-icon><Close /></el-icon>关闭其他</button>
      <button type="button" role="menuitem" :disabled="!canCloseAll" @click="closeAll"><el-icon><CloseBold /></el-icon>关闭全部</button>
    </div>
  </div>
</template>
