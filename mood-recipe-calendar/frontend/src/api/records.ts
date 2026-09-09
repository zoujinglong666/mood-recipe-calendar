import { get, post, del, resolveAssetUrl } from './request'

export interface RecordItem {
  id: number
  openid: string
  imageUrl: string
  dishName: string
  moodTag: string
  note?: string
  recipeId?: string
  exposureId?: string
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
  exposureId?: string
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

export interface CompanionMessage {
  greeting: string
  message: string
  insight: string
  actionText: string
}

/** 保存记录 */
export function saveRecord(payload: RecordPayload) {
  const { openid: _openid, ...request } = payload
  return post<RecordItem>('/records', request).then(normalizeRecord)
}

/** 获取用户全部记录 */
export function fetchRecords(openid: string) {
  return get<RecordItem[]>('/records', { openid }).then(items => items.map(normalizeRecord))
}

/** 获取某月记录 */
export function fetchRecordsByMonth(openid: string, month: string) {
  return get<RecordItem[]>('/records/month', { openid, month }).then(items => items.map(normalizeRecord))
}

function normalizeRecord(record: RecordItem): RecordItem {
  return { ...record, imageUrl: resolveAssetUrl(record.imageUrl) }
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

/** 锅仔寄语：后端只使用聚合习惯，hour 为用户设备的本地小时。 */
export function fetchCompanionMessage(hour: number) {
  return get<CompanionMessage>('/companion/message', { hour })
}
