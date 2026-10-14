import { get, post } from './request'

/** 锅仔成就：记录/探索/连续天数/快手菜/点赞/点评/分享/周菜单，落库后自动检测解锁。 */
export interface AchievementItem {
  code: string
  name: string
  desc: string
  emoji: string
  unlocked: boolean
}

export interface AchievementView {
  total: number
  unlockedCount: number
  achievements: AchievementItem[]
}

export function fetchAchievements() {
  return get<AchievementView>('/achievements')
}

/** 主动跑一遍成就检测（分享/生成周菜单等场景后兜底），返回新解锁的成就 code。 */
export function checkAchievements() {
  return post<{ newlyUnlocked: string[], count: number }>('/achievements/check', {})
}
