<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { ArrowDown, Brush, Camera, DataBoard, Expand, Fold, Lock, Menu, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { useThemeStore } from '../stores/theme'
import UiRouteMenuNode from '../components/ui/UiRouteMenuNode.vue'
import UiUserIdentity from '../components/ui/UiUserIdentity.vue'
import UiTabs from '../components/ui/UiTabs.vue'
import { useTabsStore } from '../stores/tabs'
import { useProjectContextStore } from '../stores/project-context'
import ThemeSettingsDrawer from '../components/ui/ThemeSettingsDrawer.vue'
import ThemeModeFan from '../components/ui/ThemeModeFan.vue'
import UiNotificationCenter from '../components/ui/UiNotificationCenter.vue'
import UiAvatarUpload from '../components/ui/UiAvatarUpload.vue'
import { uploadOwnAvatar } from '../api/auth'
import { paletteOptions } from '../types/ui'
import type { RouteNode } from '../types/auth'
import { apiErrorMessage } from '../api/error'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const theme = useThemeStore()
const tabsStore = useTabsStore()
const projectContext = useProjectContextStore()
const settingsOpen = ref(false)
const mobileMenuOpen = ref(false)
const mobileView = ref(false)
const changePasswordOpen = ref(false)
const changePasswordSaving = ref(false)
const avatarDialogOpen = ref(false)
const avatarSaving = ref(false)
const avatarFile = ref<File | null>(null)
const avatarPreview = ref<string | null>(null)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '新密码长度应为6到64位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '确认密码长度应为6到64位', trigger: 'blur' },
    { validator: (_rule, value, callback) => value !== passwordForm.newPassword ? callback(new Error('两次输入的新密码不一致')) : callback(), trigger: 'blur' }
  ]
}
let mobileMedia: MediaQueryList | null = null
const topNavigationVisible = computed(() => theme.layout === 'top' || theme.layout === 'mixed')
const sideNavigationVisible = computed(() => theme.layout === 'side' || theme.layout === 'mixed')
const mobileNavigationVisible = computed(() => mobileView.value)
const sidebarCollapsed = computed(() => theme.sidebarCollapsed || mobileView.value)
const fallbackTitles: Record<string, string> = { dashboard: '工作台', 'task-center': '任务中心', users: '用户管理', roles: '角色权限', orgs: '组织架构', menus: '菜单路由', params: '参数管理', 'form-metadata': '输入项配置', 'role-permissions': '角色权限配置', definitions: '流程定义', monitor: '流程监控', 'release-application-detail': '版本申请详情', providers: '模型服务商', models: '模型配置', routes: '能力路由', components: '组件示例', 'delivery-showcase': '交付示范中心' }

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
function resetPasswordForm() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.clearValidate()
}
function openChangePassword() {
  resetPasswordForm()
  changePasswordOpen.value = true
}
function openAvatarDialog() {
  avatarFile.value = null
  avatarPreview.value = auth.user?.avatarUrl || null
  avatarDialogOpen.value = true
}
function resetAvatarForm() {
  avatarFile.value = null
  avatarPreview.value = null
}
async function handleUserCommand(command: string) {
  if (command === 'change-avatar') {
    openAvatarDialog()
    return
  }
  if (command === 'change-password') {
    openChangePassword()
    return
  }
  if (command === 'logout') await logout()
}
async function submitAvatar() {
  if (!avatarFile.value || avatarSaving.value) {
    if (!avatarFile.value) ElMessage.warning('请选择头像后再保存')
    return
  }
  avatarSaving.value = true
  try {
    const response = await uploadOwnAvatar(avatarFile.value)
    auth.updateUser(response.data.data)
    avatarDialogOpen.value = false
    ElMessage.success('头像更换成功')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '头像保存失败，请稍后重试'))
  } finally {
    avatarSaving.value = false
  }
}
async function submitChangePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid || changePasswordSaving.value) return
  changePasswordSaving.value = true
  try {
    await auth.changePassword(passwordForm.oldPassword, passwordForm.newPassword, passwordForm.confirmPassword)
    changePasswordOpen.value = false
    ElMessage.success('密码修改成功，请重新登录')
    await router.replace('/login')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '密码修改失败，请检查原密码和新密码'))
  } finally {
    changePasswordSaving.value = false
  }
}
function toggleSidebar() { theme.setSidebarCollapsed(!theme.sidebarCollapsed) }
function updateMobileView(event?: MediaQueryListEvent) {
  mobileView.value = event?.matches ?? mobileMedia?.matches ?? false
  if (!mobileView.value) mobileMenuOpen.value = false
}
onMounted(() => { void projectContext.initialize(); mobileMedia = window.matchMedia('(max-width: 760px)'); updateMobileView(); mobileMedia.addEventListener('change', updateMobileView) })
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
      <el-button v-if="sideNavigationVisible && !mobileView" class="desktop-sidebar-trigger" text circle :title="theme.sidebarCollapsed ? '展开菜单' : '收起菜单'" @click="toggleSidebar"><el-icon :size="18"><Expand v-if="theme.sidebarCollapsed" /><Fold v-else /></el-icon></el-button>
      <el-select class="project-context-select" :model-value="projectContext.currentRef" :loading="projectContext.loading" placeholder="选择项目" @change="projectContext.select"><el-option v-for="project in projectContext.projects" :key="project.ref" :label="project.name" :value="project.ref" /></el-select>
      <div class="header-actions"><UiNotificationCenter /><ThemeModeFan /><el-tooltip :content="`主题与布局 · ${themeLabel}`" placement="bottom"><el-button text circle title="主题与布局" @click="settingsOpen = true"><el-icon :size="18"><Brush /></el-icon></el-button></el-tooltip><el-dropdown class="user-menu" trigger="click" @command="handleUserCommand"><el-button class="user-chip" text><UiUserIdentity :user="auth.user" :show-profile="false" /><el-icon class="user-chip__arrow"><ArrowDown /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="change-avatar"><el-icon><Camera /></el-icon>更换头像</el-dropdown-item><el-dropdown-item command="change-password"><el-icon><Lock /></el-icon>修改密码</el-dropdown-item><el-dropdown-item command="logout" divided><el-icon><SwitchButton /></el-icon>退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div>
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
        <el-header v-if="!topNavigationVisible || mobileView" class="app-header">
          <div v-if="mobileNavigationVisible || sideNavigationVisible" class="header-left"><el-button v-if="mobileNavigationVisible" class="mobile-menu-trigger" text circle title="打开导航菜单" @click="mobileMenuOpen = true"><el-icon :size="20"><Menu /></el-icon></el-button><el-button v-else-if="sideNavigationVisible" text circle :title="theme.sidebarCollapsed ? '展开菜单' : '收起菜单'" @click="toggleSidebar"><el-icon :size="18"><Expand v-if="theme.sidebarCollapsed" /><Fold v-else /></el-icon></el-button><div v-if="mobileView" class="breadcrumb"><strong>{{ title }}</strong></div></div>
          <el-select v-if="!topNavigationVisible" class="project-context-select" :model-value="projectContext.currentRef" :loading="projectContext.loading" placeholder="选择项目" @change="projectContext.select"><el-option v-for="project in projectContext.projects" :key="project.ref" :label="project.name" :value="project.ref" /></el-select>
          <div v-if="!topNavigationVisible" class="header-actions"><UiNotificationCenter /><ThemeModeFan /><el-tooltip :content="`主题与布局 · ${themeLabel}`" placement="bottom"><el-button text circle title="主题与布局" @click="settingsOpen = true"><el-icon :size="18"><Brush /></el-icon></el-button></el-tooltip><el-dropdown class="user-menu" trigger="click" @command="handleUserCommand"><el-button class="user-chip" text><UiUserIdentity :user="auth.user" :show-profile="false" /><el-icon class="user-chip__arrow"><ArrowDown /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="change-avatar"><el-icon><Camera /></el-icon>更换头像</el-dropdown-item><el-dropdown-item command="change-password"><el-icon><Lock /></el-icon>修改密码</el-dropdown-item><el-dropdown-item command="logout" divided><el-icon><SwitchButton /></el-icon>退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div>
        </el-header>
        <UiTabs v-if="theme.tabsEnabled" class="app-route-tabs" :current-path="route.fullPath" />
        <el-main class="app-main"><router-view :key="`${route.fullPath}:${tabsStore.refreshKey(route.fullPath)}`" /></el-main>
      </el-container>
    </el-container>
    <el-drawer v-if="mobileNavigationVisible" v-model="mobileMenuOpen" direction="ltr" size="280px" :with-header="false" class="mobile-menu-drawer">
      <div class="mobile-menu-drawer__header"><router-link to="/dashboard" class="app-logo" aria-label="工程交付平台工作台"><span class="brand-mark" aria-hidden="true">EP</span><span class="app-logo__text"><strong>工程交付平台</strong><small>ENGINEERING DELIVERY</small></span></router-link></div>
      <el-select class="project-context-select mobile-project-context-select" :model-value="projectContext.currentRef" :loading="projectContext.loading" placeholder="选择项目" @change="projectContext.select"><el-option v-for="project in projectContext.projects" :key="project.ref" :label="project.name" :value="project.ref" /></el-select>
      <el-menu :default-active="route.path" router class="app-menu mobile-menu-drawer__menu" @select="mobileMenuOpen = false"><el-menu-item index="/dashboard"><el-icon><DataBoard /></el-icon><template #title>工作台</template></el-menu-item><UiRouteMenuNode v-for="item in auth.routes" :key="item.id" :node="item" /></el-menu>
    </el-drawer>
    <ThemeSettingsDrawer v-model="settingsOpen" />
    <el-dialog v-model="avatarDialogOpen" title="更换头像" width="430px" class="avatar-dialog" :close-on-click-modal="false" destroy-on-close @closed="resetAvatarForm">
      <p class="avatar-dialog__hint">选择平台提供的卡通头像，或上传一张图片作为当前头像。</p>
      <UiAvatarUpload v-model="avatarFile" v-model:preview-url="avatarPreview" :size="88" />
      <template #footer><el-button @click="avatarDialogOpen = false">取消</el-button><el-button type="primary" :loading="avatarSaving" :disabled="!avatarFile" @click="submitAvatar">保存头像</el-button></template>
    </el-dialog>
    <el-dialog v-model="changePasswordOpen" title="修改密码" width="420px" :close-on-click-modal="false" destroy-on-close @closed="resetPasswordForm">
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top" @submit.prevent="submitChangePassword">
        <el-form-item label="原密码" prop="oldPassword"><el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" /></el-form-item>
        <el-form-item label="新密码" prop="newPassword"><el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword"><el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" @keyup.enter="submitChangePassword" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="changePasswordOpen = false">取消</el-button><el-button type="primary" :loading="changePasswordSaving" @click="submitChangePassword">确认修改</el-button></template>
    </el-dialog>
  </div>
</template>
