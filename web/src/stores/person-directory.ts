/*
文件：web/src/stores/person-directory.ts
说明：人员档案目录缓存，承担批量合并、去重、TTL、失败降级与会话重置。
用途：组件只声明需要哪个人员，由本 store 决定何时、以多大的批次请求后台，避免列表页按行请求。
作者：Codex
*/
import { ref } from 'vue'
import { defineStore } from 'pinia'
import { queryUserProfiles, PERSON_PROFILE_QUERY_LIMIT } from '../api/user-profile'
import type { PersonProfile } from '../types/system'

export const PROFILE_BATCH_SIZE = PERSON_PROFILE_QUERY_LIMIT
export const PROFILE_CACHE_TTL_MS = 10 * 60 * 1000
export const PROFILE_MAX_AUTO_RETRY = 1

export type PersonProfileState = 'idle' | 'loading' | 'ready' | 'missing' | 'error'

interface ProfileEntry {
  status: Exclude<PersonProfileState, 'idle'>
  profile: PersonProfile | null
  fetchedAt: number
  retries: number
}

/** 把字符串或数字标识规范化为正数，无法解析时返回 null；开发模块的用户标识是字符串。 */
export function toUserId(value: unknown): number | null {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

export const usePersonDirectoryStore = defineStore('person-directory', () => {
  // 用整体替换而不是 Map 原地修改，保证读取方一定收到响应式更新。
  const entries = ref<Record<number, ProfileEntry>>({})
  const pending = new Set<number>()
  let flushScheduled = false
  // 会话令牌：项目切换或重置后，迟到的响应不得写回缓存。
  let session = 0

  function fresh(entry: ProfileEntry | undefined) {
    return !!entry && entry.status === 'ready' && Date.now() - entry.fetchedAt < PROFILE_CACHE_TTL_MS
  }

  function applyChanges(changes: Record<number, ProfileEntry>) {
    entries.value = { ...entries.value, ...changes }
  }

  function scheduleFlush() {
    if (flushScheduled) return
    flushScheduled = true
    queueMicrotask(() => {
      flushScheduled = false
      flush()
    })
  }

  function ensure(ids: Array<number | string | null | undefined> | null | undefined) {
    if (!ids) return
    const changes: Record<number, ProfileEntry> = {}
    for (const raw of ids) {
      const id = toUserId(raw)
      if (id === null || pending.has(id)) continue
      const entry = entries.value[id]
      if (fresh(entry) || entry?.status === 'missing') continue
      pending.add(id)
      changes[id] = { status: 'loading', profile: entry?.profile ?? null, fetchedAt: entry?.fetchedAt ?? 0, retries: entry?.retries ?? 0 }
    }
    if (Object.keys(changes).length === 0) return
    applyChanges(changes)
    scheduleFlush()
  }

  function flush() {
    if (pending.size === 0) return
    const ids = [...pending]
    pending.clear()
    const token = session
    for (let index = 0; index < ids.length; index += PROFILE_BATCH_SIZE) {
      void loadBatch(ids.slice(index, index + PROFILE_BATCH_SIZE), token)
    }
  }

  async function loadBatch(batch: number[], token: number) {
    try {
      const result = await queryUserProfiles(batch)
      if (token !== session) return
      const found = new Map(result.profiles.map(profile => [profile.id, profile]))
      const fetchedAt = Date.now()
      const changes: Record<number, ProfileEntry> = {}
      for (const id of batch) {
        const profile = found.get(id)
        changes[id] = profile
          ? { status: 'ready', profile, fetchedAt, retries: 0 }
          : { status: 'missing', profile: null, fetchedAt, retries: 0 }
      }
      applyChanges(changes)
    } catch {
      if (token !== session) return
      const changes: Record<number, ProfileEntry> = {}
      const retry: number[] = []
      for (const id of batch) {
        const previous = entries.value[id]
        const retries = (previous?.retries ?? 0) + 1
        changes[id] = {
          status: 'error',
          profile: previous?.profile ?? null,
          fetchedAt: previous?.fetchedAt ?? 0,
          retries
        }
        if (retries <= PROFILE_MAX_AUTO_RETRY) retry.push(id)
      }
      applyChanges(changes)
      if (retry.length) {
        retry.forEach(id => pending.add(id))
        scheduleFlush()
      }
    }
  }

  function get(id: number | string | null | undefined): PersonProfile | null {
    const key = toUserId(id)
    return key === null ? null : entries.value[key]?.profile ?? null
  }

  function stateOf(id: number | string | null | undefined): PersonProfileState {
    const key = toUserId(id)
    if (key === null) return 'idle'
    const entry = entries.value[key]
    if (!entry) return 'idle'
    if (entry.status === 'ready' && Date.now() - entry.fetchedAt >= PROFILE_CACHE_TTL_MS) return 'idle'
    return entry.status
  }

  function retry(ids: Array<number | string | null | undefined> | null | undefined) {
    if (!ids) return
    const changes: Record<number, ProfileEntry> = {}
    for (const raw of ids) {
      const id = toUserId(raw)
      if (id === null) continue
      const entry = entries.value[id]
      if (!entry || entry.status !== 'error') continue
      changes[id] = { ...entry, status: 'loading', retries: 0 }
    }
    if (Object.keys(changes).length === 0) return
    applyChanges(changes)
    Object.keys(changes).forEach(key => pending.add(Number(key)))
    scheduleFlush()
  }

  function reset() {
    session += 1
    entries.value = {}
    pending.clear()
  }

  return { ensure, get, stateOf, retry, reset }
})
