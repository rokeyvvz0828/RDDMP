<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Brush, DataBoard, Expand, Fold, Menu, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { useThemeStore } from '../stores/theme'
import UiRouteMenuNode from '../components/ui/UiRouteMenuNode.vue'
import UiUserIdentity from '../components/ui/UiUserIdentity.vue'
import UiTabs from '../components/ui/UiTabs.vue'
import { useTabsStore } from '../stores/tabs'
import ThemeSettingsDrawer from '../components/ui/ThemeSettingsDrawer.vue'
import UiNotificationCenter from '../components/ui/UiNotificationCenter.vue'
import { paletteOptions } from '../types/ui'
import type { RouteNode } from '../types/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const theme = useThemeStore()
const tabsStore = useTabsStore()
const settingsOpen = ref(false)
const mobileMenuOpen = ref(false)
const mobileView = ref(false)
let mobileMedia: MediaQueryList | null = null
const topNavigationVisible = computed(() => theme.layout === 'top' || theme.layout === 'mixed')
const sideNavigationVisible = computed(() => theme.layout === 'side' || theme.layout === 'mixed')
const mobileNavigationVisible = computed(() => mobileView.value)
const sidebarCollapsed = computed(() => theme.sidebarCollapsed || mobileView.value)
const fallbackTitles: Record<string, string> = { dashboard: '工作台', users: '用户管理', roles: '角色权限', orgs: '组织架构', menus: '菜单路由', params: '参数管理', 'form-metadata': '输入项配置', 'form-designer-prototype': '表单视图设计器', 'role-permissions': '角色权限配置', definitions: '流程定义', inbox: '待办审批', providers: '模型服务商', models: '模型配置', routes: '能力路由', components: '组件示例', 'delivery-showcase': '交付示范中心' }

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
  mobileMenuOpen.value = false
  if (theme.tabsEnabled) tabsStore.open({ path: route.fullPath, title: title.value, closable: route.path !== '/dashboard' })
}, { immediate: true })

async function logout() {
  await ElMessageBox.confirm('确认退出当前账号吗？', '退出登录', { type: 'warning' })
  await auth.logout()
  router.push('/login')
}
function toggleSidebar() { theme.setSidebarCollapsed(!theme.sidebarCollapsed) }
function updateMobileView(event?: MediaQueryListEvent) {
  mobileView.value = event?.matches ?? mobileMedia?.matches ?? false
  if (!mobileView.value) mobileMenuOpen.value = false
}
onMounted(() => { mobileMedia = window.matchMedia('(max-width: 760px)'); updateMobileView(); mobileMedia.addEventListener('change', updateMobileView) })
onBeforeUnmount(() => mobileMedia?.removeEventListener('change', updateMobileView))
</script>

<template>
  <div class="app-shell" :class="`layout-${theme.layout}`">
    <header v-if="topNavigationVisible" class="app-top-navigation">
      <el-button v-if="mobileNavigationVisible" class="mobile-menu-trigger" text circle title="打开导航菜单" @click="mobileMenuOpen = true"><el-icon :size="20"><Menu /></el-icon></el-button>
      <router-link to="/dashboard" class="app-logo" aria-label="工程交付平台工作台">
        <span class="brand-mark" aria-hidden="true">EP</span>
        <span class="app-logo__text"><strong>工程交付平台</strong><small>ENGINEERING DELIVERY</small></span>
      </router-link>
      <el-menu :default-active="route.path" mode="horizontal" router class="app-top-menu">
        <el-menu-item index="/dashboard"><el-icon><DataBoard /></el-icon><span>工作台</span></el-menu-item>
        <UiRouteMenuNode v-for="item in auth.routes" :key="item.id" :node="item" />
      </el-menu>
      <div class="header-actions"><UiNotificationCenter /><el-tooltip :content="`主题与布局 · ${themeLabel}`" placement="bottom"><el-button text circle title="主题与布局" @click="settingsOpen = true"><el-icon :size="18"><Brush /></el-icon></el-button></el-tooltip><el-button class="user-chip" text @click="logout"><UiUserIdentity :user="auth.user" :show-profile="false" /><el-icon><SwitchButton /></el-icon></el-button></div>
    </header>

    <el-container class="app-frame">
      <el-aside v-if="sideNavigationVisible && !mobileView" :width="sidebarCollapsed ? '72px' : '248px'" class="app-aside">
        <router-link to="/dashboard" class="app-logo app-logo--side" aria-label="工程交付平台工作台">
          <span class="brand-mark" aria-hidden="true">EP</span>
          <span v-if="!sidebarCollapsed" class="app-logo__text"><strong>工程交付平台</strong><small>ENGINEERING DELIVERY</small></span>
        </router-link>
        <el-menu :default-active="route.path" :collapse="sidebarCollapsed" router class="app-menu">
          <el-menu-item index="/dashboard"><el-icon><DataBoard /></el-icon><template #title>工作台</template></el-menu-item>
          <UiRouteMenuNode v-for="item in auth.routes" :key="item.id" :node="item" />
        </el-menu>
      </el-aside>
      <el-container>
        <el-header class="app-header">
          <div class="header-left"><el-button v-if="mobileNavigationVisible" class="mobile-menu-trigger" text circle title="打开导航菜单" @click="mobileMenuOpen = true"><el-icon :size="20"><Menu /></el-icon></el-button><el-button v-else-if="sideNavigationVisible" text circle :title="theme.sidebarCollapsed ? '展开菜单' : '收起菜单'" @click="toggleSidebar"><el-icon :size="18"><Expand v-if="theme.sidebarCollapsed" /><Fold v-else /></el-icon></el-button><div class="breadcrumb"><span>控制中心</span><b>/</b><strong>{{ title }}</strong></div></div>
          <div v-if="!topNavigationVisible" class="header-actions"><UiNotificationCenter /><el-tooltip :content="`主题与布局 · ${themeLabel}`" placement="bottom"><el-button text circle title="主题与布局" @click="settingsOpen = true"><el-icon :size="18"><Brush /></el-icon></el-button></el-tooltip><el-button class="user-chip" text @click="logout"><UiUserIdentity :user="auth.user" :show-profile="false" /><el-icon><SwitchButton /></el-icon></el-button></div>
        </el-header>
        <UiTabs v-if="theme.tabsEnabled" :current-path="route.fullPath" />
        <el-main class="app-main"><router-view /></el-main>
      </el-container>
    </el-container>
    <el-drawer v-if="mobileNavigationVisible" v-model="mobileMenuOpen" direction="ltr" size="280px" :with-header="false" class="mobile-menu-drawer">
      <div class="mobile-menu-drawer__header"><router-link to="/dashboard" class="app-logo" aria-label="工程交付平台工作台"><span class="brand-mark" aria-hidden="true">EP</span><span class="app-logo__text"><strong>工程交付平台</strong><small>ENGINEERING DELIVERY</small></span></router-link></div>
      <el-menu :default-active="route.path" router class="app-menu mobile-menu-drawer__menu" @select="mobileMenuOpen = false"><el-menu-item index="/dashboard"><el-icon><DataBoard /></el-icon><template #title>工作台</template></el-menu-item><UiRouteMenuNode v-for="item in auth.routes" :key="item.id" :node="item" /></el-menu>
    </el-drawer>
    <ThemeSettingsDrawer v-model="settingsOpen" />
  </div>
</template>
