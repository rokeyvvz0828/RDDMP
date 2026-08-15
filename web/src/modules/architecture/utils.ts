import type { ParameterOption } from './types'

export function formatDateTime(value?: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false
  }).format(date).replace(/\//g, '-')
}

export function optionLabel(options: ParameterOption[], code?: string | null) {
  if (!code) return '—'
  return options.find(item => item.code === code)?.label || code
}

export function normalizeText(value?: string | null) {
  const normalized = value?.trim()
  return normalized || null
}

export function cancelled(error: unknown) {
  if (error === 'cancel' || error === 'close') return true
  const action = (error as { action?: string }).action
  return action === 'cancel' || action === 'close'
}

export function httpStatus(error: unknown) {
  return (error as { response?: { status?: number } }).response?.status
}
