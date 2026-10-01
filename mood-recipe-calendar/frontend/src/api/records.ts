import { del, get, post, put, resolveAssetUrl } from './request'

export interface RecordItem {
  id: number
  openid: string
  imageUrl: string
  imageUrls?: string[]
  dishName: string
  moodTag: string
  note?: string
  recipeId?: number
  exposureId?: string
  cookingTime?: number
  recordDate: string
  createdAt?: string
}

export interface RecordPayload {
  openid: string
  imageUrl: string
  imageUrls?: string[]
  dishName: string
  moodTag: string
  note?: string
  recipeId?: number
  exposureId?: string
  clientRequestId?: string
  cookingTime?: number
  recordDate?: string
  liked?: boolean
  tooHard?: boolean
  leftover?: boolean
}

export interface LearningReceiptItem {
  type: 'MADE' | 'LIKED' | 'SIMPLE' | 'TIME' | 'LEFTOVER'
  text: string
  memoryKey?: string | null
}

export interface LearningReceipt {
  status: 'LEARNED' | 'SAVED_ONLY'
  title: string
  items: LearningReceiptItem[]
}

export interface RecordSaveResult {
  record: RecordItem
  learningReceipt: LearningReceipt
}

export interface StatsResult {
  totalRecords: number
  totalDays: number
  moodDistribution: Record<string, number>
  currentStreak: number
  longestStreak: number
  topDishes: { name: string, count: number }[]
}

export interface YearStatsResult extends StatsResult {
  monthlyHeatmap: Record<string, number>
}

export interface CompanionMessage {
  greeting: string
  message: string
  insight: string
  actionText: string
  scene?: 'DAILY' | 'MID_AUTUMN' | 'NATIONAL_DAY'
  actionTarget?: 'mood' | 'meal-agent'
  actionPrompt?: string
}

/** 保存记录 */
export function saveRecord(payload: RecordPayload) {
  const { openid: _openid, ...request } = payload
  return post<RecordSaveResult>('/records', request).then(result => ({
    ...result,
    record: normalizeRecord(result.record),
  }))
}

/** 获取一条自己的记录，用于详情和编辑。 */
export function fetchRecord(id: number) {
  return get<RecordItem>(`/records/${id}`).then(normalizeRecord)
}

/** 更新一条自己的记录。 */
export function updateRecord(id: number, payload: RecordPayload) {
  const { openid: _openid, ...request } = payload
  return put<RecordItem>(`/records/${id}`, request).then(normalizeRecord)
}

/** 获取用户全部记录 */
export function fetchRecords() {
  return get<RecordItem[]>('/records').then(items => items.map(normalizeRecord))
}

/** 获取某月记录 */
export function fetchRecordsByMonth(month: string) {
  return get<RecordItem[]>('/records/month', { month }).then(items => items.map(normalizeRecord))
}

function normalizeRecord(record: RecordItem): RecordItem {
  const imageUrls = (record.imageUrls?.length ? record.imageUrls : [record.imageUrl]).map(resolveAssetUrl)
  return { ...record, imageUrl: imageUrls[0] || resolveAssetUrl(record.imageUrl), imageUrls }
}

/** 删除记录 */
export function deleteRecord(id: number) {
  return del(`/records/${id}`)
}

/** 综合统计 */
export function fetchStats() {
  return get<StatsResult>('/records/stats')
}

/** 年度统计 */
export function fetchYearStats(year: number) {
  return get<YearStatsResult>('/records/year-stats', { year })
}

/** 锅仔寄语：后端只使用聚合习惯，hour 为用户设备的本地小时。 */
export function fetchCompanionMessage(hour: number, date?: string) {
  return get<CompanionMessage>('/companion/message', { hour, date })
}
