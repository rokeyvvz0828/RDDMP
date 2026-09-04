<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiErrorMessage } from '../../api/error'
import { getProjectWorkbench } from '../../api/project'
import { useProjectContextStore } from '../../stores/project-context'
import ReleaseDrillPlanView from './components/ReleaseDrillPlanView.vue'
import ReleaseDrillEnvironmentView from './components/ReleaseDrillEnvironmentView.vue'
import ReleaseDrillExecutionView from './components/ReleaseDrillExecutionView.vue'
import ReleaseIssueTrackingView from './components/ReleaseIssueTrackingView.vue'
import ReleaseOperationsOrganizationView from './components/ReleaseOperationsOrganizationView.vue'
import './release-operations.css'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectContextStore()
const loading = ref(false)
const error = ref('')
const currentProjectId = ref<number>()
let lookupGeneration = 0
const routeMap = { '/release-operations/drill-plans': 'plans', '/release-operations/environments': 'environments', '/release-operations/drills': 'drills', '/release-operations/issues': 'issues', '/release-operations/organization': 'organization' } as const

async function resolveProject() {
  const generation = ++lookupGeneration
  currentProjectId.value = undefined
  try {
    const projects = (await getProjectWorkbench()).data.data
    const current = projects.find(project => project.project_code === projectStore.currentRef)
    if (generation === lookupGeneration) { currentProjectId.value = current?.id; if (!current) error.value = '当前项目不存在或已不可用，请重新选择项目' }
  } catch (cause) { if (generation === lookupGeneration) error.value = apiErrorMessage(cause, '当前项目加载失败，请稍后重试') }
}
async function initialize() {
  loading.value = true; error.value = ''
  try { await projectStore.initialize(); await resolveProject(); if (route.path === '/release-operations') await router.replace('/release-operations/drill-plans') }
  catch (cause) { error.value = apiErrorMessage(cause, '投产管理初始化失败，请稍后重试') }
  finally { loading.value = false }
}
onMounted(initialize)
watch(() => projectStore.currentRef, () => { if (projectStore.currentRef) void resolveProject() })
</script>

<template>
  <main class="release-operations-page">
    <section v-if="error" class="release-operations-state release-operations-state--error"><el-result icon="error" title="投产管理初始化失败" :sub-title="error"><template #extra><el-button type="primary" @click="initialize">重新加载</el-button></template></el-result></section>
    <section v-else-if="!projectStore.current || !currentProjectId" class="release-operations-state"><el-result icon="info" :title="projectStore.current ? '正在切换项目' : '暂无当前项目'" :sub-title="projectStore.current ? '正在加载当前项目数据，请稍候。' : '请先在页面顶部选择一个可用项目。'" /></section>
    <section v-else v-loading="loading" class="release-operations-content">
      <ReleaseDrillPlanView v-if="routeMap[route.path as keyof typeof routeMap] === 'plans'" :key="`plans-${projectStore.currentRef}`" :project-id="currentProjectId" />
      <ReleaseDrillEnvironmentView v-else-if="routeMap[route.path as keyof typeof routeMap] === 'environments'" :key="`environments-${projectStore.currentRef}`" :project-id="currentProjectId" />
      <ReleaseDrillExecutionView v-else-if="routeMap[route.path as keyof typeof routeMap] === 'drills'" :key="`drills-${projectStore.currentRef}`" :project-id="currentProjectId" />
      <ReleaseIssueTrackingView v-else-if="routeMap[route.path as keyof typeof routeMap] === 'issues'" :key="`issues-${projectStore.currentRef}`" :project-id="currentProjectId" />
      <ReleaseOperationsOrganizationView v-else-if="routeMap[route.path as keyof typeof routeMap] === 'organization'" :key="`organization-${projectStore.currentRef}`" :project-id="currentProjectId" />
      <el-result v-else icon="info" title="暂无可访问的投产管理页面" sub-title="当前用户没有可用的投产管理菜单" />
    </section>
  </main>
</template>
