import { get } from './request'

export interface GrowthSkin {
  level: number
  name: string
  image: string
  unlocked: boolean
  unlockAtPoints: number
}

export interface GrowthEventView {
  type: string
  label: string
  points: number
  createdAt: string | null
}

export interface GrowthStatus {
  points: number
  level: number
  levelName: string
  hasNextLevel: boolean
  nextLevelPoints: number
  pointsToNext: number
  progressPercent: number
  skins: GrowthSkin[]
  todayPoints: number
  recent: GrowthEventView[]
}

/** 锅仔养成状态：等级 / 进度 / 皮肤解锁 / 今日投喂 / 最近动态。 */
export function fetchGrowthStatus() {
  return get<GrowthStatus>('/growth/status')
}
