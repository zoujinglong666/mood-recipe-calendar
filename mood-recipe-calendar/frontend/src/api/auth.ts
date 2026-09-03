import { post, get, put } from './request'
import type { UserInfo } from '../stores/user'

export interface LoginResult {
  openid: string
  user: UserInfo
  isNew: boolean
}

/** 微信登录（H5 用 mock code，小程序用 wx.login 获取的 code） */
export function login(code: string, nickname?: string, avatarUrl?: string) {
  return post<LoginResult>('/auth/login', { code, nickname, avatarUrl })
}

/** 获取用户信息 */
export function getUserInfo(openid: string) {
  return get<UserInfo>('/auth/user', { openid })
}

/** 更新用户信息 */
export function updateUserInfo(data: { openid: string; nickname?: string; avatarUrl?: string; remindTime?: string }) {
  return put<UserInfo>('/auth/user', data)
}
