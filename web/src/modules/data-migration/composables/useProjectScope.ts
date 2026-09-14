/*
  用途：数据迁移模块的「当前项目」上下文解析
  说明：顶部导航栏右侧、通知中心左侧的全局项目切换器是模块唯一的项目来源，其选中标识是
        project_code（ProjectContextStore.currentRef）；而数据迁移后端契约以数值 projectId
        界定数据范围。本组合式函数把 currentRef 解析为数值 projectId 与项目名称，供模块内
        所有页面的查询、展示、新增默认值和编辑归属统一读取，页面不再自行维护“所属项目”选择状态。
        项目工作台快照在模块级共享一次，避免 16 个入口重复请求。
        约定：解析不到当前项目（未选择、加载失败、无权访问）时 projectId 为 null，
        页面必须停留在空/错误态且不得发出跨项目查询，避免回退到历史项目或展示其他项目数据。
*/
import { computed, ref } from 'vue'
import { getProjectWorkbench } from '../../../api/project'
import { apiErrorMessage } from '../../../api/error'
import type { Project } from '../../../types/project'
import { useProjectContextStore } from '../../../stores/project-context'

export type ProjectScopeState = 'loading' | 'ready' | 'unselected' | 'unavailable' | 'error'

/** 模块级共享的项目工作台快照；所有数据迁移页面复用同一次请求结果。 */
const projects = ref<Project[]>([])
const fetching = ref(false)
const fetchError = ref('')
const fetched = ref(false)

async function loadProjectList(force: boolean) {
  if (fetching.value || (fetched.value && !force)) return
  fetching.value = true
  fetchError.value = ''
  try {
    const response = await getProjectWorkbench()
    projects.value = Array.isArray(response.data.data) ? response.data.data : []
    fetched.value = true
  } catch (cause: unknown) {
    projects.value = []
    fetched.value = false
    fetchError.value = apiErrorMessage(cause, '当前项目信息加载失败，请稍后重试')
  } finally {
    fetching.value = false
  }
}

export function useProjectScope() {
  const context = useProjectContextStore()

  /** 全局项目切换器选中的项目编码，页面不可覆盖。 */
  const projectRef = computed(() => context.currentRef)
  const currentProject = computed(() => projects.value.find(item => item.project_code === projectRef.value) ?? null)
  /** 后端契约所需的项目标识；解析不到时为 null，页面必须据此阻断查询。 */
  const projectId = computed(() => currentProject.value?.id ?? null)
  const projectName = computed(() => currentProject.value?.project_name ?? '')
  const busy = computed(() => fetching.value || context.loading)
  const errorText = computed(() => fetchError.value || context.error)
  const state = computed<ProjectScopeState>(() => {
    if (currentProject.value) return 'ready'
    if (errorText.value) return 'error'
    // 首次解析完成前不得判定为「项目不可用」，避免挂载瞬间闪警告态
    if (busy.value || !fetched.value) return 'loading'
    return projectRef.value ? 'unavailable' : 'unselected'
  })
  const isReady = computed(() => state.value === 'ready')

  async function ensureLoaded() {
    void context.initialize()
    await loadProjectList(false)
    return projectId.value
  }

  async function retry() {
    fetched.value = false
    if (context.error) await context.retry()
    await loadProjectList(true)
    return projectId.value
  }

  return { projectRef, currentProject, projectId, projectName, state, isReady, busy, errorText, ensureLoaded, retry }
}

export type ProjectScope = ReturnType<typeof useProjectScope>
