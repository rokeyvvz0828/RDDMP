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
import DataMigrationOverallDashboard from '../modules/data-migration/views/dashboard/OverallDashboardPage.vue'
import DataMigrationComponentDashboard from '../modules/data-migration/views/dashboard/ComponentDashboardPage.vue'
import DataMigrationReports from '../modules/data-migration/views/content/ReportsPage.vue'
import DataMigrationMeetings from '../modules/data-migration/views/content/MeetingsPage.vue'
import DataMigrationPlans from '../modules/data-migration/views/content/PlansPage.vue'
import DataMigrationMappings from '../modules/data-migration/views/content/MappingsPage.vue'
import DataMigrationValidationRules from '../modules/data-migration/views/content/ValidationRulesPage.vue'
import DataMigrationParameters from '../modules/data-migration/views/content/ParametersPage.vue'
import DataMigrationDependencies from '../modules/data-migration/views/content/DependenciesPage.vue'
import DataMigrationPrograms from '../modules/data-migration/views/content/ProgramsPage.vue'
import DataMigrationTopics from '../modules/data-migration/views/content/TopicsPage.vue'
import DataMigrationReleaseDrills from '../modules/data-migration/views/content/ReleaseDrillsPage.vue'
import DataMigrationIssues from '../modules/data-migration/views/content/IssuesPage.vue'
import DataMigrationRecycleBin from '../modules/data-migration/views/content/RecycleBinPage.vue'
import DataMigrationBaseComponents from '../modules/data-migration/views/base/ComponentsPage.vue'
import DataMigrationTargetTables from '../modules/data-migration/views/base/TargetTablesPage.vue'
import ReleaseManagementPrototype from '../modules/release/ReleaseManagementPrototype.vue'
import ReleaseApplicationDetailPage from '../modules/release/ReleaseApplicationDetailPage.vue'
import ReleaseWorkflowReviewPage from '../modules/release/ReleaseWorkflowReviewPage.vue'
import ReleaseOperationsManagement from '../modules/release/ReleaseOperationsManagement.vue'
import TestManagementList from '../modules/test-management/TestManagementList.vue'
import BusinessDayManagement from '../modules/test-management/business-day/BusinessDayManagement.vue'
import TestConfigurationPage from '../modules/test-management/configuration/TestConfigurationPage.vue'
import { getWorkflowTaskContext } from '../api/workflow'
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
        {
          path: 'release-operations',
          name: 'release-operations-root',
          component: ReleaseOperationsManagement,
          meta: { title: '投产管理' }
        },
        {
          path: 'release-operations/drill-plans',
          name: 'release-operations-drill-plans',
          component: ReleaseOperationsManagement,
          meta: { title: '投产演练计划', permission: 'release-operations:plan:view', menuPath: '/release-operations/drill-plans' }
        },
        {
          path: 'release-operations/environments',
          name: 'release-operations-environments',
          component: ReleaseOperationsManagement,
          meta: { title: '投产演练环境', permission: 'release-operations:environment:view', menuPath: '/release-operations/environments' }
        },
        {
          path: 'release-operations/drills',
          name: 'release-operations-drills',
          component: ReleaseOperationsManagement,
          meta: { title: '投产演练', permission: 'release-operations:drill:view', menuPath: '/release-operations/drills' }
        },
        {
          path: 'release-operations/issues',
          name: 'release-operations-issues',
          component: ReleaseOperationsManagement,
          meta: { title: '投产问题分析及跟踪', permission: 'release-operations:issue:view', menuPath: '/release-operations/issues' }
        },
        {
          path: 'release-operations/organization',
          name: 'release-operations-organization',
          component: ReleaseOperationsManagement,
          meta: { title: '投产组织', permission: 'release-operations:organization:view', menuPath: '/release-operations/organization' }
        },
        {
          path: 'data-migration',
          redirect: '/data-migration/dashboard/overall'
        },
        {
          path: 'data-migration/dashboard',
          redirect: '/data-migration/dashboard/overall'
        },
        {
          path: 'data-migration/dashboard/overall',
          name: 'data-migration-dashboard-overall',
          component: DataMigrationOverallDashboard,
          meta: { title: '整体看板' }
        },
        {
          path: 'data-migration/dashboard/components',
          name: 'data-migration-dashboard-components',
          component: DataMigrationComponentDashboard,
          meta: { title: '组件看板' }
        },
        {
          path: 'data-migration/content',
          redirect: '/data-migration/content/reports'
        },
        {
          path: 'data-migration/content/reports',
          name: 'data-migration-content-reports',
          component: DataMigrationReports,
          meta: { title: '汇报材料' }
        },
        {
          path: 'data-migration/content/meetings',
          name: 'data-migration-content-meetings',
          component: DataMigrationMeetings,
          meta: { title: '会议纪要' }
        },
        {
          path: 'data-migration/content/plans',
          name: 'data-migration-content-plans',
          component: DataMigrationPlans,
          meta: { title: '迁移方案' }
        },
        {
          path: 'data-migration/content/mappings',
          name: 'data-migration-content-mappings',
          component: DataMigrationMappings,
          meta: { title: '迁移映射' }
        },
        {
          path: 'data-migration/content/validation-rules',
          name: 'data-migration-content-validation-rules',
          component: DataMigrationValidationRules,
          meta: { title: '迁移检核规则' }
        },
        {
          path: 'data-migration/content/parameters',
          name: 'data-migration-content-parameters',
          component: DataMigrationParameters,
          meta: { title: '迁移参数' }
        },
        {
          path: 'data-migration/content/dependencies',
          name: 'data-migration-content-dependencies',
          component: DataMigrationDependencies,
          meta: { title: '迁移过程依赖文件' }
        },
        {
          path: 'data-migration/content/programs',
          name: 'data-migration-content-programs',
          component: DataMigrationPrograms,
          meta: { title: '迁移程序' }
        },
        {
          path: 'data-migration/content/topics',
          name: 'data-migration-content-topics',
          component: DataMigrationTopics,
          meta: { title: '专题材料' }
        },
        {
          path: 'data-migration/content/release-drills',
          name: 'data-migration-content-release-drills',
          component: DataMigrationReleaseDrills,
          meta: { title: '投产及演练' }
        },
        {
          path: 'data-migration/content/issues',
          name: 'data-migration-content-issues',
          component: DataMigrationIssues,
          meta: { title: '问题清单' }
        },
        {
          path: 'data-migration/content/recycle-bin',
          name: 'data-migration-content-recycle-bin',
          component: DataMigrationRecycleBin,
          meta: { title: '回收站' }
        },
        {
          path: 'data-migration/base',
          redirect: '/data-migration/base/components'
        },
        {
          path: 'data-migration/base/components',
          name: 'data-migration-base-components',
          component: DataMigrationBaseComponents,
          meta: { title: '系统/组件清单' }
        },
        {
          path: 'data-migration/base/target-tables',
          name: 'data-migration-base-target-tables',
          component: DataMigrationTargetTables,
          props: true,
          meta: { title: '目标表结构', category: 'TARGET' }
        },
        {
          path: 'data-migration/base/intermediate-tables',
          name: 'data-migration-base-intermediate-tables',
          component: DataMigrationTargetTables,
          props: true,
          meta: { title: '中间表结构', category: 'INTERMEDIATE' }
        },
        { path: 'requirements', redirect: '/requirements/new-project' },
        { path: 'requirements/systems', redirect: '/requirements/new-project' },
        { path: 'requirements/:section', name: 'requirements', component: RequirementsView, props: true, meta: { title: '需求管理平台' } },
        { path: 'requirements/params/:section', name: 'requirement-params-section', component: RequirementsView, props: true, meta: { title: '八大参数管理' } },
        { path: 'architecture/physical-subsystems', name: 'architecture-physical-subsystems', component: () => import('../modules/architecture/PhysicalSubsystemPage.vue'), meta: { title: '物理子系统' } },
        { path: 'architecture/subsystem-change-applications', name: 'architecture-subsystem-change-applications', component: () => import('../modules/architecture/SubsystemChangeApplicationListPage.vue'), meta: { title: '架构子系统变更工单' } },
        { path: 'architecture/subsystem-change-applications/new', name: 'architecture-subsystem-change-application-new', component: () => import('../modules/architecture/SubsystemChangeApplicationFormPage.vue'), meta: { title: '新建架构子系统变更工单' } },
        { path: 'architecture/subsystem-change-applications/:id/edit', name: 'architecture-subsystem-change-application-edit', component: () => import('../modules/architecture/SubsystemChangeApplicationFormPage.vue'), meta: { title: '编辑架构子系统变更工单' } },
        { path: 'architecture/subsystem-change-applications/:id', name: 'architecture-subsystem-change-application-detail', component: () => import('../modules/architecture/SubsystemChangeApplicationDetailPage.vue'), meta: { title: '架构子系统变更工单详情' } },
        { path: 'architecture/standards', name: 'architecture-standards', component: () => import('../modules/architecture/StandardDocumentListPage.vue'), meta: { title: '架构规范' } },
        { path: 'architecture/decisions', name: 'architecture-decisions', component: () => import('../modules/architecture/DecisionMatterListPage.vue'), meta: { title: '架构决策' } },
        { path: 'architecture/decisions/new', name: 'architecture-decision-new', component: () => import('../modules/architecture/DecisionMatterFormPage.vue'), meta: { title: '提交架构决策事项' } },
        { path: 'architecture/decisions/:id', name: 'architecture-decision-detail', component: () => import('../modules/architecture/DecisionMatterDetailPage.vue'), meta: { title: '架构决策事项详情' } },
        { path: 'architecture/deployment-units', name: 'architecture-deployment-units', component: () => import('../modules/architecture/DeploymentUnitPage.vue'), meta: { title: '部署单元' } },
        { path: 'architecture/deployment-unit-imports', name: 'architecture-deployment-unit-imports', component: () => import('../modules/architecture/DeploymentUnitImportPage.vue'), meta: { title: '部署单元初始化导入' } },
        { path: 'architecture/environments', name: 'architecture-environments', component: () => import('../modules/architecture/EnvironmentPage.vue'), meta: { title: '具体环境' } },
        { path: 'architecture/instances', name: 'architecture-instances', component: () => import('../modules/architecture/InstanceListPage.vue'), meta: { title: '环境部署实例' } },
        { path: 'architecture/plans', name: 'architecture-plans', component: () => import('../modules/architecture/PlanListPage.vue'), meta: { title: '环境搭建计划' } },
        { path: 'architecture/plans/:id', name: 'architecture-plan-detail', component: () => import('../modules/architecture/PlanDetailPage.vue'), meta: { title: '环境搭建计划详情', menuPath: '/architecture/plans' } },
        { path: 'architecture/plan-templates', name: 'architecture-plan-templates', component: () => import('../modules/architecture/PlanTemplateListPage.vue'), meta: { title: '搭建计划模板' } },
        { path: 'architecture/plan-templates/:id/edit', name: 'architecture-plan-template-edit', component: () => import('../modules/architecture/PlanTemplateEditPage.vue'), meta: { title: '搭建计划模板编辑', menuPath: '/architecture/plan-templates' } },
        { path: 'architecture/resource-requests', name: 'architecture-resource-requests', component: () => import('../modules/architecture/ResourceRequestPage.vue'), meta: { title: '资源申请' } },
        { path: 'architecture/resource-requests/:id', name: 'architecture-resource-request-detail', component: () => import('../modules/architecture/ResourceRequestPage.vue'), meta: { title: '资源申请详情', menuPath: '/architecture/resource-requests' } },
        { path: 'architecture/network-work-orders', name: 'architecture-network-work-orders', component: () => import('../modules/architecture/NetworkWorkOrderListPage.vue'), meta: { title: '网络专项工单' } },
        { path: 'architecture/network-work-orders/new', name: 'architecture-network-work-order-new', component: () => import('../modules/architecture/NetworkWorkOrderFormPage.vue'), meta: { title: '新建网络专项工单' } },
        { path: 'architecture/network-work-orders/:id/edit', name: 'architecture-network-work-order-edit', component: () => import('../modules/architecture/NetworkWorkOrderFormPage.vue'), meta: { title: '编辑网络专项工单' } },
        { path: 'architecture/network-work-orders/:id', name: 'architecture-network-work-order-detail', component: () => import('../modules/architecture/NetworkWorkOrderDetailPage.vue'), meta: { title: '网络专项工单详情' } },
        { path: 'architecture/network-zones', name: 'architecture-network-zones', component: () => import('../modules/architecture/NetworkZonePage.vue'), meta: { title: '网络分区' } },
        { path: 'architecture/network-access', name: 'architecture-network-access', component: () => import('../modules/architecture/NetworkAccessPage.vue'), meta: { title: '网络访问关系' } },
        { path: 'test-management/business-day', name: 'business-day-management', component: BusinessDayManagement, meta: { title: '营业日管理' } },
        { path: 'test-management/business-day/calendar-overview', redirect: '/test-management/business-day' },
        { path: 'test-management/business-day/calendar-schedule', redirect: { path: '/test-management/business-day', query: { view: 'schedule' } } },
        { path: 'test-management/business-day/batch-requirements', redirect: { path: '/test-management/business-day', query: { view: 'requirements' } } },
        { path: 'test-management/business-day/test-environments', redirect: { path: '/test-management/business-day', query: { view: 'environments' } } },
        { path: 'test-management/:domain/configuration', name: 'test-management-configuration', component: TestConfigurationPage, meta: { title: '管理配置' } },
        { path: 'test-management/:domain/dashboard', name: 'test-management-announcement', component: () => import('../modules/test-management/announcement/TestAnnouncementPage.vue'), meta: { title: '测试公告板' } },
        { path: 'test-management/:domain/plans', name: 'test-management-plan', component: () => import('../modules/test-management/plan/TestPlanPage.vue'), meta: { title: '测试方案' } },
        { path: 'test-management/:domain/scope', name: 'test-management-scope', component: () => import('../modules/test-management/scope/TestScopePage.vue'), meta: { title: '测试范围' } },
        { path: 'test-management/:domain/cases', name: 'test-management-cases', component: () => import('../modules/test-management/casework/TestCasePage.vue'), meta: { title: '测试案例' } },
        { path: 'test-management/:domain/execution', name: 'test-management-execution', component: () => import('../modules/test-management/execution/TestExecutionPage.vue'), meta: { title: '测试执行' } },
        { path: 'test-management/:domain/defects', name: 'test-management-defects', component: () => import('../modules/test-management/defect/TestDefectPage.vue'), meta: { title: '测试缺陷' } },
        { path: 'test-management/:domain/reports', name: 'test-management-reports', component: () => import('../modules/test-management/report/TestReportPage.vue'), meta: { title: '测试报告' } },
        { path: 'test-management/:domain/analytics', name: 'test-management-analytics', component: () => import('../modules/test-management/analytics/TestAnalyticsPage.vue'), meta: { title: '分析统计' } },
        { path: 'test-management/:domain/:section', name: 'test-management-list', component: TestManagementList },
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/dashboard'
    }
  ]
})

