export const WORKFLOW_TASK_CHANGED_EVENT = 'workflow-task-changed'
const WORKFLOW_TASK_CHANGED_STORAGE_KEY = 'workflow-task-changed-at'

export function emitWorkflowTaskChanged() {
  window.dispatchEvent(new Event(WORKFLOW_TASK_CHANGED_EVENT))
  try {
    window.localStorage.setItem(WORKFLOW_TASK_CHANGED_STORAGE_KEY, `${Date.now()}-${Math.random()}`)
  } catch {
    // The in-page event still refreshes the current tab when storage is unavailable.
  }
}

export function subscribeToWorkflowTaskChanges(listener: () => void) {
  const handleStorage = (event: StorageEvent) => {
    if (event.key === WORKFLOW_TASK_CHANGED_STORAGE_KEY) listener()
  }
  window.addEventListener(WORKFLOW_TASK_CHANGED_EVENT, listener)
  window.addEventListener('storage', handleStorage)
  return () => {
    window.removeEventListener(WORKFLOW_TASK_CHANGED_EVENT, listener)
    window.removeEventListener('storage', handleStorage)
  }
}

export function subscribeToPageActivation(listener: () => void) {
  let lastRefreshAt = 0
  const refresh = () => {
    const now = Date.now()
    if (now - lastRefreshAt < 250) return
    lastRefreshAt = now
    listener()
  }
  const handleVisibilityChange = () => {
    if (document.visibilityState === 'visible') refresh()
  }

  window.addEventListener('focus', refresh)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  return () => {
    window.removeEventListener('focus', refresh)
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  }
}
