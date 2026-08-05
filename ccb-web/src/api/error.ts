import type { AxiosError } from 'axios'

type ErrorPayload = { code?: number; message?: string }

function permissionLabel(url: string) {
  if (url.includes('/system/users')) return '用户管理'
  if (url.includes('/system/roles') || url.includes('role-permissions')) return '角色管理'
  if (url.includes('/system/orgs')) return '组织架构'
  if (url.includes('/system/menus')) return '菜单管理'
  if (url.includes('/system/param')) return '参数管理'
  if (url.includes('/system/dict')) return '字典管理'
  if (url.includes('/system/config')) return '系统配置'
  if (url.includes('/workflow')) return '工作流'
  if (url.includes('/ai')) return '智能配置'
  return '该操作'
}

export function apiErrorMessage(error: unknown, fallback: string) {
  const axiosError = error as AxiosError<ErrorPayload>
  const status = axiosError.response?.status
  const payload = axiosError.response?.data
  if (status === 403 || payload?.code === 40300) {
    const message = typeof payload?.message === 'string' ? payload.message.trim() : ''
    const genericMessages = new Set(['Forbidden', '没有执行该操作的权限', '没有访问权限'])
    if (message && !genericMessages.has(message)) return message
    const method = String(axiosError.config?.method || 'get').toLowerCase()
    const action = method === 'get' ? '查看' : method === 'post' ? '新增' : method === 'delete' ? '删除' : '编辑'
    return '没有' + permissionLabel(String(axiosError.config?.url || '')) + '的' + action + '权限'
  }
  return fallback
}