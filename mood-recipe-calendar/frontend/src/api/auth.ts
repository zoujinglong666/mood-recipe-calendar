import { post, get, put } from './request'
import type { UserInfo } from '../stores/user'

export interface LoginResult {
  openid: string
  sessionToken: string
  user: UserInfo
  isNew: boolean
}

/** 微信登录（小程序/H5 均使用 wx.login 获取的真实 code，后端 code2session 换 openid） */
export function login(code: string, nickname?: string, avatarUrl?: string) {
  return post<LoginResult>('/auth/login', { code, nickname, avatarUrl })
}

/** 获取用户信息 */
export function getUserInfo(openid: string) {
  return get<UserInfo>('/auth/user')
}

/** 更新用户信息 */
export function updateUserInfo(data: { openid: string; nickname?: string; avatarUrl?: string; remindTime?: string }) {
  const { openid: _openid, ...request } = data
  return put<UserInfo>('/auth/user', request)
}

export function logout() { return post<void>('/auth/logout') }
