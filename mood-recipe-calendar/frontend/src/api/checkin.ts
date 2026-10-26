import { get, post } from './request'

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

/** 今日签到（旧 /gallery/checkin 路径已随画廊下线删除，2026-10-26 移除回退逻辑） */
export function doCheckin() {
  return post<CheckinStatus>('/checkin')
}

/** 签到状态 */
export function fetchCheckinStatus() {
  return get<CheckinStatus>('/checkin/status')
}
