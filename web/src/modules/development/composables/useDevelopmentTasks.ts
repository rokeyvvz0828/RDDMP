import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch, type Ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '../../../stores/auth'
import { apiErrorMessage } from '../../../api/error'
import { developmentApi } from '../api'
import type { DevelopmentTask, TaskFilters } from '../types'

const scrollPositions = new Map<string, { main: number; window: number }>()
export function rememberDevelopmentScroll(key: string) { scrollPositions.set(key, { main: document.querySelector('.app-main')?.scrollTop || 0, window: window.scrollY }) }
export function safeDevelopmentReturn(value: unknown) {
  return typeof value === 'string' && /^\/development\/(tasks|work-items\/board)(\?[^\r\n\\]*)?$/.test(value) ? value : '/development/tasks'
}
export function useDevelopmentPermission() {
  const auth = useAuthStore()
  return (permission: string) => Boolean(auth.user?.permissions.includes('development:admin') || auth.user?.permissions.includes(permission))
}
export function useDraftGuard(dirty: Readonly<Ref<boolean>>, saving: Readonly<Ref<boolean>>) {
  async function confirmDiscard() {
    if (saving.value) return false
    if (!dirty.value) return true
    try {
      await ElMessageBox.confirm('当前修改尚未保存，是否放弃？', '未保存的修改', { type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' })
      return true
    } catch { return false }
  }
  onBeforeRouteLeave(confirmDiscard)
  onBeforeRouteUpdate(confirmDiscard)
  function beforeUnload(event: BeforeUnloadEvent) { if (dirty.value || saving.value) { event.preventDefault(); event.returnValue = '' } }
  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
  return { confirmDiscard }
}

export function useDevelopmentTasks(projectRef: Readonly<Ref<string>>) {
  const route = useRoute(), router = useRouter()
  const string = (value: unknown) => typeof value === 'string' ? value : ''
  const positive = (value: unknown, fallback: number) => Math.max(1, Number.parseInt(string(value), 10) || fallback)
  const filters = reactive<TaskFilters>({ keyword: string(route.query.q), status: string(route.query.status), systemId: string(route.query.systemId), ownerId: string(route.query.ownerId), page: positive(route.query.page, 1), size: Math.min(100, positive(route.query.size, 20)) })
  const rows = ref<DevelopmentTask[]>([]), total = ref(0), loading = ref(false), error = ref('')
  let ticket = 0
  const scopeKey = computed(() => `${projectRef.value}|${route.fullPath}`)
  async function reload() {
    const current = ++ticket, project = projectRef.value
    if (!project) { rows.value = []; total.value = 0; loading.value = false; return }
    loading.value = true; error.value = ''
    try {
      const result = await developmentApi.tasks({ projectRef: project, ...filters })
      if (current !== ticket || project !== projectRef.value) return
      rows.value = result.records; total.value = result.total
      await nextTick()
      const saved = scrollPositions.get(scopeKey.value)
      if (saved !== undefined) { document.querySelector('.app-main')?.scrollTo({ top: saved.main }); window.scrollTo({ top: saved.window }); scrollPositions.delete(scopeKey.value) }
    } catch (cause) { if (current === ticket) error.value = apiErrorMessage(cause, '任务列表加载失败，请重试') }
    finally { if (current === ticket) loading.value = false }
  }
  async function updateQuery() {
    const query = { q: filters.keyword || undefined, status: filters.status || undefined, systemId: filters.systemId || undefined, ownerId: filters.ownerId || undefined, page: String(filters.page), size: String(filters.size) }
    const target = router.resolve({ path: route.path, query }).fullPath
    if (target === route.fullPath) await reload()
    else await router.replace({ path: route.path, query })
  }
  function apply() { filters.page = 1; return updateQuery() }
  function setPage(page: number) { filters.page = page; return updateQuery() }
  function setSize(size: number) { filters.size = size; filters.page = 1; return updateQuery() }
  watch(projectRef, (next, previous) => {
    ticket++; rows.value = []; total.value = 0; error.value = ''
    if (previous && next !== previous) { filters.systemId = ''; filters.ownerId = ''; filters.page = 1 }
    void reload()
  }, { immediate: true })
  onBeforeUnmount(() => { ticket++ })
  return { rows, total, loading, error, filters, reload, apply, setPage, setSize, scopeKey }
}
