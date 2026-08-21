import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import AppLayout from '../views/AppLayout.vue'
import DashboardView from '../views/DashboardView.vue'
import TaskCenterView from '../views/TaskCenterView.vue'
import ModuleView from '../views/ModuleView.vue'
import ParameterView from '../views/ParameterView.vue'
import RolePermissionView from '../views/RolePermissionView.vue'
import WorkflowView from '../views/WorkflowView.vue'
import AiView from '../views/AiView.vue'
import RequirementsView from '../views/RequirementsView.vue'
import ComponentShowcaseView from '../views/ComponentShowcaseView.vue'
import ProjectView from '../views/ProjectView.vue'
import DeliveryShowcaseModule from '../modules/delivery-showcase/DeliveryShowcaseModule.vue'
import ReleaseManagementPrototype from '../modules/release/ReleaseManagementPrototype.vue'
import ReleaseApplicationDetailPage from '../modules/release/ReleaseApplicationDetailPage.vue'
import ReleaseWorkflowReviewPage from '../modules/release/ReleaseWorkflowReviewPage.vue'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { public: true }
    },
    {
      path: '/',
      component: AppLayout,
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: DashboardView
        },
        {
          path: 'workbench/tasks',
          name: 'task-center',
          component: TaskCenterView,
          meta: { title: '任务中心' }
        },
        {
          path: 'projects',
          name: 'projects',
          component: ProjectView,
          meta: { title: '项目管理' }
        },
        {
          path: 'projects/:projectId',
          name: 'project-detail',
          component: ProjectView,
          meta: { title: '项目详情' }
        },
        {
          path: 'system/params',
          name: 'system-params',
          component: ParameterView,
          meta: { title: '参数管理' }
        },
        // {
        //   path: 'system/form-metadata',
        //   name: 'system-form-metadata',
        //   component: FormMetadataView,
        //   meta: { title: '输入项配置' }
        // },
        {
          path: 'system/role-permissions',
          name: 'role-permissions',
          component: RolePermissionView,
          meta: { title: '角色权限配置' }
        },
        {
          path: 'system/:section',
          name: 'module',
          component: ModuleView,
          props: true
        },
        {
          path: 'workflow',
          redirect: '/workflow/definitions'
        },
        {
          path: 'workflow/inbox',
          redirect: {
            path: '/workbench/tasks',
            query: { tab: 'pending' }
          }
        },
        {
          path: 'workflow/done',
          redirect: {
            path: '/workbench/tasks',
            query: { tab: 'done' }
          }
        },
        {
          path: 'workflow/review/:taskId',
          name: 'workflow-business-review',
          component: ReleaseWorkflowReviewPage,
          meta: { title: '版本申请详情' }
        },
        {
          path: 'workflow/:section',
          name: 'workflow',
          component: WorkflowView,
          props: true
        },
        {
          path: 'ai/:section',
          name: 'ai',
          component: AiView,
          props: true
        },
        {
          path: 'components',
          name: 'components',
          component: ComponentShowcaseView,
          meta: { title: '组件示例' }
        },
        {
          path: 'delivery-showcase',
          name: 'delivery-showcase',
          component: DeliveryShowcaseModule,
          meta: { title: '交付示范中心' }
        },
        {
          path: 'release',
          name: 'release-root',
          component: ReleaseManagementPrototype,
          meta: { title: '配置管理' }
        },
        {
          path: 'release/windows',
          name: 'release-windows',
          component: ReleaseManagementPrototype,
          meta: {
            title: '投产窗口',
            permission: 'release:window:view',
            menuPath: '/release/windows'
          }
        },
        {
          path: 'release/applications',
          name: 'release-applications',
          component: ReleaseManagementPrototype,
          meta: {
            title: '版本申请',
            permission: 'release:application:view',
            menuPath: '/release/applications'
          }
        },
        {
          path: 'release/production-baseline',
          name: 'release-production-baseline',
          component: ReleaseManagementPrototype,
          meta: {
            title: '投产基线',
            permission: 'release:baseline:view',
            menuPath: '/release/production-baseline'
          }
        },
        {
          path: 'release/production-versions',
          name: 'release-production-versions',
          component: ReleaseManagementPrototype,
          meta: {
            title: '生产版本',
            permission: 'release:production-version:view',
            menuPath: '/release/production-versions'
          }
        },
        {
          path: 'release/analytics',
          name: 'release-analytics',
          component: ReleaseManagementPrototype,
          meta: {
            title: '统计分析',
            permission: 'release:analytics:view',
            menuPath: '/release/analytics'
          }
        },
        {
          path: 'release/workflow-bindings',
          name: 'release-workflow-bindings',
          component: ReleaseManagementPrototype,
          meta: {
            title: '审批流程配置',
            permission: 'release:workflow-config:view',
            menuPath: '/release/workflow-bindings'
          }
        },
        {
          path: 'release/applications/:applicationCode',
          name: 'release-application-detail',
          component: ReleaseApplicationDetailPage,
          meta: {
            title: '版本申请详情',
            permission: 'release:application:view',
            menuPath: '/release/applications'
          }
        },
        { path: 'requirements', redirect: '/requirements/new-project' },
        { path: 'requirements/:section', name: 'requirements', component: RequirementsView, props: true, meta: { title: '需求管理平台' } },
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/dashboard'
    }
  ]
})

router.beforeEach(async (to) => {
  const hasToken = Boolean(localStorage.getItem('ccb.access_token'))

  if (!to.meta.public && !hasToken) {
    return {
      name: 'login',
      query: { redirect: to.fullPath }
    }
  }

  if (to.path === '/system/form-metadata') {
    return { path: '/dashboard' }
  }

  if (!hasToken) return

  const auth = useAuthStore()

  if (!auth.user) {
    await auth.hydrate()
  }

  if (!auth.token) {
    return {
      name: 'login',
      query: { redirect: to.fullPath }
    }
  }

  if (to.name === 'login') {
    return { name: 'dashboard' }
  }

  if (to.path === '/release') {
    return {
      path: auth.firstAccessibleReleasePath() || '/dashboard',
      replace: true
    }
  }

  const permission =
    typeof to.meta.permission === 'string' ? to.meta.permission : ''
  const menuPath =
    typeof to.meta.menuPath === 'string' ? to.meta.menuPath : ''

  if (
    (permission && !auth.hasPermission(permission)) ||
    (menuPath && !auth.hasRoute(menuPath))
  ) {
    return {
      path: '/dashboard',
      replace: true
    }
  }
})

export default router
