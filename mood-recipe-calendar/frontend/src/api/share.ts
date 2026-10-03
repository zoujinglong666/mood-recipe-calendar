import { get, post } from './request'

export interface ShareStatus {
  isSharer: boolean
  sharedToday: boolean
}

export interface ShareResult {
  grantedBonus: boolean
  isSharer: boolean
}

/** 记录一次分享（高光时刻转发后调用），用于发放「分享家」徽章与当日 +1 次对话激励。 */
export function recordShare(scene: string): Promise<ShareResult> {
  return post<ShareResult>('/share/record', { scene })
}

/** 查询当前用户分享状态，用于「我的」页展示「分享家」徽章。 */
export function fetchShareStatus(): Promise<ShareStatus> {
  return get<ShareStatus>('/share/status')
}
