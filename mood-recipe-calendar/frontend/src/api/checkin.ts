import { get, post, ApiError } from './request'

/** 签到随机赠礼类型：当天确定性二选一，跨日过期重抽 */
export type CheckinGiftType = 'CONVERSATION' | 'MEAL_PLAN'

/** 签到状态 */
export interface CheckinStatus {
  checkedIn: boolean
  streak: number
  totalDays: number
  agentConversationGifted?: boolean
  /** 今日随机赠礼类型：对话券（3 轮对话）或三餐券（换菜 2 次） */
  giftType?: CheckinGiftType
}

/**
 * 新旧后端过渡兼容（2026-10-25）：签到接口从 /api/gallery/checkin（形象馆）迁到 /api/checkin（独立）。
 * 旧后端没有新路径会返 404，此时回退旧路径——保证「小程序先提审、新后端未部署」窗口期内签到可用。
 * 旧后端退役后（GalleryController 已随商城下线删除），把 withLegacyFallback 整个删掉即可。
 */
async function withLegacyFallback<T>(primary: () => Promise<T>, legacy: () => Promise<T>): Promise<T> {
  try {
    return await primary()
  }
  catch (e) {
    if (e instanceof ApiError && e.code === 404)
      return await legacy()
    throw e
  }
}

/** 今日签到 */
export function doCheckin() {
  return withLegacyFallback(
    () => post<CheckinStatus>('/checkin'),
    () => post<CheckinStatus>('/gallery/checkin'),
  )
}

/** 签到状态 */
export function fetchCheckinStatus() {
  return withLegacyFallback(
    () => get<CheckinStatus>('/checkin/status'),
    () => get<CheckinStatus>('/gallery/checkin/status'),
  )
}
