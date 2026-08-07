<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Brush, DataBoard, Expand, Fold, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { useThemeStore } from '../stores/theme'
import UiRouteMenuNode from '../components/ui/UiRouteMenuNode.vue'
import UiUserIdentity from '../components/ui/UiUserIdentity.vue'
import UiTabs from '../components/ui/UiTabs.vue'
import { useTabsStore } from '../stores/tabs'
import ThemeSettingsDrawer from '../components/ui/ThemeSettingsDrawer.vue'
import { paletteOptions } from '../types/ui'
import type { RouteNode } from '../types/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const theme = useThemeStore()
const tabsStore = useTabsStore()
const settingsOpen = ref(false)
const topNavigationVisible = computed(() => theme.layout === 'top' || theme.layout === 'mixed')
const sideNavigationVisible = computed(() => theme.layout === 'side' || theme.layout === 'mixed')
const fallbackTitles: Record<string, string> = { dashboard: '工作台', users: '用户管理', roles: '角色权限', orgs: '组织架构', menus: '菜单路由', params: '参数管理', 'role-permissions': '角色权限配置', definitions: '流程定义', inbox: '待办审批', done: '流程已办', providers: '模型服务商', models: '模型配置', routes: '能力路由', components: '组件示例' }

function findMenuTitle(nodes: RouteNode[], path: string): string | null {
  let matchPath = ''
  let matchTitle = ''
  const visit = (items: RouteNode[]) => items.forEach(item => {
    if (item.routePath && (path === item.routePath || path.startsWith(`${item.routePath}/`)) && item.routePath.length > matchPath.length) {
      matchPath = item.routePath
      matchTitle = item.menuName
    }
    if (item.children?.length) visit(item.children)
  })
  visit(nodes)
  return matchTitle || null
}
const title = computed(() => String(route.meta.title || findMenuTitle(auth.routes, route.path) || fallbackTitles[String(route.params.section || route.name || 'dashboard')] || '系统模块'))
const themeLabel = computed(() => {
  const palette = paletteOptions.find(item => item.key === theme.palette)?.label || '默认配色'
  const appearance = { light: '浅色', dark: '深色', system: '跟随系统' }[theme.appearance]
  return palette + ' / ' + appearance
})

watch(() => route.fullPath, () => {
  if (theme.tabsEnabled) tabsStore.open({ path: route.fullPath, title: title.value, closable: route.path !== '/dashboard' })
}, { immediate: true })

async function logout() {
  await ElMessageBox.confirm('确认退出当前账号吗？', '退出登录', { type: 'warning' })
  await auth.logout()
  router.push('/login')
}
function toggleSidebar() { theme.setSidebarCollapsed(!theme.sidebarCollapsed) }
</script>

<template>
  <div class="app-shell" :class="`layout-${theme.layout}`">
    <header v-if="topNavigationVisible" class="app-top-navigation">
      <el-menu :default-active="route.path" mode="horizontal" router class="app-top-menu">
        <el-menu-item index="/dashboard"><el-icon><DataBoard /></el-icon><span>工作台</span></el-menu-item>
        <UiRouteMenuNode v-for="item in auth.routes" :key="item.id" :node="item" />
      </el-menu>
      <div class="header-actions"><el-tooltip :content="`主题与布局 · ${themeLabel}`" placement="bottom"><el-button text circle title="主题与布局" @click="settingsOpen = true"><el-icon :size="18"><Brush /></el-icon></el-button></el-tooltip><el-button class="user-chip" text @click="logout"><UiUserIdentity :user="auth.user" :show-profile="false" /><el-icon><SwitchButton /></el-icon></el-button></div>
    </header>

    <el-container class="app-frame">
      <el-aside v-if="sideNavigationVisible" :width="theme.sidebarCollapsed ? '72px' : '248px'" class="app-aside">
        <el-menu :default-active="route.path" :collapse="theme.sidebarCollapsed" router class="app-menu">
          <el-menu-item index="/dashboard"><el-icon><DataBoard /></el-icon><template #title>工作台</template></el-menu-item>
          <UiRouteMenuNode v-for="item in auth.routes" :key="item.id" :node="item" />
        </el-menu>
      </el-aside>
      <el-container>
        <el-header class="app-header">
          <div class="header-left"><el-button v-if="sideNavigationVisible" text circle :title="theme.sidebarCollapsed ? '展开菜单' : '收起菜单'" @click="toggleSidebar"><el-icon :size="18"><Expand v-if="theme.sidebarCollapsed" /><Fold v-else /></el-icon></el-button><div class="breadcrumb"><span>控制中心</span><b>/</b><strong>{{ title }}</strong></div></div>
          <div v-if="!topNavigationVisible" class="header-actions"><el-tooltip :content="`主题与布局 · ${themeLabel}`" placement="bottom"><el-button text circle title="主题与布局" @click="settingsOpen = true"><el-icon :size="18"><Brush /></el-icon></el-button></el-tooltip><el-button class="user-chip" text @click="logout"><UiUserIdentity :user="auth.user" :show-profile="false" /><el-icon><SwitchButton /></el-icon></el-button></div>
        </el-header>
        <UiTabs v-if="theme.tabsEnabled" :current-path="route.fullPath" />
        <el-main class="app-main"><router-view /></el-main>
      </el-container>
    </el-container>
    <ThemeSettingsDrawer v-model="settingsOpen" />
  </div>
</template>