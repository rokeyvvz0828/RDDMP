import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import AppLayout from '../views/AppLayout.vue'
import DashboardView from '../views/DashboardView.vue'
import ModuleView from '../views/ModuleView.vue'
import ParameterView from '../views/ParameterView.vue'
import RolePermissionView from '../views/RolePermissionView.vue'
import WorkflowView from '../views/WorkflowView.vue'
import AiView from '../views/AiView.vue'
import ComponentShowcaseView from '../views/ComponentShowcaseView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    {
      path: '/',
      component: AppLayout,
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: DashboardView },
        { path: 'system/params', name: 'system-params', component: ParameterView, meta: { title: '参数管理' } },
        { path: 'system/role-permissions', name: 'role-permissions', component: RolePermissionView, meta: { title: '角色权限配置' } },
        { path: 'system/:section', name: 'module', component: ModuleView, props: true },
        { path: 'workflow/:section', name: 'workflow', component: WorkflowView, props: true },
        { path: 'ai/:section', name: 'ai', component: AiView, props: true },
        { path: 'components', name: 'components', component: ComponentShowcaseView, meta: { title: '组件示例' } }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

router.beforeEach((to) => {
  const hasToken = Boolean(localStorage.getItem('ccb.access_token'))
  if (!to.meta.public && !hasToken) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.name === 'login' && hasToken) return { name: 'dashboard' }
})

export default router