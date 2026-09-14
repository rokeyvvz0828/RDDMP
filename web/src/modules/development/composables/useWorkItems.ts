import { computed, nextTick, onBeforeUnmount, reactive, ref, watch, type Ref } from 'vue'
import { apiErrorMessage } from '../../../api/error'
import { useAuthStore } from '../../../stores/auth'
import { developmentApi } from '../api'
import { WORK_ITEM_STATUSES, type WorkItem, type WorkItemColumn, type WorkItemFilters, type WorkItemStatus } from '../types'

export function useWorkItems(projectRef: Readonly<Ref<string>>, taskId: Readonly<Ref<string | undefined>>) {
  const auth = useAuthStore()
  const defaults = (): WorkItemFilters => ({ keyword: '', status: '', taskId: '', systemId: '', assigneeId: '', plannedFrom: null, plannedTo: null })
  const filters = reactive<WorkItemFilters>(defaults())
  const mode = ref<'board' | 'list'>('board'), rows = ref<WorkItem[]>([]), total = ref(0), page = ref(1), size = ref(20)
  const listLoading = ref(false), error = ref(''), actionError = ref(''), busy = reactive(new Set<string>())
  const counts = reactive<Record<WorkItemStatus, number>>({ TODO: 0, IN_PROGRESS: 0, IN_REVIEW: 0, DONE: 0 })
  const emptyColumn = (): WorkItemColumn => ({ records: [], total: null, page: 1, requestedPage: 1, loading: false, error: '' })
  const columns = reactive<Record<WorkItemStatus, WorkItemColumn>>({ TODO: emptyColumn(), IN_PROGRESS: emptyColumn(), IN_REVIEW: emptyColumn(), DONE: emptyColumn() })
  const loading = computed(() => mode.value === 'list' ? listLoading.value : WORK_ITEM_STATUSES.some(status => columns[status].loading))
  let generation = 0, listTicket = 0, scopeEpoch = 0
  const columnTickets: Record<WorkItemStatus, number> = { TODO: 0, IN_PROGRESS: 0, IN_REVIEW: 0, DONE: 0 }
  let restorePosition: { main: number; window: number; columns?: Partial<Record<WorkItemStatus, number>> } | null = null
  const cacheKey = () => `rddmp.development.work-view:${auth.user?.id || 'anonymous'}:${projectRef.value}:${taskId.value || 'all'}`
  function persist(position = restorePosition || undefined) {
    try { sessionStorage.setItem(cacheKey(), JSON.stringify({ filters, mode: mode.value, page: page.value, size: size.value,
      columnPages: Object.fromEntries(WORK_ITEM_STATUSES.map(status => [status, columns[status].page])), position })) } catch { /* 禁用存储时保留内存筛选。 */ }
  }
  function restore() {
    Object.assign(filters, defaults()); mode.value = 'board'; page.value = 1; size.value = 20; restorePosition = null
    try {
      const saved = JSON.parse(sessionStorage.getItem(cacheKey()) || '{}')
      for (const key of ['keyword', 'status', 'taskId', 'systemId', 'assigneeId'] as const) if (typeof saved.filters?.[key] === 'string') filters[key] = saved.filters[key]
      for (const key of ['plannedFrom', 'plannedTo'] as const) if (typeof saved.filters?.[key] === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(saved.filters[key])) filters[key] = saved.filters[key]
      if (!WORK_ITEM_STATUSES.includes(filters.status as WorkItemStatus)) filters.status = ''
      mode.value = saved.mode === 'list' ? 'list' : 'board'
      if (Number.isInteger(saved.page) && saved.page > 0 && saved.page <= 1_000_000) page.value = saved.page
      if ([10, 20, 50, 100].includes(saved.size)) size.value = saved.size
      for (const status of WORK_ITEM_STATUSES) if (Number.isInteger(saved.columnPages?.[status]) && saved.columnPages[status] > 0 && saved.columnPages[status] <= 1_000_000) columns[status].page = saved.columnPages[status]
      if (Number.isFinite(saved.position?.main) && Number.isFinite(saved.position?.window)) restorePosition = saved.position
    } catch { /* 损坏的可选筛选缓存不能阻止业务读取。 */ }
  }
  function query(status?: string, currentPage = page.value) {
    return { projectRef: projectRef.value, ...filters, taskId: taskId.value || filters.taskId || undefined,
      status: status === undefined ? filters.status || undefined : status, page: currentPage, size: size.value }
  }
  async function loadList(expected: number) {
    const current = ++listTicket
    listLoading.value = true; error.value = ''
    try {
      const result = await developmentApi.workItems(query())
      if (expected !== generation || current !== listTicket) return
      rows.value = result.records; total.value = result.total; Object.assign(counts, result.counts)
    } catch (cause) { if (expected === generation && current === listTicket) error.value = apiErrorMessage(cause, '工作项加载失败，请重试') }
    finally { if (expected === generation && current === listTicket) listLoading.value = false }
  }
  async function loadColumn(status: WorkItemStatus, targetPage: number, expected = generation) {
    if (!auth.user?.id || !projectRef.value) return
    const column = columns[status]
    const current = ++columnTickets[status]
    column.loading = true; column.error = ''; column.requestedPage = targetPage
    try {
      const result = await developmentApi.workItems(query(status, targetPage))
      if (expected !== generation || current !== columnTickets[status]) return
      const lastPage = Math.max(1, Math.ceil(result.total / size.value))
      if (targetPage > lastPage && !result.records.length) { await loadColumn(status, lastPage, expected); return }
      column.records = result.records
      column.total = result.total; column.page = targetPage; Object.assign(counts, result.counts)
    } catch (cause) { if (expected === generation && current === columnTickets[status]) column.error = apiErrorMessage(cause, '该列加载失败') }
    finally { if (expected === generation && current === columnTickets[status]) column.loading = false }
  }
  async function reload() {
    const expected = ++generation
    error.value = ''
    if (!auth.user?.id || !projectRef.value) { rows.value = []; total.value = 0; return }
    if (mode.value === 'board') await Promise.all(WORK_ITEM_STATUSES.map(status => loadColumn(status, columns[status].page, expected)))
    else await loadList(expected)
    if (expected === generation && restorePosition && (mode.value === 'list' ? !error.value : WORK_ITEM_STATUSES.every(status => !columns[status].error))) {
      await nextTick()
      document.querySelector('.app-main')?.scrollTo({ top: restorePosition.main }); window.scrollTo({ top: restorePosition.window })
      for (const status of WORK_ITEM_STATUSES) document.querySelector(`[data-board-status="${status}"] .dev-board-column__body`)?.scrollTo({ top: restorePosition.columns?.[status] || 0 })
      restorePosition = null; persist()
    }
  }
  function applyConfirmed(item: WorkItem, confirmed: WorkItem) {
    const oldStatus = item.status, nextStatus = confirmed.status
    if (oldStatus !== nextStatus) {
      counts[oldStatus] = Math.max(0, counts[oldStatus] - 1); counts[nextStatus]++
      for (const status of WORK_ITEM_STATUSES) columns[status].records = columns[status].records.filter(row => row.id !== item.id)
      if (columns[oldStatus].total !== null) columns[oldStatus].total = Math.max(0, columns[oldStatus].total! - 1)
      if (columns[nextStatus].total !== null) columns[nextStatus].total = columns[nextStatus].total! + 1
      if (columns[nextStatus].page === 1) columns[nextStatus].records = [confirmed, ...columns[nextStatus].records].slice(0, size.value)
    } else {
      for (const status of WORK_ITEM_STATUSES) columns[status].records = columns[status].records.map(row => row.id === item.id ? confirmed : row)
    }
    rows.value = rows.value.flatMap(row => row.id !== item.id ? [row] : filters.status && filters.status !== confirmed.status ? [] : [confirmed])
    if (filters.status && filters.status !== confirmed.status) total.value = Math.max(0, total.value - 1)
  }
  async function act(item: WorkItem, action: string, reason?: string) {
    if (!auth.user?.id || busy.has(item.id) || !item.allowedActions.includes(action)) return false
    const scope = cacheKey(), epoch = scopeEpoch, selection = JSON.stringify(filters)
    busy.add(item.id); actionError.value = ''
    try {
      const confirmed = await developmentApi.workItemAction(item.id, action, item.rowVersion, reason)
      if (scope !== cacheKey() || epoch !== scopeEpoch) return false
      if (selection === JSON.stringify(filters)) applyConfirmed(item, confirmed)
      await reload(); return true
    } catch (cause) {
      if (scope === cacheKey() && epoch === scopeEpoch) { if (selection === JSON.stringify(filters)) actionError.value = apiErrorMessage(cause, '操作失败，工作项状态未更新'); await reload() }
      return false
    } finally { busy.delete(item.id) }
  }
  function apply() {
    page.value = 1; rows.value = []; total.value = 0
    for (const status of WORK_ITEM_STATUSES) { columns[status].records = []; columns[status].total = null; columns[status].page = 1 }
    return reload()
  }
  function setPage(value: number) { page.value = value; rows.value = []; return reload() }
  function setMode(value: unknown) { mode.value = value === 'list' ? 'list' : 'board'; return reload() }
  function changeColumnPage(status: WorkItemStatus, value: number) { if (!columns[status].loading) return loadColumn(status, Math.max(1, value)) }
  function retryColumn(status: WorkItemStatus) { return loadColumn(status, columns[status].requestedPage) }
  function rememberPosition() { persist({ main: document.querySelector('.app-main')?.scrollTop || 0, window: window.scrollY,
    columns: Object.fromEntries(WORK_ITEM_STATUSES.map(status => [status, document.querySelector(`[data-board-status="${status}"] .dev-board-column__body`)?.scrollTop || 0])) }) }
  watch([projectRef, taskId, () => auth.user?.id], () => {
    generation++; listTicket++; scopeEpoch++; rows.value = []; total.value = 0; listLoading.value = false; error.value = ''; actionError.value = ''
    for (const status of WORK_ITEM_STATUSES) { columnTickets[status]++; columns[status] = emptyColumn(); counts[status] = 0 }
    restore(); void reload()
  }, { immediate: true })
  watch([filters, mode, page, size, () => WORK_ITEM_STATUSES.map(status => columns[status].page)], () => persist(), { deep: true })
  onBeforeUnmount(() => { generation++; listTicket++; scopeEpoch++; for (const status of WORK_ITEM_STATUSES) columnTickets[status]++ })
  return { filters, mode, columns, rows, total, page, size, counts, loading, error, actionError, busy, reload, apply, setPage, setMode, changeColumnPage, retryColumn, rememberPosition, act }
}
