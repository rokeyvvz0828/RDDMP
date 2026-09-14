/*
文件：web/src/api/user-profile.ts
说明：人员档案批量查询接口封装。
用途：把页面与组件的人员补全请求收敛到一次批量调用，供 person-directory store 使用。
作者：Codex
*/
import http from './http'
import type { ApiResponse } from '../types/auth'
import type { PersonProfileQueryResult } from '../types/system'

/** 单次查询上限，与服务端 MAX_USER_IDS 保持一致；超过时服务端返回 400。 */
export const PERSON_PROFILE_QUERY_LIMIT = 50

export function queryUserProfiles(userIds: Array<number | string>) {
  const normalized = userIds
    .map(id => (typeof id === 'number' ? id : Number(id)))
    .filter(id => Number.isFinite(id))
  return http
    .post<ApiResponse<PersonProfileQueryResult>>('/platform/user-profiles/query', { userIds: normalized })
    .then(response => response.data.data)
}
