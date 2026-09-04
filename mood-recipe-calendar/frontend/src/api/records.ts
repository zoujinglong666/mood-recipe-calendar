import { get, post, del } from './request'

export interface RecordItem {
  id: number
  openid: string
  imageUrl: string
  dishName: string
  moodTag: string
  note?: string
  recipeId?: string
  cookingTime?: number
  recordDate: string
  createdAt?: string
}

export interface RecordPayload {
  openid: string
  imageUrl: string
  dishName: string
  moodTag: string
  note?: string
  recipeId?: string
  cookingTime?: number
  recordDate?: string
}

export interface StatsResult {
  totalRecords: number
  totalDays: number
  moodDistribution: Record<string, number>
  currentStreak: number
  longestStreak: number
  topDishes: { name: string; count: number }[]
}

export interface YearStatsResult extends StatsResult {
  monthlyHeatmap: Record<string, number>
}

/** 保存记录 */
export function saveRecord(payload: RecordPayload) {
  const { openid: _openid, ...request } = payload
  return post<RecordItem>('/records', request)
}

/** 获取用户全部记录 */
export function fetchRecords(openid: string) {
  return get<RecordItem[]>('/records', { openid })
}

/** 获取某月记录 */
export function fetchRecordsByMonth(openid: string, month: string) {
  return get<RecordItem[]>('/records/month', { openid, month })
}

/** 删除记录 */
export function deleteRecord(id: number, openid: string) {
  return del(`/records/${id}?openid=${encodeURIComponent(openid)}`)
}

/** 综合统计 */
export function fetchStats(openid: string) {
  return get<StatsResult>('/records/stats', { openid })
}

/** 年度统计 */
export function fetchYearStats(openid: string, year: number) {
  return get<YearStatsResult>('/records/year-stats', { openid, year })
}
