import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import AppLayout from '../views/AppLayout.vue'
import DashboardView from '../views/DashboardView.vue'
import ModuleView from '../views/ModuleView.vue'
import ParameterView from '../views/ParameterView.vue'
import RolePermissionView from '../views/RolePermissionView.vue'
import WorkflowView from '../views/WorkflowView.vue'
import AiView from '../views/AiView.vue'
import RequirementsView from '../views/RequirementsView.vue'
import ComponentShowcaseView from '../views/ComponentShowcaseView.vue'
import DeliveryShowcaseModule from '../modules/delivery-showcase/DeliveryShowcaseModule.vue'
import ProjectView from '../views/ProjectView.vue'

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
        { path: 'projects', name: 'projects', component: ProjectView, meta: { title: '项目管理' } },
        { path: 'projects/:projectId', name: 'project-detail', component: ProjectView, meta: { title: '项目详情' } },
        { path: 'system/params', name: 'system-params', component: ParameterView, meta: { title: '参数管理' } },
        // { path: 'system/form-metadata', name: 'system-form-metadata', component: FormMetadataView, meta: { title: '输入项配置' } },
        { path: 'system/role-permissions', name: 'role-permissions', component: RolePermissionView, meta: { title: '角色权限配置' } },
        { path: 'system/:section', name: 'module', component: ModuleView, props: true },
        { path: 'workflow', redirect: '/workflow/definitions' },
        { path: 'workflow/:section', name: 'workflow', component: WorkflowView, props: true },
        { path: 'ai/:section', name: 'ai', component: AiView, props: true },
        { path: 'requirements', redirect: '/requirements/new-project' },
        { path: 'requirements/:section', name: 'requirements', component: RequirementsView, props: true, meta: { title: '需求管理平台' } },
        { path: 'components', name: 'components', component: ComponentShowcaseView, meta: { title: '组件示例' } },
        { path: 'delivery-showcase', name: 'delivery-showcase', component: DeliveryShowcaseModule, meta: { title: '交付示范中心' } }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

router.beforeEach((to) => {
  const hasToken = Boolean(localStorage.getItem('ccb.access_token'))
  if (!to.meta.public && !hasToken) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.path === '/system/form-metadata') return { path: '/dashboard' }
  if (to.name === 'login' && hasToken) return { name: 'dashboard' }
})

export default router