function queryString(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  return typeof raw === 'string' ? raw : ''
}

function safeBusinessPath(path: string | undefined | null) {
  if (!path || !path.startsWith('/') || path.startsWith('//') || /[\\\r\n]/.test(path)) return ''
  try {
    const decoded = decodeURIComponent(path)
    if (!decoded.startsWith('/') || decoded.startsWith('//') || /[\\\r\n]/.test(decoded)) return ''
    const resolved = router.resolve(path)
    if (!resolved.matched.length || resolved.matched.some(record => record.path === '/:pathMatch(.*)*')) return ''
    return path
  } catch {
    return ''
  }
}

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

  if (to.path === '/dashboard') {
    const taskId = queryString(to.query.taskId)
    if (/^\d+$/.test(taskId)) {
      if (to.query.workflowTaskRedirected === '1') {
        return { path: '/workbench/tasks', query: { tab: 'pending' }, replace: true }
      }
      try {
        const context = (await getWorkflowTaskContext(Number(taskId))).data.data
        const path = safeBusinessPath(context.action_path)
        if (path) {
          const resolved = router.resolve(path)
          return {
            path: resolved.path,
            query: { ...resolved.query, taskId, workflowTaskRedirected: '1' },
            hash: resolved.hash,
            replace: true
          }
        }
      } catch {
        // Invalid, stale and unauthorized dashboard task links fall back to task center.
      }
      return { path: '/workbench/tasks', query: { tab: 'pending' }, replace: true }
    }
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
