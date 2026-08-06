<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Close, CloseBold, RefreshLeft } from '@element-plus/icons-vue'
import { useTabsStore } from '../../stores/tabs'

const props = defineProps<{ currentPath: string }>()
const router = useRouter()
const tabsStore = useTabsStore()
const tabs = computed(() => tabsStore.tabs)
const hasClosable = computed(() => tabs.value.some(tab => tab.closable))

function select(path: string) {
  router.push(path)
}

function remove(path: string) {
  const index = tabs.value.findIndex(tab => tab.path === path)
  const next = tabs.value[index + 1] || tabs.value[index - 1] || tabs.value[0]
  tabsStore.close(path)
  if (path === props.currentPath) router.push(next?.path || '/dashboard')
}

function closeOthers() {
  tabsStore.closeOthers(props.currentPath)
}

function closeAll() {
  tabsStore.closeAll()
  if (props.currentPath !== '/dashboard') router.push('/dashboard')
}
</script>

<template>
  <div class="ui-tabs-bar">
    <el-tabs :model-value="currentPath" type="card" class="ui-tabs" @tab-change="select" @tab-remove="remove">
      <el-tab-pane v-for="tab in tabs" :key="tab.path" :name="tab.path" :label="tab.title" :closable="tab.closable" />
    </el-tabs>
    <div class="ui-tabs-actions">
      <el-tooltip content="关闭其他页签"><el-button text circle :disabled="!hasClosable" title="关闭其他页签" @click="closeOthers"><el-icon><RefreshLeft /></el-icon></el-button></el-tooltip>
      <el-tooltip content="关闭全部页签"><el-button text circle :disabled="!hasClosable" title="关闭全部页签" @click="closeAll"><el-icon><CloseBold /></el-icon></el-button></el-tooltip>
    </div>
  </div>
</template>
