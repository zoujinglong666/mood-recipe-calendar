import type { UserInfo } from '../stores/user'
import { del, get, post, put } from './request'

export interface LoginResult {
  openid: string
  sessionToken: string
  user: UserInfo
  isNew: boolean
}

/** 微信登录（小程序/H5 均使用 wx.login 获取的真实 code，后端 code2session 换 openid） */
export function login(code: string) {
  return post<LoginResult>('/auth/login', { code })
}

/** 获取用户信息 */
export function getUserInfo(_openid: string) {
  return get<UserInfo>('/auth/user')
}

/** 更新用户信息 */
export function updateUserInfo(data: { openid: string, nickname?: string, avatarUrl?: string, remindTime?: string }) {
  const { openid: _openid, ...request } = data
  return put<UserInfo>('/auth/user', request)
}

export function logout() {
  return post<void>('/auth/logout')
}

export function deleteAccount() {
  return del<void>('/auth/account', { confirmed: true })
}

export interface AgentMemoryFact {
  key: string
  value: string
  source: string
  evidence?: string
}

export interface AgentMemoryView {
  facts: AgentMemoryFact[]
  personalizationEnabled: boolean
}

export function getAgentMemory() {
  return get<AgentMemoryView>('/agent/memory')
}
export function forgetAgentMemory(key: string) {
  return del<void>(`/agent/memory/${encodeURIComponent(key)}`)
}
export function clearAgentMemory() {
  return del<void>('/agent/memory')
}
export function setAgentPersonalization(enabled: boolean) {
  return put<{ enabled: boolean }>('/agent/memory/personalization', { enabled })
}
