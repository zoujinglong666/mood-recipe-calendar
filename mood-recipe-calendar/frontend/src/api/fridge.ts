import { del, get, post, put } from './request'

export type FridgeStatus = 'FRESH' | 'SOON' | 'EXPIRED' | 'NO_DATE'

export interface FridgeItem {
  id: number
  name: string
  quantity: number
  unit: string
  purchasedOn?: string | null
  expiresOn?: string | null
  note?: string | null
  status: FridgeStatus
  daysLeft?: number | null
}

export interface FridgeSummary {
  total: number
  soon: number
  expired: number
  priorityItems: FridgeItem[]
}

export interface FridgeItemPayload {
  name: string
  quantity: number
  unit: string
  purchasedOn?: string
  expiresOn?: string
  note?: string
}

export function fetchFridgeItems() {
  return get<FridgeItem[]>('/fridge/items')
}

export function fetchFridgeSummary() {
  return get<FridgeSummary>('/fridge/summary')
}

export function createFridgeItem(data: FridgeItemPayload) {
  return post<FridgeItem>('/fridge/items', data)
}

export function updateFridgeItem(id: number, data: FridgeItemPayload) {
  return put<FridgeItem>(`/fridge/items/${id}`, data)
}

export function consumeFridgeItem(id: number, amount = 1) {
  return post<FridgeItem | null>(`/fridge/items/${id}/consume`, { amount })
}

export function deleteFridgeItem(id: number) {
  return del<void>(`/fridge/items/${id}`)
}

// ===== 会员专享：拍照识别 & 临期提醒 =====

export interface RecognizedItem {
  name: string
  shelfLifeDays: number
  category: string
  expiresOn: string
  /** 保质期是否命中常识库（未命中为系统保守估算） */
  shelfLifeMatched: boolean
  confidence: string
  /** 不宜冷藏提醒：AVOID=不建议 / WORSE=会加速变质；null 表示可正常冷藏 */
  storageLevel?: 'AVOID' | 'WORSE' | null
  /** 正确存法建议 */
  storageTip?: string | null
}

export interface StorageAdvice {
  inFridgeWarned: boolean
  level?: 'AVOID' | 'WORSE' | null
  tip?: string | null
}

/** 查询某食材是否不宜放冰箱（手动录入时实时提示）。 */
export function fetchStorageAdvice(name: string) {
  return get<StorageAdvice>(`/fridge/storage-advice?name=${encodeURIComponent(name)}`)
}

export interface RecognizeResult {
  items: RecognizedItem[]
  /** 降级（未识别成功或服务不可用） */
  degraded: boolean
  notice?: string | null
}

/** 拍照识别冰箱食材（会员专享）。传入已上传得到的图片 URL。 */
export function recognizeFridgeImage(imageUrl: string) {
  return post<RecognizeResult>('/fridge/recognize', { imageUrl })
}

export interface ExpiryNoticeResult {
  sent: boolean
  count: number
  names: string
}

/** 发送食材临期提醒（会员专享）。 */
export function notifyFridgeExpiring() {
  return post<ExpiryNoticeResult>('/fridge/notify-expiring', {})
}
