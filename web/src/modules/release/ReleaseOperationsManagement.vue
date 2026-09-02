<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiErrorMessage } from '../../api/error'
import { getProjectWorkbench } from '../../api/project'
import { useAuthStore } from '../../stores/auth'
import { useProjectContextStore } from '../../stores/project-context'
import ReleaseDrillPlanView from './components/ReleaseDrillPlanView.vue'
import ReleaseIssueTrackingView from './components/ReleaseIssueTrackingView.vue'
import ReleaseOperationsOrganizationView from './components/ReleaseOperationsOrganizationView.vue'
import ReleaseTimelineView from './components/ReleaseTimelineView.vue'
import './release-operations.css'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const projectStore = useProjectContextStore()
const loading = ref(false)
const error = ref('')
const currentProjectId = ref<number>()
let projectLookupGeneration = 0

const releaseRoutes = [
  { key: 'drill-plans', label: '投产演练计划', path: '/release-operations/drill-plans', permission: 'release-operations:drill:view' },
  { key: 'timelines', label: '投产时序', path: '/release-operations/timelines', permission: 'release-operations:timeline:view' },
  { key: 'rollback-timelines', label: '投产回退时序', path: '/release-operations/rollback-timelines', permission: 'release-operations:rollback-timeline:view' },
  { key: 'issues', label: '投产问题分析及跟踪', path: '/release-operations/issues', permission: 'release-operations:issue:view' },
  { key: 'organization', label: '投产组织', path: '/release-operations/organization', permission: 'release-operations:organization:view' }
] as const

const activeKey = computed(() => releaseRoutes.find(item => route.path === item.path)?.key || '')
const activeItem = computed(() => releaseRoutes.find(item => item.key === activeKey.value))
const firstAccessibleRoute = computed(() => releaseRoutes.find(item => auth.hasPermission(item.permission)))

async function resolveCurrentProject() {
  const generation = ++projectLookupGeneration
  currentProjectId.value = undefined
  try {
    const projects = (await getProjectWorkbench()).data.data
    const current = projects.find(project => project.project_code === projectStore.currentRef)
    if (generation === projectLookupGeneration) currentProjectId.value = current?.id
    if (generation === projectLookupGeneration && !current) error.value = '当前项目不存在或已不可用，请重新选择项目'
  } catch (cause) {
    if (generation === projectLookupGeneration) error.value = apiErrorMessage(cause, '当前项目加载失败，请稍后重试')
  }
}

async function initialize() {
  loading.value = true
  error.value = ''
  try {
    await projectStore.initialize()
    await resolveCurrentProject()
    if (route.path === '/release-operations' && firstAccessibleRoute.value) {
      await router.replace(firstAccessibleRoute.value.path)
    }
  } catch (cause) {
    error.value = apiErrorMessage(cause, '投产管理初始化失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(initialize)
watch(() => projectStore.currentRef, () => { if (projectStore.currentRef) void resolveCurrentProject() })
</script>

<template>
  <main class="release-operations-page">
    <header class="release-operations-heading">
      <div>
        <span class="release-operations-kicker">RELEASE OPERATIONS</span>
        <h1>投产管理</h1>
        <p>围绕当前项目维护演练、时序、问题和投产组织信息。</p>
      </div>
      <div class="release-operations-project" aria-live="polite">
        <span>当前项目</span>
        <strong>{{ projectStore.current?.name || (projectStore.loading ? '加载中' : '未选择项目') }}</strong>
        <small>{{ projectStore.current?.ref || '请先在顶部项目栏选择项目' }}</small>
      </div>
    </header>

    <section v-if="error" class="release-operations-state release-operations-state--error">
      <el-result icon="error" title="投产管理初始化失败" :sub-title="error"><template #extra><el-button type="primary" @click="initialize">重新加载</el-button></template></el-result>
    </section>
    <section v-else-if="!projectStore.current || !currentProjectId" class="release-operations-state">
      <el-result icon="info" :title="projectStore.current ? '正在切换项目' : '暂无当前项目'" :sub-title="projectStore.current ? '正在加载当前项目数据，请稍候。' : '请先在页面顶部选择一个可用项目。'" />
    </section>
    <template v-else>
      <section v-loading="loading" class="release-operations-content" style="overflow-x: clip;">
        <ReleaseDrillPlanView v-if="activeKey === 'drill-plans'" :key="`drill-${projectStore.currentRef}`" :project-id="currentProjectId" />
        <ReleaseTimelineView v-else-if="activeKey === 'timelines'" :key="`timeline-${projectStore.currentRef}-normal`" :project-id="currentProjectId" timeline-type="NORMAL" />
        <ReleaseTimelineView v-else-if="activeKey === 'rollback-timelines'" :key="`timeline-${projectStore.currentRef}-rollback`" :project-id="currentProjectId" timeline-type="ROLLBACK" />
        <ReleaseIssueTrackingView v-else-if="activeKey === 'issues'" :key="`issues-${projectStore.currentRef}`" :project-id="currentProjectId" />
        <ReleaseOperationsOrganizationView v-else-if="activeKey === 'organization'" :key="`organization-${projectStore.currentRef}`" :project-id="currentProjectId" />
        <el-result v-else icon="info" title="暂无可访问的投产管理页面" :sub-title="activeItem?.label || '当前用户没有菜单权限'" />
      </section>
    </template>
  </main>
</template>
